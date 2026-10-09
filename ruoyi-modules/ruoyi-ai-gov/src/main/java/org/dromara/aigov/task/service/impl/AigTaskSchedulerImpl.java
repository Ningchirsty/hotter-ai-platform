package org.dromara.aigov.task.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.enums.AigDataLevelEnum;
import org.dromara.aigov.enums.AigErrorClassEnum;
import org.dromara.aigov.enums.AigRouteDecisionEnum;
import org.dromara.aigov.domain.vo.AigRouteDecision;
import org.dromara.aigov.domain.vo.AigRouteHint;
import org.dromara.aigov.service.IAigRouteService;
import org.dromara.aigov.task.config.AigTaskSchedulerProperties;
import org.dromara.aigov.task.domain.AigTask;
import org.dromara.aigov.task.domain.vo.AigTaskSweepVo;
import org.dromara.aigov.task.enums.AigTaskExecutionModeEnum;
import org.dromara.aigov.task.enums.AigTaskPolicyResultEnum;
import org.dromara.aigov.task.enums.AigTaskStatusEnum;
import org.dromara.aigov.task.mapper.AigTaskMapper;
import org.dromara.aigov.task.service.IAigTaskScheduler;
import org.dromara.aigov.task.service.IAigTaskService;
import org.dromara.aigov.task.state.AigTaskStateMachine;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务调度实现。
 *
 * @author ai-gov
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigTaskSchedulerImpl implements IAigTaskScheduler {

    private final AigTaskMapper taskMapper;
    private final IAigTaskService taskService;
    private final AigTaskSchedulerProperties properties;

    /**
     * 路由引擎：预检要用它判「这条任务按策略能不能跑、跑谁」。
     */
    private final IAigRouteService routeService;

    @Override
    public AigTaskSweepVo sweep() {
        AigTaskSweepVo result = new AigTaskSweepVo();
        // 0. 新建任务的策略预检（DRAFT → POLICY_CHECKING → QUEUED/REJECTED/NEED_HUMAN）
        sweepPolicyCheck(result);
        // 1. 待重试任务重新入队
        sweepRetryWait(result);
        // 2. 在途任务超时
        sweepTimeout(result);
        if (result.getRetried() > 0 || result.getTimedOut() > 0 || result.getPolicyChecked() > 0
            || !result.getFailures().isEmpty()) {
            log.info("任务调度扫描完成, retryCandidate={}, retried={}, timeoutCandidate={}, timedOut={}, "
                    + "policyCandidate={}, policyChecked={}, skipped={}, failures={}",
                result.getRetryCandidate(), result.getRetried(), result.getTimeoutCandidate(),
                result.getTimedOut(), result.getPolicyCandidate(), result.getPolicyChecked(),
                result.getSkipped(), result.getFailures());
        }
        return result;
    }

    /**
     * 对「新建（{@code DRAFT}）且平台执行」的任务做策略预检，并推进到它的结论状态。
     *
     * <p><b>它补的是什么</b>：状态机的主干是
     * {@code DRAFT → POLICY_CHECKING → QUEUED|REJECTED|NEED_HUMAN}，但<b>没有任何东西驱动第一步</b>——
     * 调度器只处理 {@code RETRY_WAIT} 与超时，接口层也没有这一步（{@code /requeue} 只能从
     * 已有出边的状态走）。结果是：通过 {@code POST /aigov/task} 建出来的平台任务<b>永远停在 DRAFT</b>，
     * 而 {@code /execute} 又要求 {@code QUEUED} ⇒ 这条链路端到端走不通，平台上"建了就能跑"的任务
     * 实际上一次都跑不起来。（业务域执行的任务不受影响：它们走 {@code createDispatched}，
     * 刻意跳过策略校验与排队，由业务域自己驱动。）</p>
     *
     * <p><b>为什么放在扫描里而不是创建时同步做</b>：设计里 {@code DRAFT} 这个状态存在的意义
     * 就是"先登记、再由平台做策略校验"；F-04 已冻"无 MQ，编排按 sweep 模式"，把
     * 预检放进扫描与既有架构一致，也不会让"建任务"这个接口承担一次完整路由判定
     * （那会让建任务在策略表缺失时直接变成一个失败调用）。扫描周期即预检延迟上限。</p>
     *
     * <p><b>结论写哪</b>：{@code policy_result/policy_reason} 两列 + 事件
     * {@code AI_TASK_POLICY_DECIDED}（走 {@code recordPolicyDecision}）。这样即使任务
     * 被预检判为拒绝/转人工而<b>从未执行</b>，治理台也能看到"为什么没跑"。
     * 执行时那条路由结论仍会再写一次（那时以实际选中的模型为准）——两处都留痕是有意的：
     * 预检结论与执行结论回答的不是同一个问题（能不能跑 vs 实际跑了谁）。</p>
     *
     * @param result 扫描结果（累计计数）
     */
    private void sweepPolicyCheck(AigTaskSweepVo result) {
        List<AigTask> candidates = taskMapper.selectList(new LambdaQueryWrapper<AigTask>()
            .eq(AigTask::getStatus, AigTaskStatusEnum.DRAFT.getCode())
            // 只碰「平台执行」：业务域执行的任务由业务域驱动，平台替它做策略校验并排队
            // 等于替它决定"要不要跑"，而它的执行根本不经平台
            .eq(AigTask::getExecutionMode, AigTaskExecutionModeEnum.PLATFORM.getCode())
            .orderByAsc(AigTask::getCreateTime)
            .last("limit " + batchSize()));
        result.setPolicyCandidate(candidates == null ? 0 : candidates.size());
        if (candidates == null) {
            return;
        }
        for (AigTask task : candidates) {
            try {
                if (advanceThroughPolicyCheck(task)) {
                    result.setPolicyChecked(result.getPolicyChecked() + 1);
                } else {
                    result.setSkipped(result.getSkipped() + 1);
                }
            } catch (ServiceException e) {
                result.setSkipped(result.getSkipped() + 1);
                log.debug("跳过待预检任务 taskId={}：{}", task.getTaskId(), e.getMessage());
            } catch (Exception e) {
                result.getFailures().add("taskId=" + task.getTaskId() + " 策略预检失败：" + e.getMessage());
                log.error("策略预检失败, taskId={}", task.getTaskId(), e);
            }
        }
    }

    /**
     * 把一个 {@code DRAFT} 任务推过策略预检。
     *
     * @param task 任务（DRAFT + PLATFORM）
     * @return 完成了预检返回 true；结论无法映射（不该发生）返回 false
     */
    private boolean advanceThroughPolicyCheck(AigTask task) {
        AigRouteDecision decision = routeService.decide(task.getCapabilityCode(),
            AigDataLevelEnum.find(task.getDataLevel()), AigRouteHint.ofScenario(task.getScenarioCode()));
        if (decision == null) {
            // 路由引擎的约定是"不抛异常、一律用 decision 表达结论"，因此正常永远不该为空。
            // 为空说明接线被破坏（或替身没打桩）——如实跳过并留下原因，而不是抛 NPE 让整轮扫描失败
            log.warn("路由引擎未返回决策对象，跳过预检, taskId={}", task.getTaskId());
            return false;
        }
        AigTaskPolicyResultEnum policyResult =
            AigTaskPolicyResultEnum.fromDecision(AigRouteDecisionEnum.find(decision.getDecision()));
        if (policyResult == null) {
            log.warn("路由结论无法映射为任务级策略结论，跳过预检, taskId={}, decision={}",
                task.getTaskId(), decision.getDecision());
            return false;
        }
        AigTaskStatusEnum target = AigTaskStateMachine.nextAfterPolicy(policyResult.getCode());
        if (target == null) {
            log.warn("策略结论没有对应状态，跳过预检, taskId={}, policyResult={}",
                task.getTaskId(), policyResult.getCode());
            return false;
        }
        // 两步走：先入「策略校验中」（那是这次检查开始的事实），再按结论落位
        AigTask checking = taskService.transition(task.getTaskId(), task.getVersion(),
            AigTaskStatusEnum.POLICY_CHECKING, "调度器开始策略预检（能力=" + task.getCapabilityCode()
                + "，数据等级=" + task.getDataLevel() + "）", null);
        AigTask decided = taskService.recordPolicyDecision(task.getTaskId(), checking.getVersion(),
            policyResult.getCode(), decision.getReason(), decision.getReasonCode());
        String reasonText = StringUtils.isBlank(decision.getReason()) ? "" : "：" + decision.getReason();
        taskService.transition(task.getTaskId(), decided.getVersion(), target,
            "策略预检结论 " + policyResult.getCode() + "（" + policyResult.getDesc() + "）" + reasonText
                + " → " + target.getCode(), null);
        log.info("策略预检完成, taskId={}, policyResult={}, reasonCode={}, 落点={}",
            task.getTaskId(), policyResult.getCode(), decision.getReasonCode(), target.getCode());
        return true;
    }

    /**
     * 把停留超过退避时长的 {@code RETRY_WAIT} 任务重新入队。
     *
     * @param result 扫描结果（累计计数）
     */
    private void sweepRetryWait(AigTaskSweepVo result) {
        LocalDateTime deadline = LocalDateTime.now().minusSeconds(Math.max(0L, properties.getRetryDelaySeconds()));
        List<AigTask> candidates = taskMapper.selectList(new LambdaQueryWrapper<AigTask>()
            .eq(AigTask::getStatus, AigTaskStatusEnum.RETRY_WAIT.getCode())
            // 只碰「平台执行」的任务：业务域自己执行的任务（execution_mode=EXTERNAL）
            // 由业务域决定要不要重试；平台把它重新入队会**再执行一遍**（两份产出、两次计费）
            .eq(AigTask::getExecutionMode, AigTaskExecutionModeEnum.PLATFORM.getCode())
            .le(AigTask::getUpdateTime, deadline)
            .orderByAsc(AigTask::getUpdateTime)
            .last("limit " + batchSize()));
        result.setRetryCandidate(candidates == null ? 0 : candidates.size());
        if (candidates == null) {
            return;
        }
        for (AigTask task : candidates) {
            try {
                taskService.transition(task.getTaskId(), task.getVersion(), AigTaskStatusEnum.QUEUED,
                    "调度器驱动重试：等待已超过 " + properties.getRetryDelaySeconds() + " 秒，重新入队", null);
                result.setRetried(result.getRetried() + 1);
            } catch (ServiceException e) {
                // 乐观锁冲突（别的实例抢到了）或状态已变：都算跳过，不是错误。
                // 区分它们在多实例下没有意义——两者的正确处置都是「什么都不做」
                result.setSkipped(result.getSkipped() + 1);
                log.debug("跳过待重试任务 taskId={}：{}", task.getTaskId(), e.getMessage());
            } catch (Exception e) {
                result.getFailures().add("taskId=" + task.getTaskId() + " 重排失败：" + e.getMessage());
                log.error("重排任务失败, taskId={}", task.getTaskId(), e);
            }
        }
    }

    /**
     * 把「已派发/执行中」但长时间没有动静的任务记为失败。
     *
     * <p><b>为什么用 {@code update_time} 而不是 {@code started_at}</b>：异步 Provider 可能在提交后
     * 很久才开始跑，{@code started_at} 为空。{@code update_time} 是「最后一次有任何变化」，
     * 对「卡住了没有」这个问题，它才是正确的判据。</p>
     *
     * @param result 扫描结果（累计计数）
     */
    private void sweepTimeout(AigTaskSweepVo result) {
        LocalDateTime deadline = LocalDateTime.now().minusSeconds(Math.max(0L, properties.getTimeoutSeconds()));
        List<AigTask> candidates = taskMapper.selectList(new LambdaQueryWrapper<AigTask>()
            .in(AigTask::getStatus, List.of(AigTaskStatusEnum.DISPATCHED.getCode(),
                AigTaskStatusEnum.RUNNING.getCode()))
            // 只碰「平台执行」的任务：业务域执行的任务在途多久都不该由平台判超时失败——
            // 内核可能还在出图，账上已经 FAILED（甚至转人工重排，又一次计费）
            .eq(AigTask::getExecutionMode, AigTaskExecutionModeEnum.PLATFORM.getCode())
            .le(AigTask::getUpdateTime, deadline)
            .orderByAsc(AigTask::getUpdateTime)
            .last("limit " + batchSize()));
        result.setTimeoutCandidate(candidates == null ? 0 : candidates.size());
        if (candidates == null) {
            return;
        }
        for (AigTask task : candidates) {
            try {
                // 归类为 TIMEOUT（可重试），让它走「FAILED → RETRY_WAIT / NEED_HUMAN」的正常分支，
                // 而不是直接判死：超时是典型可恢复错误，重试往往就能过
                taskService.recordFailure(task.getTaskId(), task.getVersion(), AigErrorClassEnum.TIMEOUT,
                    "超过 " + properties.getTimeoutSeconds() + " 秒无任何进展（已派发/执行中）；"
                        + "按可重试错误处理，走 FAILED → 重试或转人工");
                result.setTimedOut(result.getTimedOut() + 1);
            } catch (ServiceException e) {
                result.setSkipped(result.getSkipped() + 1);
                log.debug("跳过超时任务 taskId={}：{}", task.getTaskId(), e.getMessage());
            } catch (Exception e) {
                result.getFailures().add("taskId=" + task.getTaskId() + " 超时处理失败：" + e.getMessage());
                log.error("处理超时任务失败, taskId={}", task.getTaskId(), e);
            }
        }
    }

    /**
     * 单次扫描的批量上限（至少 1，避免配成 0 时生成 {@code limit 0} 而永远扫不到东西）。
     *
     * @return 批量上限
     */
    private int batchSize() {
        return Math.max(1, properties.getBatchSize());
    }

}
