package org.dromara.ai.video;

import org.dromara.ai.video.service.VideoTaskExecutionService;
import org.dromara.ai.video.service.VideoTaskOrchestrator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 后台执行器的并发行为。
 *
 * <p>背景：生成一次要 130 秒到 11.5 分钟，而前端经 Cloudflare 访问、免费版等待源站响应的
 * 上限在 100 秒量级，同步等出片必然把结果丢掉。改成后台执行之后，「并发必须为 1」
 * 与「队列满要如实拒绝」这两件事就成了正确性的一部分，必须测。</p>
 */
class VideoTaskExecutionServiceTest {

    private static VideoTaskOrchestrator.TaskContext ctx(long taskId) {
        return new VideoTaskOrchestrator.TaskContext(taskId, "000000", 100L, 10L,
            "T2V", "wf-t2v-h3", "提示词", "高清 · 1080P", "5 秒", null, null, null,
            false, System::nanoTime, new AtomicInteger(0));
    }

    private static VideoTaskOrchestrator.ExecutionResult ok(long taskId) {
        return new VideoTaskOrchestrator.ExecutionResult(taskId, 999L, null, false);
    }

    @Test
    @DisplayName("提交后任务真的会在后台线程里执行")
    void runsTaskInBackground() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        List<Long> ran = new CopyOnWriteArrayList<>();
        VideoTaskExecutionService service = new VideoTaskExecutionService(c -> {
            ran.add(c.taskId());
            done.countDown();
            return ok(c.taskId());
        }, 4);

        assertTrue(service.submit(ctx(1L)));
        assertTrue(done.await(5, TimeUnit.SECONDS), "任务应在后台被执行");
        assertEquals(List.of(1L), ran);
        service.shutdown();
    }

    @Test
    @DisplayName("并发固定为 1：第一个还在跑时，第二个不会同时开始")
    void runsAtMostOneAtATime() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger concurrent = new AtomicInteger();
        AtomicInteger maxConcurrent = new AtomicInteger();
        List<Long> finished = new CopyOnWriteArrayList<>();

        VideoTaskExecutionService service = new VideoTaskExecutionService(c -> {
            int now = concurrent.incrementAndGet();
            maxConcurrent.accumulateAndGet(now, Math::max);
            if (c.taskId() == 1L) {
                firstStarted.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            concurrent.decrementAndGet();
            finished.add(c.taskId());
            return ok(c.taskId());
        }, 4);

        service.submit(ctx(1L));
        assertTrue(firstStarted.await(5, TimeUnit.SECONDS));
        service.submit(ctx(2L));
        // 此刻第二个只应在排队，绝不能与第一个并行
        Thread.sleep(150);
        assertEquals(1, maxConcurrent.get(), "GPU 只有一块，必须串行");
        release.countDown();

        long deadline = System.currentTimeMillis() + 5000;
        while (finished.size() < 2 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertEquals(2, finished.size(), "两个都应完成");
        assertEquals(1, maxConcurrent.get());
        service.shutdown();
    }

    @Test
    @DisplayName("队列满时必须如实拒绝（调用方据此把状态退回 QUEUED，不能留在 RUNNING）")
    void rejectsWhenQueueFull() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        // 容量 1：1 个在跑 + 1 个排队，第 3 个必然被拒
        VideoTaskExecutionService service = new VideoTaskExecutionService(c -> {
            firstStarted.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return ok(c.taskId());
        }, 1);

        assertTrue(service.submit(ctx(1L)), "第一个应被接受");
        assertTrue(firstStarted.await(5, TimeUnit.SECONDS));
        assertTrue(service.submit(ctx(2L)), "第二个应进入队列");
        assertFalse(service.submit(ctx(3L)), "队列已满，第三个必须被拒绝");
        release.countDown();
        service.shutdown();
    }

    @Test
    @DisplayName("排队计数：1 个在跑 + 1 个排队时必须报 1（前端那一行「排队 N」由它驱动）")
    void reportsQueuedCountExcludingRunningTasks() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        // 并发 1：第二个任务必然在排队。
        VideoTaskExecutionService service = new VideoTaskExecutionService(c -> {
            firstStarted.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return ok(c.taskId());
        }, 8, 1);

        assertTrue(service.submit(ctx(1L)));
        assertTrue(firstStarted.await(5, TimeUnit.SECONDS), "第一个应开始执行");
        assertEquals(0, service.queuedCount(), "只有 1 个任务时不缺排队");

        assertTrue(service.submit(ctx(2L)), "第二个应进入队列");
        // 曾经的缺陷：queued(1) - active(1) = 0，界面在真的有任务等着时显示「排队 0」。
        assertEquals(1, service.queuedCount(), "有 1 个任务在排队就必须报 1");
        assertTrue(service.isBusy());

        release.countDown();
        long deadline = System.currentTimeMillis() + 5000;
        while (service.isBusy() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertEquals(0, service.queuedCount(), "跑完后排队数归零");
        service.shutdown();
    }

    @Test
    @DisplayName("忙闲判断：空闲时不忙，有任务在跑或排队时忙（部署前用它判断会不会打断生成）")
    void reportsBusyState() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        VideoTaskExecutionService service = new VideoTaskExecutionService(c -> {
            started.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return ok(c.taskId());
        }, 4);

        assertFalse(service.isBusy(), "刚建好不该是忙");
        service.submit(ctx(1L));
        assertTrue(started.await(5, TimeUnit.SECONDS));
        assertTrue(service.isBusy(), "有任务在跑时应为忙");
        release.countDown();

        long deadline = System.currentTimeMillis() + 5000;
        while (service.isBusy() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertFalse(service.isBusy(), "跑完之后应回到空闲");
        service.shutdown();
    }

    @Test
    @DisplayName("任务抛业务异常不会打断执行器：异常已由编排器落库，执行器继续可用")
    void keepsRunningAfterTaskFailure() throws Exception {
        CountDownLatch secondDone = new CountDownLatch(1);
        List<Long> ran = new CopyOnWriteArrayList<>();
        VideoTaskExecutionService service = new VideoTaskExecutionService(c -> {
            ran.add(c.taskId());
            if (c.taskId() == 1L) {
                throw org.dromara.ai.video.exception.VideoTaskException
                    .comfyFailure("stub failure", null);
            }
            secondDone.countDown();
            return ok(c.taskId());
        }, 4);

        service.submit(ctx(1L));
        service.submit(ctx(2L));
        assertTrue(secondDone.await(5, TimeUnit.SECONDS),
            "第一个失败后，第二个仍必须被执行（异常不能把执行器带死）");
        assertEquals(List.of(1L, 2L), ran);
        service.shutdown();
    }
}
