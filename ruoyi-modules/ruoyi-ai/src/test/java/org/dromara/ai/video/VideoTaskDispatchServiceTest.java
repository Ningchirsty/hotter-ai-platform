package org.dromara.ai.video;

import org.dromara.ai.video.domain.VideoTaskStatus;
import org.dromara.ai.video.service.VideoTaskDispatchService;
import org.dromara.ai.video.service.VideoTaskExecutionService;
import org.dromara.ai.video.service.VideoTaskOrchestrator;
import org.dromara.ai.video.service.VideoTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 任务派发的三条不变量。
 *
 * <p>这段逻辑以前散在控制器里、没有测试，结果以最隐蔽的方式出过事故：失败落库只认
 * {@code QUEUED→FAILED}，任务进入 RUNNING 之后失败再也写不进去，留下一批
 * 「永远运行中、成片已丢」的僵尸任务。所以这三条必须被锁住。</p>
 */
class VideoTaskDispatchServiceTest {

    private static final long TASK_ID = 42L;

    private VideoTaskRepository repository;
    private VideoTaskExecutionService executionService;
    private VideoTaskDispatchService dispatch;

    @BeforeEach
    void setUp() {
        repository = mock(VideoTaskRepository.class);
        executionService = mock(VideoTaskExecutionService.class);
        dispatch = new VideoTaskDispatchService(repository, executionService);
    }

    private static VideoTaskOrchestrator.TaskContext ctx() {
        return new VideoTaskOrchestrator.TaskContext(TASK_ID, "000000", 100L, 10L,
            "T2V", "wf-t2v-h3", "提示词", "高清 · 1080P", "5 秒", null, null, null,
            false, System::nanoTime, new AtomicInteger(0));
    }

    @Test
    @DisplayName("认领成功并入队：返回 ACCEPTED")
    void acceptsAndSubmits() {
        when(repository.transition(eq(TASK_ID), eq(VideoTaskStatus.QUEUED),
            eq(VideoTaskStatus.RUNNING), isNull(), isNull())).thenReturn(1);
        when(executionService.submit(any())).thenReturn(true);

        assertEquals(VideoTaskDispatchService.Outcome.ACCEPTED, dispatch.dispatch(TASK_ID, () -> ctx()));
        verify(executionService, times(1)).submit(any());
    }

    @Test
    @DisplayName("已被认领（重复提交）：不重复执行，返回 ALREADY_CLAIMED")
    void doesNotRunTwice() {
        // 原子流转影响 0 行 = 别人已经认领了
        when(repository.transition(eq(TASK_ID), eq(VideoTaskStatus.QUEUED),
            eq(VideoTaskStatus.RUNNING), isNull(), isNull())).thenReturn(0);
        AtomicBoolean contextBuilt = new AtomicBoolean(false);
        Supplier<VideoTaskOrchestrator.TaskContext> factory = () -> {
            contextBuilt.set(true);
            return ctx();
        };

        assertEquals(VideoTaskDispatchService.Outcome.ALREADY_CLAIMED,
            dispatch.dispatch(TASK_ID, factory));
        verify(executionService, never()).submit(any());
        assertFalse(contextBuilt.get(), "没认领到就不该构造上下文");
    }

    @Test
    @DisplayName("队列满：必须把状态从 RUNNING 回滚到 QUEUED，否则任务永远停在运行中")
    void rollsBackWhenQueueFull() {
        when(repository.transition(eq(TASK_ID), eq(VideoTaskStatus.QUEUED),
            eq(VideoTaskStatus.RUNNING), isNull(), isNull())).thenReturn(1);
        when(executionService.submit(any())).thenReturn(false);

        assertEquals(VideoTaskDispatchService.Outcome.QUEUE_FULL,
            dispatch.dispatch(TASK_ID, () -> ctx()));
        verify(repository, times(1)).transition(eq(TASK_ID), eq(VideoTaskStatus.RUNNING),
            eq(VideoTaskStatus.QUEUED), isNull(), isNull());
    }

    @Test
    @DisplayName("认领之后任务真的会被执行（端到端串起派发与执行器）")
    void reallyRunsAfterAccept() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        VideoTaskExecutionService realExecutor = new VideoTaskExecutionService(c -> {
            done.countDown();
            return new VideoTaskOrchestrator.ExecutionResult(c.taskId(), 7L, null, false);
        }, 2);
        VideoTaskDispatchService realDispatch = new VideoTaskDispatchService(repository, realExecutor);
        when(repository.transition(eq(TASK_ID), eq(VideoTaskStatus.QUEUED),
            eq(VideoTaskStatus.RUNNING), isNull(), isNull())).thenReturn(1);

        assertEquals(VideoTaskDispatchService.Outcome.ACCEPTED,
            realDispatch.dispatch(TASK_ID, VideoTaskDispatchServiceTest::ctx));
        assertTrue(done.await(5, TimeUnit.SECONDS), "认领成功后必须真的开跑");
        realExecutor.shutdown();
    }
}
