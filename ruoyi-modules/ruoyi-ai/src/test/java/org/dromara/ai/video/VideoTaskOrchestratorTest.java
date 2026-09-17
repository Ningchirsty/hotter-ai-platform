package org.dromara.ai.video;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.dromara.ai.video.comfy.ComfyClient;
import org.dromara.ai.video.comfy.ComfyOutput;
import org.dromara.ai.video.domain.VideoTaskStatus;
import org.dromara.ai.video.domain.WorkflowVersion;
import org.dromara.ai.video.exception.VideoTaskException;
import org.dromara.ai.video.service.AssetStorage;
import org.dromara.ai.video.service.ComfyWorkerPool;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
        // 三个 H3 已在契约中提升为 PUBLISHED（见 video-workflow-contracts.json），
        // 因此这里直接使用契约原状态。此前用 markTesting 把 DRAFT 改成 TESTING 的做法
        // 已不再需要，而且 markTesting 不检查当前状态，会把 PUBLISHED 降级，故已移除。
        preparer = new H3TemplatePreparer(MAPPER,
            new org.dromara.ai.video.config.VideoTierResolutions());
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
    @DisplayName("回归：720P 任务的 720P 成片必须通过验收（曾因断言写死 1080P 被误判）")
    void accepts720pOutputFor720pTask() {
        // 事故背景：开放 720P/480P 后，assertAcceptable 仍写死 1920×1080，
        // 于是已经生成、已经落盘的 720P 成片被判为 OUTPUT_INVALID；
        // 而当时的失败落库只认 QUEUED→FAILED，任务永远是 RUNNING。
        comfy.pollState = ComfyClient.PollResult.State.SUCCEEDED;
        probe.measured = true;
        probe.width = 1280;
        probe.height = 720;
        probe.durationMillis = 5000L;
        VideoTaskOrchestrator.ExecutionResult result = orchestrator(2000)
            .execute(context("T2V", "wf-t2v-h3", "提示词", H3TemplatePreparer.TIER_720P, null, null, null));
        assertEquals(1280, result.output().width(), "720P 成片必须被接受");
        assertEquals(720, result.output().height());
        assertTrue(repository.transitions.stream().anyMatch(t -> t.startsWith("SUCCEEDED:")),
            "720P 任务必须能成功落库");
    }

    @Test
    @DisplayName("回归：480P 任务的 480P 成片必须通过验收")
    void accepts480pOutputFor480pTask() {
        comfy.pollState = ComfyClient.PollResult.State.SUCCEEDED;
        probe.measured = true;
        probe.width = 864;
        probe.height = 480;
        probe.durationMillis = 5000L;
        VideoTaskOrchestrator.ExecutionResult result = orchestrator(2000)
            .execute(context("T2V", "wf-t2v-h3", "提示词", H3TemplatePreparer.TIER_480P, null, null, null));
        assertEquals(864, result.output().width());
        assertEquals(480, result.output().height());
    }

    @Test
    @DisplayName("回归：成片分辨率与所选档位不符时必须判定输出不合规")
    void rejectsOutputThatDoesNotMatchTier() {
        comfy.pollState = ComfyClient.PollResult.State.SUCCEEDED;
        probe.measured = true;
        // 选了 1080P，却拿到 720P 成片 —— 这才是真正该拒绝的情况。
        probe.width = 1280;
        probe.height = 720;
        probe.durationMillis = 4000L;
        VideoTaskException error = assertThrows(VideoTaskException.class,
            () -> orchestrator(2000).execute(context("T2V", "wf-t2v-h3", "提示词", null, null, null)));
        assertEquals("OUTPUT_INVALID", error.getErrorCode(), "档位不符必须拒绝");
    }

    @Test
    @DisplayName("回归：进入 RUNNING 之后的失败必须落库，不能留下永远运行的任务")
    void persistsFailureAfterTaskLeftQueued() {
        // 事故背景：任务在 markSubmitted 之后已是 RUNNING，而失败落库曾用
        // QUEUED→FAILED，WHERE 不匹配、影响 0 行，失败被静默吞掉。
        comfy.pollState = ComfyClient.PollResult.State.SUCCEEDED;
        probe.measured = true;
        probe.width = 640;   // 与所选档位不符 -> OUTPUT_INVALID
        probe.height = 360;
        assertThrows(VideoTaskException.class,
            () -> orchestrator(2000).execute(context("T2V", "wf-t2v-h3", "提示词", null, null, null)));
        assertTrue(repository.transitions.contains("FAILED_IF_ACTIVE:OUTPUT_INVALID"),
            "生成后处理失败必须以「不限定起始状态」的方式落库，实际流转：" + repository.transitions);
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
        // H3 三能力已提升为 PUBLISHED，因此这里改用仍是 DRAFT 的 wf-t2v-wan
        // （T2V 下尚未交付的条目，模板文件不存在）来承载本用例的语义：
        // 拒绝原因必须是「未发布/未通过验收」，先于模板缺失判定。
        WorkflowContractRegistry draftRegistry = new WorkflowContractRegistry(
            Path.of("").toAbsolutePath().getParent().getParent().resolve("script"), MAPPER);
        draftRegistry.load();
        comfy.pollState = ComfyClient.PollResult.State.SUCCEEDED;
        VideoTaskOrchestrator draftOrchestrator = new VideoTaskOrchestrator(
            draftRegistry, preparer, comfy, repository, storage, MAPPER,
            Duration.ofMillis(1000), Duration.ofMillis(1), () -> 9100L, probe);
        VideoTaskException error = assertThrows(VideoTaskException.class,
            () -> draftOrchestrator.execute(context("T2V", "wf-t2v-wan", "提示词", null, null, null)));
        assertEquals("INVALID_CONTRACT", error.getErrorCode(),
            "DRAFT 工作流必须在提交前被拒绝");
        assertTrue(error.getMessage().contains("尚未通过实机验收"),
            "拒绝原因应为未通过验收/未发布，实际：" + error.getMessage());
        assertFalse(comfy.submitted, "DRAFT 工作流不得提交到 ComfyUI");
    }

    @Test
    @DisplayName("已发布的 H3 工作流在正式环境（requirePublished=true）可以进入执行路径")
    void acceptsPublishedWorkflow() {
        // 与上个用例配对：证明契约状态是唯一开关。
        // requirePublished=true 正是生产环境的取值（VIDEO_REQUIRE_PUBLISHED=true）。
        VideoTaskOrchestrator.TaskContext ctx = new VideoTaskOrchestrator.TaskContext(
            1L, "000000", 100L, 10L, "T2V", "wf-t2v-h3", "提示词",
            H3TemplatePreparer.TIER_1080P, H3TemplatePreparer.DURATION_5S,
            null, null, null, true, System::nanoTime, new AtomicInteger(0));
        comfy.pollState = ComfyClient.PollResult.State.SUCCEEDED;
        assertDoesNotThrow(() -> orchestrator(1000).execute(ctx),
            "已发布工作流在正式环境不应被契约层拦截");
        assertTrue(comfy.submitted, "已发布工作流应真实提交到 ComfyUI");
    }

    @Test
    @DisplayName("非法 workflowCode 必须被拒绝")
    void rejectsUnknownWorkflow() {
        assertThrows(VideoTaskException.class,
            () -> orchestrator(1000).execute(context("T2V", "wf-does-not-exist", "提示词", null, null, null)));
    }

    @Test
    @DisplayName("配置开启时，提交前必须先请求 ComfyUI 释放显存")
    void freesComfyVramBeforeSubmitWhenEnabled() {
        // 真实事故：A100 空闲显存仅剩 14%（torch_vram_free 近乎 0）时，
        // H3 任务在 MiniMaxH3Director 节点被中断并报 COMFY_EXECUTION_FAILED。
        // 打开该开关后，每次提交前先归还显存与模型缓存。
        comfy.pollState = ComfyClient.PollResult.State.SUCCEEDED;
        VideoTaskOrchestrator freeOrchestrator = new VideoTaskOrchestrator(
            registry, preparer, comfy, repository, storage, MAPPER,
            Duration.ofMillis(1000), Duration.ofMillis(1), () -> 9300L, probe, true);
        assertDoesNotThrow(
            () -> freeOrchestrator.execute(context("T2V", "wf-t2v-h3", "提示词", null, null, null)));
        assertTrue(comfy.freed, "开启开关后必须调用过 freeMemory()");
        assertTrue(comfy.submitted, "释放显存后仍应正常提交");
    }

    @Test
    @DisplayName("默认关闭时不请求释放显存（保留 ComfyUI 的模型复用速度）")
    void doesNotFreeComfyVramByDefault() {
        comfy.pollState = ComfyClient.PollResult.State.SUCCEEDED;
        assertDoesNotThrow(
            () -> orchestrator(1000).execute(context("T2V", "wf-t2v-h3", "提示词", null, null, null)));
        assertFalse(comfy.freed, "默认不应调用 freeMemory()");
    }

    @Test
    @DisplayName("多 GPU：显存不足的节点直接跳过，任务在另一张卡上完成并记下卡名")
    void failsOverToAnotherWorkerWhenVramIsInsufficient() {
        // 真实背景：单任务峰值 80,805 MiB / 81,920 MiB。GPU0 上压着别的进程（曾计划给 vLLM）
        // 时，提交上去必然在采样节点 OOM，白等十几分钟。闸门要求先看空闲显存，
        // 不够就换一张卡——而不是撞上去 OOM。
        comfy.freeVram = 4_096L;
        StubComfyClient healthy = new StubComfyClient();
        healthy.freeVram = 80_000L;
        ComfyWorkerPool pool = new ComfyWorkerPool(List.of(
            new ComfyWorkerPool.Worker("gpu0", "http://gpu0:8189", comfy),
            new ComfyWorkerPool.Worker("gpu1", "http://gpu1:8188", healthy)),
            Duration.ofSeconds(60));

        VideoTaskOrchestrator pooled = new VideoTaskOrchestrator(registry, preparer, comfy, repository,
            storage, MAPPER, Duration.ofMillis(2000), Duration.ofMillis(1), () -> 9400L, probe, false,
            pool, 65_536L, Duration.ofSeconds(5));

        assertDoesNotThrow(
            () -> pooled.execute(context("T2V", "wf-t2v-h3", "提示词", null, null, null)),
            "一张卡显存不足时，任务应换到另一张卡完成，而不是直接失败");

        assertFalse(comfy.submitted, "显存不足的节点上不应提交任务");
        assertTrue(healthy.submitted, "任务必须落到显存充足的节点上");
        assertTrue(repository.transitions.contains("SUBMITTED:prompt-stub-1@gpu1"),
            "落库必须记下承担本次生成的工作节点，排障时才知道去问哪一台；实际为 "
                + repository.transitions);
        assertTrue(pool.snapshots().stream().anyMatch(s -> s.name().equals("gpu0") && s.unavailable()),
            "显存不足的节点必须进入冷却，避免下一个任务又撞上去");
    }

    @Test
    @DisplayName("多 GPU：所有节点显存都不足时快速失败，错误信息说明原因")
    void failsFastWhenNoWorkerHasEnoughVram() {
        comfy.freeVram = 1_024L;
        ComfyWorkerPool pool = new ComfyWorkerPool(List.of(
            new ComfyWorkerPool.Worker("gpu0", "http://gpu0:8189", comfy)), Duration.ofSeconds(60));
        VideoTaskOrchestrator pooled = new VideoTaskOrchestrator(registry, preparer, comfy, repository,
            storage, MAPPER, Duration.ofMillis(2000), Duration.ofMillis(1), () -> 9401L, probe, false,
            pool, 65_536L, Duration.ofSeconds(5));

        VideoTaskException error = assertThrows(VideoTaskException.class,
            () -> pooled.execute(context("T2V", "wf-t2v-h3", "提示词", null, null, null)));
        assertEquals("GPU_NOT_READY", error.getErrorCode());
        assertTrue(error.getMessage().contains("1024"),
            "错误信息必须带上实测空闲显存，便于运维定位是哪个进程占了卡：" + error.getMessage());
        assertFalse(comfy.submitted, "显存不足时不得提交");
    }

    @Test
    @DisplayName("时长上限：按本次任务时长截断，而不是固定用契约里的一代上限")
    void durationCapFollowsRequestedDuration() {
        // 背景（真实缺陷）：截断上限原先一律取契约 maxDurationSeconds。开放 10/20 秒后，
        // 若仍用契约上限截断，长时长成片会被误截回 5 秒。
        WorkflowVersion base = registry.peek("wf-t2v-h3");
        assertEquals(5000L, orchestrator(1000).resolveDurationCapMillis("5 秒", base));
        assertEquals(10000L, orchestrator(1000).resolveDurationCapMillis("10 秒", base));
        assertEquals(20000L, orchestrator(1000).resolveDurationCapMillis("20 秒", base));
        // 契约上限仍是硬上限：请求超过契约允许的最长时长时以契约为准（纵深防御）。
        WorkflowVersion capped = withMaxDuration(base, 10);
        assertEquals(10000L, orchestrator(1000).resolveDurationCapMillis("20 秒", capped));
        // 无法解析时退回契约上限，不抛异常。
        assertEquals(10000L, orchestrator(1000).resolveDurationCapMillis("", capped));
    }

    private WorkflowVersion withMaxDuration(WorkflowVersion base, int seconds) {
        return new WorkflowVersion(base.workflowCode(), base.capabilityCode(), base.modelCode(),
            base.version(), base.status(), base.apiJsonFile(), base.checksum(), base.mapping(),
            base.fixedFieldValidation(), seconds, base.outputNodeId(), base.outputField());
    }

    private VideoTaskOrchestrator.TaskContext context(String capability, String workflow, String prompt,
                                                      Long image, Long first, Long last) {
        return context(capability, workflow, prompt, H3TemplatePreparer.TIER_1080P, image, first, last);
    }

    private VideoTaskOrchestrator.TaskContext context(String capability, String workflow, String prompt,
                                                      String tier, Long image, Long first, Long last) {
        return new VideoTaskOrchestrator.TaskContext(1L, "000000", 100L, 10L,
            capability, workflow, prompt, tier,
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
        boolean freed = false;
        /**
         * 空闲显存（MiB）。默认充足；用例可调低以验证显存闸门。
         */
        long freeVram = 80_000L;
        PollResult.State pollState = PollResult.State.SUCCEEDED;
        List<ComfyOutput> outputs = List.of(new ComfyOutput("out.mp4", "", "output",
            1920, 1080, 24.0, 5000L, 512L));

        @Override
        public boolean freeMemory() {
            freed = true;
            return true;
        }

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

        @Override
        public long freeVramMb() {
            return freeVram;
        }
    }

    /**
     * 记录状态流转的仓储替身。
     */
    private static final class FakeRepository implements VideoTaskRepository {
        final List<String> transitions = new ArrayList<>();
        final Map<Long, AssetRow> ownedAssets = new HashMap<>();

        /**
         * 已经进入终态（失败）的任务。真实 SQL 用「不在终态」做守卫，
         * 因此第二次落库必须是 0 行——替身也要照这个语义来，否则测试会掩盖真实行为。
         */
        private final java.util.Set<Long> terminalTasks = new java.util.HashSet<>();

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
        public int markFailedIfActive(long taskId, String errorCode, String errorMessage) {
            // 与真实 SQL 一致：已经是终态就不再覆盖，返回 0 行。
            if (!terminalTasks.add(taskId)) {
                return 0;
            }
            transitions.add("FAILED_IF_ACTIVE:" + errorCode);
            return 1;
        }

        @Override
        public int failAllRunning(String errorCode, String errorMessage) {
            transitions.add("FAIL_ALL_RUNNING:" + errorCode);
            return 0;
        }

        @Override
        public int markSubmitted(long taskId, String comfyPromptId, int attemptCount) {
            transitions.add("SUBMITTED:" + comfyPromptId);
            return 1;
        }

        @Override
        public int markSubmitted(long taskId, String comfyPromptId, int attemptCount, String comfyWorker) {
            transitions.add("SUBMITTED:" + comfyPromptId + "@" + comfyWorker);
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
