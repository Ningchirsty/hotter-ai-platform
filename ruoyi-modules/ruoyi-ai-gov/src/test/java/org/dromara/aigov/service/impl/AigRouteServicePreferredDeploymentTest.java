package org.dromara.aigov.service.impl;

import org.dromara.aigov.domain.vo.AigRouteCandidate;
import org.dromara.aigov.domain.vo.AigRouteDecision;
import org.dromara.aigov.domain.vo.AigRouteHint;
import org.dromara.aigov.enums.AigDataLevelEnum;
import org.dromara.aigov.enums.AigDeploymentTypeEnum;
import org.dromara.aigov.enums.AigRouteDecisionEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 策略「优先部署类型」（{@code aig_route_policy.preferred_deployment}）的排序偏好测试。
 *
 * <p><b>为什么值得单独测</b>：这个字段此前<b>只被写进 {@code policyHits} 的说明文本</b>——
 * 管理员在治理台把「优先部署类型」选成 LOCAL，页面显示 LOCAL，路由却完全不看它。
 * 现在它真的参与排序了，于是三件事必须钉住：</p>
 * <ol>
 *     <li><b>偏好优先于 usageType</b>：策略说 LOCAL，即使 LOCAL 那只是 FALLBACK 绑定，
 *         也要前置到外部 PRIMARY 之前（否则「优先」在它最该起作用的场景里等于没生效）；</li>
 *     <li><b>它只是排序、不是过滤</b>：不匹配的候选仍在候选链里，仍可被顺延使用；</li>
 *     <li><b>它绝不放宽硬约束</b>：{@code allowExternal='N'} / 严格级不外发排除掉的候选，
 *         不因为它恰好是「优先部署类型」而复活——否则「优先外部」就成了绕过禁令的后门。</li>
 * </ol>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigRouteServicePreferredDeploymentTest extends AigRouteServiceTestSupport {

    private AigRouteDecision decide(String preferredDeployment, String allowExternal) {
        stubCapability();
        stubPolicy(AigDataLevelEnum.INTERNAL, allowExternal, "Y", "N", preferredDeployment);
        // 外部 PRIMARY（顺序 1）+ 本地 FALLBACK（顺序 2）——只有两个不同部署类型的候选，
        // 「优先排序生效」与「压根没排序」才能区分开
        stubTwoCandidatesTwoProviders(1001L, 1002L);
        return routeService.decide(CAPABILITY, AigDataLevelEnum.INTERNAL, (AigRouteHint) null);
    }

    @Test
    @DisplayName("★ 策略优先 LOCAL：LOCAL 候选前置，压过 usageType 的 PRIMARY（外部）")
    void prefersLocalOverPrimaryExternal() {
        AigRouteDecision decision = decide(AigDeploymentTypeEnum.LOCAL.getCode(), "Y");

        assertTrue(AigRouteDecisionEnum.MODEL.getCode().equals(decision.getDecision()),
            "优先排序不该影响能不能调用：" + decision.getPolicyHits());
        assertEquals(LOCAL_MODEL_ID, decision.getModelId(),
            "策略说优先 LOCAL，主候选就该是本地模型——这是这个字段存在的唯一意义");
        assertEquals(AigDeploymentTypeEnum.LOCAL.getCode(), decision.getDeploymentType());
        assertEquals(LOCAL_MODEL_ID, decision.getCandidates().get(0).getModelId(),
            "候选链第一项必须与顶层字段一致，否则「谁是主候选」会分叉");
        // 只是排序：不匹配的候选仍在链里，仍可被顺延
        assertEquals(2, decision.getCandidates().size(), "偏好不过滤，外部候选仍在备选链里");
        assertEquals(EXTERNAL_MODEL_ID, decision.getCandidates().get(1).getModelId());
        // 顺序号要跟着重排：调用编排的日志与重试消息按 order 说「候选 #N」
        assertEquals(1, decision.getCandidates().get(0).getOrder());
        assertEquals(2, decision.getCandidates().get(1).getOrder(), "重排后顺序号必须重编，否则日志会指错候选");
        assertTrue(decision.getPolicyHits().stream().anyMatch(h -> h.contains("preferred_deployment=LOCAL")),
            "排障要看得出「这次为什么是它」：" + decision.getPolicyHits());
    }

    @Test
    @DisplayName("优先类型恰好已是主候选 → 顺序不变（不产生无谓重排）")
    void keepsOrderWhenPreferenceAlreadyFirst() {
        AigRouteDecision decision = decide(AigDeploymentTypeEnum.EXTERNAL_API.getCode(), "Y");

        assertEquals(EXTERNAL_MODEL_ID, decision.getModelId());
        assertEquals(EXTERNAL_MODEL_ID, decision.getCandidates().get(0).getModelId());
        assertEquals(1, decision.getCandidates().get(0).getOrder());
    }

    @Test
    @DisplayName("未声明优先部署类型 → 按 usageType 原序（既有行为不变）")
    void blankPreferenceKeepsLegacyOrder() {
        AigRouteDecision decision = decide(null, "Y");

        assertEquals(EXTERNAL_MODEL_ID, decision.getModelId(), "未声明时主候选仍是 PRIMARY 绑定");
        assertTrue(decision.getPolicyHits().stream().noneMatch(h -> h.contains("preferred_deployment")),
            "没声明就别说这件事：" + decision.getPolicyHits());
    }

    @Test
    @DisplayName("★ 偏好不放宽硬约束：策略禁止外发时，优先 EXTERNAL_API 也救不回外部候选")
    void preferenceDoesNotBypassExternalBan() {
        AigRouteDecision decision = decide(AigDeploymentTypeEnum.EXTERNAL_API.getCode(), "N");

        assertTrue(AigRouteDecisionEnum.MODEL.getCode().equals(decision.getDecision()),
            "策略禁止外发时仍应命中本地候选：policyHits=" + decision.getPolicyHits());
        assertEquals(LOCAL_MODEL_ID, decision.getModelId(),
            "被 allowExternal='N' 排除的外部候选早在可用性校验里就出局了，排序偏好看不见它");
        assertTrue(decision.getPolicyHits().stream().anyMatch(h -> h.contains("allowExternal='N'")),
            "要能看出外部候选是被硬约束排除的：" + decision.getPolicyHits());
        assertTrue(decision.getPolicyHits().stream().noneMatch(h -> h.contains("优先排序无对象")),
            "外部候选是被排除、不是「不存在」——这两句不能混：" + decision.getPolicyHits());
    }

    @Test
    @DisplayName("没有匹配的候选 → 原序，并在命中说明里写「优先排序无对象」（而不是静默不做）")
    void reportsWhenNoCandidateMatches() {
        AigRouteDecision decision = decide(AigDeploymentTypeEnum.GROUP.getCode(), "Y");

        assertEquals(EXTERNAL_MODEL_ID, decision.getModelId(), "没有 GROUP 候选，顺序按 usageType");
        assertTrue(decision.getPolicyHits().stream().anyMatch(h -> h.contains("优先排序无对象")),
            "「策略要 GROUP，但本次没有 GROUP 候选」必须说出来，否则看起来像这个开关又不生效了："
                + decision.getPolicyHits());
    }

    @Test
    @DisplayName("非法优先部署类型 → 不做排序、如实记账，但不让调用失败（它只是偏好）")
    void illegalPreferenceIsReportedNotFatal() {
        AigRouteDecision decision = decide("BOGUS", "Y");

        assertTrue(AigRouteDecisionEnum.MODEL.getCode().equals(decision.getDecision()),
            "排序偏好非法不该把调用打成 DENIED：" + decision.getPolicyHits());
        assertEquals(EXTERNAL_MODEL_ID, decision.getModelId(), "非法值不做排序，按 usageType 原序");
        assertTrue(decision.getPolicyHits().stream().anyMatch(h -> h.contains("不是合法的部署类型")),
            "要能看出非法值被如实记账（而不是静默当作没配置）：" + decision.getPolicyHits());
    }

    @Test
    @DisplayName("只有一个候选时不做无谓重排（不足两个就没有「优先」可言）")
    void singleCandidateIsUntouched() {
        stubCapability();
        stubPolicy(AigDataLevelEnum.INTERNAL, "Y", "Y", "N", AigDeploymentTypeEnum.LOCAL.getCode());
        stubExternalOnlyCandidates();

        AigRouteDecision decision = routeService.decide(CAPABILITY, AigDataLevelEnum.INTERNAL,
            (AigRouteHint) null);

        AigRouteCandidate only = decision.getCandidates().get(0);
        assertEquals(EXTERNAL_MODEL_ID, only.getModelId());
        assertEquals(1, only.getOrder());
    }

}
