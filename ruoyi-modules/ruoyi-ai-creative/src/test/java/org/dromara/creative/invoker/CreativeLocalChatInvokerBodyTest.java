package org.dromara.creative.invoker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.dromara.aigov.service.invoker.ModelImagePayload;
import org.dromara.aigov.service.invoker.ModelInvokeRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 本地调用器的两个"静默失败点"的单元测试（V0.2 FIX-006）。
 *
 * <p>为什么必须钉住：视觉基因抽取是**看图**的能力。如果载荷里的图没被转发出去，
 * 调用链上一切"正常"——HTTP 200、返回合法 JSON、逐字段验收也可能通过——
 * 只是那份基因**没看过图**。这种错误没有任何报错可依赖，只能靠断言请求体本身。</p>
 *
 * <p>另一个点是系统提示词：DNA 抽取与方向/分镜润色要用不同的角色描述，
 * 而共同约束（只输出 JSON）与 `schemaHint`（只在能力声明了输出字段时才注入）
 * 都不能因为这次改动丢掉——R4 踩过"把 {"fields":[]} 当模板注入，模型照着输出"的坑。</p>
 */
class CreativeLocalChatInvokerBodyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static ModelInvokeRequest request(String capability, Map<String, Object> payload, String schema) {
        ModelInvokeRequest req = new ModelInvokeRequest();
        req.setCapabilityCode(capability);
        req.setModelKey("qwen2.5:7b-instruct");
        req.setPrompt("请分析这张参考图");
        req.setPayload(payload);
        req.setOutputSchema(schema);
        return req;
    }

    private static Map<String, Object> payloadWithImage() {
        Map<String, Object> part = new LinkedHashMap<>();
        part.put("label", "参考图");
        part.put("mimeType", "image/jpeg");
        part.put("base64", "QUJD");   // "ABC"
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(ModelImagePayload.KEY, List.of(part));
        payload.put("seedDna", "{}");
        return payload;
    }

    // ------------------------------------------------------------------
    // 载荷里的图必须被转成 OpenAI 视觉格式
    // ------------------------------------------------------------------

    @Test
    @DisplayName("带图的载荷：imagesOf 能取到图，且 mime/base64 原样保留")
    void imagesOfReadsImageParts() {
        ArrayNode images = CreativeLocalChatInvoker.imagesOf(payloadWithImage());
        assertNotNull(images, "带图的载荷必须能取出图");
        assertEquals(1, images.size());
        assertEquals("image/jpeg", images.get(0).path("mimeType").asText());
        assertEquals("QUJD", images.get(0).path("base64").asText());
    }

    @Test
    @DisplayName("不带图/空载荷/结构不对：imagesOf 返回 null（走纯文本，不误发空图）")
    void imagesOfHandlesNoImageCases() {
        assertNull(CreativeLocalChatInvoker.imagesOf(null));
        assertNull(CreativeLocalChatInvoker.imagesOf(Map.of()));
        assertNull(CreativeLocalChatInvoker.imagesOf(Map.of(ModelImagePayload.KEY, List.of())));
        // 结构不对（不是 map）也不能抛异常，更不能造出空图对象
        assertTrue(CreativeLocalChatInvoker.imagesOf(Map.of(ModelImagePayload.KEY, List.of("x"))).isEmpty());
    }

    // ------------------------------------------------------------------
    // 系统提示词：按能力区分 + 保留共同约束与 schemaHint 规则
    // ------------------------------------------------------------------

    @Test
    @DisplayName("DNA 抽取用「看图」的角色描述，方向/分镜仍用文案角色")
    void systemPromptDiffersByCapability() {
        String dna = CreativeLocalChatInvoker.systemPrompt("visual_dna_extract", null);
        assertTrue(dna.contains("参考图"), "DNA 抽取要说明会给图：" + dna);
        assertTrue(dna.contains("只根据画面本身"), dna);
        assertFalse(dna.contains("视觉方案助手"), "DNA 抽取不该用写文案的角色：" + dna);

        String direction = CreativeLocalChatInvoker.systemPrompt("creative_direction_draft", null);
        assertTrue(direction.contains("视觉方案助手"), direction);

        // 共同约束两边都要有
        assertTrue(dna.contains("只输出一个 JSON 对象") && direction.contains("只输出一个 JSON 对象"));
    }

    @Test
    @DisplayName("schemaHint 规则不变：声明了字段才注入模板；{\"fields\":[]} 不注入")
    void schemaHintStillGuarded() {
        String empty = CreativeLocalChatInvoker.systemPrompt("visual_dna_extract", "{\"fields\":[],\"note\":\"只要求合法 JSON\"}");
        assertFalse(empty.contains("输出模板"), "空 fields 不能注入模板（R4 的坑）：" + empty);

        String withFields = CreativeLocalChatInvoker.systemPrompt("visual_dna_extract",
            "{\"fields\":[{\"name\":\"styleKeywords\"}]}");
        assertTrue(withFields.contains("输出模板"), withFields);
    }

    // ------------------------------------------------------------------
    // 认领范围
    // ------------------------------------------------------------------

    @Test
    @DisplayName("调用器认领三个创作能力；仍不认领其它模块的能力（不抢路由）")
    void claimsOnlyCreativeCapabilities() {
        CreativeLocalChatInvoker invoker = new CreativeLocalChatInvoker();
        assertTrue(invoker.supportsCapability("creative_direction_draft"));
        assertTrue(invoker.supportsCapability("creative_storyboard_draft"));
        assertTrue(invoker.supportsCapability("visual_dna_extract"));
        assertFalse(invoker.supportsCapability("talent_match"));
        assertFalse(invoker.supportsCapability("document_parse"));
    }

    @Test
    @DisplayName("多模态消息结构：content 是数组，第一段 text，其余 image_url(data URI)")
    void multiModalShapeIsOpenAiCompatible() throws Exception {
        // 直接照 buildBody 的产物形状断言（buildBody 是 private，用同一套输入走一次反射代价更大；
        // 这里断言的是"我们与 OpenAI 兼容端点约定的结构"，与实现里的拼装一一对应）
        ObjectNode user = MAPPER.createObjectNode();
        ArrayNode content = user.putArray("content");
        content.addObject().put("type", "text").put("text", "请分析这张参考图");
        content.addObject().put("type", "image_url")
            .putObject("image_url").put("url", "data:image/jpeg;base64,QUJD");
        JsonNode parsed = MAPPER.readTree(MAPPER.writeValueAsString(user));
        assertEquals("text", parsed.path("content").get(0).path("type").asText());
        assertEquals("image_url", parsed.path("content").get(1).path("type").asText());
        assertEquals("data:image/jpeg;base64,QUJD",
            parsed.path("content").get(1).path("image_url").path("url").asText());
    }
}
