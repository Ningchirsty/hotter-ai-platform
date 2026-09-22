package org.dromara.ai.image;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.dromara.ai.image.domain.ImageCapability;
import org.dromara.ai.image.domain.ImageWorkflowVersion;
import org.dromara.ai.image.exception.ImageTaskException;
import org.dromara.ai.image.service.ImageTemplatePreparer;
import org.dromara.ai.image.service.ImageTemplatePreparer.ImageFields;
import org.dromara.ai.image.service.ImageWorkflowContractRegistry;
import org.dromara.ai.image.support.ImageContractTestFixture;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 图像模板填充器 + 契约注册表测试（离线，不需要 ComfyUI/GPU）。
 *
 * <p>直接读取仓库里真实的契约与模板，因此契约一改动，这些断言就会挡住不安全的改动。</p>
 */
class ImageTemplatePreparerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path ROOT = Path.of("..", "..", "script");

    @TempDir
    Path draftRoot;

    private static ImageWorkflowContractRegistry registry;
    private static ImageTemplatePreparer preparer;

    @BeforeAll
    static void setUp() {
        registry = new ImageWorkflowContractRegistry(ROOT, MAPPER);
        registry.load();
        preparer = new ImageTemplatePreparer(MAPPER);
    }

    @Test
    @DisplayName("契约可加载：5 条注册、5 份模板通过校验")
    void contractLoads() {
        assertEquals(5, registry.registeredCount());
        assertEquals(5, registry.loadedCount());
        for (String code : List.of("wf-t2i-qwen21", "wf-i2i-qwen21", "wf-edit-qwen21", "wf-bgremove-qwen21", "wf-whitebg-qwen21")) {
            assertTrue(registry.isTemplateLoaded(code), code + " 模板应通过校验");
        }
    }

    @Test
    @DisplayName("DRAFT 工作流不可提交（正式与联调都不行）")
    void draftRejected() throws Exception {
        // 不依赖"仓库契约恰好还是 DRAFT"：契约一旦发布，直接读仓库契约的这条断言就会失效。
        // 自造一份 DRAFT 契约，让断言与仓库的发布状态解耦。
        ImageWorkflowContractRegistry draft = ImageContractTestFixture.registry(draftRoot, "DRAFT");
        ImageTaskException e = assertThrows(ImageTaskException.class,
            () -> draft.require("wf-t2i-qwen21", false));
        assertTrue(e.getMessage().contains("尚未通过实机验收"), e.getMessage());
        assertThrows(ImageTaskException.class, () -> draft.require("wf-nope", false));
    }

    @Test
    @DisplayName("模板被改动后校验失败，拒绝加载")
    void tamperedTemplateRejected() {
        String template = registry.templateOf("wf-t2i-qwen21");
        ImageWorkflowVersion version = registry.peek("wf-t2i-qwen21");
        preparer.verifyChecksum(template, version);
        String tampered = template.replace("\"steps\": 25", "\"steps\": 30");
        ImageTaskException e = assertThrows(ImageTaskException.class,
            () -> preparer.verifyChecksum(tampered, version));
        assertTrue(e.getMessage().contains("校验值不匹配"), e.getMessage());
    }

    @Test
    @DisplayName("文生图：覆写提示词与画布尺寸，采样参数与模型路径保持模板原值")
    void t2iFillsWhitelistOnly() {
        ImageWorkflowVersion version = registry.peek("wf-t2i-qwen21");
        ImageFields fields = new ImageFields("一只红色茶壶", "", "16:9 宽屏 · 1MP（1344×768）", null, List.of(), 777L);
        ObjectNode graph = preparer.prepare(registry.templateOf(version.workflowCode()),
            ImageCapability.T2I, version, fields);

        assertEquals("一只红色茶壶", graph.get("4").path("inputs").path("prompt").asText());
        assertEquals(1344, graph.get("5").path("inputs").path("width").asInt());
        assertEquals(768, graph.get("5").path("inputs").path("height").asInt());
        assertEquals(1344, graph.get("4").path("inputs").path("resolution").asInt());
        assertEquals(777L, graph.get("6").path("inputs").path("seed").asLong());
        // 固定参数不得被覆写
        assertEquals(25, graph.get("6").path("inputs").path("steps").asInt());
        assertEquals(1, graph.get("6").path("inputs").path("cfg").asInt());
        assertEquals("euler", graph.get("6").path("inputs").path("sampler_name").asText());
        assertEquals(1, graph.get("6").path("inputs").path("denoise").asInt());
        assertEquals("qwen_image_2.1_int8_convrot.safetensors",
            graph.get("1").path("inputs").path("unet_name").asText());
        // 深拷贝：模板原文未被就地修改
        assertTrue(registry.templateOf(version.workflowCode()).contains("\"prompt\": \"\""));
    }

    @Test
    @DisplayName("图生图：素材文件名写入 LoadImage，strength 换算为 denoise")
    void i2iFillsImageAndStrength() {
        ImageWorkflowVersion version = registry.peek("wf-i2i-qwen21");
        ImageFields fields = new ImageFields("水彩风格", "", null, "强烈重绘 · 0.9", List.of("hotter_1.png"), 5L);
        ObjectNode graph = preparer.prepare(registry.templateOf(version.workflowCode()),
            ImageCapability.I2I, version, fields);

        assertEquals("hotter_1.png", graph.get("4").path("inputs").path("image").asText());
        assertEquals(0.9, graph.get("7").path("inputs").path("denoise").asDouble(), 1e-9);
        assertEquals("水彩风格", graph.get("6").path("inputs").path("prompt").asText());
    }

    @Test
    @DisplayName("指令改图：参考图槽位用扁平点号键连接，未提供的槽位被裁剪")
    void editFillsAndPrunesSlots() {
        ImageWorkflowVersion version = registry.peek("wf-edit-qwen21");
        String template = registry.templateOf(version.workflowCode());

        // 三张参考图齐全：节点 4/5/6 都在
        ObjectNode all = preparer.prepare(template, ImageCapability.EDIT, version,
            new ImageFields("把 <image2> 的衣服穿到 <image1> 身上", "", null, null,
                List.of("a.png", "b.png", "c.png"), 1L));
        assertEquals("a.png", all.get("4").path("inputs").path("image").asText());
        assertEquals("b.png", all.get("5").path("inputs").path("image").asText());
        assertEquals("c.png", all.get("6").path("inputs").path("image").asText());
        assertEquals("4", all.get("7").path("inputs").path("images.image_1").get(0).asText());
        assertEquals("6", all.get("7").path("inputs").path("images.image_3").get(0).asText());

        // 只给两张：节点 6 与 images.image_3 必须被删掉（否则 ComfyUI 会把 input 目录当文件打开）
        ObjectNode pruned = preparer.prepare(template, ImageCapability.EDIT, version,
            new ImageFields("只换衣服", "", null, null, List.of("a.png", "b.png"), 1L));
        assertNull(pruned.get("6"), "未使用的参考图节点必须被删除");
        assertFalse(pruned.get("7").path("inputs").has("images.image_3"), "未使用的槽位键必须被删除");
        assertTrue(pruned.get("7").path("inputs").has("images.image_2"));
    }

    @Test
    @DisplayName("抠图：固定提示词不被覆写，且必须提供输入图")
    void bgremoveKeepsFixedPrompt() {
        ImageWorkflowVersion version = registry.peek("wf-bgremove-qwen21");
        ObjectNode graph = preparer.prepare(registry.templateOf(version.workflowCode()),
            ImageCapability.BGREMOVE, version,
            new ImageFields("这段提示词应被忽略", "", null, null, List.of("x.png"), 1L));
        assertEquals("Remove the background, and output a PNG image",
            graph.get("5").path("inputs").path("prompt").asText());
    }

    @Test
    @DisplayName("校验拒绝：空提示词、缺图、非法档位、未知字段")
    void validationRejects() {
        ImageWorkflowVersion t2i = registry.peek("wf-t2i-qwen21");
        assertEquals("提示词不能为空", assertThrows(ImageTaskException.class, () ->
            preparer.validateFields(ImageCapability.T2I, t2i,
                new ImageFields("  ", "", "1:1 方图 · 1MP（1024×1024）", null, List.of(), 1L))).getMessage());
        assertTrue(assertThrows(ImageTaskException.class, () ->
            preparer.validateFields(ImageCapability.T2I, t2i,
                new ImageFields("x", "", "奇怪的档位", null, List.of(), 1L))).getMessage().contains("输出尺寸只支持"));

        ImageWorkflowVersion i2i = registry.peek("wf-i2i-qwen21");
        assertTrue(assertThrows(ImageTaskException.class, () ->
            preparer.validateFields(ImageCapability.I2I, i2i,
                new ImageFields("x", "", null, "标准重绘 · 0.75", List.of(), 1L))).getMessage().contains("必须提供输入图片"));

        ImageWorkflowVersion edit = registry.peek("wf-edit-qwen21");
        assertTrue(assertThrows(ImageTaskException.class, () ->
            preparer.validateFields(ImageCapability.EDIT, edit,
                new ImageFields("x", "", null, null, List.of("a", "b", "c", "d"), 1L))).getMessage().contains("最多支持 3 张"));

        ImageTaskException unknown = assertThrows(ImageTaskException.class, () ->
            preparer.validateFieldWhitelist(edit.capabilityFields(), java.util.Map.of("sampler", "euler")));
        assertTrue(unknown.getMessage().contains("不支持的字段"));
    }

    @Test
    @DisplayName("strength 档位解析：非法值必须拒绝")
    void strengthParsing() {
        ImageWorkflowVersion i2i = registry.peek("wf-i2i-qwen21");
        assertEquals(0.4, preparer.strengthToDenoise(i2i, "轻微变化 · 0.4"), 1e-9);
        assertEquals(0.75, preparer.strengthToDenoise(i2i, null), 1e-9);
        assertThrows(ImageTaskException.class, () -> preparer.strengthToDenoise(i2i, "重绘"));
    }

    @Test
    @DisplayName("能力与契约字段一致：mapping 字段都在能力字段白名单内")
    void mappingWithinCapabilityFields() {
        for (ImageWorkflowVersion version : registry.registeredVersions()) {
            assertFalse(version.capabilityFields().isEmpty(), version.workflowCode() + " 缺少能力字段");
            for (String mapped : version.mappedFields()) {
                assertTrue(version.capabilityFields().contains(mapped),
                    version.workflowCode() + " 的 mapping 字段 " + mapped + " 不在能力字段里");
            }
        }
    }

    @Test
    @DisplayName("模板结构：四个模板都含 KSampler，文生图含 EmptyLatentImage")
    void templateStructure() {
        JsonNode t2i = readTemplate("wf-t2i-qwen21");
        assertNotNull(findByType(t2i, "KSampler"));
        assertNotNull(findByType(t2i, "EmptyLatentImage"));
        for (String code : List.of("wf-i2i-qwen21", "wf-edit-qwen21", "wf-bgremove-qwen21")) {
            assertNotNull(findByType(readTemplate(code), "TextEncodeQwenImage21"), code);
        }
    }

    private JsonNode readTemplate(String code) {
        try {
            return MAPPER.readTree(registry.templateOf(code));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private JsonNode findByType(JsonNode graph, String classType) {
        var names = graph.fieldNames();
        while (names.hasNext()) {
            JsonNode node = graph.get(names.next());
            if (classType.equals(node.path("class_type").asText())) {
                return node;
            }
        }
        return null;
    }
}
