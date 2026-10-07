package org.dromara.aigov.service.invoker;

import cn.hutool.extra.spring.SpringUtil;
import org.dromara.aigov.config.AigModelTestProperties;
import org.dromara.aigov.domain.vo.AigModelTestTargetVo;
import org.dromara.aigov.domain.vo.AigModelTestVo;
import org.dromara.aigov.enums.AigDeploymentTypeEnum;
import org.dromara.aigov.helper.AigModelSecretCipher;
import org.dromara.aigov.helper.SnailAiAppVerifier;
import org.dromara.aigov.mapper.AigModelConfigMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.support.GenericApplicationContext;
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

    /**
     * 让 {@code JsonUtils} 能初始化（最小 Spring 上下文）。
     *
     * <p>{@code JsonUtils.JSON_MAPPER} 是 {@code SpringUtils.getBean(JsonMapper.class)} 这种**静态**
     * 字段；没有容器时第一次被用到就 {@code ExceptionInInitializerError}，而且**同一个 JVM 里
     * 该类从此废掉**（后续全是 {@code NoClassDefFoundError}）——这个错误是"粘"的。</p>
     *
     * <p><b>本类为什么仍然装它</b>：本类的用例刻意不调用会碰 {@code JsonUtils} 的代码路径
     * （见下面 {@code chatModelStillRoutesToChatProbe} 的说明），但下游一旦有人加一句调用，
     * 就会以"另一个测试类失败"的形式在 CI 上炸——先装上，成本为零。</p>
     */
    @BeforeAll
    static void bootJsonMapper() {
        GenericApplicationContext context = new GenericApplicationContext();
        context.getBeanFactory().registerSingleton("jsonMapper", JsonMapper.builder().build());
        context.refresh();
        // hutool 的 SpringUtil 只有**实例方法**能写它的静态字段（正常由容器在 Aware 回调里调）
        new SpringUtil().setApplicationContext(context);
    }

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
            providerOf(imageInvoker()), mock(AigModelSecretCipher.class), new AigModelTestProperties(), mock(SnailAiAppVerifier.class));

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
            providerOf(null), mock(AigModelSecretCipher.class), new AigModelTestProperties(), mock(SnailAiAppVerifier.class));

        AigModelTestVo result = tester.test(target("IMAGE", "openai-compatible", true));

        assertEquals("OPENAI_IMAGE", result.getProbe());
        assertFalse(result.getOk());
        assertTrue(result.getMessage() != null && result.getMessage().contains("图像调用器不可用"),
            "实际：" + result.getMessage());
    }

    @Test
    @DisplayName("非图像模型仍走 chat 探针（这次修改没有把它带走）")
    void chatModelStillRoutesToChatProbe() {
        // modelType 传 null（历史数据就是这种形态）：不是 IMAGE，所以应落到 chat 探针。
        //
        // 这条会真的发一次 HTTP——探针内部自己兜住了网络异常（DNS 失败会立刻返回，
        // 重试 2 次 × 500ms，秒级）。之所以接受"真发一次"，是因为要证明的正是
        // "CHAT 仍然走 old path"；把它 mock 掉就只能证明我自己写的 stub 而已。
        ModelConnectionTester tester = new ModelConnectionTester(
            providerOf(imageInvoker()), mock(AigModelSecretCipher.class), new AigModelTestProperties(), mock(SnailAiAppVerifier.class));

        AigModelTestVo result = tester.test(target(null, "openai-compatible", true));

        assertEquals("OPENAI_COMPATIBLE", result.getProbe(), "CHAT/空类型模型应仍走 chat 探针");
    }

    @Test
    @DisplayName("model_type 大小写与空白不影响分流（IMAGE / image / ' IMAGE ' 都走图像探针）")
    void modelTypeIsNormalised() {
        for (String type : List.of("IMAGE", "image", " IMAGE ")) {
            ModelConnectionTester tester = new ModelConnectionTester(
                providerOf(null), mock(AigModelSecretCipher.class), new AigModelTestProperties(), mock(SnailAiAppVerifier.class));
            AigModelTestVo result = tester.test(target(type, "openai-compatible", true));
            assertEquals("OPENAI_IMAGE", result.getProbe(), "model_type=" + type + " 应走图像探针");
        }
    }

    @Test
    @DisplayName("图像模型没有端点 → 仍然是不支持，而不是误报图像探针成功")
    void imageModelWithoutEndpoint() {
        ModelConnectionTester tester = new ModelConnectionTester(
            providerOf(imageInvoker()), mock(AigModelSecretCipher.class), new AigModelTestProperties(), mock(SnailAiAppVerifier.class));

        AigModelTestVo result = tester.test(target("IMAGE", "openai-compatible", false));

        assertEquals("UNSUPPORTED", result.getProbe());
        assertFalse(result.getOk());
    }
}
