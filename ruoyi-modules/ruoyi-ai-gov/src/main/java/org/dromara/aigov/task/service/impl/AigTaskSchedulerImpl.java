package org.dromara.aigov.task.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.enums.AigErrorClassEnum;
import org.dromara.aigov.task.config.AigTaskSchedulerProperties;
import org.dromara.aigov.task.domain.AigTask;
import org.dromara.aigov.task.domain.vo.AigTaskSweepVo;
import org.dromara.aigov.task.enums.AigTaskStatusEnum;
import org.dromara.aigov.task.mapper.AigTaskMapper;
import org.dromara.aigov.task.service.IAigTaskScheduler;
import org.dromara.aigov.task.service.IAigTaskService;
import org.dromara.common.core.exception.ServiceException;
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

    @Override
    public AigTaskSweepVo sweep() {
        AigTaskSweepVo result = new AigTaskSweepVo();
        // 1. 待重试任务重新入队
        sweepRetryWait(result);
        // 2. 在途任务超时
        sweepTimeout(result);
        if (result.getRetried() > 0 || result.getTimedOut() > 0 || !result.getFailures().isEmpty()) {
            log.info("任务调度扫描完成, retryCandidate={}, retried={}, timeoutCandidate={}, timedOut={}, skipped={}, failures={}",
                result.getRetryCandidate(), result.getRetried(), result.getTimeoutCandidate(),
                result.getTimedOut(), result.getSkipped(), result.getFailures());
        }
        return result;
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
