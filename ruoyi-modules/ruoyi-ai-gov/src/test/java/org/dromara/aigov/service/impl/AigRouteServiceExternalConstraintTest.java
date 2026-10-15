package org.dromara.aigov.service.impl;

import org.dromara.aigov.domain.vo.AigRouteDecision;
import org.dromara.aigov.domain.vo.AigRouteHint;
import org.dromara.aigov.enums.AigDataLevelEnum;
import org.dromara.aigov.enums.AigRouteDecisionEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 路由引擎「业务侧外发许可」硬约束测试（设计 D02、§7.2）。
 *
 * <p><b>要钉死的分界线</b>：{@code AigRouteHint} 的其它字段都只是"在已允许的候选里挑谁"，
 * 唯独 {@code allowExternal=false} 是硬约束，必须把外部候选排除——即使路由策略那一行写着
 * {@code allowExternal='Y'}。写成"只是偏好"的表现是：Agent 明明声明禁止外发，数据还是出去了，
 * 而日志上一切正常。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigRouteServiceExternalConstraintTest extends AigRouteServiceTestSupport {

    @Test
    @DisplayName("对照组：不传业务许可时，策略允许外发则外部候选照常命中")
    void withoutBusinessConstraintExternalIsChosen() {
        stubCapability();
        stubPolicy(AigDataLevelEnum.INTERNAL, "Y", "N");
        stubTwoCandidatesWithCostLimit(null, null);

        AigRouteDecision decision = routeService.decide(CAPABILITY, AigDataLevelEnum.INTERNAL,
            (AigRouteHint) null);

        assertEquals(AigRouteDecisionEnum.MODEL.getCode(), decision.getDecision(), describe(decision));
        assertEquals(EXTERNAL_MODEL_ID, decision.getModelId(),
            "没有业务侧约束时外部主候选应命中：" + describe(decision));
    }

    @Test
    @DisplayName("★ 业务侧声明不允许外发 → 即使策略 allowExternal='Y'，外部候选也被排除")
    void businessConstraintOverridesPolicy() {
        stubCapability();
        stubPolicy(AigDataLevelEnum.INTERNAL, "Y", "N");
        stubTwoCandidatesWithCostLimit(null, null);

        AigRouteDecision decision = routeService.decide(CAPABILITY, AigDataLevelEnum.INTERNAL,
            AigRouteHint.of(null, null, Boolean.FALSE));

        assertEquals(AigRouteDecisionEnum.MODEL.getCode(), decision.getDecision(), describe(decision));
        assertEquals(LOCAL_MODEL_ID, decision.getModelId(),
            "业务侧禁止外发时只能选本地候选：" + describe(decision));
        assertTrue(decision.getPolicyHits().stream()
                .anyMatch(hit -> hit.contains("业务侧") && hit.contains("不允许外发")),
            "排除外部候选必须留下「业务侧声明」的原因，否则会被误读成模型不可用：" + decision.getPolicyHits());
    }

    @Test
    @DisplayName("业务侧允许外发（TRUE）不放宽任何约束：是否外发仍由策略决定")
    void businessAllowDoesNotOverridePolicyDeny() {
        stubCapability();
        stubPolicy(AigDataLevelEnum.INTERNAL, "N", "Y");
        stubTwoCandidatesWithCostLimit(null, null);

        AigRouteDecision decision = routeService.decide(CAPABILITY, AigDataLevelEnum.INTERNAL,
            AigRouteHint.of(null, null, Boolean.TRUE));

        assertEquals(LOCAL_MODEL_ID, decision.getModelId(),
            "策略 allowExternal='N' 时，业务侧 TRUE 不能让外部候选复活：" + describe(decision));
    }

}
