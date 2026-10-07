package org.dromara.aigov.service.invoker;

import org.dromara.aigov.config.AigExternalApiProperties;
import org.dromara.aigov.enums.AigDeploymentTypeEnum;
import org.dromara.aigov.enums.AigErrorClassEnum;
import org.dromara.aigov.enums.AigProviderTypeEnum;
import org.dromara.aigov.helper.AigModelSecretCipher;
import org.dromara.aigov.mapper.AigModelConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * 外部图像生成调用器的行为锁定测试。
 *
 * <p><b>只测不联网的部分</b>：纯函数（地址/请求体/响应解析/MIME 推断）与前置于 HTTP 的守卫。
 * 真实 HTTP 调用不在这里测——那需要打进供应商端点，属于端到端范畴（本仓的对话调用器
 * 同样没有 HTTP 层单测）。但<b>守卫必须测</b>：它们决定「该拒绝的有没有拒绝」，
 * 而其中最要紧的一条是<b>图生图载荷必须显式失败</b>——静默丢图会让模型在没看过原图的
 * 情况下出图，却以「已完成」的样子返回。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class OpenAiImageInvokerTest {

    private static final String ENDPOINT = "https://bluocto.com/v1";

    private OpenAiImageInvoker invoker;

    @BeforeEach
    void setUp() {
        AigExternalApiProperties properties = new AigExternalApiProperties();
        properties.setEnabled(true);
        // 注入独立构造的 JsonMapper：运行期由 Spring 注入平台统一的 mapper，
        // 测试里用默认配置的即可（本类只做 Map↔JSON 的普通序列化与 readTree）。
        // 这也正是本类不用 JsonUtils 的原因——那是 Spring 依赖的静态工具，纯单测里会
        // 抛 ExceptionInInitializerError，纯函数就没法测了。
        invoker = new OpenAiImageInvoker(properties, JsonMapper.builder().build(),
            mock(AigModelConfigMapper.class), mock(AigModelSecretCipher.class));
    }

    @Test
    @DisplayName("只认 EXTERNAL_API 部署类型，且 Provider 类型为 IMAGE")
    void claimsOnlyExternalApiAsImage() {
        assertTrue(invoker.supports(AigDeploymentTypeEnum.EXTERNAL_API));
        assertFalse(invoker.supports(AigDeploymentTypeEnum.LOCAL));
        assertFalse(invoker.supports(AigDeploymentTypeEnum.GROUP));
        assertEquals(AigProviderTypeEnum.IMAGE, invoker.providerType());
        assertTrue(invoker.available(), "配置 enabled=true 时应可用");
    }

    @Test
    @DisplayName("只认 IMAGE 模型类型；空值**不放行**（与对话调用器的口径刻意相反）")
    void supportsImageModelTypeOnly() {
        assertTrue(invoker.supportsModelType("IMAGE"));
        assertTrue(invoker.supportsModelType("image"), "大小写不敏感");
        assertTrue(invoker.supportsModelType("  IMAGE  "), "两侧空白应被容忍");
        assertFalse(invoker.supportsModelType("CHAT"));
        assertFalse(invoker.supportsModelType(null),
            "空 model_type 若放行，未填类型的对话模型可能被派到图像端点，错得更隐蔽");
        assertFalse(invoker.supportsModelType("  "));
    }

    @Test
    @DisplayName("地址拼接：兼容配到 /v1、带尾斜杠、以及已含完整路径三种写法")
    void buildImageUrlIsTolerant() {
        assertEquals("https://bluocto.com/v1/images/generations",
            OpenAiImageInvoker.buildImageUrl("https://bluocto.com/v1"));
        assertEquals("https://bluocto.com/v1/images/generations",
            OpenAiImageInvoker.buildImageUrl("https://bluocto.com/v1/"));
        assertEquals("https://bluocto.com/v1/images/generations",
            OpenAiImageInvoker.buildImageUrl("  https://bluocto.com/v1  "));
        assertEquals("https://bluocto.com/v1/images/generations",
            OpenAiImageInvoker.buildImageUrl("https://bluocto.com/v1/images/generations"),
            "已含完整路径时不得重复拼接");
    }

    @Test
    @DisplayName("请求体：model/prompt/n/response_format 必备；size 只在调用方给了才带")
    void buildImageBodyOmitsSizeUnlessProvided() {
        ModelInvokeRequest bare = new ModelInvokeRequest();
        bare.setModelKey("flux-2-pro");
        bare.setPrompt("一只橙色的猫");
        Map<String, Object> body = invoker.buildImageBody(bare);

        assertEquals("flux-2-pro", body.get("model"));
        assertEquals("一只橙色的猫", body.get("prompt"));
        assertEquals(1, body.get("n"));
        assertEquals("b64_json", body.get("response_format"),
            "优先要 base64：URL 会过期，且会把资产留在第三方");
        assertFalse(body.containsKey("size"),
            "不臆测默认尺寸——不同上游支持的可选尺寸不同，猜一个会把本可成功的请求判成 400");

        ModelInvokeRequest sized = new ModelInvokeRequest();
        sized.setModelKey("flux-2-pro");
        sized.setPrompt("一只橙色的猫");
        sized.setPayload(Map.of("size", " 1024x1024 "));
        assertEquals("1024x1024", invoker.buildImageBody(sized).get("size"),
            "调用方给了尺寸才带，且去过空白");
    }

    @Test
    @DisplayName("提示词必须剥掉图片 base64，否则几十 MB 会被塞进 prompt")
    void promptStripsImageBase64() {
        ModelInvokeRequest request = new ModelInvokeRequest();
        request.setPrompt("生成一张海报");
        request.setPayload(Map.of(
            "aspect", "3:4",
            ModelImagePayload.KEY, List.of(Map.of(
                "label", "参考图", "mimeType", "image/png", "base64", "QUJDREVG"))));

        String prompt = invoker.buildPrompt(request);

        assertTrue(prompt.contains("生成一张海报"));
        assertTrue(prompt.contains("aspect"), "非图片字段应保留");
        assertFalse(prompt.contains("QUJDREVG"), "base64 绝不能进提示词：" + prompt);
    }

    @Test
    @DisplayName("响应解析：能取 data[0].b64_json 与 data[0].url；缺失或非 JSON 时返回 null")
    void extractDataFields() {
        String withB64 = "{\"data\":[{\"b64_json\":\"QUJD\"}]}";
        assertEquals("QUJD", invoker.extractB64Json(withB64));
        assertNull(invoker.extractUrl(withB64));

        String withUrl = "{\"data\":[{\"url\":\"https://cdn.example.com/a.png\"}]}";
        assertEquals("https://cdn.example.com/a.png", invoker.extractUrl(withUrl));
        assertNull(invoker.extractB64Json(withUrl));

        assertNull(invoker.extractB64Json("{\"data\":[]}"));
        assertNull(invoker.extractB64Json("not json"));
        assertNull(invoker.extractB64Json(null));
    }

    @Test
    @DisplayName("MIME 推断：响应头优先，其次 URL 扩展名，最后退回 png")
    void guessMimeType() {
        assertEquals("image/jpeg", OpenAiImageInvoker.guessImageMimeType("image/jpeg", null));
        assertEquals("image/webp", OpenAiImageInvoker.guessImageMimeType("image/webp", null));
        assertEquals("image/png", OpenAiImageInvoker.guessImageMimeType("image/png", null));
        assertEquals("image/jpeg", OpenAiImageInvoker.guessImageMimeType("application/octet-stream",
            "https://cdn/a.JPG"));
        assertEquals("image/png", OpenAiImageInvoker.guessImageMimeType(null, "https://cdn/a.bin"),
            "判不出时退回 png，而不是抛异常");
    }

    @Test
    @DisplayName("守卫：请求为空 / 部署类型不对 / 缺模型ID / 缺提示词 一律显式失败且归为不可重试")
    void guardsRejectBeforeAnyNetworkCall() {
        assertInvalidRequest(setUpFailure(invoker.invoke(null)), "请求为空");

        ModelInvokeRequest wrongDeployment = baseRequest();
        wrongDeployment.setDeploymentType(AigDeploymentTypeEnum.LOCAL);
        assertInvalidRequest(setUpFailure(invoker.invoke(wrongDeployment)), "部署类型");

        ModelInvokeRequest noModelId = baseRequest();
        noModelId.setModelId(null);
        assertInvalidRequest(setUpFailure(invoker.invoke(noModelId)), "模型ID");

        ModelInvokeRequest noPrompt = baseRequest();
        noPrompt.setPrompt("   ");
        assertInvalidRequest(setUpFailure(invoker.invoke(noPrompt)), "提示词");
    }

    @Test
    @DisplayName("图生图载荷必须显式拒绝：不得静默丢图，且提示要指向真正的出路")
    void rejectsImagePayloadInsteadOfSilentlyDropping() {
        ModelInvokeRequest request = baseRequest();
        request.setPayload(Map.of(ModelImagePayload.KEY, List.of(Map.of(
            "label", "参考图", "mimeType", "image/png", "base64", "QUJD"))));

        ModelInvokeResult result = invoker.invoke(request);

        assertFalse(result.isSuccess(), "带输入图片时不得当作成功继续");
        assertEquals(AigErrorClassEnum.INVALID_REQUEST, invoker.classifyError(result),
            "这是入参问题，重试无用，应归为不可重试");
        assertNotNull(result.getErrorSummary());
        assertTrue(result.getErrorSummary().contains("/images/edits"),
            "提示要指出图生图走的是另一个端点，否则使用者不知道该往哪修：" + result.getErrorSummary());
    }

    /**
     * 造一个「除被测字段外都合法」的请求。
     *
     * @return 调用请求
     */
    private ModelInvokeRequest baseRequest() {
        ModelInvokeRequest request = new ModelInvokeRequest();
        request.setCapabilityCode("image_generation");
        request.setModelId(1L);
        request.setModelKey("flux-2-pro");
        request.setModelType("IMAGE");
        request.setDeploymentType(AigDeploymentTypeEnum.EXTERNAL_API);
        request.setEndpoint(ENDPOINT);
        request.setPrompt("一只橙色的猫");
        return request;
    }

    /**
     * 断言失败结果被归为不可重试的入参问题。
     *
     * @param result 失败结果
     * @param scene  场景描述（仅用于失败信息）
     */
    private void assertInvalidRequest(ModelInvokeResult result, String scene) {
        assertFalse(result.isSuccess(), scene + " 应失败");
        assertEquals(AigErrorClassEnum.INVALID_REQUEST, invoker.classifyError(result),
            scene + " 应归为不可重试的入参问题");
    }

    /**
     * 取失败结果（成功即断言失败）。
     *
     * @param result 调用结果
     * @return 同一结果
     */
    private ModelInvokeResult setUpFailure(ModelInvokeResult result) {
        assertFalse(result.isSuccess(), "该场景不应成功");
        return result;
    }

}
