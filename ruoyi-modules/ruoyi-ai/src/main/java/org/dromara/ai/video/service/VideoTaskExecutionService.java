package org.dromara.ai.video.service;

import lombok.extern.slf4j.Slf4j;
import org.dromara.ai.video.exception.VideoTaskException;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 视频任务的后台执行器。
 *
 * <p><b>为什么必须异步。</b>执行一次生成要 130 秒（480P/5 秒）到 11.5 分钟（1080P/5 秒）。
 * 前端经 Cloudflare 访问（源站是 cloudflared tunnel），而 Cloudflare 免费版对源站响应的
 * 等待上限在 100 秒量级。实测证据：任务 2100551562622185473 于 11:44:55 开始执行，
 * 后端跑满 130,294 毫秒正常结束并写入 SUCCEEDED，但前端 nginx 记的是 <b>499</b>——
 * Cloudflare 早已把连接断开，浏览器什么都没拿到，用户看到的是「点了没反应」。
 * 因此「在请求线程里同步等出片」在生产环境根本不成立：活干完了，结果送不回去。</p>
 *
 * <p><b>为什么并发必须等于 GPU 实例数。</b>实测一次 H3 生成在 A100-80GB 上峰值占用
 * 80,805 MiB，几乎顶满整张卡，所以「同一张卡上同时只能有一个任务」是硬约束。
 * 双卡时并发取 2——每张卡一个 ComfyUI 实例，任务由 {@link ComfyWorkerPool}
 * 绑定到具体实例上串行执行；并发数由 {@code video.comfy-workers} 的条目数决定，
 * 而不是拍一个数字。</p>
 *
 * <p>本类只负责「排队 + 跑」；任务状态的落库（含失败原因）全部由
 * {@link VideoTaskOrchestrator} 负责，因此进程被重启也不会留下说不清状态的任务——
 * 遗留的 RUNNING 会由启动钩子收敛为 {@code ORPHANED_BY_RESTART}。</p>
 */
@Slf4j
public class VideoTaskExecutionService {

    private static final AtomicInteger THREAD_SEQ = new AtomicInteger();

    /**
     * 真正执行任务的动作。抽成函数式接口而不是直接依赖 {@link VideoTaskOrchestrator}，
     * 是为了让「排队 / 队列满 / 忙闲判断」这些并发行为能脱离 ComfyUI 与 Spring 单独测试。
     */
    @FunctionalInterface
    public interface TaskRunner {
        VideoTaskOrchestrator.ExecutionResult run(VideoTaskOrchestrator.TaskContext context);
    }

    private final TaskRunner runner;

    private final ExecutorService pool;

    /**
     * 同时在执行的任务数上限（= GPU 实例数）。
     */
    private final int concurrency;

    /**
     * 正在执行的任务数。
     */
    private final AtomicInteger active = new AtomicInteger();

    /**
     * 已接受但还没开始执行的任务数。
     */
    private final AtomicInteger queued = new AtomicInteger();

    private final int queueCapacity;

    public VideoTaskExecutionService(TaskRunner runner, int queueCapacity) {
        this(runner, queueCapacity, 1);
    }

    /**
     * @param concurrency 同时执行的任务数上限，必须等于可用的 ComfyUI 实例数。
     */
    public VideoTaskExecutionService(TaskRunner runner, int queueCapacity, int concurrency) {
        this.runner = runner;
        this.queueCapacity = Math.max(1, queueCapacity);
        this.concurrency = Math.max(1, concurrency);
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "video-exec-" + THREAD_SEQ.incrementAndGet());
            // 守护线程：进程退出时不该被一个还在等 ComfyUI 的线程拖住。
            thread.setDaemon(true);
            return thread;
        };
        this.pool = new ThreadPoolExecutor(this.concurrency, this.concurrency, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(this.queueCapacity), factory,
            new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * 并发上限（= 参与执行的 ComfyUI 实例数）。
     */
    public int concurrency() {
        return concurrency;
    }

    /**
     * 把任务提交到后台执行。
     *
     * @return {@code false} 表示队列已满，调用方必须把任务状态回滚（不要让它停在 RUNNING）
     */
    public boolean submit(VideoTaskOrchestrator.TaskContext context) {
        queued.incrementAndGet();
        try {
            pool.execute(() -> {
                queued.decrementAndGet();
                active.incrementAndGet();
                try {
                    VideoTaskOrchestrator.ExecutionResult result = runner.run(context);
                    log.info("任务 {} 生成完成，成片素材 {}", context.taskId(), result.outputAssetId());
                } catch (VideoTaskException e) {
                    // 编排器已把失败原因落库，这里只记录，避免后台线程的异常无人知晓。
                    log.warn("任务 {} 执行失败（已落库）：[{}] {}",
                        context.taskId(), e.getErrorCode(), e.getMessage());
                } catch (RuntimeException e) {
                    log.error("任务 {} 执行异常（已落库）：{}", context.taskId(),
                        e.getClass().getSimpleName(), e);
                } finally {
                    active.decrementAndGet();
                }
            });
            return true;
        } catch (RejectedExecutionException e) {
            queued.decrementAndGet();
            log.warn("执行队列已满（容量 {}），任务 {} 未入队", queueCapacity, context.taskId());
            return false;
        }
    }

    /**
     * 还在排队的任务数（不含正在执行的）。
     *
     * <p>注意：{@code queued} 在任务<b>开始执行</b>时就减掉了，所以它本身已经等于
     * 「在排队」的数量。曾经这里写成 {@code queued - active}，于是「1 个在跑 + 1 个排队」
     * 会被算成 0——前端那一行「排队 N」在有任务真的等着的时候显示 0，正是这个减错造成的。</p>
     */
    public int queuedCount() {
        return Math.max(0, queued.get());
    }

    /**
     * 是否还有任务在执行或排队。部署前可用它判断「现在换版本会不会打断生成」。
     */
    public boolean isBusy() {
        return active.get() > 0 || queued.get() > 0;
    }

    public int queueCapacity() {
        return queueCapacity;
    }

    /**
     * 由 Spring 在容器关闭时调用（见 {@code @Bean(destroyMethod = "shutdown")}）。
     */
    public void shutdown() {
        pool.shutdownNow();
    }
}
