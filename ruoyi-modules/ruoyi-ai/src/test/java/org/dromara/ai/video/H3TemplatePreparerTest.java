package org.dromara.ai.video;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
        // 注入档位表，使 prepare() 会按档位改写模板分辨率（生产同样注入）。
        preparer = new H3TemplatePreparer(MAPPER,
            new org.dromara.ai.video.config.VideoTierResolutions());
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
    @DisplayName("契约加载：三个 H3 模板校验和全部通过，且均已发布为可提交")
    void loadsThreeH3Templates() {
        assertEquals(3, registry.loadedCount(), "应恰好加载 3 个 H3 模板");
        // 三个 H3 已获业务批准并提升为 PUBLISHED，因此在正式环境（requirePublished=true）可提交。
        assertDoesNotThrow(() -> registry.require("wf-t2v-h3", true),
            "已 PUBLISHED 的工作流在正式环境应可提交");
        assertTrue(registry.hasPublished(VideoCapability.T2V), "T2V 应存在已发布版本");
        assertTrue(registry.hasPublished(VideoCapability.I2V), "I2V 应存在已发布版本");
        assertTrue(registry.hasPublished(VideoCapability.FL2V), "FL2V 应存在已发布版本");
        // 注册表登记了 24 个条目（含未交付占位），但已发布的必须恰好只有这 3 个。
        // 注意 VideoCapability 枚举只有 T2V/I2V/FL2V，MFRAME 等只存在于契约里，
        // 不能用枚举逐个断言。
        long publishedCount = registry.registeredVersions().stream()
            .filter(WorkflowVersion::isPublished)
            .count();
        assertEquals(3, publishedCount, "已发布版本应恰好 3 个（三个 H3）");
    }

    @Test
    @DisplayName("未发布的 DRAFT 工作流在正式环境不得提交")
    void rejectsDraftWorkflowInFormalEnvironment() {
        // wf-t2v-wan 是 T2V 下尚未交付的 DRAFT 条目，且模板文件不存在。
        // 这里断言的是「未发布」这一拒绝原因，先于模板缺失判定。
        VideoTaskException error = assertThrows(VideoTaskException.class,
            () -> registry.require("wf-t2v-wan", true),
            "DRAFT 工作流不得在正式环境作为可提交任务提供");
        assertEquals("INVALID_CONTRACT", error.getErrorCode());
        assertTrue(error.getMessage().contains("尚未通过实机验收"),
            "拒绝原因应为未通过验收/未发布，实际：" + error.getMessage());
        assertTrue(registry.hasPublished(VideoCapability.T2V),
            "T2V 已发布的是 wf-t2v-h3，与 DRAFT 的 wf-t2v-wan 互不影响");
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
    @DisplayName("固定档位校验：未交付的时长必须被拒绝；已开放的 720P 应通过")
    void rejectsUnsupportedTiers() {
        WorkflowVersion version = versionOf("wf-t2v-h3");
        assertThrows(VideoTaskException.class, () -> preparer.prepare(
            registry.templateOf("wf-t2v-h3"), VideoCapability.T2V, version,
            new H3TemplatePreparer.H3Fields("提示词", null, null, null, H3TemplatePreparer.TIER_1080P, "10 秒")),
            "10 秒未交付，必须拒绝");
        // 720P 已按契约开放，prepare 必须放行（分辨率改写由 applyResolution 负责）。
        assertDoesNotThrow(() -> preparer.prepare(
            registry.templateOf("wf-t2v-h3"), VideoCapability.T2V, version,
            new H3TemplatePreparer.H3Fields("提示词", null, null, null, "流畅 · 720P", "5 秒")),
            "720P 已开放，不应被拒绝");
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
        // 720P 已按契约开放，入库前校验必须放行（未声明档位的拒绝见 rejectsTierNotDeclaredInContract）。
        preparer.validateFields(VideoCapability.T2V, t2v,
            new H3TemplatePreparer.H3Fields("正常提示词", null, null, null, "流畅 · 720P", "5 秒"));
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

    /**
     * 按指定档位准备节点图，并返回解析后的图。
     *
     * <p>I2V/FL2V 需要首帧（FL2V 还需尾帧）才能通过校验，这里给占位文件名——
     * 本用例只关心分辨率改写，不涉及素材读取。</p>
     */
    private ObjectNode prepareWithTier(String code, String tier) throws Exception {
        WorkflowVersion version = versionOf(code);
        String template = registry.templateOf(code);
        VideoCapability capability = VideoCapability.parse(version.capabilityCode());
        String image = capability == VideoCapability.I2V ? "first.png" : null;
        String first = capability == VideoCapability.FL2V ? "first.png" : null;
        String last = capability == VideoCapability.FL2V ? "last.png" : null;
        H3TemplatePreparer.H3Fields f = new H3TemplatePreparer.H3Fields(
            "测试提示词", image, first, last, tier, H3TemplatePreparer.DURATION_5S);
        return preparer.prepare(template, capability, version, f);
    }

    /**
     * 从节点图里读出「导演阶段」与「编码阶段」的实际分辨率。
     */
    private static int[] resolutionsOf(ObjectNode graph) throws Exception {
        ObjectNode director = (ObjectNode) graph.get(H3TemplatePreparer.DIRECTOR_NODE_ID);
        JsonNode inputs = director.get("inputs");
        ObjectNode timeline = (ObjectNode) MAPPER.readTree(inputs.path("timeline_data").asText(""));
        ObjectNode encode = (ObjectNode) graph.get(H3TemplatePreparer.ENCODE_NODE_ID).get("inputs");
        return new int[] {
            inputs.path("width").asInt(), inputs.path("height").asInt(),
            timeline.path("width").asInt(), timeline.path("height").asInt(),
            timeline.path("output").path("width").asInt(), timeline.path("output").path("height").asInt(),
            encode.path("width").asInt(), encode.path("height").asInt()
        };
    }

    @Test
    @DisplayName("多档位：契约声明 1080P/720P/480P，且三档都能通过校验")
    void contractDeclaresThreeTiers() {
        for (String code : List.of("wf-t2v-h3", "wf-i2v-h3", "wf-fl2v-h3")) {
            java.util.Set<String> tiers = versionOf(code).fixedFieldValidation().allowedTiers();
            assertEquals(3, tiers.size(), code + " 应声明 3 个档位，实际：" + tiers);
            assertTrue(tiers.contains("高清 · 1080P"), code + " 应含 1080P");
            assertTrue(tiers.contains("流畅 · 720P"), code + " 应含 720P");
            assertTrue(tiers.contains("标清 · 480P"), code + " 应含 480P");
        }
    }

    @Test
    @DisplayName("多档位：三个档位都能通过校验并产出各自的分辨率")
    void prepareAppliesResolutionPerTier() throws Exception {
        // 每档的期望值：导演阶段宽高、timeline 宽高、timeline output 宽高、编码阶段宽高。
        // 导演阶段与编码阶段刻意错开一个 16 的台阶（编码阶段用 crop=center 裁掉多余像素），
        // 使成片落在标准 1080/720/480 高度上；480P 两者相同。
        int[] expected1080 = {1920, 1088, 1920, 1088, 1920, 1088, 1920, 1080};
        int[] expected720 = {1280, 736, 1280, 736, 1280, 736, 1280, 720};
        int[] expected480 = {864, 480, 864, 480, 864, 480, 864, 480};

        for (String code : List.of("wf-t2v-h3", "wf-i2v-h3", "wf-fl2v-h3")) {
            assertArrayEquals(expected1080, resolutionsOf(prepareWithTier(code, "高清 · 1080P")),
                code + " 1080P 分辨率不对");
            assertArrayEquals(expected720, resolutionsOf(prepareWithTier(code, "流畅 · 720P")),
                code + " 720P 分辨率不对");
            assertArrayEquals(expected480, resolutionsOf(prepareWithTier(code, "标清 · 480P")),
                code + " 480P 分辨率不对");
        }
    }

    @Test
    @DisplayName("多档位：导演阶段分辨率必须是 16 的倍数（ComfyUI 硬约束）")
    void allResolutionsAreMultiplesOf16() throws Exception {
        // 自定义节点的 resolve_output_dimensions 会把导演阶段宽高对齐到 16，且要求 ≥16。
        // 若给的值不是 16 的倍数，实际生成分辨率会与标称档位不一致（例如标 720 却出 736）。
        //
        // 注意：编码节点（ImageScale）的尺寸不在此约束内——1080P 模板刻意用 1920×1080，
        // 因为 H.264 只要求偶数，而 1080 = 16×67.5。这里只校验导演阶段与参考尺寸。
        for (java.util.Map.Entry<String, org.dromara.ai.video.config.VideoTierResolutions.Resolution> e
            : new org.dromara.ai.video.config.VideoTierResolutions().getTiers().entrySet()) {
            var r = e.getValue();
            for (int v : new int[] {r.width(), r.height(), r.refMaxSize()}) {
                assertEquals(0, v % 16, e.getKey() + " 的导演阶段尺寸 " + v + " 不是 16 的倍数");
                assertTrue(v >= 16, e.getKey() + " 的尺寸 " + v + " 小于 16");
            }
            // 编码尺寸只要求偶数（H.264），且必须不超过导演阶段，否则会被拉伸放大。
            for (int v : new int[] {r.encodeWidth(), r.encodeHeight()}) {
                assertEquals(0, v % 2, e.getKey() + " 的编码尺寸 " + v + " 不是偶数");
            }
            assertTrue(r.encodeWidth() <= r.width() && r.encodeHeight() <= r.height(),
                e.getKey() + " 的编码尺寸不应超过导演阶段尺寸");
        }
    }

    @Test
    @DisplayName("多档位：未声明的档位仍必须被拒绝（不能因为放开档位就失去校验）")
    void rejectsTierNotDeclaredInContract() {
        for (String bad : List.of("超清 · 4K", "高清 · 2K", "")) {
            H3TemplatePreparer.H3Fields f = new H3TemplatePreparer.H3Fields(
                "提示词", null, null, null, bad, H3TemplatePreparer.DURATION_5S);
            assertThrows(VideoTaskException.class,
                () -> preparer.validateFields(VideoCapability.T2V, versionOf("wf-t2v-h3"), f),
                "未声明的档位必须被拒绝：" + bad);
        }
    }

    @Test
    @DisplayName("多档位：参考图最大边随档位下降，避免 720P/480P 仍按 1080P 规格缩放")
    void refMaxSizeFollowsTier() throws Exception {
        assertEquals(1920, resolutionsRefMax("高清 · 1080P"));
        assertEquals(1280, resolutionsRefMax("流畅 · 720P"));
        assertEquals(864, resolutionsRefMax("标清 · 480P"));
    }

    private int resolutionsRefMax(String tier) throws Exception {
        ObjectNode graph = prepareWithTier("wf-i2v-h3", tier);
        ObjectNode inputs = (ObjectNode) graph.get(H3TemplatePreparer.DIRECTOR_NODE_ID).get("inputs");
        ObjectNode timeline = (ObjectNode) MAPPER.readTree(inputs.path("timeline_data").asText(""));
        assertEquals(timeline.path("refMaxSize").asInt(), inputs.path("ref_max_size").asInt(),
            "timeline.refMaxSize 与节点 ref_max_size 必须一致");
        assertEquals(timeline.path("output").path("longEdge").asInt(), inputs.path("ref_max_size").asInt(),
            "output.longEdge 与节点 ref_max_size 必须一致");
        return inputs.path("ref_max_size").asInt();
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

    @Test
    @DisplayName("时长：17k+5 网格换算（5→124 / 10→243 / 20→481）")
    void frameGridFollowsMiniMaxRule() {
        assertEquals(124, H3TemplatePreparer.framesOfSeconds(5));
        assertEquals(243, H3TemplatePreparer.framesOfSeconds(10));
        assertEquals(481, H3TemplatePreparer.framesOfSeconds(20));
        for (int sec : new int[] {5, 10, 20}) {
            int f = H3TemplatePreparer.framesOfSeconds(sec);
            assertEquals(5, f % 17, sec + " 秒的帧数应满足 n % 17 == 5");
            assertTrue(f >= sec * 24, sec + " 秒的帧数不应少于 24fps × 秒数");
        }
    }

    @Test
    @DisplayName("时长：解析「N 秒」文案")
    void parsesDurationLabel() {
        assertEquals(5, H3TemplatePreparer.parseDurationSeconds("5 秒"));
        assertEquals(10, H3TemplatePreparer.parseDurationSeconds("10 秒"));
        assertEquals(20, H3TemplatePreparer.parseDurationSeconds("20 秒"));
        assertEquals(0, H3TemplatePreparer.parseDurationSeconds(""));
        assertEquals(0, H3TemplatePreparer.parseDurationSeconds(null));
    }

    @Test
    @DisplayName("时长：10/20 秒必须同步改写节点与 timeline 的全部帧数路径")
    void prepareAppliesDurationToAllFramePaths() throws Exception {
        // 需同时改 7 处；漏一处就会出现「节点让生成 N 帧、时间线只导出 M 帧」。
        for (int sec : new int[] {10, 20}) {
            int frames = H3TemplatePreparer.framesOfSeconds(sec);
            String label = sec + " 秒";
            for (String code : List.of("wf-t2v-h3", "wf-i2v-h3", "wf-fl2v-h3")) {
                ObjectNode graph = prepareWithDuration(code, "标清 · 480P", label);
                ObjectNode inputs = (ObjectNode) graph.get(H3TemplatePreparer.DIRECTOR_NODE_ID).get("inputs");
                ObjectNode tl = (ObjectNode) MAPPER.readTree(inputs.path("timeline_data").asText(""));
                String where = code + " " + label;

                assertEquals(frames, inputs.path("total_frames").asInt(), where + " 节点 total_frames");
                assertEquals(frames, tl.path("totalFrames").asInt(), where + " timeline.totalFrames");
                assertEquals(sec, tl.path("durationSec").asInt(), where + " timeline.durationSec");
                assertEquals(frames, tl.path("gen").path("defaultFrameCount").asInt(),
                    where + " gen.defaultFrameCount");
                JsonNode seg = tl.path("segments").path(0);
                assertEquals(frames, seg.path("length").asInt(), where + " segment.length");
                assertEquals(frames, seg.path("frameCount").asInt(), where + " segment.frameCount");
                assertEquals(sec, seg.path("durationSec").asInt(), where + " segment.durationSec");
                assertEquals(sec, tl.path("shots").path(0).path("durationSec").asInt(),
                    where + " shot.durationSec");
                // 关键帧按段均分：fl2v 有首尾两帧，各占一半。
                JsonNode keyframes = tl.path("keyframes");
                if (keyframes.size() > 0) {
                    int per = Math.max(1, frames / keyframes.size());
                    for (JsonNode kf : keyframes) {
                        assertEquals(per, kf.path("length").asInt(), where + " keyframe.length");
                        assertEquals(per, kf.path("frameCount").asInt(), where + " keyframe.frameCount");
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("时长：1080P 只允许 5 秒；720P 到 10 秒；480P 到 20 秒")
    void durationMatrixPerTier() {
        var tiers = new org.dromara.ai.video.config.VideoTierResolutions();
        assertEquals(List.of("5 秒"), tiers.durationsOf("高清 · 1080P"));
        assertEquals(List.of("5 秒", "10 秒"), tiers.durationsOf("流畅 · 720P"));
        assertEquals(List.of("5 秒", "10 秒", "20 秒"), tiers.durationsOf("标清 · 480P"));
        assertTrue(tiers.supports("标清 · 480P", "20 秒"));
        assertFalse(tiers.supports("高清 · 1080P", "10 秒"), "1080P 不应开放 10 秒");
        assertFalse(tiers.supports("流畅 · 720P", "20 秒"), "720P 不应开放 20 秒");
        assertFalse(tiers.supports("标清 · 480P", "30 秒"), "未声明的时长必须被拒绝");
    }

    @Test
    @DisplayName("时长：超出档位允许范围必须在入库前被拒绝")
    void rejectsDurationNotAllowedForTier() {
        WorkflowVersion version = versionOf("wf-t2v-h3");
        for (String[] bad : List.of(new String[] {"高清 · 1080P", "10 秒"},
            new String[] {"流畅 · 720P", "20 秒"}, new String[] {"标清 · 480P", "30 秒"})) {
            H3TemplatePreparer.H3Fields f = new H3TemplatePreparer.H3Fields(
                "提示词", null, null, null, bad[0], bad[1]);
            VideoTaskException e = assertThrows(VideoTaskException.class,
                () -> preparer.validateFields(VideoCapability.T2V, version, f),
                bad[0] + " + " + bad[1] + " 必须被拒绝");
            assertTrue(e.getMessage().contains("只支持时长"), "报错应说明允许的时长：" + e.getMessage());
        }
        for (String[] ok : List.of(new String[] {"高清 · 1080P", "5 秒"},
            new String[] {"流畅 · 720P", "10 秒"}, new String[] {"标清 · 480P", "20 秒"})) {
            preparer.validateFields(VideoCapability.T2V, version,
                new H3TemplatePreparer.H3Fields("提示词", null, null, null, ok[0], ok[1]));
        }
    }

    /**
     * 按「档位 + 时长」准备节点图（I2V/FL2V 补占位素材以通过校验）。
     */
    private ObjectNode prepareWithDuration(String code, String tier, String duration) throws Exception {
        WorkflowVersion version = versionOf(code);
        VideoCapability capability = VideoCapability.parse(version.capabilityCode());
        String image = capability == VideoCapability.I2V ? "first.png" : null;
        String first = capability == VideoCapability.FL2V ? "first.png" : null;
        String last = capability == VideoCapability.FL2V ? "last.png" : null;
        return preparer.prepare(registry.templateOf(code), capability, version,
            new H3TemplatePreparer.H3Fields("测试提示词", image, first, last, tier, duration));
    }

    /**
     * 真实验证用：把「能力 + 档位 + 时长」渲染成节点图并写到 {@code /tmp/graph.json}。
     *
     * <p>借测试运行器执行，因此 classpath 与生产一致；不参与断言。
     * 用法：{@code -Dtest=H3TemplatePreparerTest#exportGraph -Dcap=I2V -Dtier=... -Ddur=...}</p>
     */
    @Test
    @DisplayName("工具：导出节点图供 ComfyUI 实测（不参与断言）")
    void exportGraph() throws Exception {
        String cap = System.getProperty("cap", "I2V");
        String tier = System.getProperty("tier", "标清 · 480P");
        String dur = System.getProperty("dur", "5 秒");
        String image = System.getProperty("img", "hotter_2100272341336666113_8f8699d9.png");
        String prompt = System.getProperty("prompt", "镜头缓慢推进，主体清晰，光影自然");

        VideoCapability capability = VideoCapability.parse(cap);
        String code = "wf-" + cap.toLowerCase() + "-h3";
        WorkflowVersion version = registry.peek(code);
        String first = capability == VideoCapability.FL2V ? image : null;
        String imageFile = capability == VideoCapability.I2V ? image : null;
        ObjectNode graph = preparer.prepare(registry.templateOf(code), capability, version,
            new H3TemplatePreparer.H3Fields(prompt, imageFile, first, null, tier, dur));

        ObjectNode inputs = (ObjectNode) graph.get(H3TemplatePreparer.DIRECTOR_NODE_ID).get("inputs");
        ObjectNode timeline = (ObjectNode) MAPPER.readTree(inputs.path("timeline_data").asText(""));
        ObjectNode encode = (ObjectNode) graph.get(H3TemplatePreparer.ENCODE_NODE_ID).get("inputs");
        System.out.printf("[export] %s tier=%s dur=%s director=%dx%d frames=%d encode=%dx%d timeline=%dx%d%n",
            code, tier, dur, inputs.path("width").asInt(), inputs.path("height").asInt(),
            inputs.path("total_frames").asInt(), encode.path("width").asInt(), encode.path("height").asInt(),
            timeline.path("width").asInt(), timeline.path("height").asInt());
        java.nio.file.Files.writeString(Path.of("/tmp/graph.json"),
            MAPPER.writeValueAsString(graph), StandardCharsets.UTF_8);
        System.out.println("[export] 已写出 /tmp/graph.json");
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
                                fixed.path("tier").asText(null), fixed.path("dur").asText(null),
                                readTiers(fixed)),
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

    /**
     * 读取契约里的 supportedTiers（与 WorkflowContractRegistry 的解析保持一致）。
     */
    private static java.util.Set<String> readTiers(JsonNode fixedNode) {
        JsonNode node = fixedNode.path("supportedTiers");
        if (!node.isArray()) {
            return java.util.Set.of();
        }
        java.util.LinkedHashSet<String> tiers = new java.util.LinkedHashSet<>();
        for (JsonNode item : node) {
            String text = item.asText("").trim();
            if (!text.isEmpty()) {
                tiers.add(text);
            }
        }
        return java.util.Collections.unmodifiableSet(tiers);
    }
}
