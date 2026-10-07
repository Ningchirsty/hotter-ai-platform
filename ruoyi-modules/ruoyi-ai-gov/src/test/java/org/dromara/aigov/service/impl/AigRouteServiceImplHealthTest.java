package org.dromara.aigov.service.impl;

import org.dromara.aigov.domain.vo.AigRouteDecision;
import org.dromara.aigov.enums.AigDataLevelEnum;
import org.dromara.aigov.enums.AigRouteDecisionEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 路由引擎「健康状态参与选型」行为锁定测试。
 *
 * <p><b>这里真正要钉住的不是「DOWN 会被排除」，而是「未测过不能被排除」。</b>
 * {@code health_status} 由人工点一下连通性测试才写入，绝大多数模型是 {@code null}。
 * 如果实现成「要求 UP 才可用」，等于把所有从未测过的模型一次性判成不可用——
 * 路由会大面积返回「无可用模型」，而这是一次看起来像「更安全」的改动引出的严重回归。
 * 因此口径必须是：<b>只在明确 DOWN 时排除</b>，未知与 DEGRADED 一律放行。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigRouteServiceImplHealthTest extends AigRouteServiceTestSupport {

    @Test
    @DisplayName("健康状态 DOWN：必须被排除，且说明里写明是健康检查导致的")
    void downModelIsExcluded() {
        stubCapability();
        stubPolicy(AigDataLevelEnum.INTERNAL, "Y", "N");
        stubExternalOnlyCandidates("DOWN");

        AigRouteDecision decision = routeService.decide(CAPABILITY, AigDataLevelEnum.INTERNAL);

        assertEquals(AigRouteDecisionEnum.DENIED.getCode(), decision.getDecision(),
            "唯一候选为 DOWN 时应判无可用模型（此处 fallbackToManual='N' → DENIED）；实际=" + describe(decision));
        assertTrue(decision.getPolicyHits().stream().anyMatch(hit -> hit.contains("健康检查为 DOWN")),
            "排除原因必须写明是健康状态，否则排障时会误以为是等级或权限问题：" + decision.getPolicyHits());
    }

    @Test
    @DisplayName("健康状态为空（从未测过）：必须照常选中——把「没测过」判成不可用会造成大面积回归")
    void nullHealthStillSelectsModel() {
        stubCapability();
        stubPolicy(AigDataLevelEnum.INTERNAL, "Y", "N");
        stubExternalOnlyCandidates(null);

        AigRouteDecision decision = routeService.decide(CAPABILITY, AigDataLevelEnum.INTERNAL);

        assertEquals(AigRouteDecisionEnum.MODEL.getCode(), decision.getDecision(),
            "从未做过健康检查不等于不可用，必须照常参与路由；实际=" + describe(decision));
    }

    @Test
    @DisplayName("健康状态 DEGRADED：降级但仍可用，必须照常选中")
    void degradedHealthStillSelectsModel() {
        stubCapability();
        stubPolicy(AigDataLevelEnum.INTERNAL, "Y", "N");
        stubExternalOnlyCandidates("DEGRADED");

        AigRouteDecision decision = routeService.decide(CAPABILITY, AigDataLevelEnum.INTERNAL);

        assertEquals(AigRouteDecisionEnum.MODEL.getCode(), decision.getDecision(),
            "DEGRADED 是「还能用但性能降级」，不该被当成不可用；实际=" + describe(decision));
    }

}
