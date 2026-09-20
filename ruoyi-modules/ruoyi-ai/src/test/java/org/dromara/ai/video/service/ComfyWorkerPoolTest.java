package org.dromara.ai.video.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.dromara.ai.video.comfy.ComfyClient;
import org.dromara.ai.video.comfy.ComfyOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ComfyUI 工作节点池的并发语义。
 *
 * <p>存在的理由：单任务峰值显存 80,805 MiB / 81,920 MiB，所以「并发 2」的正确含义是
 * <b>两张卡各跑一个任务</b>，而不是「一个实例上同时塞两个任务」。池子必须保证
 * 同一时刻一台实例最多只有一个持有者，否则两个任务会互相把显存挤爆；
 * 同时故障节点要能被跳过，不能让新任务接着往坑里掉。</p>
 */
class ComfyWorkerPoolTest {

    private static ComfyWorkerPool pool(int workerCount, Duration cooldown) {
        List<ComfyWorkerPool.Worker> workers = new java.util.ArrayList<>();
        for (int i = 0; i < workerCount; i++) {
            workers.add(new ComfyWorkerPool.Worker("gpu" + i, "http://gpu" + i + ":818" + (8 + i),
                new NoopComfyClient()));
        }
        return new ComfyWorkerPool(workers, cooldown);
    }

    @Test
    @DisplayName("同一节点同时只能被一个任务占用（第三个人拿不到，而不是排队进同一个实例）")
    void eachWorkerServesOneTaskAtATime() {
        ComfyWorkerPool pool = pool(2, Duration.ofSeconds(60));

        ComfyWorkerPool.Lease first = pool.acquire(Duration.ZERO);
        ComfyWorkerPool.Lease second = pool.acquire(Duration.ZERO);
        assertNotNull(first, "两张卡应能同时借出两个租约");
        assertNotNull(second, "两张卡应能同时借出两个租约");
        assertNotEquals(first.name(), second.name(), "两个任务必须落到不同的卡上");

        assertNull(pool.acquire(Duration.ZERO), "两个节点都占用时第三个任务应拿不到节点（超时为 0）");

        first.close();
        assertNotNull(pool.acquire(Duration.ZERO), "归还后应能立刻再借出");
    }

    @Test
    @DisplayName("借出的是同一个 client：任务必须回提交它的那台实例轮询")
    void leaseExposesTheSameClientAsTheWorker() {
        ComfyWorkerPool pool = pool(1, Duration.ofSeconds(60));
        try (ComfyWorkerPool.Lease lease = pool.acquire(Duration.ofSeconds(1))) {
            assertNotNull(lease);
            assertSame(lease.worker().client(), lease.client(),
                "任务期间必须一直用同一个 ComfyUI 实例：history 与输出都在进程内");
        }
    }

    @Test
    @DisplayName("冷却中的节点被跳过，不再接新任务")
    void unavailableWorkerIsSkipped() {
        ComfyWorkerPool pool = pool(2, Duration.ofSeconds(60));
        pool.markUnavailable("gpu0", "显存被别的进程占用");

        ComfyWorkerPool.Lease lease = pool.acquire(Duration.ZERO);
        assertNotNull(lease);
        assertEquals("gpu1", lease.name(), "冷却中的 gpu0 不得再接任务");

        // 不归还这个租约：gpu0 在冷却、gpu1 被占用，此时不应再有任务能开工。
        assertNull(pool.acquire(Duration.ZERO), "只剩一个健康节点，也只能同时跑一个任务");
        lease.close();

        ComfyWorkerPool.Snapshot down = pool.snapshots().stream()
            .filter(s -> s.name().equals("gpu0")).findFirst().orElseThrow();
        assertTrue(down.unavailable());
        assertEquals("显存被别的进程占用", down.reason());
        assertTrue(down.cooldownSecondsLeft() > 0, "冷却剩余时间要能被运维接口看到");
    }

    @Test
    @DisplayName("冷却到期后节点自动恢复，不需要重启服务")
    void workerRecoversAfterCooldown() throws Exception {
        ComfyWorkerPool pool = pool(1, Duration.ofMillis(80));
        pool.markUnavailable("gpu0", "临时占用");
        assertNull(pool.acquire(Duration.ZERO), "冷却期内不应借出");

        Thread.sleep(150L);
        assertNotNull(pool.acquire(Duration.ZERO), "冷却到期后应自动恢复");
    }

    @Test
    @DisplayName("租约重复关闭是安全的（执行器异常路径会多关一次）")
    void closingTwiceIsHarmless() {
        ComfyWorkerPool pool = pool(1, Duration.ofSeconds(60));
        ComfyWorkerPool.Lease lease = pool.acquire(Duration.ZERO);
        lease.close();
        lease.close();
        assertNotNull(pool.acquire(Duration.ZERO), "重复关闭不得把节点永久锁死");
    }

    @Test
    @DisplayName("空池必须直接拒绝装配，而不是运行时静默不干活")
    void emptyPoolIsRejected() {
        IllegalStateException error = org.junit.jupiter.api.Assertions.assertThrows(
            IllegalStateException.class, () -> new ComfyWorkerPool(List.of(), Duration.ofSeconds(1)));
        assertTrue(error.getMessage().contains("不能为空"));
    }

    /**
     * 池测试不关心 ComfyUI 协议，只关心互斥与健康状态。
     */
    private static final class NoopComfyClient implements ComfyClient {

        @Override
        public boolean isReachable() {
            return true;
        }

        @Override
        public boolean freeMemory() {
            return true;
        }

        @Override
        public String uploadImage(String fileName, byte[] content, String mimeType) {
            return fileName;
        }

        @Override
        public String submitPrompt(JsonNode graph) {
            return "stub";
        }

        @Override
        public PollResult poll(String promptId) {
            return PollResult.running();
        }

        @Override
        public byte[] fetchOutput(ComfyOutput output) {
            return new byte[0];
        }
    }
}
