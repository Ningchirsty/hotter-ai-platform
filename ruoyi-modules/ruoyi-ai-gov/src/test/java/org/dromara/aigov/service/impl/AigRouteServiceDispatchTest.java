package org.dromara.aigov.service.impl;

import org.dromara.aigov.domain.vo.AigModelVo;
import org.dromara.aigov.domain.vo.AigRouteDecision;
import org.dromara.aigov.enums.AigDataLevelEnum;
import org.dromara.aigov.enums.AigDeploymentTypeEnum;
import org.dromara.aigov.enums.AigRouteDecisionEnum;
import org.dromara.aigov.enums.AigUsageTypeEnum;
import org.dromara.aigov.service.invoker.ModelInvoker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 路由「按部署类型 + 模型类型」派发调用器的行为锁定测试。
 *
 * <p><b>为什么这段必须单独测</b>：外部聚合网关把「同一部署类型下只有一种能力」这条前提打破了——
 * 同一个 {@code EXTERNAL_API} 下既有对话模型（{@code /chat/completions}）又有图像模型
 * （{@code /images/generations}）。若派发只看部署类型，两个调用器会互相抢，
 * 结果取决于 Spring Bean 装配顺序：表现是「偶尔好好的、偶尔把图像请求发到对话端点」，
 * 而且两边都不会报「派错了」，只会报各自的上游错误。</p>
 *
 * <p>关键的负向用例是最后一条：不能让对话调用器<b>顶替</b>图像模型。
 * 那种"兜底"看着更宽容，实际是把错误推迟到上游，更难排查。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigRouteServiceDispatchTest extends AigRouteServiceTestSupport {

    @Test
    @DisplayName("IMAGE 模型必须落到图像调用器，而不是对话调用器")
    void imageModelGoesToImageInvoker() {
        stubCapability();
        stubPolicy(AigDataLevelEnum.INTERNAL, "Y", "N");
        stubModelWithType("IMAGE");
        useInvokers(List.of(chatInvoker(), imageInvoker()));

        AigRouteDecision decision = routeService.decide(CAPABILITY, AigDataLevelEnum.INTERNAL);

        assertEquals(AigRouteDecisionEnum.MODEL.getCode(), decision.getDecision(),
            "候选模型本身可用，应命中；实际=" + describe(decision));
        assertEquals("OpenAiImageInvoker", decision.getInvoker(),
            "IMAGE 模型被派给了错误的调用器；实际=" + describe(decision));
    }

    @Test
    @DisplayName("对照：CHAT 模型必须落到对话调用器")
    void chatModelGoesToChatInvoker() {
        stubCapability();
        stubPolicy(AigDataLevelEnum.INTERNAL, "Y", "N");
        stubModelWithType("CHAT");
        useInvokers(List.of(chatInvoker(), imageInvoker()));

        AigRouteDecision decision = routeService.decide(CAPABILITY, AigDataLevelEnum.INTERNAL);

        assertEquals(AigRouteDecisionEnum.MODEL.getCode(), decision.getDecision());
        assertEquals("OpenAiCompatibleInvoker", decision.getInvoker(),
            "CHAT 模型被派给了错误的调用器；实际=" + describe(decision));
    }

    @Test
    @DisplayName("空 model_type：对话调用器放行（兼容历史数据），图像调用器不放行")
    void blankModelTypeGoesToChatInvoker() {
        stubCapability();
        stubPolicy(AigDataLevelEnum.INTERNAL, "Y", "N");
        stubModelWithType(null);
        useInvokers(List.of(chatInvoker(), imageInvoker()));

        AigRouteDecision decision = routeService.decide(CAPABILITY, AigDataLevelEnum.INTERNAL);

        assertEquals("OpenAiCompatibleInvoker", decision.getInvoker(),
            "未填 model_type 的历史模型不该突然派不出去；实际=" + describe(decision));
    }

    @Test
    @DisplayName("IMAGE 模型但只有对话调用器：不得顶替，如实记为「未找到调用器」")
    void imageModelWithoutImageInvokerDoesNotFallBackToChat() {
        stubCapability();
        stubPolicy(AigDataLevelEnum.INTERNAL, "Y", "N");
        stubModelWithType("IMAGE");
        useInvokers(List.of(chatInvoker()));

        AigRouteDecision decision = routeService.decide(CAPABILITY, AigDataLevelEnum.INTERNAL);

        assertEquals(AigRouteDecisionEnum.MODEL.getCode(), decision.getDecision(),
            "模型可用就该判 MODEL，缺的是调用器而不是模型；实际=" + describe(decision));
        assertNull(decision.getInvoker(), "对话调用器不该顶替图像模型；实际=" + describe(decision));
        assertTrue(decision.getPolicyHits().stream().anyMatch(hit -> hit.contains("未找到同时支持")),
            "必须写明「找不到同时支持该部署类型与模型类型的调用器」：" + decision.getPolicyHits());
    }

    @Test
    @DisplayName("调用器 providerType 返回 null（覆写有缺陷）：不得 NPE，退化为 UNKNOWN")
    void nullProviderTypeDegradesToUnknown() {
        stubCapability();
        stubPolicy(AigDataLevelEnum.INTERNAL, "Y", "N");
        stubModelWithType("IMAGE");
        // imageInvoker() 刻意不桩 providerType()，mock 默认返回 null —— 正好覆盖这条防护
        useInvokers(List.of(imageInvoker()));

        AigRouteDecision decision = routeService.decide(CAPABILITY, AigDataLevelEnum.INTERNAL);

        assertEquals(AigRouteDecisionEnum.MODEL.getCode(), decision.getDecision(),
            "providerType 为 null 不该让整条路由被兜底成 DENIED；实际=" + describe(decision));
        assertTrue(decision.getPolicyHits().stream().anyMatch(hit -> hit.contains("Provider类型=UNKNOWN")),
            "应在说明里体现为 UNKNOWN：" + decision.getPolicyHits());
    }

    /**
     * 桩：候选模型带指定 model_type。
     *
     * @param modelType 模型类型（可为 null）
     */
    private void stubModelWithType(String modelType) {
        when(capabilityModelMapper.selectList(any()))
            .thenReturn(List.of(binding(EXTERNAL_MODEL_ID, AigUsageTypeEnum.PRIMARY, 1)));
        AigModelVo vo = model(EXTERNAL_MODEL_ID, "vendor/model");
        vo.setModelType(modelType);
        when(modelViewMapper.selectModelListByIds(anyList())).thenReturn(List.of(vo));
        when(modelGovernanceMapper.selectList(any()))
            .thenReturn(List.of(governance(EXTERNAL_MODEL_ID, AigDeploymentTypeEnum.EXTERNAL_API.getCode())));
    }

    /**
     * 模拟对话调用器：EXTERNAL_API + 只认 CHAT（空值放行）。
     *
     * @return 调用器
     */
    private ModelInvoker chatInvoker() {
        ModelInvoker invoker = mock(ModelInvoker.class);
        when(invoker.invokerName()).thenReturn("OpenAiCompatibleInvoker");
        when(invoker.supports(any())).thenReturn(true);
        when(invoker.available()).thenReturn(true);
        when(invoker.supportsModelType(any())).thenAnswer(call -> {
            String type = call.getArgument(0);
            return type == null || type.isBlank() || "CHAT".equalsIgnoreCase(type);
        });
        return invoker;
    }

    /**
     * 模拟图像调用器：EXTERNAL_API + 只认 IMAGE（空值不放行）。
     *
     * @return 调用器
     */
    private ModelInvoker imageInvoker() {
        ModelInvoker invoker = mock(ModelInvoker.class);
        when(invoker.invokerName()).thenReturn("OpenAiImageInvoker");
        when(invoker.supports(any())).thenReturn(true);
        when(invoker.available()).thenReturn(true);
        when(invoker.supportsModelType(any())).thenAnswer(call -> {
            String type = call.getArgument(0);
            return "IMAGE".equalsIgnoreCase(type);
        });
        return invoker;
    }

}
