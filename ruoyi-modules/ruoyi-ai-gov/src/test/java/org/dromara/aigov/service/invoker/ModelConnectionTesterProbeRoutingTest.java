package org.dromara.aigov.service.invoker;

import org.dromara.aigov.config.AigModelTestProperties;
import org.dromara.aigov.domain.vo.AigModelTestTargetVo;
import org.dromara.aigov.domain.vo.AigModelTestVo;
import org.dromara.aigov.enums.AigDeploymentTypeEnum;
import org.dromara.aigov.helper.AigModelSecretCipher;
import org.dromara.aigov.mapper.AigModelConfigMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.databind.json.JsonMapper;

import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * 连通性探测的**分流**测试（2026-10-07 修）。
 *
 * <p><b>修的是什么</b>：原先所有 {@code openai-compatible} 模型都走 chat 探针
 * （{@code messages[].content} 是字符串）。对图像模型来说那条路是错的——图像端点要
 * {@code model + prompt + n + response_format}，bluocto（New API 系）会稳定回
 * {@code 400 Input should be a valid list: ….content}，于是**一条完全可用的图像通道，
 * 点「测试连接」永远判 UNHEALTHY**，而且报错还把人引向"模型名写错了"。</p>
 *
 * <p>这里钉住的是分流本身（不需要网络）：
 * <ol>
 *     <li>{@code model_type=IMAGE} + 有端点 → {@code probe=OPENAI_IMAGE}，且**确实调了图像调用器**；</li>
 *     <li>图像模型但调用器未装配 → 给出明确结论（不是"连接失败"这种含糊说法）；</li>
 *     <li>非图像模型 → 仍然走 chat 探针（{@code probe=OPENAI_COMPATIBLE}），没有被这次修改带走。</li>
 * </ol>
 * 真实 HTTP、真实出图不在这里测——那是端到端范畴（与 {@code OpenAiImageInvokerTest} 同一取舍）。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class ModelConnectionTesterProbeRoutingTest {

    private static final String ENDPOINT = "https://bluocto.com/v1";

    /** 只提供一个调用器的 ObjectProvider（Spring 的 ObjectProvider 是接口，这里给最小实现）。 */
    private static ObjectProvider<ModelInvoker> providerOf(ModelInvoker invoker) {
        List<ModelInvoker> list = invoker == null ? List.of() : List.of(invoker);
        return new ObjectProvider<>() {
            @Override
            public ModelInvoker getObject() {
                return list.isEmpty() ? null : list.get(0);
            }

            @Override
            public Iterator<ModelInvoker> iterator() {
                return list.iterator();
            }
        };
    }

    /** 真实构造的 OpenAiImageInvoker：mapper 返回 null → 走到"未配置访问地址"的失败分支，不发任何请求。 */
    private static OpenAiImageInvoker imageInvoker() {
        return new OpenAiImageInvoker(new org.dromara.aigov.config.AigExternalApiProperties(),
            JsonMapper.builder().build(), mock(AigModelConfigMapper.class), mock(AigModelSecretCipher.class));
    }

    private static AigModelTestTargetVo target(String modelType, String adapter, boolean withEndpoint) {
        AigModelTestTargetVo vo = new AigModelTestTargetVo();
        vo.setModelId(1L);
        vo.setModelKey("qwen-image-3.0-pro");
        vo.setModelType(modelType);
        vo.setAdapterKey(adapter);
        vo.setDeploymentType(AigDeploymentTypeEnum.EXTERNAL_API.getCode());
        vo.setApiEndpoint(withEndpoint ? ENDPOINT : null);
        return vo;
    }

    @Test
    @DisplayName("图像模型走图像探针，不再走 chat 探针")
    void imageModelUsesImageProbe() {
        ModelConnectionTester tester = new ModelConnectionTester(
            providerOf(imageInvoker()), mock(AigModelSecretCipher.class), new AigModelTestProperties());

        AigModelTestVo result = tester.test(target("IMAGE", "openai-compatible", true));

        assertEquals("OPENAI_IMAGE", result.getProbe(), "图像模型必须走图像探针");
        assertFalse(result.getOk(), "调用器拿不到端点时本次探测必须判失败");
        assertTrue(result.getMessage() != null && result.getMessage().startsWith("图像生成调用失败"),
            "失败结论要说清是「图像生成」这条链路，实际：" + result.getMessage());
        // 关键反证：不能出现 chat 探针的那套措辞（说明根本没走 chat 那条路）
        assertFalse(result.getMessage().contains("chat/completions"),
            "图像模型不应落到 chat 探针的提示上：" + result.getMessage());
    }

    @Test
    @DisplayName("图像模型但图像调用器没装配 → 明确说「调用器不可用」，不冒充网络失败")
    void imageModelWithoutInvoker() {
        ModelConnectionTester tester = new ModelConnectionTester(
            providerOf(null), mock(AigModelSecretCipher.class), new AigModelTestProperties());

        AigModelTestVo result = tester.test(target("IMAGE", "openai-compatible", true));

        assertEquals("OPENAI_IMAGE", result.getProbe());
        assertFalse(result.getOk());
        assertTrue(result.getMessage() != null && result.getMessage().contains("图像调用器不可用"),
            "实际：" + result.getMessage());
    }

    @Test
    @DisplayName("非图像模型仍走 chat 探针（这次修改没有把它带走）")
    void chatModelStillUsesChatProbe() {
        ModelConnectionTester tester = new ModelConnectionTester(
            providerOf(imageInvoker()), mock(AigModelSecretCipher.class), new AigModelTestProperties());

        AigModelTestVo result = tester.test(target("CHAT", "openai-compatible", true));

        assertEquals("OPENAI_COMPATIBLE", result.getProbe(), "CHAT 模型应仍走 chat 探针");
    }

    @Test
    @DisplayName("model_type 大小写与空白不影响分流（IMAGE / image / ' IMAGE ' 都走图像探针）")
    void modelTypeIsNormalised() {
        for (String type : List.of("IMAGE", "image", " IMAGE ")) {
            ModelConnectionTester tester = new ModelConnectionTester(
                providerOf(null), mock(AigModelSecretCipher.class), new AigModelTestProperties());
            AigModelTestVo result = tester.test(target(type, "openai-compatible", true));
            assertEquals("OPENAI_IMAGE", result.getProbe(), "model_type=" + type + " 应走图像探针");
        }
    }

    @Test
    @DisplayName("图像模型没有端点 → 仍然是不支持，而不是误报图像探针成功")
    void imageModelWithoutEndpoint() {
        ModelConnectionTester tester = new ModelConnectionTester(
            providerOf(imageInvoker()), mock(AigModelSecretCipher.class), new AigModelTestProperties());

        AigModelTestVo result = tester.test(target("IMAGE", "openai-compatible", false));

        assertEquals("UNSUPPORTED", result.getProbe());
        assertFalse(result.getOk());
    }
}
