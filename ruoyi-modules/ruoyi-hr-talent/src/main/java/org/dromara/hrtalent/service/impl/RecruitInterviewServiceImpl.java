package org.dromara.hrtalent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.hrtalent.domain.bo.recruitment.InterviewCancelBo;
import org.dromara.hrtalent.domain.bo.recruitment.InterviewFeedbackBo;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitInterviewBo;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitInterviewQueryBo;
import org.dromara.hrtalent.domain.entity.RecruitInterview;
import org.dromara.hrtalent.domain.entity.RecruitInterviewer;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitInterviewVo;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitInterviewerVo;
import org.dromara.hrtalent.enums.InterviewResultEnum;
import org.dromara.hrtalent.event.InterviewResultChangedEvent;
import org.dromara.hrtalent.mapper.RecruitInterviewMapper;
import org.dromara.hrtalent.mapper.RecruitInterviewerMapper;
import org.dromara.hrtalent.service.recruitment.IRecruitInterviewService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 面试服务实现（SPEC-P3 §2.3 / §3.3，设计文档 §7.3、§8.6、§21.13）。
 *
 * <p><b>业务规则</b>：</p>
 * <ul>
 *     <li>面试轮次 {@code round_no} 从 1 开始，支持一面/二面/扩展轮次；
 *     同一应聘记录同一轮次只允许一条有效面试记录（设计文档 §21.13）。</li>
 *     <li>面试官一人或多人逐行写入 {@code hr_recruit_interviewer}，
 *     个人意见保存在该表的 {@code feedback}，并同步 {@code feedback_status}/{@code feedback_time}；
 *     汇总结论与评分写入 {@code hr_recruit_interview} 的 {@code result}/{@code score}/{@code feedback}。</li>
 *     <li><b>改期</b>：原记录置为 {@code rescheduled} 并在备注追加改期原因（保留操作记录），
 *     同时新增一条有效面试记录（§7.3 第 7 条、§21.13）。</li>
 *     <li><b>取消</b>：记录 {@code cancel_reason}，待反馈面试官置为 {@code waived}。</li>
 *     <li><b>缺席</b>（§8.6）：由面试官提交 {@code result=absent} 表达，其余待反馈面试官置为 {@code waived}。</li>
 *     <li>提交面试结论后发布 {@link InterviewResultChangedEvent}；
 *     本服务不写应聘记录与计划任务，阶段流转由事件消费者负责（§21.6）。</li>
 * </ul>
 *
 * <p><b>安全</b>：日志只记录ID、轮次与状态，不记录面试意见正文。</p>
 *
 * @author hr-talent
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecruitInterviewServiceImpl implements IRecruitInterviewService {

    /**
     * 面试状态：待安排（时间未定）。
     */
    private static final String STATUS_PENDING = "pending";

    /**
     * 面试状态：已安排。
     */
    private static final String STATUS_SCHEDULED = "scheduled";

    /**
     * 面试状态：已完成。
     */
    private static final String STATUS_FINISHED = "finished";

    /**
     * 面试状态：已取消。
     */
    private static final String STATUS_CANCELLED = "cancelled";

    /**
     * 面试状态：已改期（保留的历史记录）。
     */
    private static final String STATUS_RESCHEDULED = "rescheduled";

    /**
     * 反馈状态：待反馈。
     */
    private static final String FEEDBACK_PENDING = "pending";

    /**
     * 反馈状态：已提交。
     */
    private static final String FEEDBACK_SUBMITTED = "submitted";

    /**
     * 反馈状态：无需反馈（取消/改期/缺席后置位）。
     */
    private static final String FEEDBACK_WAIVED = "waived";

    /**
     * 面试官角色：主面。
     */
    private static final String ROLE_LEAD = "lead";

    /**
     * 面试官角色：协同。
     */
    private static final String ROLE_ASSIST = "assist";

    /**
     * 单场面试面试官人数上限（与字段校验保持一致）。
     */
    private static final int MAX_INTERVIEWER = 20;

    /**
     * 备注列最大长度（与 DDL {@code remark varchar(500)} 一致）。
     */
    private static final int REMARK_MAX_LENGTH = 500;

    /**
     * 面试记录 Mapper。
     */
    private final RecruitInterviewMapper recruitInterviewMapper;

    /**
     * 面试参与人 Mapper。
     */
    private final RecruitInterviewerMapper recruitInterviewerMapper;

    /**
     * 领域事件发布器（面试结果变化，消费者：应聘阶段服务、计划状态服务）。
     */
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public PageResult<RecruitInterviewVo> queryPage(RecruitInterviewQueryBo bo, PageQuery pageQuery) {
        RecruitInterviewQueryBo query = bo == null ? new RecruitInterviewQueryBo() : bo;
        // 按面试官过滤：先取该面试官参与的面试ID，避免在面试表上做无法索引的拼接条件
        List<Long> scopedInterviewIds = null;
        if (query.getInterviewerUserId() != null) {
            scopedInterviewIds = recruitInterviewerMapper.selectList(new LambdaQueryWrapper<RecruitInterviewer>()
                    .eq(RecruitInterviewer::getUserId, query.getInterviewerUserId()))
                .stream()
                .map(RecruitInterviewer::getInterviewId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
            if (scopedInterviewIds.isEmpty()) {
                return PageResult.build(List.of(), 0L);
            }
        }
        LambdaQueryWrapper<RecruitInterview> wrapper = new LambdaQueryWrapper<RecruitInterview>()
            .eq(query.getApplicationId() != null, RecruitInterview::getApplicationId, query.getApplicationId())
            .eq(query.getRoundNo() != null, RecruitInterview::getRoundNo, query.getRoundNo())
            .eq(StringUtils.isNotBlank(query.getStatus()), RecruitInterview::getStatus, query.getStatus())
            .eq(StringUtils.isNotBlank(query.getResult()), RecruitInterview::getResult, query.getResult())
            .eq(StringUtils.isNotBlank(query.getMethod()), RecruitInterview::getMethod, query.getMethod())
            .in(scopedInterviewIds != null, RecruitInterview::getInterviewId, scopedInterviewIds)
            .ge(query.getScheduleTimeBegin() != null, RecruitInterview::getScheduleTime, query.getScheduleTimeBegin())
            .le(query.getScheduleTimeEnd() != null, RecruitInterview::getScheduleTime, query.getScheduleTimeEnd())
            .orderByDesc(RecruitInterview::getCreateTime);
        Page<RecruitInterviewVo> page = recruitInterviewMapper.selectVoPage(pageQuery.build(), wrapper);
        fillDetails(page.getRecords());
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    @Override
    public RecruitInterviewVo getDetail(Long interviewId) {
        loadInterview(interviewId);
        RecruitInterviewVo vo = recruitInterviewMapper.selectVoById(interviewId);
        if (vo == null) {
            throw new ServiceException("面试记录不存在或已删除");
        }
        fillDetails(List.of(vo));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long schedule(RecruitInterviewBo bo) {
        int roundNo = resolveRoundNo(bo.getRoundNo());
        if (bo.getScheduleTime() == null) {
            throw new ServiceException("计划面试时间不能为空");
        }
        // 只读校验应聘记录存在性，并带出计划任务ID（不写对方表）
        loadApplicationSummary(bo.getApplicationId());
        // 同一应聘记录同一轮次只允许一条有效面试记录（设计文档 §21.13）
        Long exists = recruitInterviewMapper.selectCount(new LambdaQueryWrapper<RecruitInterview>()
            .eq(RecruitInterview::getApplicationId, bo.getApplicationId())
            .eq(RecruitInterview::getRoundNo, roundNo)
            .in(RecruitInterview::getStatus, STATUS_PENDING, STATUS_SCHEDULED));
        if (exists != null && exists > 0) {
            throw new ServiceException("该应聘记录第 " + roundNo + " 轮已存在有效面试安排，请改期或先取消原面试");
        }
        List<Long> interviewerIds = normalizeInterviewers(bo.getInterviewerIds());
        RecruitInterview entity = new RecruitInterview();
        entity.setApplicationId(bo.getApplicationId());
        entity.setRoundNo(roundNo);
        entity.setScheduleTime(bo.getScheduleTime());
        entity.setEndTime(bo.getEndTime());
        entity.setMethod(bo.getMethod());
        entity.setLocation(bo.getLocation());
        entity.setStatus(STATUS_SCHEDULED);
        entity.setResult(InterviewResultEnum.PENDING.getCode());
        entity.setRemark(bo.getRemark());
        recruitInterviewMapper.insert(entity);
        saveInterviewers(entity.getInterviewId(), interviewerIds);
        log.info("安排面试, interviewId={}, applicationId={}, roundNo={}, interviewerCount={}",
            entity.getInterviewId(), entity.getApplicationId(), roundNo, interviewerIds.size());
        return entity.getInterviewId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long reschedule(RecruitInterviewBo bo) {
        RecruitInterview exist = loadInterview(bo.getInterviewId());
        if (STATUS_CANCELLED.equals(exist.getStatus())) {
            throw new ServiceException("已取消的面试不能改期，请重新安排");
        }
        if (STATUS_RESCHEDULED.equals(exist.getStatus())) {
            throw new ServiceException("该面试记录已改期，请对最新面试记录操作");
        }
        if (STATUS_FINISHED.equals(exist.getStatus())) {
            throw new ServiceException("已完成的面试不能改期");
        }
        LocalDateTime newTime = bo.getScheduleTime() == null ? exist.getScheduleTime() : bo.getScheduleTime();
        if (newTime == null) {
            throw new ServiceException("计划面试时间不能为空");
        }
        // 面试官：入参缺省时沿用原面试官（反馈状态重置为待反馈）
        List<Long> targetUserIds;
        if (bo.getInterviewerIds() == null) {
            List<Long> oldUserIds = listInterviewers(exist.getInterviewId()).stream()
                .map(RecruitInterviewer::getUserId)
                .toList();
            targetUserIds = normalizeInterviewers(oldUserIds.toArray(new Long[0]));
        } else {
            targetUserIds = normalizeInterviewers(bo.getInterviewerIds());
        }
        String reason = StringUtils.isBlank(bo.getChangeReason()) ? "未填写原因" : bo.getChangeReason().trim();

        RecruitInterview created = new RecruitInterview();
        created.setApplicationId(exist.getApplicationId());
        created.setRoundNo(exist.getRoundNo());
        created.setScheduleTime(newTime);
        created.setEndTime(bo.getEndTime() == null ? exist.getEndTime() : bo.getEndTime());
        created.setMethod(StringUtils.isNotBlank(bo.getMethod()) ? bo.getMethod() : exist.getMethod());
        created.setLocation(StringUtils.isNotBlank(bo.getLocation()) ? bo.getLocation() : exist.getLocation());
        created.setStatus(STATUS_SCHEDULED);
        created.setResult(InterviewResultEnum.PENDING.getCode());
        created.setRemark("改期自面试记录 #" + exist.getInterviewId());
        recruitInterviewMapper.insert(created);
        saveInterviewers(created.getInterviewId(), targetUserIds);

        // 原记录保留为已改期并追加操作留痕（§7.3 第 7 条）
        RecruitInterview oldUpdate = new RecruitInterview();
        oldUpdate.setInterviewId(exist.getInterviewId());
        oldUpdate.setStatus(STATUS_RESCHEDULED);
        oldUpdate.setRemark(appendRemark(exist.getRemark(),
            "【改期】" + reason + "，新面试记录 #" + created.getInterviewId()));
        recruitInterviewMapper.updateById(oldUpdate);
        waivePendingInterviewers(exist.getInterviewId(), null);
        log.info("面试改期, oldInterviewId={}, newInterviewId={}, roundNo={}, interviewerCount={}",
            exist.getInterviewId(), created.getInterviewId(), exist.getRoundNo(), targetUserIds.size());
        return created.getInterviewId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(InterviewCancelBo bo) {
        RecruitInterview exist = loadInterview(bo.getInterviewId());
        String reason = bo.getCancelReason() == null ? null : bo.getCancelReason().trim();
        if (StringUtils.isBlank(reason)) {
            throw new ServiceException("取消面试必须填写原因");
        }
        if (STATUS_CANCELLED.equals(exist.getStatus())) {
            throw new ServiceException("该面试已取消，无需重复取消");
        }
        if (STATUS_FINISHED.equals(exist.getStatus())) {
            throw new ServiceException("已完成的面试不能取消");
        }
        if (STATUS_RESCHEDULED.equals(exist.getStatus())) {
            throw new ServiceException("该面试记录已改期，请对最新面试记录操作");
        }
        RecruitInterview update = new RecruitInterview();
        update.setInterviewId(exist.getInterviewId());
        update.setStatus(STATUS_CANCELLED);
        update.setCancelReason(reason);
        update.setRemark(appendRemark(exist.getRemark(), "【取消】" + reason));
        recruitInterviewMapper.updateById(update);
        waivePendingInterviewers(exist.getInterviewId(), null);
        log.info("取消面试, interviewId={}, applicationId={}", exist.getInterviewId(), exist.getApplicationId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void feedback(InterviewFeedbackBo bo) {
        RecruitInterview exist = loadInterview(bo.getInterviewId());
        if (STATUS_CANCELLED.equals(exist.getStatus())) {
            throw new ServiceException("已取消的面试不能提交反馈");
        }
        if (STATUS_RESCHEDULED.equals(exist.getStatus())) {
            throw new ServiceException("该面试记录已改期，请在最新面试记录上提交反馈");
        }
        if (StringUtils.isBlank(bo.getFeedback())) {
            throw new ServiceException("面试意见不能为空");
        }
        String result = bo.getResult();
        if (StringUtils.isNotBlank(result) && InterviewResultEnum.find(result) == null) {
            throw new ServiceException("面试结果不合法，请使用字典 recruit_interview_result 的编码"
                + "（pending/pass/fail/reserve/absent）");
        }
        Long operatorId = currentUserId();
        if (operatorId == null) {
            throw new ServiceException("无法识别当前登录用户，不能提交面试反馈");
        }
        RecruitInterviewer interviewer = recruitInterviewerMapper.selectOne(new LambdaQueryWrapper<RecruitInterviewer>()
            .eq(RecruitInterviewer::getInterviewId, exist.getInterviewId())
            .eq(RecruitInterviewer::getUserId, operatorId)
            .last("limit 1"));
        if (interviewer == null) {
            throw new ServiceException("当前用户不是该面试的面试官，不能提交反馈");
        }
        LocalDateTime now = LocalDateTime.now();
        // 1) 个人意见与评分逐人保存，并同步反馈状态与反馈时间
        interviewer.setFeedback(bo.getFeedback());
        interviewer.setScore(bo.getScore());
        interviewer.setFeedbackStatus(FEEDBACK_SUBMITTED);
        interviewer.setFeedbackTime(now);
        recruitInterviewerMapper.updateById(interviewer);

        // 2) 汇总结论：结果缺省时沿用原值（安排时为 pending），评分缺省时取已反馈面试官均值
        String overallResult = StringUtils.isNotBlank(result) ? result : exist.getResult();
        BigDecimal overallScore = resolveOverallScore(exist.getInterviewId(), interviewer, bo.getOverallScore());
        RecruitInterview summary = new RecruitInterview();
        summary.setInterviewId(exist.getInterviewId());
        summary.setResult(overallResult);
        summary.setScore(overallScore);
        if (StringUtils.isNotBlank(bo.getConclusion())) {
            summary.setFeedback(bo.getConclusion());
        }
        if (StringUtils.isNotBlank(result)) {
            summary.setFeedbackTime(now);
            if (!InterviewResultEnum.PENDING.getCode().equals(result)) {
                summary.setStatus(STATUS_FINISHED);
            }
        }
        recruitInterviewMapper.updateById(summary);

        // 3) 缺席（§8.6）：其余待反馈面试官无需再反馈
        if (InterviewResultEnum.ABSENT.getCode().equals(overallResult)) {
            waivePendingInterviewers(exist.getInterviewId(), operatorId);
        }

        // 4) 提交面试结果后发布领域事件（消费者：应聘阶段服务、计划状态服务）
        if (StringUtils.isNotBlank(result)) {
            RecruitInterviewVo application = loadApplicationSummary(exist.getApplicationId());
            eventPublisher.publishEvent(new InterviewResultChangedEvent(
                exist.getInterviewId(),
                exist.getApplicationId(),
                application.getPlanItemId(),
                exist.getRoundNo(),
                overallResult,
                operatorId));
        }
        log.info("提交面试反馈, interviewId={}, applicationId={}, operatorId={}, overallResult={}",
            exist.getInterviewId(), exist.getApplicationId(), operatorId, overallResult);
    }

    @Override
    public List<RecruitInterviewVo> myTodos() {
        Long userId = currentUserId();
        if (userId == null) {
            throw new ServiceException("无法识别当前登录用户，不能查询面试待办");
        }
        List<Long> interviewIds = recruitInterviewerMapper.selectList(new LambdaQueryWrapper<RecruitInterviewer>()
                .eq(RecruitInterviewer::getUserId, userId)
                .eq(RecruitInterviewer::getFeedbackStatus, FEEDBACK_PENDING))
            .stream()
            .map(RecruitInterviewer::getInterviewId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (interviewIds.isEmpty()) {
            return List.of();
        }
        // 已取消/已改期的记录不再出现在待办中
        List<RecruitInterviewVo> records = recruitInterviewMapper.selectVoList(new LambdaQueryWrapper<RecruitInterview>()
            .in(RecruitInterview::getInterviewId, interviewIds)
            .in(RecruitInterview::getStatus, STATUS_PENDING, STATUS_SCHEDULED)
            .orderByAsc(RecruitInterview::getScheduleTime));
        fillDetails(records);
        return records;
    }

    /* ------------------------------------------------------------------ 内部方法 ------------------------------------------------------------------ */

    /**
     * 加载面试记录，不存在时抛中文提示。
     *
     * @param interviewId 面试记录ID
     * @return 面试实体
     */
    private RecruitInterview loadInterview(Long interviewId) {
        if (interviewId == null) {
            throw new ServiceException("面试记录ID不能为空");
        }
        RecruitInterview entity = recruitInterviewMapper.selectById(interviewId);
        if (entity == null) {
            throw new ServiceException("面试记录不存在或已删除");
        }
        return entity;
    }

    /**
     * 只读加载应聘记录摘要（候选人姓名、岗位、计划任务ID、当前阶段）。
     *
     * @param applicationId 应聘记录ID
     * @return 摘要对象，非空
     */
    private RecruitInterviewVo loadApplicationSummary(Long applicationId) {
        if (applicationId == null) {
            throw new ServiceException("应聘记录ID不能为空");
        }
        List<RecruitInterviewVo> summaries = recruitInterviewMapper.selectApplicationSummaries(List.of(applicationId));
        if (summaries == null || summaries.isEmpty()) {
            throw new ServiceException("应聘记录不存在或已删除");
        }
        return summaries.get(0);
    }

    /**
     * 校验并规范化面试轮次。
     *
     * @param roundNo 轮次
     * @return 规范化后的轮次
     */
    private int resolveRoundNo(Integer roundNo) {
        if (roundNo == null) {
            throw new ServiceException("面试轮次不能为空");
        }
        if (roundNo < 1) {
            throw new ServiceException("面试轮次必须从 1 开始");
        }
        return roundNo;
    }

    /**
     * 去重并校验面试官用户ID数组。
     *
     * @param interviewerIds 面试官用户ID数组
     * @return 去重后的面试官用户ID列表
     */
    private List<Long> normalizeInterviewers(Long[] interviewerIds) {
        if (interviewerIds == null || interviewerIds.length == 0) {
            throw new ServiceException("请至少指定一名面试官");
        }
        LinkedHashSet<Long> distinct = new LinkedHashSet<>();
        for (Long userId : interviewerIds) {
            if (userId != null) {
                distinct.add(userId);
            }
        }
        if (distinct.isEmpty()) {
            throw new ServiceException("请至少指定一名面试官");
        }
        if (distinct.size() > MAX_INTERVIEWER) {
            throw new ServiceException("面试官最多 " + MAX_INTERVIEWER + " 人");
        }
        return new ArrayList<>(distinct);
    }

    /**
     * 逐行写入面试官：顺序第一人为主面（lead），其余为协同（assist），反馈状态均为待反馈。
     *
     * @param interviewId    面试记录ID
     * @param interviewerIds 面试官用户ID列表
     */
    private void saveInterviewers(Long interviewId, List<Long> interviewerIds) {
        int index = 0;
        for (Long userId : interviewerIds) {
            RecruitInterviewer row = new RecruitInterviewer();
            row.setInterviewId(interviewId);
            row.setUserId(userId);
            row.setInterviewerRole(index == 0 ? ROLE_LEAD : ROLE_ASSIST);
            row.setFeedbackStatus(FEEDBACK_PENDING);
            recruitInterviewerMapper.insert(row);
            index++;
        }
    }

    /**
     * 读取某场面试的全部面试官，按主键升序（与写入顺序一致）。
     *
     * @param interviewId 面试记录ID
     * @return 面试官列表
     */
    private List<RecruitInterviewer> listInterviewers(Long interviewId) {
        return recruitInterviewerMapper.selectList(new LambdaQueryWrapper<RecruitInterviewer>()
            .eq(RecruitInterviewer::getInterviewId, interviewId)
            .orderByAsc(RecruitInterviewer::getInterviewerId));
    }

    /**
     * 将某场面试中仍待反馈的面试官置为无需反馈。
     *
     * @param interviewId  面试记录ID
     * @param exceptUserId 需要排除的面试官用户ID（提交缺席结论者本人），可为 null
     */
    private void waivePendingInterviewers(Long interviewId, Long exceptUserId) {
        LambdaUpdateWrapper<RecruitInterviewer> wrapper = new LambdaUpdateWrapper<RecruitInterviewer>()
            .eq(RecruitInterviewer::getInterviewId, interviewId)
            .eq(RecruitInterviewer::getFeedbackStatus, FEEDBACK_PENDING)
            .set(RecruitInterviewer::getFeedbackStatus, FEEDBACK_WAIVED);
        if (exceptUserId != null) {
            wrapper.ne(RecruitInterviewer::getUserId, exceptUserId);
        }
        recruitInterviewerMapper.update(null, wrapper);
    }

    /**
     * 计算汇总评分：优先取入参，其次取已反馈面试官评分的平均值（保留两位小数）。
     *
     * @param interviewId 面试记录ID
     * @param current     当前提交反馈的面试官（内存中的最新值优先于库内旧值）
     * @param provided    入参汇总评分，可为 null
     * @return 汇总评分，无任何评分时返回 null
     */
    private BigDecimal resolveOverallScore(Long interviewId, RecruitInterviewer current, BigDecimal provided) {
        if (provided != null) {
            return provided;
        }
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (RecruitInterviewer row : listInterviewers(interviewId)) {
            BigDecimal score = Objects.equals(row.getInterviewerId(), current.getInterviewerId())
                ? current.getScore()
                : row.getScore();
            if (score != null) {
                sum = sum.add(score);
                count++;
            }
        }
        if (count == 0) {
            return null;
        }
        return sum.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    /**
     * 追加备注留痕，并按列长度截断（保留最新内容）。
     *
     * @param remark 原备注，可为空
     * @param append 追加内容
     * @return 追加后的备注
     */
    private String appendRemark(String remark, String append) {
        String merged = StringUtils.isBlank(remark) ? append : remark + "；" + append;
        if (merged.length() > REMARK_MAX_LENGTH) {
            merged = merged.substring(merged.length() - REMARK_MAX_LENGTH);
        }
        return merged;
    }

    /**
     * 取当前登录用户ID；无登录态（如定时任务、单测上下文）时返回 null。
     *
     * @return 用户ID或 null
     */
    private Long currentUserId() {
        try {
            return LoginHelper.getUserId();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 批量补全面试官明细与候选人摘要（列表与详情共用）。
     *
     * @param records 面试记录列表，允许为空
     */
    private void fillDetails(List<RecruitInterviewVo> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> interviewIds = records.stream()
            .map(RecruitInterviewVo::getInterviewId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        Map<Long, List<RecruitInterviewerVo>> grouped = new HashMap<>();
        if (!interviewIds.isEmpty()) {
            List<RecruitInterviewer> rows = recruitInterviewerMapper.selectList(new LambdaQueryWrapper<RecruitInterviewer>()
                .in(RecruitInterviewer::getInterviewId, interviewIds)
                .orderByAsc(RecruitInterviewer::getInterviewerId));
            for (RecruitInterviewer row : rows) {
                RecruitInterviewerVo vo = MapstructUtils.convert(row, RecruitInterviewerVo.class);
                if (vo == null || row.getInterviewId() == null) {
                    continue;
                }
                grouped.computeIfAbsent(row.getInterviewId(), key -> new ArrayList<>()).add(vo);
            }
        }
        List<Long> applicationIds = records.stream()
            .map(RecruitInterviewVo::getApplicationId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        Map<Long, RecruitInterviewVo> summaryMap = new HashMap<>();
        if (!applicationIds.isEmpty()) {
            List<RecruitInterviewVo> summaries = recruitInterviewMapper.selectApplicationSummaries(applicationIds);
            if (summaries != null) {
                for (RecruitInterviewVo summary : summaries) {
                    if (summary.getApplicationId() != null) {
                        summaryMap.put(summary.getApplicationId(), summary);
                    }
                }
            }
        }
        for (RecruitInterviewVo vo : records) {
            List<RecruitInterviewerVo> interviewers = grouped.getOrDefault(vo.getInterviewId(), List.of());
            vo.setInterviewers(interviewers);
            vo.setInterviewerIds(interviewers.stream()
                .map(RecruitInterviewerVo::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toArray(Long[]::new));
            RecruitInterviewVo summary = summaryMap.get(vo.getApplicationId());
            if (summary != null) {
                vo.setApplicationNo(summary.getApplicationNo());
                vo.setCandidateName(summary.getCandidateName());
                vo.setJobName(summary.getJobName());
                vo.setPlanItemId(summary.getPlanItemId());
                vo.setCurrentStage(summary.getCurrentStage());
            }
        }
    }

}
