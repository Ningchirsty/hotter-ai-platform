package org.dromara.hrtalent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.SpringUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.hrtalent.domainservice.ApplicationStageDomainService;
import org.dromara.hrtalent.domainservice.TalentScopeDomainService;
import org.dromara.hrtalent.domain.bo.recruitment.ApplicationTransferBo;
import org.dromara.hrtalent.domain.bo.recruitment.ApplicationTransitionBo;
import org.dromara.hrtalent.domain.bo.recruitment.ArrivalRegisterBo;
import org.dromara.hrtalent.domain.bo.recruitment.NoArrivalBo;
import org.dromara.hrtalent.domain.bo.recruitment.OfferRegisterBo;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitApplicationBo;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitApplicationQueryBo;
import org.dromara.hrtalent.domain.entity.RecruitApplication;
import org.dromara.hrtalent.domain.entity.RecruitBackground;
import org.dromara.hrtalent.domain.entity.RecruitInterview;
import org.dromara.hrtalent.domain.entity.RecruitJob;
import org.dromara.hrtalent.domain.entity.RecruitPlanApplicationRel;
import org.dromara.hrtalent.domain.entity.RecruitPlanItem;
import org.dromara.hrtalent.domain.entity.RecruitStageLog;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitApplicationVo;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitStageLogVo;
import org.dromara.hrtalent.enums.ApplicationResultEnum;
import org.dromara.hrtalent.enums.CandidateStageEnum;
import org.dromara.hrtalent.event.ApplicationStageChangedEvent;
import org.dromara.hrtalent.event.CandidateArrivedEvent;
import org.dromara.hrtalent.mapper.RecruitApplicationMapper;
import org.dromara.hrtalent.mapper.RecruitBackgroundMapper;
import org.dromara.hrtalent.mapper.RecruitInterviewMapper;
import org.dromara.hrtalent.mapper.RecruitJobMapper;
import org.dromara.hrtalent.mapper.RecruitPlanApplicationRelMapper;
import org.dromara.hrtalent.mapper.RecruitPlanItemMapper;
import org.dromara.hrtalent.mapper.RecruitStageLogMapper;
import org.dromara.hrtalent.mapper.TalentProfileMapper;
import org.dromara.hrtalent.service.recruitment.IPlanItemStatusService;
import org.dromara.hrtalent.service.recruitment.IRecruitApplicationService;
import org.dromara.hrtalent.support.HrTalentErrorCode;
import org.dromara.hrtalent.support.RecruitBusinessNoGenerator;
import org.dromara.hrtalent.support.SensitiveAuditRecorder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 应聘记录与阶段流转服务实现（SPEC-P3 §2.2 / §3.2 / 设计文档 §7.2、§7.4、§7.5、§9.4、§9.6、§21.7）。
 *
 * <p><b>阶段流转</b>（{@link #transition}）：规则全部委托 {@link ApplicationStageDomainService} 纯函数判定；
 * 本类只负责把事实（面试安排、面试结论、背调结论、邀约结果、计划报到日期）读出来，
 * 并在<b>同一事务</b>内完成「更新应聘记录 + 追加阶段历史」。</p>
 *
 * <p><b>报到事务</b>（{@link #registerArrival}）：同一事务内完成「更新应聘记录 → 累加关联计划任务
 * {@code credited_arrival_qty} → 调用 {@link IPlanItemStatusService#refreshItemStatus(Long)}」，
 * 绝不自行拼接计划任务状态值（§11.1）。一个到岗结果只能计入一条有效月度任务（§9.4、§9.5）。</p>
 *
 * <p><b>跨域只读</b>：进入一面/二面/待背调/待录用所需的事实来自 {@code hr_recruit_interview} 与
 * {@code hr_recruit_background}，本类<b>只读</b>这两张表（复用面试域与背调域的 Mapper），不写对方任何一列。</p>
 *
 * <p><b>快照原则</b>：不写入 {@code hr_talent_profile}，人才状态更新由人才主档服务消费事件完成（§7.6.1、§7.6.4）。</p>
 *
 * <p><b>口径一：结果流转不改变阶段</b>（主控 P3 裁决）。「淘汰 / 候选人放弃 / 暂缓 / 人才保留」四类转入
 * <b>只改 {@code current_status}</b>，{@code current_stage} 保持不变，阶段历史中的 {@code to_stage}
 * 记为与 {@code from_stage} 相同。原因是字典 {@code recruit_candidate_stage} 只有
 * {@code new/resume_review/invite/first_interview/second_interview/background/offer/pending_arrival/arrived}
 * 九个值，没有这四类结果的编码；强行落码会与字典和前端不一致，变化改由
 * {@code current_status} 与阶段历史的 {@code result} 字段表达。</p>
 *
 * <p><b>口径二：未报到暂用 {@code withdrawn}</b>。字典 {@code recruit_application_result}
 * 只有 {@code processing/passed/rejected/withdrawn/paused/talent_pool}，<b>没有</b>「未报到」专用编码，
 * 因此 {@link #registerNoArrival} 按 §7.6.4「候选人主动放弃」的口径权宜置为 {@code withdrawn}
 * 并写入 {@code no_arrival_reason}。<b>这是已知字典缺口</b>：若后续需要精确区分「未报到」与「主动放弃」，
 * 需先补字典编码再调整此处，禁止在代码中自行造码。</p>
 *
 * @author hr-talent
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecruitApplicationServiceImpl implements IRecruitApplicationService {

    /**
     * 面试状态：已取消（不参与「是否存在面试安排」的判定）。
     */
    private static final String INTERVIEW_STATUS_CANCELLED = "cancelled";

    /**
     * 计划计入关系类型：来源计入。
     */
    private static final String REL_TYPE_PLAN = "plan";

    /**
     * 计划计入关系类型：到岗计入。
     */
    private static final String REL_TYPE_ARRIVAL = "arrival";

    /**
     * 计入标志：已计入。
     */
    private static final String CREDITED_YES = "1";

    /**
     * 计入标志：未计入。
     */
    private static final String CREDITED_NO = "0";

    /**
     * 应聘记录 Mapper。
     */
    private final RecruitApplicationMapper recruitApplicationMapper;

    /**
     * 阶段历史 Mapper（只追加）。
     */
    private final RecruitStageLogMapper recruitStageLogMapper;

    /**
     * 岗位执行项 Mapper（只读岗位与计划任务归属）。
     */
    private final RecruitJobMapper recruitJobMapper;

    /**
     * 月度计划任务 Mapper（累加到岗人数）。
     */
    private final RecruitPlanItemMapper recruitPlanItemMapper;

    /**
     * 计划任务与应聘记录关联 Mapper（表达「计入哪一条月度任务」）。
     */
    private final RecruitPlanApplicationRelMapper recruitPlanApplicationRelMapper;

    /**
     * 面试记录 Mapper（<b>只读</b>，用于阶段前置校验）。
     */
    private final RecruitInterviewMapper recruitInterviewMapper;

    /**
     * 背调记录 Mapper（<b>只读</b>，用于阶段前置校验）。
     */
    private final RecruitBackgroundMapper recruitBackgroundMapper;

    /**
     * 阶段机领域服务（纯规则）。
     */
    private final ApplicationStageDomainService stageDomainService;

    /**
     * 人才可见范围领域服务（列表授权条件的唯一来源，§11.1、§21.14）。
     */
    private final TalentScopeDomainService talentScopeDomainService;

    /**
     * 人才主档 Mapper（<b>只读</b>）：仅用于按可见范围解析人才ID集合，不写人才任何一列。
     */
    private final TalentProfileMapper talentProfileMapper;

    /**
     * 月度计划任务状态重算服务（报到时刷新状态，禁止自行拼接状态值）。
     */
    private final IPlanItemStatusService planItemStatusService;

    /**
     * 业务编号生成器（应聘编号）。
     */
    private final RecruitBusinessNoGenerator businessNoGenerator;

    /**
     * 敏感操作审计记录器（管理员例外跳转留痕）。
     */
    private final SensitiveAuditRecorder sensitiveAuditRecorder;

    /* ------------------------------------------------------------------ 查询 ------------------------------------------------------------------ */

    @Override
    public PageResult<RecruitApplicationVo> queryPage(RecruitApplicationQueryBo bo, PageQuery pageQuery) {
        RecruitApplicationQueryBo query = bo == null ? new RecruitApplicationQueryBo() : bo;
        // 可见范围硬约束（§11.1、§21.14）：条件一律由 TalentScopeDomainService 统一生成，
        // 本类不自行实现任何授权规则；超管不受范围限制，其余用户先解析可见人才ID再过滤
        TalentScopeDomainService.ScopeCondition scope = talentScopeDomainService.currentScope();
        List<Long> visibleTalentIds = null;
        if (!scope.unlimited()) {
            // 领域服务的 wrapper 必须“原样”交给只读 Mapper 的 ${ew.customSqlSegment} 消费，
            // 禁止把它 getCustomSqlSegment() 的结果搬到别的 wrapper 上（会重复 WHERE 且参数错位）
            visibleTalentIds = talentProfileMapper.selectVisibleTalentIds(
                talentScopeDomainService.visibleTalentWrapper(scope));
            if (visibleTalentIds.isEmpty()) {
                // 无任何可见人才：直接返回空页，避免退化为全量查询
                return PageResult.build(List.of(), 0L);
            }
        }
        LambdaQueryWrapper<RecruitApplication> wrapper = new LambdaQueryWrapper<RecruitApplication>()
            .like(StringUtils.isNotBlank(query.getApplicationNo()), RecruitApplication::getApplicationNo, query.getApplicationNo())
            .eq(query.getTalentId() != null, RecruitApplication::getTalentId, query.getTalentId())
            .eq(query.getJobId() != null, RecruitApplication::getJobId, query.getJobId())
            .eq(StringUtils.isNotBlank(query.getCurrentStage()), RecruitApplication::getCurrentStage, query.getCurrentStage())
            .eq(StringUtils.isNotBlank(query.getCurrentStatus()), RecruitApplication::getCurrentStatus, query.getCurrentStatus())
            .eq(query.getRecruiterId() != null, RecruitApplication::getRecruiterId, query.getRecruiterId())
            .eq(query.getSourceChannelId() != null, RecruitApplication::getSourceChannelId, query.getSourceChannelId())
            .eq(StringUtils.isNotBlank(query.getSourceType()), RecruitApplication::getSourceType, query.getSourceType())
            .ge(query.getContactDateBegin() != null, RecruitApplication::getContactDate, query.getContactDateBegin())
            .le(query.getContactDateEnd() != null, RecruitApplication::getContactDate, query.getContactDateEnd())
            .ge(query.getApplyTimeBegin() != null, RecruitApplication::getApplyTime, query.getApplyTimeBegin())
            .le(query.getApplyTimeEnd() != null, RecruitApplication::getApplyTime, query.getApplyTimeEnd())
            .ge(query.getNextFollowTimeBegin() != null, RecruitApplication::getNextFollowTime, query.getNextFollowTimeBegin())
            .le(query.getNextFollowTimeEnd() != null, RecruitApplication::getNextFollowTime, query.getNextFollowTimeEnd())
            .in(visibleTalentIds != null, RecruitApplication::getTalentId, visibleTalentIds)
            .orderByDesc(RecruitApplication::getCreateTime);
        Page<RecruitApplicationVo> page = recruitApplicationMapper.selectVoPage(pageQuery.build(), wrapper);
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    @Override
    public RecruitApplicationVo getDetail(Long applicationId) {
        RecruitApplicationVo vo = recruitApplicationMapper.selectVoById(loadApplication(applicationId).getApplicationId());
        if (vo == null) {
            throw new ServiceException("应聘记录不存在或已删除");
        }
        return vo;
    }

    @Override
    public List<RecruitStageLogVo> listStageLogs(Long applicationId) {
        loadApplication(applicationId);
        return recruitStageLogMapper.selectVoList(new LambdaQueryWrapper<RecruitStageLog>()
            .eq(RecruitStageLog::getApplicationId, applicationId)
            .orderByAsc(RecruitStageLog::getOperateTime)
            .orderByAsc(RecruitStageLog::getLogId));
    }

    /* ------------------------------------------------------------------ 新增 / 编辑 ------------------------------------------------------------------ */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(RecruitApplicationBo bo) {
        if (bo == null || bo.getTalentId() == null) {
            throw new ServiceException("人才主档不能为空");
        }
        if (bo.getJobId() == null) {
            throw new ServiceException("应聘岗位不能为空");
        }
        RecruitJob job = recruitJobMapper.selectById(bo.getJobId());
        if (job == null) {
            throw new ServiceException("应聘岗位不存在或已删除");
        }
        validateSalary(bo.getExpectedSalaryMin(), bo.getExpectedSalaryMax());
        LocalDateTime now = LocalDateTime.now();
        RecruitApplication entity = MapstructUtils.convert(bo, RecruitApplication.class);
        if (entity == null) {
            entity = new RecruitApplication();
        }
        // 服务端权威字段：主键、编号、阶段、结果与版本不接受前端写入
        entity.setApplicationId(null);
        entity.setApplicationNo(businessNoGenerator.nextApplicationNo());
        entity.setCurrentStage(CandidateStageEnum.NEW.getCode());
        entity.setCurrentStatus(ApplicationResultEnum.PROCESSING.getCode());
        entity.setApplyTime(bo.getApplyTime() == null ? now : bo.getApplyTime());
        entity.setStageEnterTime(now);
        entity.setRecruiterId(bo.getRecruiterId() == null ? job.getOwnerId() : bo.getRecruiterId());
        entity.setVersion(0);
        try {
            recruitApplicationMapper.insert(entity);
        } catch (DuplicateKeyException e) {
            log.warn("应聘编号生成冲突, applicationNo={}", entity.getApplicationNo());
            throw new ServiceException("应聘编号生成冲突，请重试");
        }
        Long operatorId = currentUserId();
        // 1. 首条阶段历史：从「无」进入「新建」
        RecruitStageLog stageLog = new RecruitStageLog();
        stageLog.setApplicationId(entity.getApplicationId());
        stageLog.setFromStage(null);
        stageLog.setToStage(CandidateStageEnum.NEW.getCode());
        stageLog.setActionType(ApplicationStageDomainService.ACTION_MOVE);
        stageLog.setResult(ApplicationResultEnum.PROCESSING.getCode());
        stageLog.setComment("创建应聘记录");
        stageLog.setOperatorId(operatorId);
        stageLog.setOperateTime(now);
        recruitStageLogMapper.insert(stageLog);
        // 2. 计划任务计入关系：用于后续跨月结转与到岗归属（§9.2、§9.4）
        if (job.getPlanItemId() != null) {
            insertPlanRel(entity.getApplicationId(), job.getPlanItemId(), REL_TYPE_PLAN, CREDITED_NO, now, operatorId);
        }
        log.info("新增应聘记录, applicationId={}, jobId={}, talentId={}, planItemId={}",
            entity.getApplicationId(), job.getJobId(), entity.getTalentId(), job.getPlanItemId());
        return entity.getApplicationId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(RecruitApplicationBo bo) {
        RecruitApplication exist = loadApplication(bo.getApplicationId());
        validateSalary(
            bo.getExpectedSalaryMin() == null ? exist.getExpectedSalaryMin() : bo.getExpectedSalaryMin(),
            bo.getExpectedSalaryMax() == null ? exist.getExpectedSalaryMax() : bo.getExpectedSalaryMax());
        RecruitApplication update = MapstructUtils.convert(bo, RecruitApplication.class);
        if (update == null) {
            update = new RecruitApplication();
        }
        // 白名单更新：阶段/结果/岗位/主档/编号/报到信息一律不得由编辑接口改写
        update.setApplicationId(exist.getApplicationId());
        update.setTalentId(null);
        update.setJobId(null);
        update.setApplicationNo(null);
        update.setCurrentStage(null);
        update.setCurrentStatus(null);
        update.setStageEnterTime(null);
        update.setOfferResult(null);
        update.setOfferDate(null);
        update.setPlanArrivalDate(null);
        update.setArrivalDate(null);
        update.setNoArrivalReason(null);
        update.setRejectReason(null);
        update.setVersion(bo.getVersion());
        int rows = recruitApplicationMapper.updateById(update);
        if (rows == 0) {
            log.warn("应聘记录更新版本冲突, applicationId={}, version={}", exist.getApplicationId(), bo.getVersion());
            throw new ServiceException("应聘记录已被他人修改，请刷新后重试");
        }
        log.info("更新应聘记录, applicationId={}, version={}", exist.getApplicationId(), bo.getVersion());
    }

    /* ------------------------------------------------------------------ 阶段流转 ------------------------------------------------------------------ */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transition(Long applicationId, ApplicationTransitionBo bo) {
        RecruitApplication application = loadApplication(applicationId);
        stageDomainService.validateNotEnded(application.getCurrentStatus());
        CandidateStageEnum fromStage = stageDomainService.requireStage(application.getCurrentStage());
        // §9.6 第 2 步：校验当前阶段与请求前置阶段一致
        if (StringUtils.isNotBlank(bo.getFromStage()) && !bo.getFromStage().equals(application.getCurrentStage())) {
            throw new ServiceException("应聘记录当前阶段已变更为「" + fromStage.getDesc() + "」，请刷新后重试");
        }
        boolean hasStage = StringUtils.isNotBlank(bo.getToStage());
        boolean hasResult = StringUtils.isNotBlank(bo.getResult());
        if (hasStage == hasResult) {
            throw new ServiceException("目标阶段与目标结果必须二选一");
        }
        boolean override = Boolean.TRUE.equals(bo.getOverride());
        ApplicationStageDomainService.TransitionPlan plan;
        if (hasStage) {
            CandidateStageEnum toStage = stageDomainService.requireStage(bo.getToStage());
            // 允许在本接口一并登记邀约结果与计划报到日期，减少一次往返（§7.2 第 5 条）
            String offerResult = StringUtils.isNotBlank(bo.getOfferResult()) ? bo.getOfferResult() : application.getOfferResult();
            LocalDate planArrivalDate = bo.getPlanArrivalDate() != null ? bo.getPlanArrivalDate() : application.getPlanArrivalDate();
            ApplicationStageDomainService.StageFacts facts = loadFacts(application, offerResult, planArrivalDate);
            plan = stageDomainService.planMove(fromStage, toStage, override, bo.getOverrideReason(), facts);
        } else {
            plan = stageDomainService.planOutcome(fromStage,
                stageDomainService.requireResult(bo.getResult()), bo.getReasonCode(), bo.getComment());
        }
        LocalDateTime now = LocalDateTime.now();
        RecruitApplication update = new RecruitApplication();
        update.setApplicationId(applicationId);
        update.setVersion(bo.getVersion());
        update.setCurrentStage(plan.toStage().getCode());
        update.setCurrentStatus(plan.result().getCode());
        update.setStageEnterTime(now);
        if (bo.getNextFollowTime() != null) {
            update.setNextFollowTime(bo.getNextFollowTime());
        }
        if (StringUtils.isNotBlank(bo.getOfferResult())) {
            update.setOfferResult(bo.getOfferResult());
            update.setOfferDate(application.getOfferDate() == null ? LocalDate.now() : application.getOfferDate());
        }
        if (bo.getPlanArrivalDate() != null) {
            update.setPlanArrivalDate(bo.getPlanArrivalDate());
        }
        // 淘汰 / 放弃时把原因落到应聘记录，便于列表直接展示
        if (ApplicationResultEnum.REJECTED == plan.result() || ApplicationResultEnum.WITHDRAWN == plan.result()) {
            update.setRejectReason(StringUtils.isNotBlank(bo.getComment()) ? bo.getComment() : bo.getReasonCode());
        }
        int rows = recruitApplicationMapper.updateById(update);
        if (rows == 0) {
            log.warn("应聘阶段流转版本冲突, applicationId={}, version={}", applicationId, bo.getVersion());
            throw new ServiceException("应聘记录已被他人修改，请刷新后重试");
        }
        // 阶段流转与历史写入必须在同一事务内（§21.7）
        insertStageLog(applicationId, plan.fromStage().getCode(), plan.toStage().getCode(), plan.actionType(),
            plan.result().getCode(), bo.getReasonCode(), bo.getComment(), bo.getNextFollowTime(), now);
        if (override) {
            // §7.2 第 6 条：管理员例外跳转必须写审计
            sensitiveAuditRecorder.record(SensitiveAuditRecorder.EVENT_STAGE_OVERRIDE,
                SensitiveAuditRecorder.BIZ_APPLICATION, applicationId, bo.getOverrideReason(),
                SensitiveAuditRecorder.RESULT_SUCCESS);
        }
        publishAfterCommit(new ApplicationStageChangedEvent(applicationId, application.getTalentId(),
            application.getJobId(), resolvePlanItemId(application), plan.fromStage().getCode(),
            plan.toStage().getCode(), plan.actionType(), currentUserId()));
        log.info("应聘阶段流转完成, applicationId={}, from={}, to={}, action={}, result={}, override={}",
            applicationId, plan.fromStage().getCode(), plan.toStage().getCode(), plan.actionType(),
            plan.result().getCode(), override);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transfer(Long applicationId, ApplicationTransferBo bo) {
        RecruitApplication application = loadApplication(applicationId);
        stageDomainService.validateNotEnded(application.getCurrentStatus());
        if (bo.getRecruiterId() == null && bo.getJobId() == null) {
            throw new ServiceException("请至少指定新的招聘负责人或新的应聘岗位");
        }
        LocalDateTime now = LocalDateTime.now();
        Long operatorId = currentUserId();
        RecruitApplication update = new RecruitApplication();
        update.setApplicationId(applicationId);
        update.setVersion(bo.getVersion());
        if (bo.getRecruiterId() != null) {
            update.setRecruiterId(bo.getRecruiterId());
        }
        Long planItemId = resolvePlanItemId(application);
        if (bo.getJobId() != null && !bo.getJobId().equals(application.getJobId())) {
            RecruitJob job = recruitJobMapper.selectById(bo.getJobId());
            if (job == null) {
                throw new ServiceException("目标岗位不存在或已删除");
            }
            // 已计入到岗统计的应聘记录不能换岗，避免一个到岗结果被计入两条月度任务（§9.4）
            if (countCreditedRel(applicationId) > 0) {
                throw new ServiceException("该应聘记录已计入到岗统计，不能更换岗位");
            }
            update.setJobId(bo.getJobId());
            // 关闭旧的有效计入关系，并按新岗位重新建立
            recruitPlanApplicationRelMapper.update(null, new LambdaUpdateWrapper<RecruitPlanApplicationRel>()
                .eq(RecruitPlanApplicationRel::getApplicationId, applicationId)
                .eq(RecruitPlanApplicationRel::getCreditedFlag, CREDITED_NO)
                .isNull(RecruitPlanApplicationRel::getEffectiveEnd)
                .set(RecruitPlanApplicationRel::getEffectiveEnd, now));
            if (job.getPlanItemId() != null) {
                insertPlanRel(applicationId, job.getPlanItemId(), REL_TYPE_PLAN, CREDITED_NO, now, operatorId);
                planItemId = job.getPlanItemId();
            } else {
                planItemId = null;
            }
        }
        int rows = recruitApplicationMapper.updateById(update);
        if (rows == 0) {
            log.warn("应聘记录转移版本冲突, applicationId={}, version={}", applicationId, bo.getVersion());
            throw new ServiceException("应聘记录已被他人修改，请刷新后重试");
        }
        // 转移不是阶段变化，但仍留一条历史，保证「谁在何时把候选人转给了谁」可追溯（§8.5）
        insertStageLog(applicationId, application.getCurrentStage(), application.getCurrentStage(), "transfer",
            application.getCurrentStatus(), null, bo.getReason(), null, now);
        publishAfterCommit(new ApplicationStageChangedEvent(applicationId, application.getTalentId(),
            update.getJobId() == null ? application.getJobId() : update.getJobId(), planItemId,
            application.getCurrentStage(), application.getCurrentStage(), "transfer", operatorId));
        log.info("应聘记录转移完成, applicationId={}, jobChanged={}, recruiterChanged={}",
            applicationId, update.getJobId() != null, update.getRecruiterId() != null);
    }

    /* ------------------------------------------------------------------ 邀约 / 报到 ------------------------------------------------------------------ */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void registerOffer(Long applicationId, OfferRegisterBo bo) {
        RecruitApplication application = loadApplication(applicationId);
        stageDomainService.validateNotEnded(application.getCurrentStatus());
        LocalDateTime now = LocalDateTime.now();
        RecruitApplication update = new RecruitApplication();
        update.setApplicationId(applicationId);
        update.setVersion(bo.getVersion());
        update.setOfferResult(bo.getOfferResult());
        update.setOfferDate(bo.getOfferDate() == null ? LocalDate.now() : bo.getOfferDate());
        update.setPlanArrivalDate(bo.getPlanArrivalDate());
        if (StringUtils.isNotBlank(bo.getRemark())) {
            update.setRemark(bo.getRemark());
        }
        int rows = recruitApplicationMapper.updateById(update);
        if (rows == 0) {
            log.warn("邀约登记版本冲突, applicationId={}, version={}", applicationId, bo.getVersion());
            throw new ServiceException("应聘记录已被他人修改，请刷新后重试");
        }
        insertStageLog(applicationId, application.getCurrentStage(), application.getCurrentStage(), "offer",
            application.getCurrentStatus(), null,
            "登记邀约结果：" + bo.getOfferResult() + "，计划报到日期：" + bo.getPlanArrivalDate(), null, now);
        log.info("邀约登记完成, applicationId={}, offerResult={}, planArrivalDate={}",
            applicationId, bo.getOfferResult(), bo.getPlanArrivalDate());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void registerArrival(Long applicationId, ArrivalRegisterBo bo) {
        RecruitApplication application = loadApplication(applicationId);
        if (application.getArrivalDate() != null) {
            throw new ServiceException("该应聘记录已登记实际报到，不能重复登记");
        }
        stageDomainService.validateNotEnded(application.getCurrentStatus());
        // 报到必须建立在「已记录邀约结果与计划报到日期」之上（§7.2 第 5 条）；未流转到待报到时按等价资料放行
        boolean inPendingArrival = CandidateStageEnum.PENDING_ARRIVAL.getCode().equals(application.getCurrentStage());
        boolean offerRegistered = StringUtils.isNotBlank(application.getOfferResult()) && application.getPlanArrivalDate() != null;
        if (!inPendingArrival && !offerRegistered) {
            throw new ServiceException("登记实际报到前必须记录邀约结果与计划报到日期");
        }
        LocalDateTime now = LocalDateTime.now();
        Long operatorId = currentUserId();
        // 1. 更新应聘记录（乐观锁）
        RecruitApplication update = new RecruitApplication();
        update.setApplicationId(applicationId);
        update.setVersion(bo.getVersion());
        update.setCurrentStage(CandidateStageEnum.ARRIVED.getCode());
        update.setCurrentStatus(ApplicationResultEnum.PASSED.getCode());
        update.setArrivalDate(bo.getArrivalDate());
        update.setStageEnterTime(now);
        if (StringUtils.isNotBlank(bo.getRemark())) {
            update.setRemark(bo.getRemark());
        }
        int rows = recruitApplicationMapper.updateById(update);
        if (rows == 0) {
            log.warn("报到登记版本冲突, applicationId={}, version={}", applicationId, bo.getVersion());
            throw new ServiceException("应聘记录已被他人修改，请刷新后重试");
        }
        // 2. 解析唯一有效月度任务并计入到岗人数（§9.4 一个到岗结果只能计入一条有效月度任务）
        Long planItemId = resolveCreditPlanItem(application, bo.getPlanItemId());
        if (planItemId != null) {
            creditPlanItem(applicationId, planItemId, now, operatorId);
            // 3. 刷新月度计划任务状态：必须调用领域服务，禁止自行拼接状态值（§11.1）
            planItemStatusService.refreshItemStatus(planItemId);
        } else {
            log.warn("报到登记未找到关联月度计划任务，本次不计入到岗人数, applicationId={}", applicationId);
        }
        // 4. 阶段历史（与阶段变更同事务，只追加）
        insertStageLog(applicationId, application.getCurrentStage(), CandidateStageEnum.ARRIVED.getCode(),
            ApplicationStageDomainService.ACTION_ARRIVE, ApplicationResultEnum.PASSED.getCode(),
            null, "登记实际报到：" + bo.getArrivalDate(), null, now);
        publishAfterCommit(new CandidateArrivedEvent(applicationId, application.getTalentId(),
            planItemId, bo.getArrivalDate(), operatorId));
        publishAfterCommit(new ApplicationStageChangedEvent(applicationId, application.getTalentId(),
            application.getJobId(), planItemId, application.getCurrentStage(),
            CandidateStageEnum.ARRIVED.getCode(), ApplicationStageDomainService.ACTION_ARRIVE, operatorId));
        log.info("报到登记完成, applicationId={}, arrivalDate={}, planItemId={}", applicationId, bo.getArrivalDate(), planItemId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void registerNoArrival(Long applicationId, NoArrivalBo bo) {
        RecruitApplication application = loadApplication(applicationId);
        stageDomainService.validateNotEnded(application.getCurrentStatus());
        if (application.getArrivalDate() != null) {
            throw new ServiceException("该应聘记录已登记实际报到，不能再登记未报到");
        }
        LocalDateTime now = LocalDateTime.now();
        RecruitApplication update = new RecruitApplication();
        update.setApplicationId(applicationId);
        update.setVersion(bo.getVersion());
        // 未报到视为候选人主动放弃，结束本次应聘；不计入月度计划任务到岗人数
        update.setCurrentStatus(ApplicationResultEnum.WITHDRAWN.getCode());
        update.setNoArrivalReason(bo.getNoArrivalReason());
        update.setRejectReason(bo.getNoArrivalReason());
        if (StringUtils.isNotBlank(bo.getRemark())) {
            update.setRemark(bo.getRemark());
        }
        int rows = recruitApplicationMapper.updateById(update);
        if (rows == 0) {
            log.warn("未报到登记版本冲突, applicationId={}, version={}", applicationId, bo.getVersion());
            throw new ServiceException("应聘记录已被他人修改，请刷新后重试");
        }
        insertStageLog(applicationId, application.getCurrentStage(), application.getCurrentStage(), "no_arrival",
            ApplicationResultEnum.WITHDRAWN.getCode(), null, bo.getNoArrivalReason(), bo.getNextFollowTime(), now);
        log.info("未报到登记完成, applicationId={}", applicationId);
    }

    /* ------------------------------------------------------------------ 阶段事实装载（跨域只读） ------------------------------------------------------------------ */

    /**
     * 装载阶段跳转事实：面试安排 / 一面结论 / 最终面试结论 / 背调结论（§7.2 第 1~4 条）。
     *
     * <p>只读 {@code hr_recruit_interview} 与 {@code hr_recruit_background}，不写对方任何一列。</p>
     *
     * @param application    应聘记录
     * @param offerResult    邀约结果（可能与入参合并后的值）
     * @param planArrivalDate 计划报到日期（可能与入参合并后的值）
     * @return 阶段跳转事实
     */
    private ApplicationStageDomainService.StageFacts loadFacts(RecruitApplication application,
                                                               String offerResult,
                                                               LocalDate planArrivalDate) {
        List<RecruitInterview> interviews = recruitInterviewMapper.selectList(
            new LambdaQueryWrapper<RecruitInterview>()
                .eq(RecruitInterview::getApplicationId, application.getApplicationId()));
        boolean scheduled = false;
        boolean firstConcluded = false;
        boolean finalConcluded = false;
        int maxRound = Integer.MIN_VALUE;
        for (RecruitInterview interview : interviews) {
            if (INTERVIEW_STATUS_CANCELLED.equals(interview.getStatus())) {
                continue;
            }
            scheduled = true;
            int round = interview.getRoundNo() == null ? 1 : interview.getRoundNo();
            if (round == 1 && stageDomainService.isInterviewResultConcluded(interview.getResult())) {
                firstConcluded = true;
            }
            if (round > maxRound) {
                maxRound = round;
                finalConcluded = stageDomainService.isInterviewResultConcluded(interview.getResult());
            }
        }
        List<RecruitBackground> backgrounds = recruitBackgroundMapper.selectList(
            new LambdaQueryWrapper<RecruitBackground>()
                .eq(RecruitBackground::getApplicationId, application.getApplicationId())
                .orderByDesc(RecruitBackground::getBackgroundId));
        RecruitBackground background = backgrounds.isEmpty() ? null : backgrounds.get(0);
        boolean backgroundConcluded = background != null
            && stageDomainService.isBackgroundResultConcluded(background.getResult());
        boolean backgroundWaived = background != null
            && stageDomainService.isBackgroundWaivedWithReason(background.getResult(), background.getWaiveReason());
        return new ApplicationStageDomainService.StageFacts(scheduled, firstConcluded, finalConcluded,
            backgroundConcluded, backgroundWaived, offerResult, planArrivalDate);
    }

    /* ------------------------------------------------------------------ 计划任务计入 ------------------------------------------------------------------ */

    /**
     * 解析本次报到应计入的唯一有效月度计划任务（§9.4）。
     *
     * <p>优先级：入参显式指定 → 当前有效的计划计入关系（多于一条时按
     * {@link HrTalentErrorCode#MSG_HR_PLAN_004} 报错）→ 岗位上的计划任务归属。</p>
     *
     * @param application    应聘记录
     * @param explicitItemId 入参指定的计划任务ID，可为空
     * @return 计划任务ID，无法确定时返回 null（表示不计入）
     */
    private Long resolveCreditPlanItem(RecruitApplication application, Long explicitItemId) {
        if (explicitItemId != null) {
            RecruitPlanItem item = recruitPlanItemMapper.selectById(explicitItemId);
            if (item == null) {
                throw new ServiceException("计划任务不存在或已删除");
            }
            return explicitItemId;
        }
        List<RecruitPlanApplicationRel> rels = recruitPlanApplicationRelMapper.selectList(
            new LambdaQueryWrapper<RecruitPlanApplicationRel>()
                .eq(RecruitPlanApplicationRel::getApplicationId, application.getApplicationId())
                .isNull(RecruitPlanApplicationRel::getEffectiveEnd));
        Set<Long> itemIds = rels.stream()
            .map(RecruitPlanApplicationRel::getPlanItemId)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (itemIds.size() > 1) {
            throw new ServiceException(HrTalentErrorCode.MSG_HR_PLAN_004);
        }
        if (itemIds.size() == 1) {
            return itemIds.iterator().next();
        }
        if (application.getJobId() != null) {
            RecruitJob job = recruitJobMapper.selectById(application.getJobId());
            if (job != null && job.getPlanItemId() != null) {
                return job.getPlanItemId();
            }
        }
        return null;
    }

    /**
     * 把到岗结果计入指定的月度计划任务（标记计入关系 + 累加人数）。
     *
     * @param applicationId 应聘记录ID
     * @param planItemId    月度计划任务ID
     * @param now           操作时间
     * @param operatorId    操作人用户ID
     */
    private void creditPlanItem(Long applicationId, Long planItemId, LocalDateTime now, Long operatorId) {
        int relRows = recruitPlanApplicationRelMapper.update(null, new LambdaUpdateWrapper<RecruitPlanApplicationRel>()
            .eq(RecruitPlanApplicationRel::getApplicationId, applicationId)
            .eq(RecruitPlanApplicationRel::getPlanItemId, planItemId)
            .eq(RecruitPlanApplicationRel::getCreditedFlag, CREDITED_NO)
            .isNull(RecruitPlanApplicationRel::getEffectiveEnd)
            .set(RecruitPlanApplicationRel::getCreditedFlag, CREDITED_YES)
            .set(RecruitPlanApplicationRel::getCreditedTime, now)
            .set(RecruitPlanApplicationRel::getOperatorId, operatorId));
        if (relRows == 0) {
            // 没有现成的有效计入关系（例如岗位未关联计划任务后补指定）：补一条到岗计入关系
            insertPlanRel(applicationId, planItemId, REL_TYPE_ARRIVAL, CREDITED_YES, now, operatorId);
        }
        int rows = recruitPlanItemMapper.update(null, new LambdaUpdateWrapper<RecruitPlanItem>()
            .eq(RecruitPlanItem::getItemId, planItemId)
            .setSql("credited_arrival_qty = credited_arrival_qty + 1"));
        if (rows == 0) {
            throw new ServiceException(HrTalentErrorCode.MSG_HR_PLAN_004);
        }
    }

    /**
     * 新增一条计划任务与应聘记录的计入关系。
     *
     * @param applicationId 应聘记录ID
     * @param planItemId    月度计划任务ID
     * @param relationType  关联类型（plan/arrival）
     * @param creditedFlag  是否已计入
     * @param now           生效时间
     * @param operatorId    操作人用户ID
     */
    private void insertPlanRel(Long applicationId, Long planItemId, String relationType,
                               String creditedFlag, LocalDateTime now, Long operatorId) {
        RecruitPlanApplicationRel rel = new RecruitPlanApplicationRel();
        rel.setPlanItemId(planItemId);
        rel.setApplicationId(applicationId);
        rel.setRelationType(relationType);
        rel.setEffectiveStart(now);
        rel.setCreditedFlag(creditedFlag);
        rel.setCreditedTime(CREDITED_YES.equals(creditedFlag) ? now : null);
        rel.setOperatorId(operatorId);
        recruitPlanApplicationRelMapper.insert(rel);
    }

    /**
     * 统计该应聘记录已计入到岗统计的有效关系数量。
     *
     * @param applicationId 应聘记录ID
     * @return 已计入的关系数量
     */
    private long countCreditedRel(Long applicationId) {
        return recruitPlanApplicationRelMapper.selectCount(new LambdaQueryWrapper<RecruitPlanApplicationRel>()
            .eq(RecruitPlanApplicationRel::getApplicationId, applicationId)
            .eq(RecruitPlanApplicationRel::getCreditedFlag, CREDITED_YES));
    }

    /**
     * 解析应聘记录当前关联的月度计划任务ID（用于事件载荷，可为空）。
     *
     * @param application 应聘记录
     * @return 计划任务ID，无法确定时返回 null
     */
    private Long resolvePlanItemId(RecruitApplication application) {
        List<RecruitPlanApplicationRel> rels = recruitPlanApplicationRelMapper.selectList(
            new LambdaQueryWrapper<RecruitPlanApplicationRel>()
                .eq(RecruitPlanApplicationRel::getApplicationId, application.getApplicationId())
                .isNull(RecruitPlanApplicationRel::getEffectiveEnd));
        for (RecruitPlanApplicationRel rel : rels) {
            if (rel.getPlanItemId() != null) {
                return rel.getPlanItemId();
            }
        }
        if (application.getJobId() != null) {
            RecruitJob job = recruitJobMapper.selectById(application.getJobId());
            if (job != null) {
                return job.getPlanItemId();
            }
        }
        return null;
    }

    /* ------------------------------------------------------------------ 内部方法 ------------------------------------------------------------------ */

    /**
     * 加载应聘记录，不存在时抛中文提示异常。
     *
     * @param applicationId 应聘记录ID
     * @return 应聘记录实体
     */
    private RecruitApplication loadApplication(Long applicationId) {
        if (applicationId == null) {
            throw new ServiceException("应聘记录ID不能为空");
        }
        RecruitApplication application = recruitApplicationMapper.selectById(applicationId);
        if (application == null) {
            throw new ServiceException("应聘记录不存在或已删除");
        }
        return application;
    }

    /**
     * 追加一条阶段历史（只追加，不覆盖、不物理删除）。
     *
     * @param applicationId  应聘记录ID
     * @param fromStage      原阶段
     * @param toStage        目标阶段
     * @param actionType     操作动作
     * @param result         阶段结果
     * @param reasonCode     原因编码
     * @param comment        阶段说明
     * @param nextFollowTime 下一步日期
     * @param operateTime    操作时间
     */
    private void insertStageLog(Long applicationId, String fromStage, String toStage, String actionType,
                                String result, String reasonCode, String comment,
                                LocalDateTime nextFollowTime, LocalDateTime operateTime) {
        RecruitStageLog stageLog = new RecruitStageLog();
        stageLog.setApplicationId(applicationId);
        stageLog.setFromStage(fromStage);
        stageLog.setToStage(toStage);
        stageLog.setActionType(actionType);
        stageLog.setResult(result);
        stageLog.setReasonCode(reasonCode);
        stageLog.setComment(comment);
        stageLog.setNextFollowTime(nextFollowTime);
        stageLog.setOperatorId(currentUserId());
        stageLog.setOperateTime(operateTime);
        recruitStageLogMapper.insert(stageLog);
    }

    /**
     * 校验期望薪资：低值不得大于高值。
     *
     * @param salaryMin 期望薪资低值
     * @param salaryMax 期望薪资高值
     */
    private void validateSalary(BigDecimal salaryMin, BigDecimal salaryMax) {
        if (salaryMin != null && salaryMax != null && salaryMin.compareTo(salaryMax) > 0) {
            throw new ServiceException("期望薪资低值不能大于高值");
        }
    }

    /**
     * 取当前登录用户ID；无登录态时返回 null。
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
     * 事务提交后发布领域事件（§21.6）：事件只负责后续派生刷新，不承载必须原子完成的写操作。
     *
     * @param event 领域事件
     */
    private void publishAfterCommit(Object event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    SpringUtils.context().publishEvent(event);
                }
            });
        } else {
            SpringUtils.context().publishEvent(event);
        }
    }

}
