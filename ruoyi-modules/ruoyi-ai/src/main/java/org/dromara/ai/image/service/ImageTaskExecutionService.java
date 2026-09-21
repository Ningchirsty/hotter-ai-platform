package org.dromara.ai.image.service;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 图像任务后台执行器。
 *
 * <p>并发度对应 ComfyUI 实例（显卡）数：同一张卡上并行提交多个采样会互相抢显存，
 * 因此默认并发 1。队列满时<b>如实拒绝</b>而不是无限堆积，调用方据此把状态回滚为 QUEUED。</p>
 */
@Slf4j
public class ImageTaskExecutionService {

    /**
     * 任务执行体（由编排器实现）。
     */
    @FunctionalInterface
    public interface TaskRunner {
        ImageTaskOrchestrator.ExecutionResult run(ImageTaskOrchestrator.TaskContext context);
    }

    private final TaskRunner runner;
    private final ThreadPoolExecutor executor;
    private final int concurrency;
    private final int queueCapacity;
    private final AtomicInteger queued = new AtomicInteger();
    private final AtomicInteger active = new AtomicInteger();

    public ImageTaskExecutionService(TaskRunner runner, int queueCapacity, int concurrency) {
        this.runner = runner;
        this.concurrency = Math.max(1, concurrency);
        this.queueCapacity = Math.max(1, queueCapacity);
        AtomicInteger threadSeq = new AtomicInteger();
        this.executor = new ThreadPoolExecutor(
            this.concurrency, this.concurrency, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(this.queueCapacity),
            runnable -> {
                Thread thread = new Thread(runnable, "image-exec-" + threadSeq.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.AbortPolicy());
    }

    public int concurrency() {
        return concurrency;
    }

    public int queueCapacity() {
        return queueCapacity;
    }

    public int queuedCount() {
        return queued.get();
    }

    public boolean isBusy() {
        return active.get() > 0 || queued.get() > 0;
    }

    public boolean submit(ImageTaskOrchestrator.TaskContext context) {
        queued.incrementAndGet();
        try {
            executor.execute(() -> {
                queued.decrementAndGet();
                active.incrementAndGet();
                try {
                    runner.run(context);
                } catch (Exception e) {
                    // 编排器已经把失败落库；这里只记录，避免线程池吞掉堆栈
                    log.warn("图像任务 {} 执行抛出异常：{}", context.taskId(), e.toString());
                } finally {
                    active.decrementAndGet();
                }
            });
            return true;
        } catch (RejectedExecutionException e) {
            queued.decrementAndGet();
            log.warn("图像任务 {} 被拒：执行队列已满（容量 {}）", context.taskId(), queueCapacity);
            return false;
        }
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
