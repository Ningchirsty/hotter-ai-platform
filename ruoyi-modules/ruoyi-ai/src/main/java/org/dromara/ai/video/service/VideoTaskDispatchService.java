package org.dromara.ai.video.service;

import lombok.extern.slf4j.Slf4j;
import org.dromara.ai.video.domain.VideoTaskStatus;

import java.util.function.Supplier;

/**
 * 任务派发：把「认领任务」与「排进后台执行器」放在一起，并保证认领与入队要么都成立、要么都不成立。
 *
 * <p>单独抽出来是因为这段逻辑曾经以最隐蔽的方式出过事故：失败落库只认
 * {@code QUEUED→FAILED}，任务进入 RUNNING 之后失败就再也写不进去，
 * 结果是一批「永远运行中、成片已丢」的僵尸任务。这里的三条不变量必须可测：</p>
 *
 * <ol>
 *   <li><b>不重复执行</b>：用一次原子的 {@code QUEUED → RUNNING} 当锁，影响 0 行就说明
 *       已经被别人认领，直接返回，绝不重复提交——重复执行意味着白烧一次 GPU。</li>
 *   <li><b>队列满要回滚</b>：入队被拒时必须把状态退回 {@code QUEUED}，否则任务会永远停在
 *       RUNNING，用户既等不到成片也看不到原因。</li>
 *   <li><b>已认领的不再报错</b>：重复点击是正常的用户行为，如实返回当前状态即可。</li>
 * </ol>
 */
@Slf4j
public class VideoTaskDispatchService {

    /**
     * 派发结果。
     */
    public enum Outcome {
        /**
         * 已接受并进入后台队列。
         */
        ACCEPTED,
        /**
         * 任务已被（本次或之前的请求）认领为 RUNNING，没有重复提交。
         */
        ALREADY_CLAIMED,
        /**
         * 后台队列已满；状态已退回 QUEUED，调用方应给出明确提示。
         */
        QUEUE_FULL
    }

    private final VideoTaskRepository repository;

    private final VideoTaskExecutionService executionService;

    public VideoTaskDispatchService(VideoTaskRepository repository,
                                    VideoTaskExecutionService executionService) {
        this.repository = repository;
        this.executionService = executionService;
    }

    /**
     * 认领并派发任务。
     *
     * @param taskId          任务主键
     * @param contextFactory  真正执行时才会用到，因此延迟构造（队列满时不必白构造）
     * @return 派发结果
     */
    public Outcome dispatch(long taskId, Supplier<VideoTaskOrchestrator.TaskContext> contextFactory) {
        int claimed = repository.transition(taskId, VideoTaskStatus.QUEUED,
            VideoTaskStatus.RUNNING, null, null);
        if (claimed == 0) {
            log.info("任务 {} 已被认领（重复提交），不再重复执行", taskId);
            return Outcome.ALREADY_CLAIMED;
        }
        if (!executionService.submit(contextFactory.get())) {
            int rolledBack = repository.transition(taskId, VideoTaskStatus.RUNNING,
                VideoTaskStatus.QUEUED, null, null);
            log.warn("任务 {} 因执行队列已满被拒，状态回滚影响行数={}", taskId, rolledBack);
            return Outcome.QUEUE_FULL;
        }
        return Outcome.ACCEPTED;
    }
}
