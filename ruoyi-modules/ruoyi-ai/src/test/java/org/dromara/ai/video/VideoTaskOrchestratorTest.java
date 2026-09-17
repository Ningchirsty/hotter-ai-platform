package org.dromara.ai.video;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.dromara.ai.video.comfy.ComfyClient;
import org.dromara.ai.video.comfy.ComfyOutput;
import org.dromara.ai.video.domain.VideoTaskStatus;
import org.dromara.ai.video.exception.VideoTaskException;
import org.dromara.ai.video.service.AssetStorage;
import org.dromara.ai.video.service.H3TemplatePreparer;
import org.dromara.ai.video.service.MediaProbe;
import org.dromara.ai.video.service.VideoTaskOrchestrator;
import org.dromara.ai.video.service.VideoTaskRepository;
import org.dromara.ai.video.service.WorkflowContractRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用可控 ComfyUI 替身验证任务编排，覆盖文档 §5.5 要求的故障、超时与输出超 5 秒场景。
 *
 * <p>这些测试不接触 GPU，也不连接真实 ComfyUI。</p>
 */
class VideoTaskOrchestratorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WorkflowContractRegistry registry;
    private H3TemplatePreparer preparer;
    private StubComfyClient comfy;
    private FakeRepository repository;
    private FakeAssetStorage storage;
    private StubMediaProbe probe;

    @BeforeEach
    void setUp() {
        Path repoRoot = Path.of("").toAbsolutePath().getParent().getParent();
        registry = new WorkflowContractRegistry(repoRoot.resolve("script"), MAPPER);
        registry.load();
        // 交接文档 §5 要求在隔离联调环境把已完成单侧验收的工作流设为 TESTING；
        // 仓库契约本身必须保持 DRAFT，所以这里只改内存中的注册状态。
        assertTrue(registry.markTesting("wf-t2v-h3"), "T2V 应可标记为 TESTING");
        assertTrue(registry.markTesting("wf-i2v-h3"), "I2V 应可标记为 TESTING");
        assertTrue(registry.markTesting("wf-fl2v-h3"), "FL2V 应可标记为 TESTING");
        preparer = new H3TemplatePreparer(MAPPER);
        comfy = new StubComfyClient();
        repository = new FakeRepository();
        storage = new FakeAssetStorage();
        probe = new StubMediaProbe();
    }

    private VideoTaskOrchestrator orchestrator(long pollBudgetMillis) {
        // 注入确定性主键生成器与可控探针：
        // IdGeneratorUtil 依赖 Spring 容器，ffprobe 依赖运行环境，二者都不能进单测。
        java.util.concurrent.atomic.AtomicLong seq = new java.util.concurrent.atomic.AtomicLong(9000L);
        return new VideoTaskOrchestrator(registry, preparer, comfy, repository, storage, MAPPER,
            Duration.ofMillis(pollBudgetMillis), Duration.ofMillis(1), seq::incrementAndGet, probe);
    }

    /**
     * 可控的成片探针替身：默认「未实测」，用例可显式设置实测值。
     */
    private static final class StubMediaProbe extends MediaProbe {
        boolean measured = false;
        Integer width = 1920;
        Integer height = 1080;
        Double fps = 24.0;
        Long durationMillis = 5000L;
        boolean truncateCalled = false;
        Double capturedFps = null;

        StubMediaProbe() {
            super("ffprobe-not-used-in-tests", "ffmpeg-not-used-in-tests", 5);
        }

        @Override
        public Probe probe(java.nio.file.Path file) {
            return measured
                ? new Probe(width, height, fps, durationMillis, true)
                : Probe.unmeasured();
        }

        @Override
        public java.nio.file.Path truncate(java.nio.file.Path source, long maxMillis, Double fps) {
            truncateCalled = true;
            capturedFps = fps;
            // 模拟帧精确截断后的重新实测：时长变为上限值。
            this.durationMillis = maxMillis;
            return java.nio.file.Path.of("/tmp/capped.mp4");
        }

        @Override
        public void assertAcceptable(Probe p) {
            // 委托真实实现，保持 1080P 校验语义一致。
            super.assertAcceptable(p);
        }
    }

    @Test
    @DisplayName("ComfyUI 不可达时不提交，并落库为失败")
    void failsFastWhenComfyUnreachable() {
        comfy.reachable = false;
        VideoTaskException error = assertThrows(VideoTaskException.class,
            () -> orchestrator(1000).execute(context("T2V", "wf-t2v-h3", "提示词", null, null, null)));
        assertEquals("COMFY_FAILURE", error.getErrorCode());
        assertEquals(1, repository.transitions.size(), "应记录一次状态流转");
        assertTrue(repository.transitions.get(0).contains("FAILED"),
            "不可达必须落库为 FAILED，而不是留下 RUNNING");
        assertFalse(comfy.submitted, "不可达时不应提交任务");
    }

    @Test
    @DisplayName("ComfyUI 执行失败时落库为 FAILED")
    void recordsExecutionFailure() {
        comfy.pollState = ComfyClient.PollResult.State.FAILED;
        assertThrows(VideoTaskException.class,
            () -> orchestrator(2000).execute(context("T2V", "wf-t2v-h3", "提示词", null, null, null)));
        assertTrue(repository.transitions.stream().anyMatch(t -> t.contains("FAILED")));
    }

    @Test
    @DisplayName("轮询超时落库为 TIMEOUT")
    void recordsTimeout() {
        comfy.pollState = ComfyClient.PollResult.State.RUNNING;
        VideoTaskException error = assertThrows(VideoTaskException.class,
            () -> orchestrator(120).execute(context("T2V", "wf-t2v-h3", "提示词", null, null, null)));
        assertEquals("COMFY_TIMEOUT", error.getErrorCode());
        assertTrue(repository.transitions.stream().anyMatch(t -> t.contains("TIMEOUT")),
            "超时必须落库为 TIMEOUT");
    }

    @Test
    @DisplayName("回归：实测成片 5.167 秒时必须标记 truncation_applied 并真实截断")
    void marksTruncationWhenOverFiveSeconds() {
        comfy.pollState = ComfyClient.PollResult.State.SUCCEEDED;
        // 真实 A100 实测值：124 帧 @24fps = 5167ms。截断判定来自 ffprobe，
        // 不再来自 ComfyUI 输出（后者根本不返回时长）。
        probe.measured = true;
        probe.durationMillis = 5167L;
        probe.width = 1920;
        probe.height = 1080;
        probe.fps = 24.0;
        VideoTaskOrchestrator.ExecutionResult result = orchestrator(2000)
            .execute(context("T2V", "wf-t2v-h3", "提示词", null, null, null));
        assertTrue(result.truncated(), "5.167 秒必须被标记为需要截断");
        assertTrue(probe.truncateCalled, "必须真的调用截断，而不是只打标记");
        assertEquals(24.0, probe.capturedFps, "必须把实测帧率传给截断，才能帧精确（24fps 下 5s=120 帧）");
        assertEquals(5000L, probe.durationMillis, "截断后应重新实测为 5 秒");
        // 回归：任务指标必须来自实测，曾因误用 ComfyUI 输出导致全为 NULL。
        assertEquals(1920, result.output().width(), "返回值必须带实测宽度");
        assertEquals(1080, result.output().height(), "返回值必须带实测高度");
        assertEquals(5000L, result.output().durationMillis(), "返回值必须带实测时长");
        assertTrue(repository.transitions.stream().anyMatch(t -> t.startsWith("SUCCEEDED:")),
            "必须落库成功状态");
    }

    @Test
    @DisplayName("实测成片 5 秒以内不标记截断")
    void noTruncationWhenWithinLimit() {
        comfy.pollState = ComfyClient.PollResult.State.SUCCEEDED;
        probe.measured = true;
        probe.durationMillis = 5000L;
        probe.width = 1920;
        probe.height = 1080;
        probe.fps = 24.0;
        VideoTaskOrchestrator.ExecutionResult result = orchestrator(2000)
            .execute(context("T2V", "wf-t2v-h3", "提示词", null, null, null));
        assertFalse(result.truncated());
        assertFalse(probe.truncateCalled, "未超时不应触发截断");
        assertEquals(1920, result.output().width());
        assertEquals(5000L, result.output().durationMillis());
    }

    @Test
    @DisplayName("回归：实测分辨率不是 1080P 时必须判定输出不合规")
    void rejectsNonPlain1080pOutput() {
        comfy.pollState = ComfyClient.PollResult.State.SUCCEEDED;
        probe.measured = true;
        probe.width = 1280;
        probe.height = 720;
        probe.durationMillis = 4000L;
        VideoTaskException error = assertThrows(VideoTaskException.class,
            () -> orchestrator(2000).execute(context("T2V", "wf-t2v-h3", "提示词", null, null, null)));
        assertEquals("OUTPUT_INVALID", error.getErrorCode(), "720P 成片不得通过验收");
    }

    @Test
    @DisplayName("环境缺少 ffprobe 时退化为未实测：不得声称已截断或已合规")
    void unmeasuredEnvironmentDoesNotClaimCompliance() {
        comfy.pollState = ComfyClient.PollResult.State.SUCCEEDED;
        probe.measured = false;
        VideoTaskOrchestrator.ExecutionResult result = orchestrator(2000)
            .execute(context("T2V", "wf-t2v-h3", "提示词", null, null, null));
        assertFalse(result.truncated(), "未实测就不能声称已截断");
        assertFalse(probe.truncateCalled, "未实测不应盲截");
    }

    @Test
    @DisplayName("素材归属：跨用户素材按不存在处理")
    void rejectsAssetFromAnotherUser() {
        repository.ownedAssets.clear();
        comfy.pollState = ComfyClient.PollResult.State.SUCCEEDED;
        comfy.outputs = List.of(new ComfyOutput("out.mp4", "", "output",
            1920, 1080, 24.0, 4000L, 1024L));
        assertThrows(VideoTaskException.class,
            () -> orchestrator(2000).execute(context("I2V", "wf-i2v-h3", "提示词", 999L, null, null)),
            "不属于当前用户的素材必须拒绝");
        assertFalse(comfy.uploaded, "越权素材不应被上传到 ComfyUI");
    }

    @Test
    @DisplayName("DRAFT 工作流不得执行（未实机验收前不可提交）")
    void rejectsDraftWorkflow() {
        // 独立的注册表保持契约原始状态（DRAFT）
        WorkflowContractRegistry draftRegistry = new WorkflowContractRegistry(
            Path.of("").toAbsolutePath().getParent().getParent().resolve("script"), MAPPER);
        draftRegistry.load();
        comfy.pollState = ComfyClient.PollResult.State.SUCCEEDED;
        VideoTaskOrchestrator draftOrchestrator = new VideoTaskOrchestrator(
            draftRegistry, preparer, comfy, repository, storage, MAPPER,
            Duration.ofMillis(1000), Duration.ofMillis(1), () -> 9100L, probe);
        VideoTaskException error = assertThrows(VideoTaskException.class,
            () -> draftOrchestrator.execute(context("T2V", "wf-t2v-h3", "提示词", null, null, null)));
        assertEquals("INVALID_CONTRACT", error.getErrorCode(),
            "DRAFT 工作流必须在提交前被拒绝");
        assertFalse(comfy.submitted, "DRAFT 工作流不得提交到 ComfyUI");
    }

    @Test
    @DisplayName("非法 workflowCode 必须被拒绝")
    void rejectsUnknownWorkflow() {
        assertThrows(VideoTaskException.class,
            () -> orchestrator(1000).execute(context("T2V", "wf-does-not-exist", "提示词", null, null, null)));
    }

    private VideoTaskOrchestrator.TaskContext context(String capability, String workflow, String prompt,
                                                      Long image, Long first, Long last) {
        return new VideoTaskOrchestrator.TaskContext(1L, "000000", 100L, 10L,
            capability, workflow, prompt, H3TemplatePreparer.TIER_1080P,
            H3TemplatePreparer.DURATION_5S, image, first, last, false,
            System::nanoTime, new AtomicInteger(0));
    }

    /**
     * 可控 ComfyUI 替身。
     */
    private static final class StubComfyClient implements ComfyClient {
        boolean reachable = true;
        boolean submitted = false;
        boolean uploaded = false;
        PollResult.State pollState = PollResult.State.SUCCEEDED;
        List<ComfyOutput> outputs = List.of(new ComfyOutput("out.mp4", "", "output",
            1920, 1080, 24.0, 5000L, 512L));

        @Override
        public String uploadImage(String fileName, byte[] content, String mimeType) {
            uploaded = true;
            return fileName;
        }

        @Override
        public String submitPrompt(JsonNode graph) {
            submitted = true;
            return "prompt-stub-1";
        }

        @Override
        public PollResult poll(String promptId) {
            if (pollState == PollResult.State.SUCCEEDED) {
                return PollResult.succeeded(outputs);
            }
            if (pollState == PollResult.State.FAILED) {
                return PollResult.failed("stub failure");
            }
            return PollResult.running();
        }

        @Override
        public byte[] fetchOutput(ComfyOutput output) {
            return "fake-mp4-bytes".getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public boolean isReachable() {
            return reachable;
        }
    }

    /**
     * 记录状态流转的仓储替身。
     */
    private static final class FakeRepository implements VideoTaskRepository {
        final List<String> transitions = new ArrayList<>();
        final Map<Long, AssetRow> ownedAssets = new HashMap<>();

        @Override
        public long insertAsset(AssetRow asset) {
            ownedAssets.put(asset.id() == null ? 1L : asset.id(), asset);
            return 1L;
        }

        @Override
        public AssetRow requireOwnedAsset(long assetId, String tenantId, long userId) {
            AssetRow row = ownedAssets.get(assetId);
            if (row == null) {
                throw VideoTaskException.assetNotFound("素材不存在或无权访问");
            }
            return row;
        }

        @Override
        public long insertTask(TaskRow task) {
            return 1L;
        }

        @Override
        public int transition(long taskId, VideoTaskStatus from, VideoTaskStatus to,
                              String errorCode, String errorMessage) {
            transitions.add(from + "->" + to);
            return 1;
        }

        @Override
        public int markSubmitted(long taskId, String comfyPromptId, int attemptCount) {
            transitions.add("SUBMITTED:" + comfyPromptId);
            return 1;
        }

        @Override
        public int markSucceeded(long taskId, long outputAssetId, long coverAssetId,
                                 Integer width, Integer height, Double fps,
                                 Long durationMillis, boolean truncated) {
            transitions.add("SUCCEEDED:truncated=" + truncated);
            return 1;
        }

        @Override
        public void appendEvent(long id, long taskId, String tenantId, int sequence,
                               String eventType, String detail) {
            // 测试不校验事件表
        }

        // 其余方法在本测试中不被调用
        @Override
        public Long findByIdempotencyKey(String tenantId, long userId, String key) {
            return null;
        }

        @Override
        public Map<String, Object> requireOwnedTask(long taskId, String tenantId, long userId) {
            return Map.of();
        }

        @Override
        public List<Map<String, Object>> listOwnedTasks(String t, long u, String s, int o, int l) {
            return List.of();
        }

        @Override
        public long countOwnedTasks(String tenantId, long userId, String status) {
            return 0;
        }

        @Override
        public List<Map<String, Object>> listOwnedAssets(String t, long u, int o, int l) {
            return List.of();
        }

        @Override
        public long countOwnedAssets(String tenantId, long userId) {
            return 0;
        }

        @Override
        public int softDeleteAsset(long assetId, String tenantId, long userId) {
            return 0;
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
     * 内存素材存储替身。
     */
    private static final class FakeAssetStorage implements AssetStorage {

        /** 提供一个真实存在的临时文件，使「截断后重算大小/校验和」路径也被覆盖。 */
        private final java.nio.file.Path local;

        FakeAssetStorage() {
            try {
                local = java.nio.file.Files.createTempFile("video-orch-test-", ".mp4");
                java.nio.file.Files.write(local, "capped-content".getBytes(StandardCharsets.UTF_8));
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public String storeUpload(String tenantId, long userId, String originalName,
                                 byte[] content, String contentType) {
            return tenantId + "/" + userId + "/upload/fake.png";
        }

        @Override
        public String storeOutput(String tenantId, long userId, long taskId, String fileName,
                                 byte[] content, String contentType) {
            return tenantId + "/" + userId + "/output/fake.mp4";
        }

        @Override
        public byte[] read(String storageKey) {
            return "fake-image".getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public java.nio.file.Path localPath(String storageKey) {
            return local;
        }
    }
}
