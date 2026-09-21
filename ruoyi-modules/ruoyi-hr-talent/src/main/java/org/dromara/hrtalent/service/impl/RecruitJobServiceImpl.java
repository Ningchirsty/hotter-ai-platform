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
import org.dromara.hrtalent.domain.bo.recruitment.RecruitJobAssignBo;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitJobBo;
import org.dromara.hrtalent.domain.bo.recruitment.RecruitJobQueryBo;
import org.dromara.hrtalent.domain.entity.RecruitJob;
import org.dromara.hrtalent.domain.vo.recruitment.RecruitJobVo;
import org.dromara.hrtalent.enums.JobStatusEnum;
import org.dromara.hrtalent.enums.RecruitModeEnum;
import org.dromara.hrtalent.enums.UrgencyEnum;
import org.dromara.hrtalent.mapper.RecruitJobMapper;
import org.dromara.hrtalent.service.recruitment.IRecruitJobService;
import org.dromara.hrtalent.support.RecruitBusinessNoGenerator;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 招聘岗位执行项服务实现（SPEC-P2 §3.4 / §4）。
 *
 * <p><b>业务规则</b>：</p>
 * <ul>
 *     <li>岗位编号由 {@link RecruitBusinessNoGenerator#nextJobNo()} 生成，数据库唯一索引为最终约束。</li>
 *     <li>薪资：低值不得大于高值；填写任一端必须明确薪资周期，违反给中文提示。</li>
 *     <li>招聘形式取 {@link RecruitModeEnum} 的 code，紧急程度取 {@link UrgencyEnum} 的 code，
 *     岗位状态取 {@link JobStatusEnum} 的 code，均不接受中文或未知编码。</li>
 *     <li>岗位是乐观锁聚合根：更新必须回传 {@code version}，命中 0 行即版本冲突并提示刷新。</li>
 * </ul>
 *
 * <p><b>分配说明</b>：{@link #assign} 只写 {@code owner_id}、{@code assistant_ids}、
 * {@code first_interviewer_id}、{@code second_interviewer_id} 四个字段；其中一/二面面试官是岗位上的
 * 「计划默认值」，安排面试时带出，每轮面试的实际面试官归属 {@code hr_recruit_interviewer}
 * （按面试记录关联，后续阶段实现），本阶段不写该表。</p>
 *
 * @author hr-talent
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecruitJobServiceImpl implements IRecruitJobService {

    /**
     * 动作：关闭岗位。
     */
    private static final String ACTION_CLOSE = "close";

    /**
     * 动作：重新开放岗位。
     */
    private static final String ACTION_REOPEN = "reopen";

    /**
     * 是否需要猎头：否。
     */
    private static final String HEADHUNTER_NO = "0";

    /**
     * 是否需要猎头：是。
     */
    private static final String HEADHUNTER_YES = "1";

    /**
     * 岗位招聘人数默认值。
     */
    private static final int DEFAULT_RECRUIT_COUNT = 0;

    /**
     * 协助人字段最大存储长度（与 DDL {@code assistant_ids varchar(255)} 一致）。
     */
    private static final int ASSISTANT_IDS_MAX_LENGTH = 255;

    /**
     * 岗位 Mapper。
     */
    private final RecruitJobMapper recruitJobMapper;

    /**
     * 业务编号生成器（岗位编号唯一事实来源）。
     */
    private final RecruitBusinessNoGenerator businessNoGenerator;

    @Override
    public PageResult<RecruitJobVo> queryPage(RecruitJobQueryBo bo, PageQuery pageQuery) {
        RecruitJobQueryBo query = bo == null ? new RecruitJobQueryBo() : bo;
        LambdaQueryWrapper<RecruitJob> wrapper = new LambdaQueryWrapper<RecruitJob>()
            .like(StringUtils.isNotBlank(query.getJobNo()), RecruitJob::getJobNo, query.getJobNo())
            .like(StringUtils.isNotBlank(query.getJobName()), RecruitJob::getJobName, query.getJobName())
            .eq(query.getDemandId() != null, RecruitJob::getDemandId, query.getDemandId())
            .eq(query.getPlanItemId() != null, RecruitJob::getPlanItemId, query.getPlanItemId())
            .eq(query.getCompanyDeptId() != null, RecruitJob::getCompanyDeptId, query.getCompanyDeptId())
            .eq(query.getUseDeptId() != null, RecruitJob::getUseDeptId, query.getUseDeptId())
            .eq(query.getOwnerId() != null, RecruitJob::getOwnerId, query.getOwnerId())
            .eq(StringUtils.isNotBlank(query.getStatus()), RecruitJob::getStatus, query.getStatus())
            .eq(StringUtils.isNotBlank(query.getRecruitMode()), RecruitJob::getRecruitMode, query.getRecruitMode())
            .eq(StringUtils.isNotBlank(query.getUrgency()), RecruitJob::getUrgency, query.getUrgency())
            .eq(StringUtils.isNotBlank(query.getJobLevel()), RecruitJob::getJobLevel, query.getJobLevel())
            .like(StringUtils.isNotBlank(query.getWorkCity()), RecruitJob::getWorkCity, query.getWorkCity())
            .eq(StringUtils.isNotBlank(query.getHeadhunterFlag()), RecruitJob::getHeadhunterFlag, query.getHeadhunterFlag())
            .ge(query.getPublishDateBegin() != null, RecruitJob::getPublishDate, query.getPublishDateBegin())
            .le(query.getPublishDateEnd() != null, RecruitJob::getPublishDate, query.getPublishDateEnd())
            .orderByDesc(RecruitJob::getCreateTime);
        Page<RecruitJobVo> page = recruitJobMapper.selectVoPage(pageQuery.build(), wrapper);
        return PageResult.build(page.getRecords(), page.getTotal());
    }

    @Override
    public RecruitJobVo getDetail(Long jobId) {
        RecruitJob job = loadJob(jobId);
        RecruitJobVo vo = MapstructUtils.convert(job, RecruitJobVo.class);
        if (vo == null) {
            throw new ServiceException("岗位不存在或已删除");
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(RecruitJobBo bo) {
        // 先做纯规则校验（薪资、枚举、状态），全部通过后才落库
        validateSalary(bo.getSalaryMin(), bo.getSalaryMax(), bo.getSalaryPeriod());
        String status = resolveCreateStatus(bo.getStatus());
        String recruitMode = resolveRecruitMode(bo.getRecruitMode());
        String urgency = resolveUrgency(bo.getUrgency(), true);
        String headhunterFlag = resolveHeadhunterFlag(bo.getHeadhunterFlag());
        String assistantIds = joinAssistantIds(bo.getAssistantIds());
        RecruitJob entity = MapstructUtils.convert(bo, RecruitJob.class);
        if (entity == null) {
            entity = new RecruitJob();
        }
        // 服务端权威字段：主键、编号、版本不接受前端写入
        entity.setJobId(null);
        entity.setJobNo(businessNoGenerator.nextJobNo());
        entity.setVersion(0);
        entity.setStatus(status);
        entity.setRecruitMode(recruitMode);
        entity.setUrgency(urgency);
        entity.setHeadhunterFlag(headhunterFlag);
        entity.setRecruitCount(bo.getRecruitCount() == null ? DEFAULT_RECRUIT_COUNT : bo.getRecruitCount());
        entity.setAssistantIds(assistantIds);
        if (JobStatusEnum.OPEN.getCode().equals(status) && entity.getPublishDate() == null) {
            entity.setPublishDate(LocalDate.now());
        }
        try {
            recruitJobMapper.insert(entity);
        } catch (DuplicateKeyException e) {
            // 业务编号唯一索引冲突：生成器只保证单进程内不重复，冲突时提示重试
            log.warn("岗位编号生成冲突, jobNo={}", entity.getJobNo());
            throw new ServiceException("岗位编号生成冲突，请重试");
        }
        log.info("新增岗位执行项, jobId={}, jobNo={}, status={}", entity.getJobId(), entity.getJobNo(), status);
        return entity.getJobId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(RecruitJobBo bo) {
        RecruitJob exist = loadJob(bo.getJobId());
        JobStatusEnum current = requireStatus(exist.getStatus());
        if (JobStatusEnum.CLOSED == current) {
            throw new ServiceException("已关闭的岗位不可修改，请先重新开放");
        }
        // 薪资：以入参未提供的字段回落到库内现值后再校验，避免局部更新绕过规则
        validateSalary(
            bo.getSalaryMin() == null ? exist.getSalaryMin() : bo.getSalaryMin(),
            bo.getSalaryMax() == null ? exist.getSalaryMax() : bo.getSalaryMax(),
            StringUtils.isNotBlank(bo.getSalaryPeriod()) ? bo.getSalaryPeriod() : exist.getSalaryPeriod());

        RecruitJob update = MapstructUtils.convert(bo, RecruitJob.class);
        if (update == null) {
            update = new RecruitJob();
        }
        update.setJobId(exist.getJobId());
        update.setRecruitMode(resolveRecruitMode(bo.getRecruitMode()));
        update.setUrgency(resolveUrgency(bo.getUrgency(), false));
        update.setAssistantIds(joinAssistantIds(bo.getAssistantIds()));
        // 状态流转：已关闭只能走「重新开放」动作，编辑接口不得直接把岗位改成已关闭
        if (StringUtils.isNotBlank(bo.getStatus()) && !bo.getStatus().equals(exist.getStatus())) {
            JobStatusEnum target = requireStatus(bo.getStatus());
            if (JobStatusEnum.CLOSED == target) {
                throw new ServiceException("关闭岗位请使用关闭动作（actions/close）");
            }
            if (!JobStatusEnum.canTransfer(exist.getStatus(), target.getCode())) {
                throw new ServiceException("岗位状态不允许从「" + current.getDesc() + "」变更为「" + target.getDesc() + "」");
            }
            if (JobStatusEnum.OPEN == target && exist.getPublishDate() == null) {
                update.setPublishDate(LocalDate.now());
            }
        }
        // 乐观锁：版本号缺失直接判定为参数非法（BO 已分组校验，此处兜底）
        if (bo.getVersion() == null) {
            throw new ServiceException("版本号不能为空，请刷新后重试");
        }
        update.setVersion(bo.getVersion());
        int rows = recruitJobMapper.updateById(update);
        if (rows == 0) {
            log.warn("岗位执行项更新版本冲突, jobId={}, version={}", exist.getJobId(), bo.getVersion());
            throw new ServiceException("岗位数据已被他人修改，请刷新后重试");
        }
        log.info("更新岗位执行项, jobId={}, version={}", exist.getJobId(), bo.getVersion());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long[] jobIds) {
        if (jobIds == null || jobIds.length == 0) {
            return;
        }
        List<Long> ids = Arrays.stream(jobIds).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return;
        }
        long exists = recruitJobMapper.selectCount(new LambdaQueryWrapper<RecruitJob>()
            .in(RecruitJob::getJobId, ids));
        if (exists == 0) {
            throw new ServiceException("岗位不存在或已删除");
        }
        recruitJobMapper.deleteByIds(ids);
        log.info("逻辑删除岗位执行项, jobIds={}", ids);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void action(Long jobId, String action) {
        String code = action == null ? "" : action.trim();
        RecruitJob job = loadJob(jobId);
        JobStatusEnum current = requireStatus(job.getStatus());
        int currentVersion = job.getVersion() == null ? 0 : job.getVersion();
        LambdaUpdateWrapper<RecruitJob> wrapper = new LambdaUpdateWrapper<RecruitJob>()
            .eq(RecruitJob::getJobId, jobId)
            .eq(RecruitJob::getVersion, currentVersion);
        if (ACTION_CLOSE.equals(code)) {
            if (JobStatusEnum.CLOSED == current) {
                throw new ServiceException("岗位已关闭，无需重复关闭");
            }
            wrapper.set(RecruitJob::getStatus, JobStatusEnum.CLOSED.getCode())
                .set(RecruitJob::getCloseDate, job.getCloseDate() == null ? LocalDate.now() : job.getCloseDate());
        } else if (ACTION_REOPEN.equals(code)) {
            if (JobStatusEnum.CLOSED != current) {
                throw new ServiceException("仅已关闭的岗位可以重新开放");
            }
            // 重新开放需要显式清空关闭日期，故使用条件更新而非实体更新
            wrapper.set(RecruitJob::getStatus, JobStatusEnum.OPEN.getCode())
                .set(RecruitJob::getCloseDate, null)
                .set(RecruitJob::getPublishDate, job.getPublishDate() == null ? LocalDate.now() : job.getPublishDate());
        } else {
            throw new ServiceException("不支持的岗位动作：" + code);
        }
        wrapper.set(RecruitJob::getVersion, currentVersion + 1);
        int rows = recruitJobMapper.update(null, wrapper);
        if (rows == 0) {
            log.warn("岗位执行项动作版本冲突, jobId={}, action={}", jobId, code);
            throw new ServiceException("岗位数据已被他人修改，请刷新后重试");
        }
        log.info("岗位执行项动作完成, jobId={}, action={}, from={}", jobId, code, current.getCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assign(RecruitJobAssignBo bo) {
        RecruitJob job = loadJob(bo.getJobId());
        JobStatusEnum current = requireStatus(job.getStatus());
        if (JobStatusEnum.CLOSED == current) {
            throw new ServiceException("已关闭的岗位不能分配人员");
        }
        boolean anything = bo.getOwnerId() != null
            || bo.getAssistantIds() != null
            || bo.getFirstInterviewerId() != null
            || bo.getSecondInterviewerId() != null;
        if (!anything) {
            throw new ServiceException("请至少分配一项：招聘负责人、协助人、一面面试官或二面面试官");
        }
        String assistantIds = joinAssistantIds(bo.getAssistantIds());
        if (assistantIds != null && assistantIds.length() > ASSISTANT_IDS_MAX_LENGTH) {
            throw new ServiceException("协助人过多，存储长度已超过 " + ASSISTANT_IDS_MAX_LENGTH + " 个字符");
        }
        // 只写人员字段：不触碰状态、名称、薪资等其它列
        int currentVersion = job.getVersion() == null ? 0 : job.getVersion();
        LambdaUpdateWrapper<RecruitJob> wrapper = new LambdaUpdateWrapper<RecruitJob>()
            .eq(RecruitJob::getJobId, job.getJobId())
            .eq(RecruitJob::getVersion, currentVersion)
            .set(RecruitJob::getVersion, currentVersion + 1);
        if (bo.getOwnerId() != null) {
            wrapper.set(RecruitJob::getOwnerId, bo.getOwnerId());
        }
        if (bo.getAssistantIds() != null) {
            wrapper.set(RecruitJob::getAssistantIds, assistantIds);
        }
        if (bo.getFirstInterviewerId() != null) {
            wrapper.set(RecruitJob::getFirstInterviewerId, bo.getFirstInterviewerId());
        }
        if (bo.getSecondInterviewerId() != null) {
            wrapper.set(RecruitJob::getSecondInterviewerId, bo.getSecondInterviewerId());
        }
        int rows = recruitJobMapper.update(null, wrapper);
        if (rows == 0) {
            log.warn("岗位执行项分配版本冲突, jobId={}", job.getJobId());
            throw new ServiceException("岗位数据已被他人修改，请刷新后重试");
        }
        log.info("岗位执行项分配完成, jobId={}, ownerAssigned={}, assistantCount={}, firstInterviewerAssigned={}, secondInterviewerAssigned={}",
            job.getJobId(), bo.getOwnerId() != null,
            bo.getAssistantIds() == null ? -1 : bo.getAssistantIds().length,
            bo.getFirstInterviewerId() != null, bo.getSecondInterviewerId() != null);
    }

    /* ------------------------------------------------------------------ 内部方法 ------------------------------------------------------------------ */

    /**
     * 加载岗位，不存在时抛中文提示异常。
     *
     * @param jobId 岗位执行项ID
     * @return 岗位实体
     */
    private RecruitJob loadJob(Long jobId) {
        if (jobId == null) {
            throw new ServiceException("岗位ID不能为空");
        }
        RecruitJob job = recruitJobMapper.selectById(jobId);
        if (job == null) {
            throw new ServiceException("岗位不存在或已删除");
        }
        return job;
    }

    /**
     * 解析岗位状态编码，未知编码一律按非法处理（fail-safe）。
     *
     * @param status 状态编码
     * @return 状态枚举
     */
    private JobStatusEnum requireStatus(String status) {
        JobStatusEnum item = JobStatusEnum.find(status);
        if (item == null) {
            throw new ServiceException("未知的岗位状态：" + status);
        }
        return item;
    }

    /**
     * 校验薪资规则：低值不得大于高值，且填写薪资本必须明确薪资周期。
     *
     * @param salaryMin    薪资低值，可为 null
     * @param salaryMax    薪资高值，可为 null
     * @param salaryPeriod 薪资周期，可为 null
     */
    private void validateSalary(BigDecimal salaryMin, BigDecimal salaryMax, String salaryPeriod) {
        if (salaryMin != null && salaryMax != null && salaryMin.compareTo(salaryMax) > 0) {
            throw new ServiceException("薪资低值不能大于薪资高值");
        }
        if ((salaryMin != null || salaryMax != null) && StringUtils.isBlank(salaryPeriod)) {
            throw new ServiceException("填写薪资时必须明确薪资周期（如 month/year/day）");
        }
    }

    /**
     * 解析新增时的岗位状态：默认草稿，允许直接发布，禁止新建即暂停或关闭。
     *
     * @param status 入参状态编码，可为空
     * @return 规范化后的状态编码
     */
    private String resolveCreateStatus(String status) {
        if (StringUtils.isBlank(status)) {
            return JobStatusEnum.DRAFT.getCode();
        }
        JobStatusEnum target = requireStatus(status);
        if (JobStatusEnum.PAUSED == target || JobStatusEnum.CLOSED == target) {
            throw new ServiceException("新建岗位状态只能为草稿或招聘中");
        }
        return target.getCode();
    }

    /**
     * 解析紧急程度编码；新增场景空值回落为「普通」，更新场景空值保持原值不被覆盖。
     *
     * @param urgency   入参编码，可为空
     * @param isCreate  是否新增场景
     * @return 规范化后的编码，更新场景返回 null 表示不修改
     */
    private String resolveUrgency(String urgency, boolean isCreate) {
        if (StringUtils.isBlank(urgency)) {
            return isCreate ? UrgencyEnum.NORMAL.getCode() : null;
        }
        if (UrgencyEnum.find(urgency) == null) {
            throw new ServiceException("紧急程度不合法，请使用字典 recruit_urgency 的编码（normal/urgent/very_urgent）");
        }
        return urgency;
    }

    /**
     * 解析招聘形式编码，只接受 {@link RecruitModeEnum} 定义的稳定编码。
     *
     * @param recruitMode 入参编码，可为空
     * @return 规范化后的编码，空值返回 null（表示不写/不修改）
     */
    private String resolveRecruitMode(String recruitMode) {
        if (StringUtils.isBlank(recruitMode)) {
            return null;
        }
        if (RecruitModeEnum.find(recruitMode) == null) {
            throw new ServiceException("招聘形式不合法，请使用字典 recruit_mode 的编码"
                + "（internal/social/campus/headhunter/referral/other）");
        }
        return recruitMode;
    }

    /**
     * 解析是否需要猎头标志，空值回落为「否」。
     *
     * @param headhunterFlag 入参标志，可为空
     * @return 规范化后的标志
     */
    private String resolveHeadhunterFlag(String headhunterFlag) {
        if (StringUtils.isBlank(headhunterFlag)) {
            return HEADHUNTER_NO;
        }
        if (!HEADHUNTER_NO.equals(headhunterFlag) && !HEADHUNTER_YES.equals(headhunterFlag)) {
            throw new ServiceException("是否需要猎头只能为 0 或 1");
        }
        return headhunterFlag;
    }

    /**
     * 将协助人用户ID数组拼接为英文逗号分隔串（去重、去空）。
     *
     * @param assistantIds 协助人用户ID数组，null 表示不修改，空数组表示清空
     * @return 逗号分隔串；无有效元素时返回 null（表示清空/不写）
     */
    private String joinAssistantIds(Long[] assistantIds) {
        if (assistantIds == null) {
            return null;
        }
        LinkedHashSet<Long> distinct = Arrays.stream(assistantIds)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (distinct.isEmpty()) {
            return null;
        }
        return distinct.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

}
