package org.dromara.ai.video.service;

import lombok.extern.slf4j.Slf4j;
import org.dromara.ai.video.comfy.ComfyClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ComfyUI 工作节点池（一卡一实例）。
 *
 * <p><b>为什么需要池，而不是把并发数调大就行。</b>实测一次 H3 生成在 A100-80GB 上峰值占用
 * <b>80,805 MiB</b>，几乎顶满整张卡。所以「并发 2」不是把线程数改成 2 那么简单：
 * 必须保证<b>同一张卡上同时只有一个任务</b>，否则两个任务必然互相把显存挤爆。
 * 一台机器两张卡 → 两个 ComfyUI 实例 → 池里两个 worker，各自串行。</p>
 *
 * <p><b>为什么任务要和实例绑定。</b>ComfyUI 的 {@code /history}、{@code /view}、{@code /free}
 * 都是<b>进程内</b>的：提交到 8189 的任务必须回 8189 轮询与取片，跑到 8188 上是查不到的。
 * 因此这里不是「随便挑一个能用的 client」，而是「借出一个 worker，任务全程用它」，
 * 用完归还。</p>
 *
 * <p><b>健康与冷却。</b>某个 worker 所在的卡被别的进程占住（例如同机其它服务抢显存）时，
 * 该 worker 会被标记为不可用并进入冷却，池子会把任务交给另一个 worker；全部不可用时
 * 由调用方快速失败并说明原因，而不是撞上去 OOM。</p>
 */
@Slf4j
public class ComfyWorkerPool {

    /**
     * 一个 ComfyUI 实例。
     */
    public record Worker(String name, String baseUrl, ComfyClient client) {
    }

    private final List<Worker> workers = new ArrayList<>();

    /**
     * 每个 worker 是否正被占用。
     */
    private final Map<String, AtomicBoolean> busy = new ConcurrentHashMap<>();

    /**
     * 冷却截止时间（epoch millis）。到点之前不派任务给它。
     */
    private final Map<String, Long> unavailableUntil = new ConcurrentHashMap<>();

    /**
     * 最近一次不可用原因，供 {@code GET /video/workers} 展示。
     */
    private final Map<String, String> unavailableReason = new ConcurrentHashMap<>();

    private final Duration cooldown;

    public ComfyWorkerPool(List<Worker> workers, Duration cooldown) {
        if (workers == null || workers.isEmpty()) {
            throw new IllegalStateException("ComfyUI 工作节点池不能为空");
        }
        this.workers.addAll(workers);
        this.cooldown = cooldown == null ? Duration.ofSeconds(60) : cooldown;
        for (Worker w : workers) {
            busy.put(w.name(), new AtomicBoolean(false));
        }
        log.info("ComfyUI 工作节点池已装配：{}", names());
    }

    public int size() {
        return workers.size();
    }

    public String names() {
        StringBuilder sb = new StringBuilder();
        for (Worker w : workers) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(w.name()).append('@').append(w.baseUrl);
        }
        return sb.toString();
    }

    /**
     * 借出一个空闲且健康的 worker。
     *
     * @param timeout 最长等待时间（都用满时会等待，而不是立刻失败）
     * @return 借出的租约；超时仍无可用 worker 时返回 {@code null}
     */
    public Lease acquire(Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (true) {
            for (Worker w : workers) {
                if (isUnavailable(w.name())) {
                    continue;
                }
                if (busy.get(w.name()).compareAndSet(false, true)) {
                    return new Lease(w);
                }
            }
            if (System.nanoTime() >= deadline) {
                return null;
            }
            try {
                Thread.sleep(500L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
    }

    private boolean isUnavailable(String name) {
        Long until = unavailableUntil.get(name);
        if (until == null) {
            return false;
        }
        if (System.currentTimeMillis() >= until) {
            unavailableUntil.remove(name);
            unavailableReason.remove(name);
            return false;
        }
        return true;
    }

    /**
     * 标记某 worker 暂时不可用（进入冷却），并把当前任务让给别人。
     */
    public void markUnavailable(String name, String reason) {
        unavailableUntil.put(name, System.currentTimeMillis() + cooldown.toMillis());
        unavailableReason.put(name, reason);
        log.warn("ComfyUI 工作节点 {} 进入冷却 {}s：{}", name, cooldown.toSeconds(), reason);
    }

    /**
     * 节点状态快照，供运维接口展示。
     */
    public List<Snapshot> snapshots() {
        List<Snapshot> list = new ArrayList<>(workers.size());
        for (Worker w : workers) {
            boolean down = isUnavailable(w.name());
            list.add(new Snapshot(w.name(), w.baseUrl(), busy.get(w.name()).get(), down,
                unavailableReason.get(w.name()),
                down ? Math.max(0, (unavailableUntil.get(w.name()) - System.currentTimeMillis()) / 1000) : 0));
        }
        return list;
    }

    /**
     * 节点状态。
     */
    public record Snapshot(String name, String baseUrl, boolean busy, boolean unavailable,
                           String reason, long cooldownSecondsLeft) {
    }

    /**
     * 借出的租约。必须用 try-with-resources 归还，否则该 worker 会一直被占用。
     */
    public final class Lease implements AutoCloseable {

        private final Worker worker;

        private boolean returned;

        private Lease(Worker worker) {
            this.worker = worker;
        }

        public Worker worker() {
            return worker;
        }

        public ComfyClient client() {
            return worker.client();
        }

        public String name() {
            return worker.name();
        }

        @Override
        public void close() {
            if (!returned) {
                returned = true;
                busy.get(worker.name()).set(false);
            }
        }
    }
}
