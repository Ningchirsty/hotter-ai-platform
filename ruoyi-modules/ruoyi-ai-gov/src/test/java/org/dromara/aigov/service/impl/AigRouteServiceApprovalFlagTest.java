package org.dromara.aigov.service.impl;

import org.dromara.aigov.domain.vo.AigRouteDecision;
import org.dromara.aigov.domain.vo.AigRouteHint;
import org.dromara.aigov.enums.AigDataLevelEnum;
import org.dromara.aigov.enums.AigRouteDecisionEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 路由侧「是否需要调用审批」的转达测试（C3）。
 *
 * <p><b>这一小段为什么单独测</b>：{@code aig_route_policy.require_approval} 此前
 * 只是被拼进说明文本；它现在被读成结构化字段 {@code decision.approvalRequired}，
 * 交给统一调用入口去核对授权。<b>路由侧只负责转达，不做判定</b>（它拿不到调用人）——
 * 因此「转达有没有生效」这件事必须钉住：漏掉它，策略上写着"需要审批"，
 * 调用照样跑出去，而且没有任何报错。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigRouteServiceApprovalFlagTest extends AigRouteServiceTestSupport {

    @Test
    @DisplayName("★ require_approval='Y' → 决策带上 approvalRequired=true 并在 policyHits 里说明")
    void approvalRequiredIsCarriedOnDecision() {
        stubCapability();
        // allowExternal='Y'：INTERNAL 级数据可以外发，才会真的选出一个外部候选（决策=MODEL）
        stubPolicy(AigDataLevelEnum.INTERNAL, "Y", "Y", "Y");
        stubExternalOnlyCandidates();

        // 显式转型：decide 有 (能力,等级,String场景) 与 (能力,等级,AigRouteHint) 两个重载，
        // 直接传 null 会因歧义编译不过；带 hint 的那个才是真实入口
        AigRouteDecision decision = routeService.decide(CAPABILITY, AigDataLevelEnum.INTERNAL,
            (AigRouteHint) null);

        // 路由本身照常选出候选：审批是调用入口的前置闸门，不是路由过滤条件
        assertTrue(AigRouteDecisionEnum.MODEL.getCode().equals(decision.getDecision()),
            "要求审批不该把决策变成 DENIED：拦不拦留给调用入口统一判（那里才有调用人）");
        assertTrue(decision.isApprovalRequired(), "必须把「需要审批」结构化地传给调用入口");
        assertTrue(decision.getPolicyHits().stream().anyMatch(h -> h.contains("要求调用授权审批")),
            "排障要能看出「为什么这次要审批」：" + decision.getPolicyHits());
    }

    @Test
    @DisplayName("require_approval='N' → approvalRequired=false（默认行为与引入审批之前一致）")
    void approvalNotRequiredWhenFlagIsN() {
        stubCapability();
        stubPolicy(AigDataLevelEnum.INTERNAL, "Y", "Y", "N");
        stubExternalOnlyCandidates();

        AigRouteDecision decision = routeService.decide(CAPABILITY, AigDataLevelEnum.INTERNAL,
            (AigRouteHint) null);

        assertFalse(decision.isApprovalRequired());
    }

    @Test
    @DisplayName("被策略拒绝时（如严格级数据禁止外发且无本地候选）不会因为审批标记而放行")
    void approvalFlagDoesNotRescueADeniedDecision() {
        stubCapability();
        // 严格级 + 只允许非外部部署 → 只有一个外部候选，会被全排除
        stubPolicy(AigDataLevelEnum.RESTRICTED, "N", "N", "Y");
        stubExternalOnlyCandidates();

        AigRouteDecision decision = routeService.decide(CAPABILITY, AigDataLevelEnum.RESTRICTED,
            (AigRouteHint) null);

        assertTrue(AigRouteDecisionEnum.DENIED.getCode().equals(decision.getDecision()),
            "审批标记不是放行开关：路由层面的硬约束（严格级不外发）仍然说了算");
    }

}
