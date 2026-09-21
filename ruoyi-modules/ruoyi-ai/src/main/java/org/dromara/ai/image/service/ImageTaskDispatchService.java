package org.dromara.ai.image.service;

import lombok.extern.slf4j.Slf4j;

import java.util.function.Supplier;

/**
 * 图像任务派发：原子认领 + 入队，队列满则回滚。
 *
 * <p>三条不变量（与视频模块一致，都是真机踩出来的）：</p>
 * <ol>
 *   <li>认领靠一次带前置状态的 {@code QUEUED → RUNNING} 更新，影响 0 行说明已被别人认领，
 *       直接返回 ALREADY_CLAIMED，不再重复烧 GPU；</li>
 *   <li>入队失败必须把状态回滚为 QUEUED，否则任务会永远卡在 RUNNING；</li>
 *   <li>重复提交不是错误，要幂等地当成功处理。</li>
 * </ol>
 */
@Slf4j
public class ImageTaskDispatchService {

    /**
     * 派发结果。
     */
    public enum Outcome {
        /**
         * 已认领并入队。
         */
        ACCEPTED,
        /**
         * 已被其他线程认领（重复提交），不重复执行。
         */
        ALREADY_CLAIMED,
        /**
         * 执行队列已满，状态已回滚。
         */
        QUEUE_FULL
    }

    private final ImageTaskRepository repository;
    private final ImageTaskExecutionService executionService;

    public ImageTaskDispatchService(ImageTaskRepository repository, ImageTaskExecutionService executionService) {
        this.repository = repository;
        this.executionService = executionService;
    }

    public Outcome dispatch(long taskId, Supplier<ImageTaskOrchestrator.TaskContext> contextFactory) {
        int claimed = repository.transition(taskId, org.dromara.ai.image.domain.ImageTaskStatus.QUEUED,
            org.dromara.ai.image.domain.ImageTaskStatus.RUNNING, null, null);
        if (claimed == 0) {
            log.info("图像任务 {} 已被认领（重复提交），不再重复执行", taskId);
            return Outcome.ALREADY_CLAIMED;
        }
        boolean accepted = executionService.submit(contextFactory.get());
        if (!accepted) {
            int rolledBack = repository.transition(taskId, org.dromara.ai.image.domain.ImageTaskStatus.RUNNING,
                org.dromara.ai.image.domain.ImageTaskStatus.QUEUED, null, null);
            log.warn("图像任务 {} 因执行队列已满被拒，状态回滚影响行数={}", taskId, rolledBack);
            return Outcome.QUEUE_FULL;
        }
        return Outcome.ACCEPTED;
    }
}
