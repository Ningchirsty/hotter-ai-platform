package org.dromara.aigov.service.impl;

import org.dromara.aigov.domain.vo.AigRouteDecision;
import org.dromara.aigov.domain.vo.AigRouteHint;
import org.dromara.aigov.enums.AigDataLevelEnum;
import org.dromara.aigov.enums.AigRouteDecisionEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 路由引擎「本次预算」过滤行为测试（设计 §4.4 第 3 步「过滤…超过预算…的 Provider」）。
 *
 * <p><b>为什么必须单独测这一段</b>：预算过滤最危险的形态不是「不生效」，而是
 * <b>被理解成「有预算就可以多花」</b>——即实现时把它当成一条放行条件。
 * 正确语义是它<b>只做减法</b>：声明的单次上限高于本次预算的候选直接不选，
 * 而且它绝不能覆盖数据等级、外发禁令、生命周期这些治理约束。
 * 这类错误在「预算充足」的正常路径上完全看不出来，只有把预算压到很低、
 * 或与 allowExternal='N' 同时出现时才分道扬镳。</p>
 *
 * <p>另外两个边界同样容易写错，且错了都不报错：</p>
 * <ul>
 *     <li><b>没提预算 ≠ 预算为零</b>：调用方不传预算时必须<b>不做任何过滤</b>，
 *         否则等于「没提要求就把所有候选排除干净」；</li>
 *     <li><b>上限与预算相等应放行</b>：判定是「高于预算才排除」，
 *         写成「大于等于」会让恰好卡在预算上的候选被无理由剔掉。</li>
 * </ul>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigRouteServiceBudgetTest extends AigRouteServiceTestSupport {

    /**
     * 本次预算。
     */
    private static final BigDecimal BUDGET = new BigDecimal("0.50");

    /**
     * 构造带预算的路由提示。
     *
     * @param maxCost 本次预算
     * @return 路由提示
     */
    private static AigRouteHint hint(BigDecimal maxCost) {
        return AigRouteHint.of(null, maxCost);
    }

    @Test
    @DisplayName("声明上限高于预算的候选被排除，预算内的候选照常命中")
    void excludesCandidatesAboveBudget() {
        stubCapability();
        stubPolicy(AigDataLevelEnum.INTERNAL, "Y", "Y");
        // 外部这家贵（1.00 > 0.50）→ 应被排除；本地这家便宜（0.10 ≤ 0.50）→ 应改选它
        stubTwoCandidatesWithCostLimit("1.00", "0.10");

        AigRouteDecision decision = routeService.decide(CAPABILITY, AigDataLevelEnum.INTERNAL, hint(BUDGET));

        assertEquals(AigRouteDecisionEnum.MODEL.getCode(), decision.getDecision(),
            "预算内仍有可用候选，应命中；实际=" + describe(decision));
        assertEquals(LOCAL_MODEL_ID, decision.getModelId(),
            "超预算的外部候选必须被排除，改选预算内的本地候选；实际=" + describe(decision));
        assertTrue(decision.getPolicyHits().stream().anyMatch(hit -> hit.contains("高于本次预算")),
            "排除原因要写清楚是「超预算」，否则事后会以为是模型不可用：" + decision.getPolicyHits());
    }

    @Test
    @DisplayName("边界：声明上限恰好等于预算时必须放行（判定是「高于」而不是「大于等于」）")
    void equalToBudgetIsAllowed() {
        stubCapability();
        stubPolicy(AigDataLevelEnum.INTERNAL, "Y", "Y");
        stubSingleCandidateWithCostLimit("0.50");

        AigRouteDecision decision = routeService.decide(CAPABILITY, AigDataLevelEnum.INTERNAL, hint(BUDGET));

        assertEquals(AigRouteDecisionEnum.MODEL.getCode(), decision.getDecision(),
            "上限 == 预算 应放行；写成「大于等于才排除」会把恰好卡在预算上的候选无理由剔掉；实际="
                + describe(decision));
        assertEquals(EXTERNAL_MODEL_ID, decision.getModelId());
    }

    @Test
    @DisplayName("没提预算：不做任何过滤，也不产生预算相关提示")
    void noBudgetMeansNoFilteringAndNoNoise() {
        stubCapability();
        stubPolicy(AigDataLevelEnum.INTERNAL, "Y", "Y");
        // 上限很高，但调用方没有预算约束，不该被排除
        stubTwoCandidatesWithCostLimit("99.00", "88.00");

        AigRouteDecision decision = routeService.decide(CAPABILITY, AigDataLevelEnum.INTERNAL,
            (AigRouteHint) null);

        assertEquals(EXTERNAL_MODEL_ID, decision.getModelId(),
            "调用方没提预算就不该过滤；把「没提要求」当成「预算为零」会把候选全排除干净。实际="
                + describe(decision));
        assertFalse(decision.getPolicyHits().stream().anyMatch(hit -> hit.contains("预算")),
            "没有预算约束时不应产生任何预算相关提示（否则每次调用都刷无用行）："
                + decision.getPolicyHits());
    }

    @Test
    @DisplayName("未声明单次上限：默认放行并写入可见提示（新列一上线不能把所有模型排除干净）")
    void undeclaredCostLimitPassesWithNotice() {
        stubCapability();
        stubPolicy(AigDataLevelEnum.INTERNAL, "Y", "Y");
        stubSingleCandidateWithCostLimit(null);

        AigRouteDecision decision = routeService.decide(CAPABILITY, AigDataLevelEnum.INTERNAL, hint(BUDGET));

        assertEquals(AigRouteDecisionEnum.MODEL.getCode(), decision.getDecision(),
            "未声明上限默认放行；默认严格会让「带预算的调用」在既有模型上一律挑不出候选；实际="
                + describe(decision));
        assertTrue(decision.getPolicyHits().stream().anyMatch(hit -> hit.contains("未声明单次成本上限")),
            "必须提示「无法校验是否超预算」，否则没人知道这次的钱根本没被管：" + decision.getPolicyHits());
        assertTrue(decision.getPolicyHits().stream().anyMatch(hit -> hit.contains("require-model-cost")),
            "提示里要给出彻底关闭这条口子的开关：" + decision.getPolicyHits());
    }

    @Test
    @DisplayName("严格模式：未声明上限的模型被排除（require-model-cost=true）")
    void strictModeExcludesUndeclaredCostLimit() {
        stubCapability();
        stubPolicy(AigDataLevelEnum.INTERNAL, "Y", "N");
        stubSingleCandidateWithCostLimit(null);
        routeProperties.setRequireModelCost(true);
        serviceWithProperties();

        AigRouteDecision decision = routeService.decide(CAPABILITY, AigDataLevelEnum.INTERNAL, hint(BUDGET));

        assertEquals(AigRouteDecisionEnum.DENIED.getCode(), decision.getDecision(),
            "严格模式下未声明上限应被排除；fallbackToManual='N' 故直接拒绝；实际=" + describe(decision));
        assertTrue(decision.getPolicyHits().stream().anyMatch(hit -> hit.contains("require-model-cost=true")),
            "排除原因要指明是严格开关导致的：" + decision.getPolicyHits());
    }

    @Test
    @DisplayName("全部候选都超预算：按策略收敛，说明不能指向错误的排查方向")
    void allCandidatesAboveBudgetConvergesWithAccurateReason() {
        stubCapability();
        stubPolicy(AigDataLevelEnum.INTERNAL, "Y", "N");
        stubTwoCandidatesWithCostLimit("9.00", "8.00");

        AigRouteDecision decision = routeService.decide(CAPABILITY, AigDataLevelEnum.INTERNAL, hint(BUDGET));

        assertEquals(AigRouteDecisionEnum.DENIED.getCode(), decision.getDecision(),
            "全部超预算且 fallbackToManual='N' 时应拒绝；实际=" + describe(decision));
        assertTrue(decision.getPolicyHits().stream().anyMatch(hit -> hit.contains("按本次预算过滤后无可用候选")),
            "原因必须写「按预算过滤后无候选」，而不是退化成「未绑定任何模型」——"
                + "后者会把人带去查绑定配置，而真实原因是预算给了太低：" + decision.getPolicyHits());
    }

    @Test
    @DisplayName("关键不变式：预算不能放宽治理口径——策略禁止外发时，预算内的外部模型照样不能用")
    void budgetMustNotBypassGovernance() {
        stubCapability();
        // 策略禁止外发：这正是「预算不许放宽」的那条约束
        stubPolicy(AigDataLevelEnum.INTERNAL, "N", "N");
        // 外部候选便宜（0.10 ≤ 0.50，预算完全够），但它是外部部署
        stubTwoCandidatesWithCostLimit("0.10", "88.00");

        AigRouteDecision decision = routeService.decide(CAPABILITY, AigDataLevelEnum.INTERNAL, hint(BUDGET));

        assertEquals(AigRouteDecisionEnum.DENIED.getCode(), decision.getDecision(),
            "预算充足也不能让被禁止外发的候选变得可用；实际=" + describe(decision));
        assertTrue(decision.getPolicyHits().stream().anyMatch(hit -> hit.contains("allowExternal='N'")),
            "排除原因要写明是策略禁止外发，而不是「预算不够」：" + decision.getPolicyHits());
    }

    @Test
    @DisplayName("无提示（null）与「只有场景没有预算」等价：都不做预算过滤")
    void scenarioOnlyHintDoesNotFilterByBudget() {
        stubCapability();
        stubPolicy(AigDataLevelEnum.INTERNAL, "Y", "Y");
        stubTwoCandidatesWithCostLimit("99.00", "88.00");

        AigRouteDecision decision = routeService.decide(CAPABILITY, AigDataLevelEnum.INTERNAL,
            AigRouteHint.ofScenario(null));

        assertEquals(EXTERNAL_MODEL_ID, decision.getModelId(),
            "只有场景、没有预算时不该按预算过滤；实际=" + describe(decision));
    }

    /**
     * 用当前的 {@code routeProperties} 重建路由服务。
     *
     * <p>装置里的服务是在 {@code @BeforeEach} 用默认配置构造的，本类有一个用例需要
     * 先改配置再重建——否则改的属性不会生效，而用例会以「看起来通过」的方式失败：
     * 严格开关没打开、未声明上限被放行，恰好与断言期望相反时会立刻暴露，
     * 但若断言写反就会被误判成实现有问题。</p>
     */
    private void serviceWithProperties() {
        useInvokers(java.util.List.of());
    }

}
