package org.dromara.ai.video;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.dromara.ai.video.domain.VideoCapability;
import org.dromara.ai.video.domain.WorkflowVersion;
import org.dromara.ai.video.exception.VideoTaskException;
import org.dromara.ai.video.service.H3TemplatePreparer;
import org.dromara.ai.video.service.WorkflowContractRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 契约填充与安全边界的离线测试。
 *
 * <p>这些测试使用仓库内真实的契约与模板文件，但<b>不调用 ComfyUI</b>，
 * 因此可以在没有 GPU 的环境运行。真正的显存/耗时/成片验收必须另行在 GPU 实机完成。</p>
 */
class H3TemplatePreparerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static Path contractRoot;
    private static WorkflowContractRegistry registry;
    private static H3TemplatePreparer preparer;

    @BeforeAll
    static void setUp() throws Exception {
        // Surefire 的 working directory 是模块目录，契约在仓库根的 script 下。
        Path moduleDir = Path.of("").toAbsolutePath();
        Path repoRoot = moduleDir.getParent().getParent();
        contractRoot = repoRoot.resolve("script");
        assertTrue(Files.isDirectory(contractRoot.resolve("video/workflows")),
            "契约目录必须存在：" + contractRoot);
        registry = new WorkflowContractRegistry(contractRoot, MAPPER);
        registry.load();
        preparer = new H3TemplatePreparer(MAPPER);
    }

    @Test
    @DisplayName("诊断：确认契约根目录与契约文件可被定位")
    void contractRootIsLocatable() {
        System.out.println("[diag] moduleDir  = " + Path.of("").toAbsolutePath());
        System.out.println("[diag] contractRoot = " + contractRoot);
        System.out.println("[diag] contract exists = "
            + Files.isRegularFile(contractRoot.resolve(WorkflowContractRegistry.CONTRACT_FILE)));
        System.out.println("[diag] workflows dir = "
            + Files.isDirectory(contractRoot.resolve("video/workflows")));
        System.out.println("[diag] registered=" + registry.registeredCount()
            + " loaded=" + registry.loadedCount());
        assertTrue(Files.isRegularFile(contractRoot.resolve(WorkflowContractRegistry.CONTRACT_FILE)),
            "契约文件必须可定位");
    }

    @Test
    @DisplayName("契约加载：三个 H3 模板校验和全部通过并进入可提交集合")
    void loadsThreeH3Templates() {
        assertEquals(3, registry.loadedCount(), "应恰好加载 3 个 H3 模板");
        // DRAFT 状态仍不可提交（未通过实机验收）
        assertThrows(VideoTaskException.class,
            () -> registry.require("wf-t2v-h3", false),
            "DRAFT 工作流不得作为可提交任务提供");
        assertFalse(registry.hasPublished(VideoCapability.T2V), "任何 H3 版本在实机验收前都不得标记已发布");
    }

    @Test
    @DisplayName("校验值守卫：模板被改动后必须拒绝加载")
    void rejectsTamperedTemplate() {
        WorkflowVersion version = versionOf("wf-t2v-h3");
        String original = registry.templateOf("wf-t2v-h3");
        String tampered = original.replace("minimax_h3_fl2va_bf16.safetensors", "evil.safetensors");
        assertNotEquals(original, tampered, "测试前提：替换应真的改动了内容");
        assertThrows(VideoTaskException.class,
            () -> preparer.verifyChecksum(tampered, version),
            "校验值不匹配时必须拒绝");
    }

    @Test
    @DisplayName("T2V：只写白名单字段，采样参数与模型路径保持不变")
    void t2vOnlyWritesWhitelistedFields() throws Exception {
        WorkflowVersion version = versionOf("wf-t2v-h3");
        String template = registry.templateOf("wf-t2v-h3");
        JsonNode before = MAPPER.readTree(template);

        JsonNode graph = preparer.prepare(template, VideoCapability.T2V, version,
            fields("城市夜景延时，霓虹沿街道流动汇聚成品牌标志", null, null, null));

        JsonNode directorBefore = before.get("5").get("inputs");
        JsonNode directorAfter = graph.get("5").get("inputs");

        // 提示词确实写入三处
        assertEquals("城市夜景延时，霓虹沿街道流动汇聚成品牌标志",
            directorAfter.get("global_prompt").asText());
        JsonNode timeline = MAPPER.readTree(directorAfter.get("timeline_data").asText());
        assertEquals("城市夜景延时，霓虹沿街道流动汇聚成品牌标志",
            timeline.get("global").get("prompt").asText());
        assertEquals("城市夜景延时，霓虹沿街道流动汇聚成品牌标志",
            timeline.get("segments").get(0).get("prompt").asText());
        assertEquals("城市夜景延时，霓虹沿街道流动汇聚成品牌标志",
            timeline.get("shots").get(0).get("prompt").asText());

        // 固定后台参数非白名单，必须一字不改
        assertEquals(directorBefore.get("cfg").asText(), directorAfter.get("cfg").asText(), "cfg 不得被覆写");
        assertEquals(directorBefore.get("steps").asText(), directorAfter.get("steps").asText(), "steps 不得被覆写");
        assertEquals(directorBefore.get("sampler").asText(), directorAfter.get("sampler").asText(), "sampler 不得被覆写");
        assertEquals(directorBefore.get("width").asText(), directorAfter.get("width").asText(), "width 不得被覆写");
        assertEquals(directorBefore.get("total_frames").asText(), directorAfter.get("total_frames").asText());
        // 模型路径节点完全不变
        assertEquals(before.get("1").get("inputs").get("unet_name").asText(),
            graph.get("1").get("inputs").get("unet_name").asText(), "模型路径不得被覆写");
        assertEquals(before.get("2").get("inputs").get("clip_name").asText(),
            graph.get("2").get("inputs").get("clip_name").asText());
    }

    @Test
    @DisplayName("原模板对象不被修改（深拷贝语义）")
    void doesNotMutateTemplate() throws Exception {
        WorkflowVersion version = versionOf("wf-t2v-h3");
        String template = registry.templateOf("wf-t2v-h3");
        preparer.prepare(template, VideoCapability.T2V, version,
            fields("深拷贝验证", null, null, null));
        JsonNode after = MAPPER.readTree(template);
        assertEquals("", after.get("5").get("inputs").get("global_prompt").asText(),
            "传入的模板原文必须保持空提示词");
    }

    @Test
    @DisplayName("I2V：图片写入 keyframes 与分镜/分段")
    void i2vWritesImage() throws Exception {
        WorkflowVersion version = versionOf("wf-i2v-h3");
        JsonNode graph = preparer.prepare(registry.templateOf("wf-i2v-h3"), VideoCapability.I2V, version,
            fields("镜头从包装细节缓慢拉远", "input_abc123.png", null, null));
        JsonNode timeline = MAPPER.readTree(graph.get("5").get("inputs").get("timeline_data").asText());
        assertEquals("input_abc123.png", timeline.get("keyframes").get(0).get("imageFile").asText());
        assertEquals("input_abc123.png", timeline.get("segments").get(0).get("genImage").get("imageFile").asText());
        assertEquals("input_abc123.png", timeline.get("shots").get(0).get("startImage").get("imageFile").asText());
    }

    @Test
    @DisplayName("I2V 缺少图片必须被拒绝")
    void i2vRequiresImage() {
        WorkflowVersion version = versionOf("wf-i2v-h3");
        assertThrows(VideoTaskException.class,
            () -> preparer.prepare(registry.templateOf("wf-i2v-h3"), VideoCapability.I2V, version,
                fields("缺少图片", null, null, null)),
            "缺失图片必须拒绝");
    }

    @Test
    @DisplayName("FL2V：首尾帧分别写入 keyframes[0] 与 keyframes[1]")
    void fl2vWritesBothFrames() throws Exception {
        WorkflowVersion version = versionOf("wf-fl2v-h3");
        JsonNode graph = preparer.prepare(registry.templateOf("wf-fl2v-h3"), VideoCapability.FL2V, version,
            fields("从产品特写切换至完整场景", null, "first_1.png", "last_2.png"));
        JsonNode timeline = MAPPER.readTree(graph.get("5").get("inputs").get("timeline_data").asText());
        assertEquals("first_1.png", timeline.get("keyframes").get(0).get("imageFile").asText());
        assertEquals("last_2.png", timeline.get("keyframes").get(1).get("imageFile").asText());
        assertEquals("last_2.png", timeline.get("segments").get(0).get("endImage").get("imageFile").asText());
    }

    @Test
    @DisplayName("FL2V 缺任一首尾帧必须被拒绝")
    void fl2vRequiresBothFrames() {
        WorkflowVersion version = versionOf("wf-fl2v-h3");
        assertThrows(VideoTaskException.class, () -> preparer.prepare(
            registry.templateOf("wf-fl2v-h3"), VideoCapability.FL2V, version,
            fields("只有首帧", null, "first_1.png", null)));
        assertThrows(VideoTaskException.class, () -> preparer.prepare(
            registry.templateOf("wf-fl2v-h3"), VideoCapability.FL2V, version,
            fields("只有尾帧", null, null, "last_2.png")));
    }

    @Test
    @DisplayName("固定档位校验：720P / 10 秒不得提交")
    void rejectsUnsupportedTiers() {
        WorkflowVersion version = versionOf("wf-t2v-h3");
        assertThrows(VideoTaskException.class, () -> preparer.prepare(
            registry.templateOf("wf-t2v-h3"), VideoCapability.T2V, version,
            new H3TemplatePreparer.H3Fields("提示词", null, null, null, "流畅 · 720P", "5 秒")),
            "720P 未交付，必须拒绝");
        assertThrows(VideoTaskException.class, () -> preparer.prepare(
            registry.templateOf("wf-t2v-h3"), VideoCapability.T2V, version,
            new H3TemplatePreparer.H3Fields("提示词", null, null, null, H3TemplatePreparer.TIER_1080P, "10 秒")),
            "10 秒未交付，必须拒绝");
    }

    @Test
    @DisplayName("空提示词必须被拒绝")
    void rejectsBlankPrompt() {
        WorkflowVersion version = versionOf("wf-t2v-h3");
        assertThrows(VideoTaskException.class, () -> preparer.prepare(
            registry.templateOf("wf-t2v-h3"), VideoCapability.T2V, version,
            fields("   ", null, null, null)));
    }

    @Test
    @DisplayName("回归：validateFields 必须拦住空提示词（入库前校验）")
    void validateFieldsRejectsBlankPromptBeforePersist() {
        // 该用例对应一个已修复的真实缺陷：控制器在入库前调用 validateFields，
        // 但当时该方法不校验提示词，导致空提示词任务被入库后才在执行阶段失败。
        WorkflowVersion version = versionOf("wf-t2v-h3");
        VideoTaskException error = assertThrows(VideoTaskException.class,
            () -> preparer.validateFields(VideoCapability.T2V, version,
                fields("   ", null, null, null)),
            "入库前校验必须拒绝空提示词");
        assertEquals("INVALID_CONTRACT", error.getErrorCode());
    }

    @Test
    @DisplayName("回归：validateFields 必须拦住缺失素材与不支持的档位")
    void validateFieldsRejectsMissingAssetsAndTier() {
        WorkflowVersion i2v = versionOf("wf-i2v-h3");
        assertThrows(VideoTaskException.class, () -> preparer.validateFields(
            VideoCapability.I2V, i2v, fields("正常提示词", null, null, null)),
            "I2V 缺图片必须在入库前被拒绝");
        WorkflowVersion fl2v = versionOf("wf-fl2v-h3");
        assertThrows(VideoTaskException.class, () -> preparer.validateFields(
            VideoCapability.FL2V, fl2v, fields("正常提示词", null, "f.png", null)),
            "FL2V 缺尾帧必须在入库前被拒绝");
        WorkflowVersion t2v = versionOf("wf-t2v-h3");
        assertThrows(VideoTaskException.class, () -> preparer.validateFields(
            VideoCapability.T2V, t2v,
            new H3TemplatePreparer.H3Fields("正常提示词", null, null, null, "流畅 · 720P", "5 秒")),
            "720P 必须在入库前被拒绝");
        assertThrows(VideoTaskException.class, () -> preparer.validateFields(
            VideoCapability.T2V, t2v,
            new H3TemplatePreparer.H3Fields("正常提示词", null, null, null,
                H3TemplatePreparer.TIER_1080P, "10 秒")),
            "10 秒必须在入库前被拒绝");
    }

    @Test
    @DisplayName("合法字段通过入库前校验（对照组）")
    void validateFieldsAcceptsLegalPayload() {
        WorkflowVersion version = versionOf("wf-t2v-h3");
        preparer.validateFields(VideoCapability.T2V, version,
            fields("正常的视频描述", null, null, null));
        preparer.validateFields(VideoCapability.I2V, versionOf("wf-i2v-h3"),
            fields("正常的视频描述", "img.png", null, null));
        preparer.validateFields(VideoCapability.FL2V, versionOf("wf-fl2v-h3"),
            fields("正常的视频描述", null, "a.png", "b.png"));
    }

    @Test
    @DisplayName("字段白名单：非契约字段必须被拒绝")
    void rejectsUnknownFields() {
        List<String> allowed = List.of("desc", "tier", "dur");
        preparer.validateFieldWhitelist(allowed, Map.of("desc", "ok", "tier", "高清 · 1080P"));
        assertThrows(VideoTaskException.class,
            () -> preparer.validateFieldWhitelist(allowed, Map.of("nodeId", "1")),
            "任意节点覆写字段必须拒绝");
        assertThrows(VideoTaskException.class,
            () -> preparer.validateFieldWhitelist(allowed, Map.of("sampler", "euler")),
            "采样参数覆盖必须拒绝");
    }

    @Test
    @DisplayName("模板与能力不匹配必须被拒绝")
    void rejectsCapabilityMismatch() {
        WorkflowVersion i2vVersion = versionOf("wf-i2v-h3");
        assertThrows(VideoTaskException.class,
            () -> preparer.prepare(registry.templateOf("wf-i2v-h3"), VideoCapability.T2V, i2vVersion,
                fields("用图生视频模板跑文生视频", "x.png", null, null)),
            "task_type 前缀不匹配必须拒绝");
    }

    @Test
    @DisplayName("安全边界：模板不得包含样例附件引用")
    void templateHasNoSampleAttachmentReference() throws Exception {
        for (String code : List.of("wf-t2v-h3", "wf-i2v-h3", "wf-fl2v-h3")) {
            String content = Files.readString(
                contractRoot.resolve("video/workflows/api/" + code + "-v0.1.0.json"),
                StandardCharsets.UTF_8);
            assertFalse(content.matches("(?is).*(mmwebwx|webwxgetmsgimg|@crypt_|MsgID=).*"),
                code + " 不得残留样例附件引用");
            JsonNode timeline = MAPPER.readTree(
                MAPPER.readTree(content).get("5").get("inputs").get("timeline_data").asText());
            for (JsonNode keyframe : timeline.path("keyframes")) {
                assertEquals("", keyframe.path("imageFile").asText(),
                    code + " 的 keyframes 必须已清空样例图片");
            }
        }
    }

    @Test
    @DisplayName("SHA-256 计算与契约校验值一致")
    void sha256MatchesContractChecksum() throws Exception {
        for (String code : List.of("wf-t2v-h3", "wf-i2v-h3", "wf-fl2v-h3")) {
            String content = Files.readString(
                contractRoot.resolve("video/workflows/api/" + code + "-v0.1.0.json"),
                StandardCharsets.UTF_8);
            assertFalse(preparer.verifyChecksumException(content, versionOf(code)),
                code + " 的校验值应与契约一致");
        }
    }

    private static H3TemplatePreparer.H3Fields fields(String text, String imageFile,
                                                     String firstFile, String lastFile) {
        return new H3TemplatePreparer.H3Fields(text, imageFile, firstFile, lastFile,
            H3TemplatePreparer.TIER_1080P, H3TemplatePreparer.DURATION_5S);
    }

    private static WorkflowVersion versionOf(String code) {
        try {
            // 通过反射无关的最小路径取得已加载版本：DRAFT 也在注册表中。
            return registry.require(code, false);
        } catch (VideoTaskException e) {
            // DRAFT 不可提交，但测试需要其版本定义，改由契约直接解析。
            return readVersionFromContract(code);
        }
    }

    private static WorkflowVersion readVersionFromContract(String code) {
        try {
            JsonNode root = MAPPER.readTree(Files.readString(
                contractRoot.resolve(WorkflowContractRegistry.CONTRACT_FILE), StandardCharsets.UTF_8));
            for (JsonNode capability : root.path("capabilities")) {
                for (JsonNode binding : capability.path("workflows")) {
                    if (code.equals(binding.path("workflowCode").asText())) {
                        List<org.dromara.ai.video.domain.WorkflowMapping> mappings = new java.util.ArrayList<>();
                        for (JsonNode item : binding.path("mapping")) {
                            if (!item.path("nodeId").asText("").startsWith("TBD")) {
                                mappings.add(new org.dromara.ai.video.domain.WorkflowMapping(
                                    item.path("field").asText(""),
                                    item.path("nodeId").asText(""),
                                    item.path("inputKey").asText(""),
                                    item.path("note").asText("")));
                            }
                        }
                        JsonNode fixed = binding.path("fixedFieldValidation");
                        return new WorkflowVersion(
                            code,
                            capability.path("capabilityCode").asText(""),
                            binding.path("modelCode").asText(null),
                            binding.path("version").asText(""),
                            binding.path("status").asText("DRAFT"),
                            binding.path("apiJsonFile").asText(""),
                            binding.path("checksum").asText(""),
                            mappings,
                            new WorkflowVersion.FixedFieldValidation(
                                fixed.path("tier").asText(null), fixed.path("dur").asText(null)),
                            5,
                            binding.path("outputRule").path("nodeId").asText("7"),
                            binding.path("outputRule").path("outputField").asText("videos"));
                    }
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        throw new IllegalStateException("契约中找不到工作流：" + code);
    }
}
