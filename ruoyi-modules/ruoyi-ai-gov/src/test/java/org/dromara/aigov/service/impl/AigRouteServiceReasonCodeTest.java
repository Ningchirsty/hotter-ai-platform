package org.dromara.aigov.service.impl;

import org.dromara.aigov.domain.vo.AigRouteDecision;
import org.dromara.aigov.enums.AigDataLevelEnum;
import org.dromara.aigov.enums.AigPolicyReasonCodeEnum;
import org.dromara.aigov.enums.AigRouteDecisionEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 策略细因（{@code reasonCode}）的口径测试。
 *
 * <p><b>为什么细因值得单独钉一遍</b>：它是「为什么没成」的<b>可聚合</b>答案，
 * 运维靠它回答「上周有多少次是因为没配策略被拒」——这类问题从
 * {@code policyHits} 的长文本里查不出来。而它的失效方式有两种，方向相反：</p>
 * <ul>
 *     <li><b>该给不给</b>：细因恒空，于是统计永远是零（看起来像"没问题"）；</li>
 *     <li><b>不该给乱给</b>：把多候选排除的合成结果硬写成一个原因，
 *         读的人以为那就是全部原因，排障被引向错误方向。</li>
 * </ul>
 * <p>因此这里既有「无歧义时必须给」，也有「有歧义时<b>必须留空</b>」。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigRouteServiceReasonCodeTest extends AigRouteServiceTestSupport {

    @Test
    @DisplayName("★ 未配置该「能力 × 数据等级」策略 → DENIED 且细因=NO_ROUTE_POLICY")
    void missingPolicyCarriesReasonCode() {
        stubCapability();
        // 刻意不桩策略：mapper 返回 null，正是"这一格没配"的现实形态

        AigRouteDecision decision = routeService.decide(CAPABILITY, AigDataLevelEnum.INTERNAL);

        assertEquals(AigRouteDecisionEnum.DENIED.getCode(), decision.getDecision(),
            "默认拒绝（不默认放行）；实际=" + describe(decision));
        assertEquals(AigPolicyReasonCodeEnum.NO_ROUTE_POLICY.getCode(), decision.getReasonCode(),
            "补一行策略就能解决，所以要能被按细因统计出来；实际=" + describe(decision));
    }

    @Test
    @DisplayName("★ 能力一个模型都没绑 → 细因=NO_MODEL_BOUND")
    void noBindingCarriesReasonCode() {
        stubCapability();
        stubPolicy(AigDataLevelEnum.INTERNAL, "Y", "N");
        when(capabilityModelMapper.selectList(any())).thenReturn(List.of());

        AigRouteDecision decision = routeService.decide(CAPABILITY, AigDataLevelEnum.INTERNAL);

        assertEquals(AigRouteDecisionEnum.DENIED.getCode(), decision.getDecision(),
            "无候选且策略不允许转人工 → 拒绝；实际=" + describe(decision));
        assertEquals(AigPolicyReasonCodeEnum.NO_MODEL_BOUND.getCode(), decision.getReasonCode(),
            "实际=" + describe(decision));
    }

    @Test
    @DisplayName("★ 候选被逐个排除（原因是合成的）→ 细因必须留空：不猜")
    void ambiguousExclusionLeavesReasonCodeEmpty() {
        stubCapability();
        // 策略禁止外发，而唯一候选是外部部署 → 它被排除，于是"无可用模型"
        stubPolicy(AigDataLevelEnum.INTERNAL, "N", "N");
        stubExternalOnlyCandidates();

        AigRouteDecision decision = routeService.decide(CAPABILITY, AigDataLevelEnum.INTERNAL);

        assertEquals(AigRouteDecisionEnum.DENIED.getCode(), decision.getDecision(),
            "唯一候选被外发禁令排除，且不允许转人工 → 拒绝；实际=" + describe(decision));
        assertNull(decision.getReasonCode(),
            "这种拒绝的原因是「多个排除条件的合成」，挑一个写进去会让人以为那是全部原因；"
                + "具体原因在 policyHits 里逐条可查。实际=" + describe(decision));
        assertTrue(decision.getPolicyHits().stream()
                .anyMatch(hit -> hit.contains("数据外发被策略禁止")),
            "可读原因仍必须给：细因留空不等于不解释；实际=" + decision.getPolicyHits());
    }

    @Test
    @DisplayName("命中模型时不带细因（细因只回答「为什么没成」）")
    void modelDecisionHasNoReasonCode() {
        stubCapability();
        stubPolicy(AigDataLevelEnum.INTERNAL, "Y", "N");
        stubSingleCandidate("LOCAL");

        AigRouteDecision decision = routeService.decide(CAPABILITY, AigDataLevelEnum.INTERNAL);

        assertEquals(AigRouteDecisionEnum.MODEL.getCode(), decision.getDecision(),
            "实际=" + describe(decision));
        assertNull(decision.getReasonCode(), "成功的决策没有「没成」的细因；实际=" + describe(decision));
    }

}
