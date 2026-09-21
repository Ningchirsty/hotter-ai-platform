package org.dromara.ai.image;

import org.dromara.ai.image.domain.ImageTaskStatus;
import org.dromara.ai.image.service.ImageTaskDispatchService;
import org.dromara.ai.image.service.ImageTaskDispatchService.Outcome;
import org.dromara.ai.image.service.ImageTaskExecutionService;
import org.dromara.ai.image.service.ImageTaskOrchestrator;
import org.dromara.ai.image.service.ImageTaskRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 派发不变量：重复提交不重复执行、队列满必须回滚状态。
 */
class ImageTaskDispatchServiceTest {

    @Test
    @DisplayName("正常派发：认领成功并执行")
    void accepted() throws Exception {
        RecordingRepository repository = new RecordingRepository(1);
        CountDownLatch ran = new CountDownLatch(1);
        ImageTaskExecutionService executor = new ImageTaskExecutionService(
            context -> {
                ran.countDown();
                return null;
            }, 4, 1);
        try {
            ImageTaskDispatchService service = new ImageTaskDispatchService(repository, executor);
            assertEquals(Outcome.ACCEPTED, service.dispatch(1L, () -> ctx(1L)));
            assertTrue(ran.await(3, TimeUnit.SECONDS));
        } finally {
            executor.shutdown();
        }
    }

    @Test
    @DisplayName("重复提交：前置状态不符时不重复执行")
    void alreadyClaimed() {
        RecordingRepository repository = new RecordingRepository(0);
        ImageTaskExecutionService executor = new ImageTaskExecutionService(context -> null, 4, 1);
        try {
            ImageTaskDispatchService service = new ImageTaskDispatchService(repository, executor);
            assertEquals(Outcome.ALREADY_CLAIMED, service.dispatch(1L, () -> ctx(1L)));
            assertEquals(0, repository.executedCount());
        } finally {
            executor.shutdown();
        }
    }

    @Test
    @DisplayName("队列满：回滚 RUNNING → QUEUED 并返回 QUEUE_FULL")
    void queueFullRollsBack() throws Exception {
        RecordingRepository repository = new RecordingRepository(1);
        CountDownLatch blocker = new CountDownLatch(1);
        ImageTaskExecutionService executor = new ImageTaskExecutionService(
            context -> {
                try {
                    blocker.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            }, 1, 1);
        try {
            ImageTaskDispatchService service = new ImageTaskDispatchService(repository, executor);
            // 第一个占住线程，第二个占满队列，第三个必须被拒
            assertEquals(Outcome.ACCEPTED, service.dispatch(1L, () -> ctx(1L)));
            awaitBusy(executor);
            assertEquals(Outcome.ACCEPTED, service.dispatch(2L, () -> ctx(2L)));
            assertEquals(Outcome.QUEUE_FULL, service.dispatch(3L, () -> ctx(3L)));
            assertTrue(repository.transitions().contains("RUNNING->QUEUED"));
        } finally {
            blocker.countDown();
            executor.shutdown();
        }
    }

    private void awaitBusy(ImageTaskExecutionService executor) throws Exception {
        for (int i = 0; i < 100 && !executor.isBusy(); i++) {
            Thread.sleep(10);
        }
    }

    /**
     * 执行上下文替身：绝不传 null —— 真机上 context 永远存在，
     * 传 null 只会测出替身自己的 NPE（曾经就是这样误报过）。
     */
    private ImageTaskOrchestrator.TaskContext ctx(long taskId) {
        return new ImageTaskOrchestrator.TaskContext(
            taskId, "000000", 1L, null, "T2I", "wf-t2i-qwen21",
            "prompt", "", "1:1 方图 · 1MP（1024×1024）", null, List.of(),
            false, () -> 1L, new AtomicInteger());
    }

    /**
     * 只实现派发用到的两个动作。
     */
    static class RecordingRepository implements ImageTaskRepository {
        private final int claimResult;
        private final List<String> transitions = new ArrayList<>();
        private final AtomicInteger executed = new AtomicInteger();

        RecordingRepository(int claimResult) {
            this.claimResult = claimResult;
        }

        List<String> transitions() {
            return transitions;
        }

        int executedCount() {
            return executed.get();
        }

        @Override
        public int transition(long taskId, ImageTaskStatus expectedFrom, ImageTaskStatus target,
                              String errorCode, String errorMessage) {
            transitions.add(expectedFrom.name() + "->" + target.name());
            if (expectedFrom == ImageTaskStatus.QUEUED && target == ImageTaskStatus.RUNNING) {
                return claimResult;
            }
            return 1;
        }

        @Override
        public long insertAsset(AssetRow row) {
            return 1;
        }

        @Override
        public AssetRow requireOwnedAsset(long assetId, String tenantId, long userId) {
            return null;
        }

        @Override
        public long insertTask(TaskRow row) {
            executed.incrementAndGet();
            return 1;
        }

        @Override
        public Long findByIdempotencyKey(String t, long u, String k) {
            return null;
        }

        @Override
        public Map<String, Object> requireOwnedTask(long taskId, String t, long u) {
            return Map.of();
        }

        @Override
        public List<Map<String, Object>> listOwnedTasks(String t, long u, String s, int o, int l) {
            return List.of();
        }

        @Override
        public long countOwnedTasks(String t, long u, String s) {
            return 0;
        }

        @Override
        public List<Map<String, Object>> listOwnedAssets(String t, long u, int o, int l) {
            return List.of();
        }

        @Override
        public long countOwnedAssets(String t, long u) {
            return 0;
        }

        @Override
        public int softDeleteAsset(long assetId, String t, long u) {
            return 1;
        }

        @Override
        public int markFailedIfActive(long taskId, String errorCode, String errorMessage) {
            return 1;
        }

        @Override
        public int failAllRunning(String errorCode, String errorMessage) {
            return 0;
        }

        @Override
        public int markSubmitted(long taskId, String promptId, int attemptCount, String worker) {
            return 1;
        }

        @Override
        public int markSucceeded(long taskId, long outputAssetId, Integer width, Integer height,
                                 boolean hasAlpha, long sizeBytes) {
            return 1;
        }

        @Override
        public void appendEvent(long eventId, long taskId, String tenantId, int sequence,
                                String eventType, String detail) {
        }

        @Override
        public List<Map<String, Object>> listEvents(long taskId, String tenantId) {
            return List.of();
        }

        @Override
        public int cancelQueued(long taskId, String tenantId, long userId) {
            return 0;
        }

        /**
         * 未被使用的编排器类型引用（保持与接口签名一致）。
         */
        static ImageTaskOrchestrator unusedReference() {
            return null;
        }
    }
}
