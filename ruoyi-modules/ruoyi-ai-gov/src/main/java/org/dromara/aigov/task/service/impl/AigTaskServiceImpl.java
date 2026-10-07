package org.dromara.aigov.task.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.constant.AigConstants;
import org.dromara.aigov.enums.AigDataLevelEnum;
import org.dromara.aigov.enums.AigErrorClassEnum;
import org.dromara.aigov.task.config.AigCallbackProperties;
import org.dromara.aigov.task.domain.AigCallback;
import org.dromara.aigov.task.domain.AigTask;
import org.dromara.aigov.task.domain.AigTaskEvent;
import org.dromara.aigov.task.domain.AigTaskResult;
import org.dromara.aigov.task.domain.AigTaskSnapshot;
import org.dromara.aigov.task.domain.bo.AigTaskCallbackBo;
import org.dromara.aigov.task.domain.bo.AigTaskCreateBo;
import org.dromara.aigov.task.domain.bo.AigTaskQueryBo;
import org.dromara.aigov.task.domain.bo.AigTaskResultBo;
import org.dromara.aigov.task.domain.bo.AigTaskResultSelectBo;
import org.dromara.aigov.task.domain.bo.AigTaskReviewBo;
import org.dromara.aigov.task.domain.vo.AigCallbackVo;
import org.dromara.aigov.task.domain.vo.AigTaskDetailVo;
import org.dromara.aigov.task.domain.vo.AigTaskEventVo;
import org.dromara.aigov.task.domain.vo.AigTaskResultVo;
import org.dromara.aigov.task.domain.vo.AigTaskSnapshotVo;
import org.dromara.aigov.task.domain.vo.AigTaskVo;
import org.dromara.aigov.task.enums.AigCandidateStatusEnum;
import org.dromara.aigov.task.enums.AigTaskEventTypeEnum;
import org.dromara.aigov.task.enums.AigTaskStatusEnum;
import org.dromara.aigov.task.enums.AigTaskTypeEnum;
import org.dromara.aigov.task.helper.AigTaskActorProvider;
import org.dromara.aigov.task.helper.AigTaskCallbackSigner;
import org.dromara.aigov.task.mapper.AigCallbackMapper;
import org.dromara.aigov.task.mapper.AigTaskEventMapper;
import org.dromara.aigov.task.mapper.AigTaskMapper;
import org.dromara.aigov.task.mapper.AigTaskResultMapper;
import org.dromara.aigov.task.mapper.AigTaskSnapshotMapper;
import org.dromara.aigov.task.service.IAigTaskService;
import org.dromara.aigov.task.state.AigTaskStateMachine;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * AI 统一任务编排服务实现（设计 §9）。
 *
 * @author ai-gov
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigTaskServiceImpl implements IAigTaskService {

    /**
     * 默认最大自动尝试次数（设计 §9.2）。
     */
    private static final int DEFAULT_MAX_ATTEMPT = 3;

    /**
     * 任务号长度（{@code aig_task.task_no varchar(32)}）。
     */
    private static final int TASK_NO_LENGTH = 32;

    /**
     * 状态：允许外发。
     */
    private static final String YES = "Y";

    /**
     * 状态：不允许外发。
     */
    private static final String NO = "N";

    private final AigTaskMapper taskMapper;
    private final AigTaskSnapshotMapper snapshotMapper;
    private final AigTaskEventMapper eventMapper;
    private final AigTaskResultMapper resultMapper;
    private final AigCallbackMapper callbackMapper;
    private final AigTaskCallbackSigner callbackSigner;
    private final AigCallbackProperties callbackProperties;
    private final AigTaskActorProvider actorProvider;

    /**
     * 时间范围在 params 中的键。
     */
    private static final String KEY_BEGIN_TIME = "beginTime";

    /**
     * 时间范围在 params 中的键。
     */
    private static final String KEY_END_TIME = "endTime";

    @Override
    public PageResult<AigTaskVo> queryPage(AigTaskQueryBo bo, PageQuery pageQuery) {
        AigTaskQueryBo query = bo == null ? new AigTaskQueryBo() : bo;
        Object beginTime = resolveRange(query, KEY_BEGIN_TIME, query.getBeginTime());
        Object endTime = resolveRange(query, KEY_END_TIME, query.getEndTime());
        LambdaQueryWrapper<AigTask> wrapper = new LambdaQueryWrapper<AigTask>()
            .eq(StringUtils.isNotBlank(query.getTaskType()), AigTask::getTaskType, query.getTaskType())
            .eq(StringUtils.isNotBlank(query.getProjectType()), AigTask::getProjectType, query.getProjectType())
            .eq(query.getProjectId() != null, AigTask::getProjectId, query.getProjectId())
            .eq(StringUtils.isNotBlank(query.getStatus()), AigTask::getStatus, query.getStatus())
            .eq(StringUtils.isNotBlank(query.getCapabilityCode()), AigTask::getCapabilityCode,
                query.getCapabilityCode())
            .eq(StringUtils.isNotBlank(query.getScenarioCode()), AigTask::getScenarioCode, query.getScenarioCode())
            .eq(StringUtils.isNotBlank(query.getDataLevel()), AigTask::getDataLevel, query.getDataLevel())
            .eq(StringUtils.isNotBlank(query.getExternalCall()), AigTask::getExternalCall, query.getExternalCall())
            .eq(StringUtils.isNotBlank(query.getProviderCode()), AigTask::getProviderCode, query.getProviderCode())
            .eq(StringUtils.isNotBlank(query.getTraceId()), AigTask::getTraceId, query.getTraceId())
            .eq(query.getCreateBy() != null, AigTask::getCreateBy, query.getCreateBy())
            .eq(StringUtils.isNotBlank(query.getReviewStatus()), AigTask::getReviewStatus, query.getReviewStatus())
            .eq(StringUtils.isNotBlank(query.getTaskNo()), AigTask::getTaskNo, query.getTaskNo())
            .ge(beginTime != null, AigTask::getCreateTime, beginTime)
            .le(endTime != null, AigTask::getCreateTime, endTime)
            .orderByDesc(AigTask::getCreateTime);
        Page<AigTaskVo> voPage = taskMapper.selectVoPage(pageQuery.build(), wrapper);
        List<AigTaskVo> rows = voPage.getRecords();
        fillLabels(rows);
        return PageResult.build(rows, voPage.getTotal());
    }

    @Override
    public AigTaskDetailVo getDetail(Long taskId) {
        if (taskId == null) {
            throw new ServiceException("任务ID不能为空");
        }
        AigTask task = loadTask(taskId);
        AigTaskDetailVo detail = new AigTaskDetailVo();
        AigTaskVo taskVo = new AigTaskVo();
        BeanUtil.copyProperties(task, taskVo);
        fillLabels(List.of(taskVo));
        detail.setTask(taskVo);

        if (task.getInputSnapshotId() != null) {
            AigTaskSnapshot snapshot = snapshotMapper.selectById(task.getInputSnapshotId());
            if (snapshot != null) {
                AigTaskSnapshotVo snapshotVo = new AigTaskSnapshotVo();
                BeanUtil.copyProperties(snapshot, snapshotVo);
                detail.setSnapshot(snapshotVo);
            }
        }
        // 事件流按序号升序：排障看的是「先后」，倒序会让因果读起来是反的
        List<AigTaskEvent> events = eventMapper.selectList(new LambdaQueryWrapper<AigTaskEvent>()
            .eq(AigTaskEvent::getTaskId, taskId)
            .orderByAsc(AigTaskEvent::getSequence));
        if (CollUtil.isNotEmpty(events)) {
            for (AigTaskEvent event : events) {
                AigTaskEventVo eventVo = new AigTaskEventVo();
                BeanUtil.copyProperties(event, eventVo);
                AigTaskEventTypeEnum type = AigTaskEventTypeEnum.find(event.getEventType());
                eventVo.setEventTypeLabel(type == null ? event.getEventType() : type.getDesc());
                detail.getEvents().add(eventVo);
            }
        }
        List<AigTaskResult> results = resultMapper.selectList(new LambdaQueryWrapper<AigTaskResult>()
            .eq(AigTaskResult::getTaskId, taskId)
            .orderByDesc(AigTaskResult::getCreateTime));
        if (CollUtil.isNotEmpty(results)) {
            for (AigTaskResult result : results) {
                AigTaskResultVo resultVo = new AigTaskResultVo();
                BeanUtil.copyProperties(result, resultVo);
                AigCandidateStatusEnum candidate = AigCandidateStatusEnum.find(result.getCandidateStatus());
                resultVo.setCandidateStatusLabel(candidate == null ? null : candidate.getDesc());
                detail.getResults().add(resultVo);
            }
        }
        return detail;
    }

    /**
     * 回填状态/类型等展示标签。
     *
     * <p>在服务端回填而不是让前端各维护一份枚举映射：同一个状态在任务列表、任务详情、
     * 审计页都要显示，三处各写一份映射迟早会不一致（而「已取消」显示成「已完成」
     * 是会被当真的）。</p>
     *
     * @param rows 任务视图列表
     */
    private void fillLabels(List<AigTaskVo> rows) {
        if (CollUtil.isEmpty(rows)) {
            return;
        }
        for (AigTaskVo row : rows) {
            AigTaskStatusEnum status = AigTaskStatusEnum.find(row.getStatus());
            row.setStatusLabel(status == null ? row.getStatus() : status.getDesc());
            AigTaskTypeEnum type = AigTaskTypeEnum.find(row.getTaskType());
            row.setTaskTypeLabel(type == null ? row.getTaskType() : type.getDesc());
        }
    }

    /**
     * 解析时间范围：优先取显式字段，其次取 {@code params} 中的平台惯例键。
     *
     * @param query         查询条件
     * @param key           params 中的键
     * @param explicitValue 显式字段值（可为 null）
     * @return 时间值，无值时返回 null
     */
    private Object resolveRange(AigTaskQueryBo query, String key, Object explicitValue) {
        if (explicitValue != null) {
            return explicitValue;
        }
        Map<String, Object> params = query.getParams();
        if (params == null || params.isEmpty()) {
            return null;
        }
        Object value = params.get(key);
        if (value == null || StringUtils.isBlank(String.valueOf(value))) {
            return null;
        }
        return value;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(AigTaskCreateBo bo) {
        if (bo == null) {
            throw new ServiceException("创建任务入参不能为空");
        }
        AigTaskTypeEnum taskType = AigTaskTypeEnum.find(bo.getTaskType());
        if (taskType == null) {
            throw new ServiceException("未知的任务类型：" + bo.getTaskType());
        }
        AigDataLevelEnum dataLevel = AigDataLevelEnum.find(bo.getDataLevel());
        if (dataLevel == null) {
            throw new ServiceException("未知的数据等级：" + bo.getDataLevel());
        }
        if (StringUtils.isBlank(bo.getSnapshotJson())) {
            throw new ServiceException("输入快照不能为空：执行与审核只引用快照，缺了它结果无法复现");
        }
        // 幂等：同一业务域 + 提交人 + 幂等键只建一个任务（设计 §9.2）
        Long submitterId = resolveSubmitterId();
        Long existing = findByIdempotencyKey(bo.getProjectType(), bo.getIdempotencyKey(), submitterId);
        if (existing != null) {
            log.info("命中创建幂等键，返回既有任务, taskId={}, projectType={}, idempotencyKey={}",
                existing, bo.getProjectType(), bo.getIdempotencyKey());
            return existing;
        }

        // 外发许可只收紧：严格级数据一律置 N，与路由引擎的硬约束保持同一口径。
        // 否则会出现「任务上标着允许外发，路由却因 STRICT 拦下」的假象——
        // 看任务的人以为发得出去，看审计的人发现没发，两边都对不上。
        String allowExternal = YES.equalsIgnoreCase(bo.getAllowExternal()) ? YES : NO;
        if (dataLevel.externalForbidden() && YES.equals(allowExternal)) {
            allowExternal = NO;
            log.info("数据等级 {} 为严格级，外发许可被强制置为 N（与路由硬约束同口径）", dataLevel.getCode());
        }

        AigTask task = new AigTask();
        task.setTaskNo(newTaskNo());
        task.setTaskType(taskType.getCode());
        task.setCapabilityCode(bo.getCapabilityCode());
        task.setScenarioCode(bo.getScenarioCode());
        task.setProjectType(bo.getProjectType());
        task.setProjectId(bo.getProjectId());
        task.setAgentVersionId(bo.getAgentVersionId());
        task.setDataLevel(dataLevel.getCode());
        task.setAllowExternal(allowExternal);
        task.setStatus(AigTaskStatusEnum.DRAFT.getCode());
        task.setAttemptNo(0);
        task.setMaxAttempt(bo.getMaxAttempt() == null || bo.getMaxAttempt() < 1
            ? DEFAULT_MAX_ATTEMPT : bo.getMaxAttempt());
        task.setIdempotencyKey(bo.getIdempotencyKey());
        task.setProgress(0);
        task.setExternalCall(NO);
        task.setVersion(0);
        task.setDelFlag("0");
        task.setRemark(bo.getRemark());
        // createBy/createTime 显式写入，不依赖 MetaObjectHandler 的自动填充。
        // 原因：create_by 同时是幂等唯一键 (project_type, create_by, idempotency_key) 的一段，
        // 而幂等查询用的正是这里的同一次取值。若依赖自动填充，写入值与查询值就可能来自
        // 两个不同的来源（登录态 vs 无登录态），结果是「明明有幂等键却建了第二个任务」，
        // 而且库层的唯一键也会因为 create_by 为 NULL 而失去去重能力（MySQL 唯一键不约束 NULL）。
        task.setCreateBy(submitterId);
        task.setCreateTime(LocalDateTime.now());
        // 落库顺序：**先任务、再快照、最后回填关联**。
        // 不能反过来——aig_task_snapshot.task_id 是 NOT NULL，且「没有任务的快照」本身
        // 就是脏数据；而任务的 input_snapshot_id 可空，所以它天然是后填的。
        // 三步都在同一个事务里，外部看不到中间态。
        taskMapper.insert(task);
        if (task.getTaskId() == null) {
            throw new ServiceException("任务创建失败：未取回主键");
        }
        AigTaskSnapshot snapshot = freezeSnapshot(task, bo);
        snapshotMapper.insert(snapshot);
        AigTask link = new AigTask();
        link.setTaskId(task.getTaskId());
        link.setInputSnapshotId(snapshot.getSnapshotId());
        if (taskMapper.updateById(link) != 1) {
            // 回填失败会留下「有快照、任务却指不到它」的孤儿关联，而执行时正是靠这个指针
            // 找到快照——这类账实不符必须在事务里直接失败，不能留给事后排查
            throw new ServiceException("任务创建失败：快照关联回填未生效，taskId=" + task.getTaskId());
        }
        task.setInputSnapshotId(snapshot.getSnapshotId());

        appendEvent(task, 1, AigTaskEventTypeEnum.AI_TASK_CREATED, null, AigTaskStatusEnum.DRAFT,
            "任务创建并冻结输入快照 snapshotId=" + snapshot.getSnapshotId()
                + "，hash=" + snapshot.getSnapshotHash(), null);
        log.info("创建 AI 任务完成, taskId={}, taskNo={}, taskType={}, snapshotId={}",
            task.getTaskId(), task.getTaskNo(), task.getTaskType(), snapshot.getSnapshotId());
        return task.getTaskId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AigTask transition(Long taskId, Integer expectedVersion, AigTaskStatusEnum toStatus,
                              String detail, String payloadJson) {
        if (taskId == null) {
            throw new ServiceException("任务ID不能为空");
        }
        if (expectedVersion == null) {
            throw new ServiceException("期望版本不能为空：状态迁移必须带乐观锁版本");
        }
        AigTask current = loadTask(taskId);
        AigTaskStatusEnum from = AigTaskStatusEnum.find(current.getStatus());
        if (from == null) {
            throw new ServiceException("任务当前状态非法：" + current.getStatus());
        }
        if (toStatus == null) {
            throw new ServiceException("目标状态不能为空");
        }
        if (!AigTaskStateMachine.canTransition(from, toStatus)) {
            throw new ServiceException("非法的状态迁移：" + AigTaskStateMachine.describeAllowed(from)
                + "，本次请求迁移到 " + toStatus.getCode());
        }

        AigTask update = new AigTask();
        update.setTaskId(taskId);
        update.setStatus(toStatus.getCode());
        update.setVersion(expectedVersion);
        applyStatusSideEffects(update, current, from, toStatus);
        int rows = taskMapper.updateById(update);
        if (rows == 0) {
            // 乐观锁命中：并发方已经改过。这里必须报错而不是重试覆盖——
            // 两条迁移各自都可能合法，但合在一起（如「取消」与「完成」）会互相矛盾，
            // 只能让调用方重新读取当前状态再决定动作。
            throw new ServiceException("任务状态已被并发修改，请重新读取后重试：taskId=" + taskId
                + "，期望版本=" + expectedVersion + "，当前状态=" + current.getStatus());
        }

        AigTask latest = loadTask(taskId);
        int sequence = nextSequence(taskId);
        appendEvent(latest, sequence, AigTaskEventTypeEnum.AI_TASK_STATUS_CHANGED, from, toStatus,
            detail, payloadJson);
        log.info("任务状态迁移, taskId={}, {}→{}, attemptNo={}, sequence={}",
            taskId, from.getCode(), toStatus.getCode(), latest.getAttemptNo(), sequence);
        return latest;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AigTask recordExecutionFacts(Long taskId, Integer expectedVersion, String traceId,
                                        String routeSnapshot, boolean externalCall, Long latencyMs) {
        if (taskId == null) {
            throw new ServiceException("任务ID不能为空");
        }
        if (expectedVersion == null) {
            throw new ServiceException("期望版本不能为空：记录执行事实同样需要乐观锁版本");
        }
        AigTask update = new AigTask();
        update.setTaskId(taskId);
        update.setVersion(expectedVersion);
        update.setTraceId(traceId);
        update.setRouteSnapshot(routeSnapshot);
        update.setExternalCall(externalCall ? YES : NO);
        update.setLatencyMs(latencyMs);
        if (taskMapper.updateById(update) == 0) {
            throw new ServiceException("任务已被并发修改，执行事实未记录：taskId=" + taskId
                + "，期望版本=" + expectedVersion);
        }
        return loadTask(taskId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AigTask markDispatched(Long taskId, Integer expectedVersion, String providerCode,
                                  String providerJobId, boolean externalCall) {
        if (taskId == null) {
            throw new ServiceException("任务ID不能为空");
        }
        if (expectedVersion == null) {
            throw new ServiceException("期望版本不能为空：状态迁移必须带乐观锁版本");
        }
        if (StringUtils.isBlank(providerCode)) {
            throw new ServiceException("Provider 编码不能为空：回调要靠它定位任务");
        }
        AigTask current = loadTask(taskId);
        AigTaskStatusEnum from = AigTaskStatusEnum.find(current.getStatus());
        AigTaskStatusEnum to = AigTaskStatusEnum.DISPATCHED;
        if (!AigTaskStateMachine.canTransition(from, to)) {
            throw new ServiceException("非法的状态迁移：" + AigTaskStateMachine.describeAllowed(from)
                + "，本次请求迁移到 " + to.getCode());
        }
        AigTask update = new AigTask();
        update.setTaskId(taskId);
        update.setStatus(to.getCode());
        update.setVersion(expectedVersion);
        update.setProviderCode(providerCode);
        update.setProviderJobId(providerJobId);
        update.setExternalCall(externalCall ? YES : NO);
        applyStatusSideEffects(update, current, from, to);
        int rows = taskMapper.updateById(update);
        if (rows == 0) {
            throw new ServiceException("任务状态已被并发修改，请重新读取后重试：taskId=" + taskId
                + "，期望版本=" + expectedVersion + "，当前状态=" + current.getStatus());
        }
        AigTask latest = loadTask(taskId);
        appendEvent(latest, nextSequence(taskId), AigTaskEventTypeEnum.AI_TASK_STATUS_CHANGED, from, to,
            "已派发到 Provider=" + providerCode + "，外部作业ID=" + StringUtils.blankToDefault(providerJobId, "-")
                + "，外部调用=" + (externalCall ? "是" : "否"), null);
        log.info("任务已派发, taskId={}, providerCode={}, providerJobId={}, externalCall={}",
            taskId, providerCode, providerJobId, externalCall);
        return latest;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AigTaskStatusEnum recordFailure(Long taskId, Integer expectedVersion, AigErrorClassEnum errorClass,
                                           String errorMessage) {
        // 第一步：先记失败（→ FAILED），顺带把本次尝试计入 attempt_no
        AigTask failed = transition(taskId, expectedVersion, AigTaskStatusEnum.FAILED,
            "执行失败" + (errorClass == null ? "（错误分类未知）" : "（分类 " + errorClass.getCode()
                + "：" + errorClass.getDesc() + "）") + StringUtils.blankToDefault(errorMessage, ""),
            null);
        AigTaskStatusEnum resting = AigTaskStateMachine.restingAfterFailure(errorClass,
            failed.getAttemptNo() == null ? 0 : failed.getAttemptNo(),
            failed.getMaxAttempt() == null ? DEFAULT_MAX_ATTEMPT : failed.getMaxAttempt());
        if (resting == AigTaskStatusEnum.FAILED) {
            // 「停在 FAILED」不是迁移，因此不再写事件——事件流的语义是「状态变了」。
            // 停留原因已在上面那条 FAILED 事件的说明里，不需要额外一条「没变化」的记录。
            log.info("任务失败后停在 FAILED（无自动出路，需运维/人工介入）, taskId={}, attemptNo={}",
                taskId, failed.getAttemptNo());
            return resting;
        }
        AigTask next = transition(taskId, failed.getVersion(), resting,
            resting == AigTaskStatusEnum.RETRY_WAIT
                ? "可自动重试（已尝试 " + failed.getAttemptNo() + "/" + failed.getMaxAttempt() + " 次），转入等待重试"
                : "按错误分类转人工处理", null);
        return AigTaskStatusEnum.find(next.getStatus());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long recordResult(AigTaskResultBo bo) {
        if (bo == null || bo.getTaskId() == null) {
            throw new ServiceException("任务ID不能为空");
        }
        AigTask task = loadTask(bo.getTaskId());
        AigCandidateStatusEnum candidate = StringUtils.isBlank(bo.getCandidateStatus())
            ? AigCandidateStatusEnum.CANDIDATE
            : AigCandidateStatusEnum.find(bo.getCandidateStatus());
        if (candidate == null) {
            throw new ServiceException("未知的候选状态：" + bo.getCandidateStatus());
        }
        if (!candidate.isAutoAssignable()) {
            // 自动流程只筛除、不放行：选定哪一张有业务后果，必须有人担。
            // 让编排器直接置 APPROVED，一旦选错就找不到责任人。
            throw new ServiceException("候选状态 " + candidate.getCode()
                + " 不能由自动流程写入：自动 QA 只筛除、不放行，选定必须由人工操作");
        }
        AigTaskResult result = new AigTaskResult();
        result.setTaskId(bo.getTaskId());
        result.setAttemptNo(bo.getAttemptNo() == null
            ? (task.getAttemptNo() == null ? 0 : task.getAttemptNo()) : bo.getAttemptNo());
        result.setResultType(bo.getResultType());
        result.setAssetId(bo.getAssetId());
        result.setStructuredOutputJson(bo.getStructuredOutputJson());
        result.setValidationResult(bo.getValidationResult());
        result.setValidationDetail(bo.getValidationDetail());
        result.setCandidateStatus(candidate.getCode());
        result.setQaVerdict(bo.getQaVerdict());
        result.setQaDetail(bo.getQaDetail());
        result.setDelFlag("0");
        result.setRemark(bo.getRemark());
        resultMapper.insert(result);
        appendEvent(task, nextSequence(bo.getTaskId()), AigTaskEventTypeEnum.AI_TASK_RESULT_RECORDED, null, null,
            "回写结果 resultId=" + result.getResultId() + "，类型=" + bo.getResultType()
                + "，候选状态=" + candidate.getCode(), null);
        log.info("回写任务结果, taskId={}, resultId={}, resultType={}, candidateStatus={}",
            bo.getTaskId(), result.getResultId(), bo.getResultType(), candidate.getCode());
        return result.getResultId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long selectCandidate(AigTaskResultSelectBo bo) {
        if (bo == null || bo.getTaskId() == null || bo.getResultId() == null) {
            throw new ServiceException("任务ID与候选结果ID不能为空");
        }
        AigTask task = loadTask(bo.getTaskId());
        // 只有「待人工复核」的任务才谈得上交付物：未成功、已取消、已终态的任务不该产出选定结果
        if (!AigTaskStatusEnum.REVIEW_PENDING.getCode().equals(task.getStatus())) {
            throw new ServiceException("只有「待人工复核」的任务可以选定候选，当前状态=" + task.getStatus()
                + "：taskId=" + bo.getTaskId()
                + "（执行尚未成功，或任务已被取消/拒绝；这些情况下没有可交付的候选）");
        }
        AigTaskResult result = resultMapper.selectById(bo.getResultId());
        if (result == null) {
            throw new ServiceException("候选结果不存在：" + bo.getResultId());
        }
        // 防止「拿 A 任务的 resultId 去选定到 B 任务下」——参数来自前端，必须核对归属
        if (!bo.getTaskId().equals(result.getTaskId())) {
            throw new ServiceException("候选结果 " + bo.getResultId() + " 不属于任务 "
                + bo.getTaskId() + "（实际属于 " + result.getTaskId() + "）");
        }
        AigCandidateStatusEnum current = AigCandidateStatusEnum.find(result.getCandidateStatus());
        if (current == AigCandidateStatusEnum.APPROVED) {
            return bo.getResultId();
        }
        if (current == AigCandidateStatusEnum.REJECTED) {
            // 自动 QA 明确筛除的候选不接受选定：要推翻自动结论需要一条显式通道，
            // 不能让「已知不合格」的候选因为一次顺手点击变成交付物
            throw new ServiceException("该候选已被自动质检筛除，不能直接选定。"
                + "若确认质检结论有误，请先修正质检依据（这是一条需要显式开放的通道）");
        }

        // 单选：同一任务只允许一个「已选定」。允许并列会让「到底交付哪一张」无法回答，
        // 而下游（投放/排版/交付清单）需要一个确定的答案。
        List<AigTaskResult> approved = resultMapper.selectList(new LambdaQueryWrapper<AigTaskResult>()
            .eq(AigTaskResult::getTaskId, bo.getTaskId())
            .eq(AigTaskResult::getCandidateStatus, AigCandidateStatusEnum.APPROVED.getCode()));
        if (CollUtil.isNotEmpty(approved)) {
            for (AigTaskResult previous : approved) {
                if (previous.getResultId().equals(bo.getResultId())) {
                    continue;
                }
                AigTaskResult reset = new AigTaskResult();
                reset.setResultId(previous.getResultId());
                // 回到候选而不是筛除：它只是「没被选中」，不是「不合格」
                reset.setCandidateStatus(AigCandidateStatusEnum.CANDIDATE.getCode());
                reset.setSelectedBy(null);
                reset.setSelectedAt(null);
                resultMapper.updateById(reset);
                appendEvent(task, nextSequence(bo.getTaskId()), AigTaskEventTypeEnum.AI_TASK_REVIEWED,
                    null, null, "取消此前的选定 resultId=" + previous.getResultId()
                        + "（同任务单选：改为选定 resultId=" + bo.getResultId() + "）", null);
            }
        }

        AigTaskResult update = new AigTaskResult();
        update.setResultId(bo.getResultId());
        update.setCandidateStatus(AigCandidateStatusEnum.APPROVED.getCode());
        update.setSelectedBy(actorProvider.currentUserId());
        update.setSelectedAt(LocalDateTime.now());
        update.setRemark(bo.getRemark());
        resultMapper.updateById(update);
        appendEvent(task, nextSequence(bo.getTaskId()), AigTaskEventTypeEnum.AI_TASK_REVIEWED, null, null,
            "人工选定交付物 resultId=" + bo.getResultId()
                + StringUtils.blankToDefault(bo.getRemark(), ""), null);
        log.info("人工选定候选资产, taskId={}, resultId={}, selectedBy={}",
            bo.getTaskId(), bo.getResultId(), actorProvider.currentUserId());
        return bo.getResultId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AigTask review(AigTaskReviewBo bo) {
        if (bo == null || bo.getTaskId() == null) {
            throw new ServiceException("任务ID不能为空");
        }
        if (bo.getApproved() == null) {
            throw new ServiceException("复核结论不能为空");
        }
        if (!Boolean.TRUE.equals(bo.getApproved()) && StringUtils.isBlank(bo.getComment())) {
            // 拒绝必须说明原因：没有理由的拒绝既无法申诉也无法改进
            throw new ServiceException("拒绝时必须填写复核意见");
        }
        AigTaskStatusEnum target = Boolean.TRUE.equals(bo.getApproved())
            ? AigTaskStatusEnum.APPROVED : AigTaskStatusEnum.REJECTED;
        AigTask reviewed = transition(bo.getTaskId(), bo.getExpectedVersion(), target,
            (Boolean.TRUE.equals(bo.getApproved()) ? "人工复核通过" : "人工复核拒绝")
                + StringUtils.blankToDefault(bo.getComment(), ""), null);

        AigTask update = new AigTask();
        update.setTaskId(reviewed.getTaskId());
        update.setVersion(reviewed.getVersion());
        update.setReviewStatus(target.getCode());
        update.setReviewedBy(actorProvider.currentUserId());
        update.setReviewedAt(LocalDateTime.now());
        update.setReviewComment(bo.getComment());
        taskMapper.updateById(update);
        return loadTask(bo.getTaskId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AigCallbackVo handleCallback(AigTaskCallbackBo bo) {
        if (bo == null) {
            return notAdvanced("REJECTED_UNSIGNED", "回调入参为空", null);
        }
        String payloadHash = DigestUtil.sha256Hex(StringUtils.blankToDefault(bo.getRawPayload(), ""));
        AigTaskCallbackSigner.VerifyResult verify = callbackSigner.verify(bo.getProviderCode(),
            bo.getRawPayload(), bo.getSignAlgorithm(), bo.getSignature());
        if (!verify.verified()) {
            // 未验签一律不推进状态，但必须留痕：是对方乱推、密钥配错，还是有人伪造，
            // 这三种情况的处置完全不同，没有记录就无从判断
            writeCallback(null, bo, payloadHash, NO, verify.detail(), "REJECTED_UNSIGNED", NO);
            log.warn("回调验签未通过, providerCode={}, jobId={}, 原因={}",
                bo.getProviderCode(), bo.getProviderJobId(), verify.detail());
            return notAdvanced("REJECTED_UNSIGNED", "验签未通过：" + verify.detail(), null);
        }

        // 幂等：同一 (providerCode, eventId) 只处理一次。
        // 账本的口径是「一行一个事件」，不是「一行一次投递」——否则重复投递会把账本撑成
        // 同一件事的多行，按事件计数的地方就会重复统计。故重复投递只回报不复记。
        if (StringUtils.isNotBlank(bo.getEventId())) {
            AigCallback existing = findCallback(bo.getProviderCode(), bo.getEventId());
            if (existing != null) {
                log.info("回调重复投递（幂等忽略）, providerCode={}, eventId={}, 首次处理={}",
                    bo.getProviderCode(), bo.getEventId(), existing.getProcessResult());
                AigCallbackVo duplicate = base("DUPLICATE", "该事件已处理过，本次幂等忽略");
                duplicate.setTaskId(existing.getTaskId());
                duplicate.setDuplicate(true);
                return duplicate;
            }
        }

        AigTask task = StringUtils.isBlank(bo.getProviderJobId()) ? null
            : taskMapper.selectOne(new LambdaQueryWrapper<AigTask>()
                .eq(AigTask::getProviderCode, bo.getProviderCode())
                .eq(AigTask::getProviderJobId, bo.getProviderJobId()));
        if (task == null) {
            writeCallback(null, bo, payloadHash, YES, "按 providerCode + providerJobId 未定位到任务",
                "TASK_NOT_FOUND", NO);
            log.warn("回调定位不到任务, providerCode={}, jobId={}", bo.getProviderCode(), bo.getProviderJobId());
            return notAdvanced("TASK_NOT_FOUND", "定位不到对应任务：providerCode=" + bo.getProviderCode()
                + "，providerJobId=" + bo.getProviderJobId(), null);
        }

        AigTaskStatusEnum to = AigTaskStatusEnum.find(bo.getToStatus());
        if (to == null) {
            writeCallback(task.getTaskId(), bo, payloadHash, YES, "未知目标状态：" + bo.getToStatus(),
                "ORDER_STALE", NO);
            return notAdvanced("ORDER_STALE", "回调声明了未知的目标状态：" + bo.getToStatus(), task.getTaskId());
        }
        AigTaskStatusEnum current = AigTaskStatusEnum.find(task.getStatus());
        if (!AigTaskStateMachine.canTransition(current, to)) {
            // 顺序过期（ORDER_STALE）：迟到或重复的完成回调打在已终态/已变更的任务上。
            // 这类回调绝不能照单全收——一条迟到的「成功」若盖掉已经取消的任务，
            // 账上就会出现「已取消却拿到了结果」。
            writeCallback(task.getTaskId(), bo, payloadHash, YES,
                "顺序过期：当前状态 " + task.getStatus() + "，" + AigTaskStateMachine.describeAllowed(current),
                "ORDER_STALE", NO);
            log.warn("回调顺序过期, taskId={}, 当前={}, 回调要求={}", task.getTaskId(), task.getStatus(),
                to.getCode());
            AigCallbackVo stale = base("ORDER_STALE", "回调顺序过期，未推进状态（当前 "
                + task.getStatus() + " 不允许迁移到 " + to.getCode() + "）");
            stale.setTaskId(task.getTaskId());
            return stale;
        }
        try {
            AigTask moved = transition(task.getTaskId(), task.getVersion(), to,
                StringUtils.blankToDefault(bo.getDetail(), "Provider 回调推进状态"), null);
            writeCallback(task.getTaskId(), bo, payloadHash, YES, "已按回调推进状态", "ACCEPTED", NO);
            AigCallbackVo accepted = base("ACCEPTED", "已推进任务状态到 " + moved.getStatus());
            accepted.setTaskId(task.getTaskId());
            accepted.setTaskStatus(moved.getStatus());
            accepted.setAccepted(true);
            return accepted;
        } catch (ServiceException e) {
            // 并发冲突（乐观锁）在这里落地为 ORDER_STALE：状态已被别的路径改过，
            // 本条回调基于的「当前状态」判断已经失效
            writeCallback(task.getTaskId(), bo, payloadHash, YES, "并发冲突：" + e.getMessage(),
                "ORDER_STALE", NO);
            AigCallbackVo conflict = base("ORDER_STALE", "并发冲突，未推进状态：" + e.getMessage());
            conflict.setTaskId(task.getTaskId());
            return conflict;
        }
    }

    /**
     * 冻结输入快照。
     *
     * <p>哈希对<b>原样入库的那串字节</b>计算，而不是对反序列化再序列化的结果——
     * 后者会随序列化配置（字段顺序、null 处理）产生不同字节，
     * 于是「快照有没有被改过」这个判断会时不时误报，最终没人再信它。</p>
     *
     * @param task 任务（此时可能尚未落库，只用到数据等级等字段）
     * @param bo   创建入参
     * @return 快照实体（未落库）
     */
    private AigTaskSnapshot freezeSnapshot(AigTask task, AigTaskCreateBo bo) {
        AigTaskSnapshot snapshot = new AigTaskSnapshot();
        snapshot.setTaskId(task.getTaskId());
        snapshot.setSnapshotVersion(1);
        snapshot.setSnapshotJson(bo.getSnapshotJson());
        snapshot.setSnapshotHash(DigestUtil.sha256Hex(bo.getSnapshotJson()));
        snapshot.setDataLevel(task.getDataLevel());
        snapshot.setAllowExternal(task.getAllowExternal());
        snapshot.setBudgetAmount(bo.getBudgetAmount());
        snapshot.setNegativeConstraints(bo.getNegativeConstraints());
        snapshot.setFrozenAt(LocalDateTime.now());
        snapshot.setCreateBy(actorProvider.currentUserId());
        snapshot.setCreateTime(LocalDateTime.now());
        return snapshot;
    }

    /**
     * 施加状态迁移带来的字段副作用。
     *
     * <p>两处规则写在这里而不是散落在调用点：</p>
     * <ol>
     *     <li><b>迁移到 FAILED 时 {@code attempt_no} 加一</b>：{@code attempt_no} 是
     *         「已尝试次数」，也是幂等键的一半（{@code task_id + attempt_no}，重试不重复计费）。
     *         失败不计数的话，同一幂等键会被下一次尝试复用，重试就变成了「重复提交同一笔」；</li>
     *     <li><b>进入 RUNNING 记开始时间、进入成功/失败/取消记结束时间</b>：
     *         耗时与「卡了多久」都靠这两个时间戳，缺了就只能靠事件时间倒推。</li>
     * </ol>
     *
     * @param update 待提交的更新实体
     * @param current 当前任务（取旧值）
     * @param from    源状态
     * @param to      目标状态
     */
    private void applyStatusSideEffects(AigTask update, AigTask current, AigTaskStatusEnum from,
                                        AigTaskStatusEnum to) {
        if (to == AigTaskStatusEnum.FAILED) {
            int attempt = current.getAttemptNo() == null ? 0 : current.getAttemptNo();
            update.setAttemptNo(attempt + 1);
        }
        if (to == AigTaskStatusEnum.RUNNING && from == AigTaskStatusEnum.DISPATCHED) {
            update.setStartedAt(LocalDateTime.now());
        }
        if (to == AigTaskStatusEnum.SUCCEEDED || to == AigTaskStatusEnum.FAILED
            || to == AigTaskStatusEnum.CANCELLED) {
            update.setFinishedAt(LocalDateTime.now());
        }
    }

    /**
     * 解析本次创建的「提交人」，作为幂等作用域。
     *
     * <p><b>为什么不直接用 {@link AigTaskActorProvider#currentUserId()}</b>：它可以是 null
     * （调度器/Agent 发起，没有登录用户），而 null 在幂等唯一键与等值查询里都不能用——
     * 前者不约束 NULL，后者恒为 UNKNOWN。用哨兵值把「系统」变成一个真实身份，
     * 重复提交才会被真正挡住。</p>
     *
     * @return 登录用户ID；无登录上下文时返回 {@link AigConstants#SYSTEM_SUBMITTER_ID}
     */
    private Long resolveSubmitterId() {
        Long actorId = actorProvider.currentUserId();
        return actorId == null ? AigConstants.SYSTEM_SUBMITTER_ID : actorId;
    }

    /**
     * 查询创建幂等键对应的既有任务。
     *
     * @param projectType    业务域
     * @param idempotencyKey 幂等键
     * @param submitterId    提交人（{@link #resolveSubmitterId()} 的结果，保证非空）
     * @return 既有任务ID；无则返回 null
     */
    private Long findByIdempotencyKey(String projectType, String idempotencyKey, Long submitterId) {
        if (StringUtils.isBlank(idempotencyKey)) {
            return null;
        }
        AigTask existing = taskMapper.selectOne(new LambdaQueryWrapper<AigTask>()
            .eq(AigTask::getProjectType, projectType)
            .eq(AigTask::getIdempotencyKey, idempotencyKey)
            .eq(AigTask::getCreateBy, submitterId)
            .last("limit 1"));
        return existing == null ? null : existing.getTaskId();
    }

    /**
     * 取任务内下一个事件序号（当前最大值 + 1）。
     *
     * <p>唯一键 {@code (task_id, sequence)} 是兜底：并发写入若产生重号，插入直接失败，
     * 而不是留下两条顺序不明的事件。</p>
     *
     * @param taskId 任务ID
     * @return 下一个序号（从 1 开始）
     */
    private int nextSequence(Long taskId) {
        List<AigTaskEvent> events = eventMapper.selectList(new LambdaQueryWrapper<AigTaskEvent>()
            .eq(AigTaskEvent::getTaskId, taskId)
            .orderByDesc(AigTaskEvent::getSequence)
            .last("limit 1"));
        if (events == null || events.isEmpty() || events.get(0).getSequence() == null) {
            return 1;
        }
        return events.get(0).getSequence() + 1;
    }

    /**
     * 追加一条任务事件。
     *
     * @param task       任务
     * @param sequence   序号
     * @param type       事件类型
     * @param from       源状态（可空）
     * @param to         目标状态（可空）
     * @param detail     可读说明
     * @param payloadJson 载荷（可空）
     */
    private void appendEvent(AigTask task, int sequence, AigTaskEventTypeEnum type,
                             AigTaskStatusEnum from, AigTaskStatusEnum to,
                             String detail, String payloadJson) {
        AigTaskEvent event = new AigTaskEvent();
        event.setTaskId(task.getTaskId());
        event.setSequence(sequence);
        event.setEventType(type.getCode());
        event.setFromStatus(from == null ? null : from.getCode());
        event.setToStatus(to == null ? null : to.getCode());
        event.setAttemptNo(task.getAttemptNo());
        event.setPayloadJson(payloadJson);
        event.setDetail(detail);
        event.setActorId(actorProvider.currentUserId());
        event.setTraceId(task.getTraceId());
        event.setOperateTime(LocalDateTime.now());
        eventMapper.insert(event);
    }

    /**
     * 按幂等键查已处理的回调。
     *
     * @param providerCode Provider 编码
     * @param eventId      外部事件ID
     * @return 既有回调；无则返回 null
     */
    private AigCallback findCallback(String providerCode, String eventId) {
        return callbackMapper.selectOne(new LambdaQueryWrapper<AigCallback>()
            .eq(AigCallback::getProviderCode, providerCode)
            .eq(AigCallback::getEventId, eventId)
            .last("limit 1"));
    }

    /**
     * 写一条回调账本（无论成败都留痕）。
     *
     * <p><b>唯一键兜底</b>：前置查重是「先查后插」，并发下存在窗口。此时唯一键
     * {@code (provider_code, event_id)} 会拒绝插入——这里把它当成「同一事件已被并发方记录」，
     * 记一条 warn 后返回，而不是让整次回调以数据库异常收场。理由：重复投递是常态
     * （对方超时重推），把它变成 500 会让对方继续重推，而每次重推都可能再撞一次。</p>
     *
     * <p><b>与数据库语义相关</b>：本处理依赖 MySQL/MariaDB 的行为——唯一键冲突
     * 只让该语句失败，不会把整个事务置为中止状态。若将来部署到 PostgreSQL，
     * 需把账本写入改成独立事务（REQUIRES_NEW）的记录器，否则冲突后无法继续本事务。</p>
     *
     * @param taskId        任务ID（可空）
     * @param bo            回调入参
     * @param payloadHash   载荷哈希
     * @param verified      验签是否通过（Y/N）
     * @param detail        处理说明
     * @param processResult 处理结果
     * @param idempotentHit 是否命中重复（Y/N）
     */
    private void writeCallback(Long taskId, AigTaskCallbackBo bo, String payloadHash, String verified,
                               String detail, String processResult, String idempotentHit) {
        AigCallback callback = new AigCallback();
        callback.setTaskId(taskId);
        callback.setProviderCode(bo.getProviderCode());
        callback.setProviderJobId(bo.getProviderJobId());
        callback.setEventId(bo.getEventId());
        callback.setSignatureVerified(verified);
        callback.setSignAlgorithm(StringUtils.blankToDefault(bo.getSignAlgorithm(),
            AigTaskCallbackSigner.ALGORITHM));
        callback.setPayloadHash(payloadHash);
        callback.setIdempotentHit(idempotentHit);
        callback.setProcessResult(processResult);
        callback.setDetail(StringUtils.substring(detail, 0, 1000));
        callback.setReceivedAt(LocalDateTime.now());
        try {
            callbackMapper.insert(callback);
        } catch (DuplicateKeyException e) {
            log.warn("回调账本唯一键冲突（并发重复投递），已按幂等忽略：providerCode={}, eventId={}",
                bo.getProviderCode(), bo.getEventId());
        }
    }

    /**
     * 加载任务，不存在时抛异常。
     *
     * @param taskId 任务ID
     * @return 任务
     */
    private AigTask loadTask(Long taskId) {
        AigTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new ServiceException("任务不存在：" + taskId);
        }
        return task;
    }

    /**
     * 生成不可猜测的任务号。
     *
     * <p>刻意不用自增/snowflake：任务号会出现在回调地址、日志与用户可见的界面上，
     * 可枚举的编号意味着别人能按顺序猜到别人的任务。</p>
     *
     * @return 32 位任务号
     */
    private String newTaskNo() {
        String raw = IdUtil.fastSimpleUUID();
        return raw.length() > TASK_NO_LENGTH ? raw.substring(0, TASK_NO_LENGTH) : raw;
    }

    /**
     * 构造一个基础回调结果。
     *
     * @param processResult 处理结果
     * @param detail        说明
     * @return 结果对象
     */
    private AigCallbackVo base(String processResult, String detail) {
        AigCallbackVo vo = new AigCallbackVo();
        vo.setProcessResult(processResult);
        vo.setDetail(detail);
        return vo;
    }

    /**
     * 构造一个「未推进」的回调结果。
     *
     * <p>{@code processResult} 由调用方显式传入而不是从文案里推断：它是与回调账本
     * 同口径的机器可读结论，靠判断说明文本的首字来定码，改一次文案就会静默错位。</p>
     *
     * @param processResult 处理结果（REJECTED_UNSIGNED/TASK_NOT_FOUND/ORDER_STALE…）
     * @param detail        说明
     * @param taskId        任务ID（可空）
     * @return 结果对象
     */
    private AigCallbackVo notAdvanced(String processResult, String detail, Long taskId) {
        AigCallbackVo vo = base(processResult, detail);
        vo.setTaskId(taskId);
        return vo;
    }

}
