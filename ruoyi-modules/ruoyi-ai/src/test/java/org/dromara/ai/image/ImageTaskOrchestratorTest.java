package org.dromara.ai.image;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dromara.ai.image.domain.ImageTaskStatus;
import org.dromara.ai.image.exception.ImageTaskException;
import org.dromara.ai.image.service.ImageAssetProbe;
import org.dromara.ai.image.service.ImageAssetStore;
import org.dromara.ai.image.service.ImageTaskOrchestrator;
import org.dromara.ai.image.service.ImageTaskRepository;
import org.dromara.ai.image.service.ImageTemplatePreparer;
import org.dromara.ai.image.service.ImageWorkflowContractRegistry;
import org.dromara.ai.video.comfy.ComfyClient;
import org.dromara.ai.video.comfy.ComfyOutput;
import org.dromara.ai.video.service.AssetStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 编排器测试：用 ComfyUI 替身 + 内存仓储覆盖成功/不可达/执行失败/超时/越权/未发布六条路径。
 *
 * <p>不占用 GPU，也不接触数据库。</p>
 */
class ImageTaskOrchestratorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path ROOT = Path.of("..", "..", "script");

    @TempDir
    Path tempDir;

    private ImageWorkflowContractRegistry registry;
    private StubRepository repository;
    private StubClient client;
    private MemoryStorage storage;
    private ImageTaskOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        registry = new ImageWorkflowContractRegistry(ROOT, MAPPER);
        registry.load();
        repository = new StubRepository();
        client = new StubClient();
        storage = new MemoryStorage(tempDir);
        orchestrator = new ImageTaskOrchestrator(
            registry, new ImageTemplatePreparer(MAPPER), repository,
            new ImageAssetStore(storage), new ImageAssetProbe(), client,
            () -> 900001L, Duration.ofSeconds(300), Duration.ofMillis(20), false);
    }

    @Test
    @DisplayName("成功路径：提交 → 轮询 → 实测归档 → 落库为 SUCCEEDED")
    void successPath() throws Exception {
        registry.markTesting("wf-t2i-qwen21");
        client.pollState = ComfyClient.PollResult.State.SUCCEEDED;
        client.output = png(64, 48, true);

        ImageTaskOrchestrator.ExecutionResult result = orchestrator.execute(context("wf-t2i-qwen21", "T2I", List.of()));

        assertEquals(900001L, result.outputAssetId());
        assertEquals(64, result.width());
        assertEquals(48, result.height());
        assertTrue(result.hasAlpha());
        assertEquals(1, repository.submitted);
        assertEquals(1, repository.succeeded);
        assertEquals(ImageTaskStatus.SUCCEEDED.name(), repository.lastStatus);
        assertEquals(1, repository.insertedAssets.size());
        assertEquals("IMAGE", repository.insertedAssets.get(0).assetType());
        assertTrue(repository.events.contains("SUBMITTED"));
        assertTrue(repository.events.contains("SUCCEEDED"));
    }

    @Test
    @DisplayName("ComfyUI 不可达：不提交、落库 FAILED、抛业务异常")
    void unreachablePersistsFailure() {
        registry.markTesting("wf-t2i-qwen21");
        client.reachable = false;

        ImageTaskException e = assertThrows(ImageTaskException.class,
            () -> orchestrator.execute(context("wf-t2i-qwen21", "T2I", List.of())));
        assertEquals("COMFY_FAILURE", e.getErrorCode());
        assertEquals(0, repository.submitted);
        assertEquals("FAILED", repository.lastStatus);
        assertEquals("COMFY_FAILURE", repository.lastErrorCode);
    }

    @Test
    @DisplayName("ComfyUI 执行失败：状态流转为 FAILED 且原因可读")
    void executionFailure() {
        registry.markTesting("wf-t2i-qwen21");
        client.pollState = ComfyClient.PollResult.State.FAILED;
        client.pollError = "ComfyUI 节点执行失败：KSampler(#6)，RuntimeError：boom";

        ImageTaskException e = assertThrows(ImageTaskException.class,
            () -> orchestrator.execute(context("wf-t2i-qwen21", "T2I", List.of())));
        assertTrue(e.getMessage().contains("KSampler"));
        assertEquals("FAILED", repository.lastStatus);
        assertEquals("COMFY_EXECUTION_FAILED", repository.lastErrorCode);
    }

    @Test
    @DisplayName("轮询超时：落库 TIMEOUT（全局预算为 0 时立即超时）")
    void timeoutPersistsTimeout() {
        registry.markTesting("wf-t2i-qwen21");
        client.pollState = ComfyClient.PollResult.State.RUNNING;
        ImageTaskOrchestrator zeroBudget = new ImageTaskOrchestrator(
            registry, new ImageTemplatePreparer(MAPPER), repository,
            new ImageAssetStore(storage), new ImageAssetProbe(), client,
            () -> 900002L, Duration.ZERO, Duration.ofMillis(1), false);

        ImageTaskException e = assertThrows(ImageTaskException.class,
            () -> zeroBudget.execute(context("wf-t2i-qwen21", "T2I", List.of())));
        assertEquals("COMFY_TIMEOUT", e.getErrorCode());
        assertEquals("TIMEOUT", repository.lastStatus);
    }

    @Test
    @DisplayName("跨用户素材按不存在处理，且不触发上传")
    void crossUserAssetRejected() {
        registry.markTesting("wf-i2i-qwen21");
        repository.assetNotFound = true;

        ImageTaskException e = assertThrows(ImageTaskException.class,
            () -> orchestrator.execute(context("wf-i2i-qwen21", "I2I", List.of(42L))));
        assertEquals("ASSET_NOT_FOUND", e.getErrorCode());
        assertEquals(0, client.uploads.size());
        assertEquals("FAILED", repository.lastStatus);
    }

    @Test
    @DisplayName("DRAFT 工作流拒绝提交（即使其它条件都满足）")
    void draftRejected() {
        ImageTaskException e = assertThrows(ImageTaskException.class,
            () -> orchestrator.execute(context("wf-t2i-qwen21", "T2I", List.of())));
        assertTrue(e.getMessage().contains("尚未通过实机验收"), e.getMessage());
        assertEquals("FAILED", repository.lastStatus);
        assertEquals(0, repository.submitted);
    }

    @Test
    @DisplayName("能力与工作流不匹配时拒绝")
    void capabilityMismatch() {
        registry.markTesting("wf-t2i-qwen21");
        ImageTaskException e = assertThrows(ImageTaskException.class,
            () -> orchestrator.execute(context("wf-t2i-qwen21", "I2I", List.of(1L))));
        assertTrue(e.getMessage().contains("能力与工作流不匹配"), e.getMessage());
    }

    @Test
    @DisplayName("抠图输出缺少 alpha 时判为输出非法")
    void alphaRequired() throws Exception {
        registry.markTesting("wf-bgremove-qwen21");
        repository.assets.put(7L, new ImageTaskRepository.AssetRow(7L, "000000", 1L, null, "IMAGE", "UPLOAD",
            "in.png", storage.storeUpload("000000", 1L, "in.png", png(32, 32, false), "image/png"), "image/png",
            100L, null, 32, 32, false, null));
        client.pollState = ComfyClient.PollResult.State.SUCCEEDED;
        client.output = png(32, 32, false);

        ImageTaskException e = assertThrows(ImageTaskException.class,
            () -> orchestrator.execute(context("wf-bgremove-qwen21", "BGREMOVE", List.of(7L))));
        assertEquals("OUTPUT_INVALID", e.getErrorCode());
        assertTrue(e.getMessage().contains("透明通道"), e.getMessage());
    }

    @Test
    @DisplayName("输入素材被真实上传，文件名由服务端生成（不使用浏览器原名）")
    void uploadsInputAsset() throws Exception {
        registry.markTesting("wf-i2i-qwen21");
        repository.assets.put(11L, new ImageTaskRepository.AssetRow(11L, "000000", 1L, null, "IMAGE", "UPLOAD",
            "浏览器原名.png", storage.storeUpload("000000", 1L, "浏览器原名.png", png(16, 16, true), "image/png"),
            "image/png", 100L, null, 16, 16, true, null));
        client.pollState = ComfyClient.PollResult.State.SUCCEEDED;
        client.output = png(16, 16, true);

        orchestrator.execute(context("wf-i2i-qwen21", "I2I", List.of(11L)));

        assertEquals(1, client.uploads.size());
        String uploaded = client.uploads.get(0);
        assertTrue(uploaded.startsWith("hotter_img_"), uploaded);
        assertFalse(uploaded.contains("浏览器原名"), "不得使用浏览器原始文件名");
    }

    private ImageTaskOrchestrator.TaskContext context(String workflowCode, String capability, List<Long> assets) {
        return new ImageTaskOrchestrator.TaskContext(
            5001L, "000000", 1L, null, capability, workflowCode,
            "一只红色茶壶", "", "1:1 方图 · 1MP（1024×1024）", "标准重绘 · 0.75", assets,
            false, () -> 1L, new AtomicInteger());
    }

    private static byte[] png(int width, int height, boolean alpha) throws Exception {
        BufferedImage image = new BufferedImage(width, height, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    /**
     * ComfyUI 替身。
     */
    static class StubClient implements ComfyClient {
        boolean reachable = true;
        PollResult.State pollState = PollResult.State.RUNNING;
        String pollError = "ComfyUI 执行失败";
        byte[] output = new byte[0];
        final List<String> uploads = new ArrayList<>();

        @Override
        public String uploadImage(String fileName, byte[] content, String mimeType) {
            uploads.add(fileName);
            return fileName;
        }

        @Override
        public String submitPrompt(com.fasterxml.jackson.databind.JsonNode graph) {
            return "prompt-1";
        }

        @Override
        public PollResult poll(String promptId) {
            return switch (pollState) {
                case SUCCEEDED -> PollResult.succeeded(List.of(
                    new ComfyOutput("out_00001_.png", "image", "output", null, null, null, null, (long) output.length)));
                case FAILED -> PollResult.failed(pollError);
                default -> PollResult.running();
            };
        }

        @Override
        public byte[] fetchOutput(ComfyOutput output) {
            return this.output;
        }

        @Override
        public boolean isReachable() {
            return reachable;
        }
    }

    /**
     * 内存仓储替身：只记录被调用的关键动作。
     */
    static class StubRepository implements ImageTaskRepository {
        final Map<Long, AssetRow> assets = new LinkedHashMap<>();
        final List<AssetRow> insertedAssets = new ArrayList<>();
        final List<String> events = new ArrayList<>();
        int submitted;
        int succeeded;
        String lastStatus;
        String lastErrorCode;
        boolean assetNotFound;

        /**
         * 当前状态，用于模拟真实 SQL 的「只更新非终态行」语义。
         *
         * <p>这一点很关键：编排器先把 RUNNING 流转为 TIMEOUT/FAILED，抛出的异常再由外层
         * {@code markFailedIfActive} 兜底；真库因为 WHERE 条件会忽略终态行，替身必须一致，
         * 否则会把 TIMEOUT 覆盖成 FAILED，测出一个不存在的缺陷。</p>
         */
        private String currentStatus = "RUNNING";

        private boolean terminal(String status) {
            return "SUCCEEDED".equals(status) || "FAILED".equals(status)
                || "CANCELED".equals(status) || "TIMEOUT".equals(status);
        }

        @Override
        public long insertAsset(AssetRow row) {
            insertedAssets.add(row);
            assets.put(row.id(), row);
            return 1;
        }

        @Override
        public AssetRow requireOwnedAsset(long assetId, String tenantId, long userId) {
            if (assetNotFound || !assets.containsKey(assetId)) {
                throw ImageTaskException.assetNotFound("素材不存在或无权访问");
            }
            return assets.get(assetId);
        }

        @Override
        public long insertTask(TaskRow row) {
            return 1;
        }

        @Override
        public Long findByIdempotencyKey(String tenantId, long userId, String idempotencyKey) {
            return null;
        }

        @Override
        public Map<String, Object> requireOwnedTask(long taskId, String tenantId, long userId) {
            return new LinkedHashMap<>();
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
        public int transition(long taskId, ImageTaskStatus expectedFrom, ImageTaskStatus target,
                              String errorCode, String errorMessage) {
            if (!expectedFrom.name().equals(currentStatus)) {
                return 0;
            }
            currentStatus = target.name();
            lastStatus = target.name();
            lastErrorCode = errorCode;
            return 1;
        }

        @Override
        public int markFailedIfActive(long taskId, String errorCode, String errorMessage) {
            if (terminal(currentStatus)) {
                return 0;
            }
            currentStatus = "FAILED";
            lastStatus = "FAILED";
            lastErrorCode = errorCode;
            return 1;
        }

        @Override
        public int failAllRunning(String errorCode, String errorMessage) {
            return 0;
        }

        @Override
        public int markSubmitted(long taskId, String promptId, int attemptCount, String worker) {
            submitted++;
            return 1;
        }

        @Override
        public int markSucceeded(long taskId, long outputAssetId, Integer width, Integer height,
                                 boolean hasAlpha, long sizeBytes) {
            succeeded++;
            currentStatus = "SUCCEEDED";
            lastStatus = "SUCCEEDED";
            return 1;
        }

        @Override
        public void appendEvent(long eventId, long taskId, String tenantId, int sequence,
                                String eventType, String detail) {
            events.add(eventType);
        }

        @Override
        public List<Map<String, Object>> listEvents(long taskId, String tenantId) {
            return List.of();
        }

        @Override
        public int cancelQueued(long taskId, String tenantId, long userId) {
            return 0;
        }
    }

    /**
     * 落到临时目录的存储替身（让 ImageIO 能真实实测输出）。
     */
    static class MemoryStorage implements AssetStorage {
        private final Path root;

        MemoryStorage(Path root) {
            this.root = root;
        }

        @Override
        public String storeUpload(String tenantId, long userId, String originalName, byte[] content, String contentType) {
            return write(originalName, content);
        }

        @Override
        public String storeOutput(String tenantId, long userId, long taskId, String fileName,
                                  byte[] content, String contentType) {
            return write(taskId + "_" + fileName, content);
        }

        private String write(String name, byte[] content) {
            try {
                Path target = root.resolve(name.replace('/', '_'));
                Files.write(target, content);
                return target.getFileName().toString();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public byte[] read(String storageKey) {
            try {
                return Files.readAllBytes(root.resolve(storageKey));
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        public Path localPath(String storageKey) {
            Path path = root.resolve(storageKey);
            return Files.isRegularFile(path) ? path : null;
        }

        @Override
        public String keyOf(Path file) {
            return file.getFileName().toString();
        }

        @Override
        public Path thumbnailPath(String storageKey) {
            return root.resolve("thumb_" + storageKey + ".jpg");
        }
    }
}
