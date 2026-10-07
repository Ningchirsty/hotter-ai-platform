package org.dromara.aigov.service.impl;

import org.dromara.aigov.domain.vo.AigRouteDecision;
import org.dromara.aigov.enums.AigDataLevelEnum;
import org.dromara.aigov.enums.AigRouteDecisionEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 路由引擎「场景强制绑定 Provider」行为锁定测试（设计 §4.4 第 4 步）。
 *
 * <p><b>为什么必须单独测这一段</b>：这个功能的全部价值集中在一条不变式上——
 * <b>它只能收窄候选，不能放宽任何治理条件</b>。而「放宽」这种错误在正常路径上
 * 看不出来：把某个场景钉到一家外部供应商，如果实现是「先按场景挑，再跳过治理校验」，
 * 那么配置正确时结果一模一样，只有在该模型本应被 allowExternal='N' / 数据等级 /
 * 生命周期拦下时才分道扬镳——而那时数据已经发出去了。</p>
 *
 * <p>因此本测试特意用<b>同一套候选</b>（外部 + 本地各一个）跑多种绑定，
 * 并显式验证「绑定到外部供应商也不会让 allowExternal='N' 失效」。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigRouteServiceScenarioTest extends AigRouteServiceTestSupport {

    /**
     * 外部候选的供应商。
     */
    private static final long PROVIDER_EXTERNAL = 1001L;

    /**
     * 本地候选的供应商。
     */
    private static final long PROVIDER_LOCAL = 1002L;

    @Test
    @DisplayName("不带场景：候选不受供应商限制，仍按用途/优先级选中外部主候选（对照基线）")
    void withoutScenarioNothingIsNarrowed() {
        stubCapability();
        stubPolicy(AigDataLevelEnum.INTERNAL, "Y", "Y");
        stubTwoCandidatesTwoProviders(PROVIDER_EXTERNAL, PROVIDER_LOCAL);

        AigRouteDecision decision = routeService.decide(CAPABILITY, AigDataLevelEnum.INTERNAL);

        assertEquals(AigRouteDecisionEnum.MODEL.getCode(), decision.getDecision(),
            "未带场景时应正常命中；实际=" + describe(decision));
        assertEquals(EXTERNAL_MODEL_ID, decision.getModelId(),
            "对照基线：外部主候选应被选中，否则后续「被场景收窄」的断言无法归因");
        assertEquals(2, decision.getCandidates().size(), "两个候选都应在候选链里");
        // 无场景概念的调用占绝大多数，不该为它刷一行无意义提示
        assertFalse(decision.getPolicyHits().stream().anyMatch(hit -> hit.contains("场景=")),
            "未带场景时不应产生任何场景相关说明：" + decision.getPolicyHits());
    }

    @Test
    @DisplayName("场景绑定本地供应商：外部候选被排除，改选本地候选")
    void scenarioBindingNarrowsToAllowedProvider() {
        stubCapability();
        stubPolicy(AigDataLevelEnum.INTERNAL, "Y", "Y");
        stubTwoCandidatesTwoProviders(PROVIDER_EXTERNAL, PROVIDER_LOCAL);
        stubScenarioBinding(PROVIDER_LOCAL);

        AigRouteDecision decision = routeService.decide(CAPABILITY, AigDataLevelEnum.INTERNAL, SCENARIO);

        assertEquals(AigRouteDecisionEnum.MODEL.getCode(), decision.getDecision(),
            "收窄后仍有可用候选，应命中；实际=" + describe(decision));
        assertEquals(LOCAL_MODEL_ID, decision.getModelId(),
            "外部候选属于未绑定供应商，必须被排除，改选本地候选");
        assertEquals(1, decision.getCandidates().size(),
            "候选链也应只剩被允许的那一个，否则 fallback 会顺延到被场景排除的供应商");
        assertTrue(decision.getPolicyHits().stream().anyMatch(hit -> hit.contains("场景=" + SCENARIO)
                && hit.contains("强制绑定供应商")),
            "收窄这件事必须出现在 policyHits 里，便于事后回答「为什么没走默认首选」："
                + decision.getPolicyHits());
        assertTrue(decision.getPolicyHits().stream().anyMatch(hit -> hit.contains("排除 modelId=" + EXTERNAL_MODEL_ID)),
            "被排除的候选要逐条写明原因与它的供应商：" + decision.getPolicyHits());
    }

    @Test
    @DisplayName("关键不变式：绑定到外部供应商也不能让 allowExternal='N' 失效")
    void scenarioBindingMustNotBypassGovernance() {
        stubCapability();
        // 策略禁止外发——这正是场景绑定「不许放宽」的那条约束
        stubPolicy(AigDataLevelEnum.INTERNAL, "N", "Y");
        stubTwoCandidatesTwoProviders(PROVIDER_EXTERNAL, PROVIDER_LOCAL);
        // 场景把范围钉在「外部供应商」上：本地候选因此被收窄掉
        stubScenarioBinding(PROVIDER_EXTERNAL);

        AigRouteDecision decision = routeService.decide(CAPABILITY, AigDataLevelEnum.INTERNAL, SCENARIO);

        // 外部候选被治理拦下、本地候选被场景收窄掉 → 无候选可用 → 转人工
        assertEquals(AigRouteDecisionEnum.MANUAL.getCode(), decision.getDecision(),
            "allowExternal='N' 必须仍然拦得住外部候选，哪怕场景强制绑定它；实际=" + describe(decision));
        assertTrue(decision.getPolicyHits().stream().anyMatch(hit -> hit.contains("allowExternal='N'")),
            "外部候选被排除的原因必须写明是策略禁止外发，而不是「供应商不匹配」："
                + decision.getPolicyHits());
        assertTrue(decision.getPolicyHits().stream().anyMatch(hit -> hit.contains("场景=" + SCENARIO)
                && hit.contains("强制绑定")),
            "同时也要能看到场景收窄发生过：" + decision.getPolicyHits());
    }

    @Test
    @DisplayName("场景绑定把候选全排除：按策略收敛，且不能报成「能力未绑定任何模型」")
    void allCandidatesNarrowedAwayConvergesWithAccurateReason() {
        stubCapability();
        stubPolicy(AigDataLevelEnum.INTERNAL, "Y", "N");
        stubTwoCandidatesTwoProviders(PROVIDER_EXTERNAL, PROVIDER_LOCAL);
        stubScenarioBinding(9999L);

        AigRouteDecision decision = routeService.decide(CAPABILITY, AigDataLevelEnum.INTERNAL, SCENARIO);

        assertEquals(AigRouteDecisionEnum.DENIED.getCode(), decision.getDecision(),
            "fallbackToManual='N' 时应拒绝；实际=" + describe(decision));
        // 这两条一起锁住「说明不能指向错误的排查方向」
        assertFalse(decision.getPolicyHits().stream().anyMatch(hit -> hit.contains("未绑定任何启用状态的模型")),
            "真实原因是场景把候选全排除了，报成「没绑定模型」会把人带去查 binding 配置：" + decision.getPolicyHits());
        assertTrue(decision.getPolicyHits().stream().anyMatch(hit -> hit.contains("场景强制绑定后无可用候选")),
            "必须给出与真实原因一致的说明：" + decision.getPolicyHits());
    }

    @Test
    @DisplayName("绑定行存在但未指定供应商：视为未配置（不做收窄），而不是「不允许任何供应商」")
    void bindingWithoutProviderIsTreatedAsUnconfigured() {
        stubCapability();
        stubPolicy(AigDataLevelEnum.INTERNAL, "Y", "Y");
        stubTwoCandidatesTwoProviders(PROVIDER_EXTERNAL, PROVIDER_LOCAL);
        stubScenarioBindingWithoutProvider();

        AigRouteDecision decision = routeService.decide(CAPABILITY, AigDataLevelEnum.INTERNAL, SCENARIO);

        assertEquals(AigRouteDecisionEnum.MODEL.getCode(), decision.getDecision(),
            "配置不全等同于未配置；若解释成「允许零个供应商」会把该场景的调用全部掐死；实际="
                + describe(decision));
        assertEquals(EXTERNAL_MODEL_ID, decision.getModelId(), "未做收窄时应回到默认首选");
        assertTrue(decision.getPolicyHits().stream().anyMatch(hit -> hit.contains("未指定供应商")
                && hit.contains("不做场景收窄")),
            "必须明确提示配置不全，否则管理员以为已生效：" + decision.getPolicyHits());
    }

    @Test
    @DisplayName("场景编码大小写不敏感：请求写小写也要命中绑定")
    void scenarioCodeIsCaseInsensitive() {
        stubCapability();
        stubPolicy(AigDataLevelEnum.INTERNAL, "Y", "Y");
        stubTwoCandidatesTwoProviders(PROVIDER_EXTERNAL, PROVIDER_LOCAL);
        stubScenarioBinding(PROVIDER_LOCAL);

        AigRouteDecision decision = routeService.decide(CAPABILITY, AigDataLevelEnum.INTERNAL,
            SCENARIO.toLowerCase());

        assertEquals(LOCAL_MODEL_ID, decision.getModelId(),
            "小写场景编码未命中绑定会让「按约定钉死」在部分调用方静默失效；实际=" + describe(decision));
        assertTrue(decision.getPolicyHits().stream().anyMatch(hit -> hit.contains("场景=" + SCENARIO)),
            "说明里应使用规范化后的大写编码：" + decision.getPolicyHits());
    }

    @Test
    @DisplayName("未配置绑定的场景：不收窄，并留下「供应商不受场景限制」的可见说明")
    void scenarioWithoutBindingIsNotNarrowedButSaysSo() {
        stubCapability();
        stubPolicy(AigDataLevelEnum.INTERNAL, "Y", "Y");
        stubTwoCandidatesTwoProviders(PROVIDER_EXTERNAL, PROVIDER_LOCAL);
        // 默认装置即「无绑定行」

        AigRouteDecision decision = routeService.decide(CAPABILITY, AigDataLevelEnum.INTERNAL, SCENARIO);

        assertEquals(EXTERNAL_MODEL_ID, decision.getModelId(), "未配置绑定就不该收窄");
        assertTrue(decision.getPolicyHits().stream().anyMatch(hit -> hit.contains("未配置强制绑定")),
            "带场景却没绑定时必须说明「场景未生效」，否则「配了以为生效」无从察觉："
                + decision.getPolicyHits());
    }

    @Test
    @DisplayName("两参重载等价于「不带场景」：老调用方行为不变")
    void twoArgOverloadEqualsNullScenario() {
        stubCapability();
        stubPolicy(AigDataLevelEnum.INTERNAL, "Y", "Y");
        stubTwoCandidatesTwoProviders(PROVIDER_EXTERNAL, PROVIDER_LOCAL);

        AigRouteDecision viaOverload = routeService.decide(CAPABILITY, AigDataLevelEnum.INTERNAL);
        AigRouteDecision viaExplicitNull = routeService.decide(CAPABILITY, AigDataLevelEnum.INTERNAL, null);

        assertEquals(viaExplicitNull.getModelId(), viaOverload.getModelId(),
            "两参重载不能悄悄改变既有调用方的决策结果");
        assertEquals(viaExplicitNull.getCandidates().size(), viaOverload.getCandidates().size());
    }

}
