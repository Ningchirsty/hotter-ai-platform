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
 * <p><b>这里真正要钉住的不是「坏状态会被排除」，而是「未测过不能被排除」。</b>
 * {@code health_status} 由人工点一下连通性测试才写入，绝大多数模型是 {@code null}。
 * 如果实现成「要求 UP 才可用」，等于把所有从未测过的模型一次性判成不可用——
 * 路由会大面积返回「无可用模型」，而这是一次看起来像「更安全」的改动引出的严重回归。
 * 因此口径必须是：<b>只在明确「坏」时排除</b>，{@code UNKNOWN}/null/{@code DEGRADED} 一律放行。</p>
 *
 * <p><b>2026-10-08 补上 {@code UNHEALTHY}</b>：本测试原先只覆盖 {@code DOWN}，
 * 而 {@code DOWN} <b>全仓库没有任何代码写过</b>——写入端
 * {@code ModelConnectionTester} 实际落库的是 {@code HEALTHY/UNHEALTHY}。
 * 也就是说这条测试当时测的是一个<b>生产永远不会出现的取值</b>，
 * 于是「测试发现坏模型 → 路由避开」这个机制坏了很久都没人发现。
 * 现在两个词汇都锁上，并附一条反向断言（写入端确实产 {@code UNHEALTHY}）。</p>
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

    @Test
    @DisplayName("★ 健康状态 UNHEALTHY：必须被排除——这是写入端真实产出的「坏」值")
    void unhealthyModelIsExcluded() {
        stubCapability();
        stubPolicy(AigDataLevelEnum.INTERNAL, "Y", "N");
        stubExternalOnlyCandidates("UNHEALTHY");

        AigRouteDecision decision = routeService.decide(CAPABILITY, AigDataLevelEnum.INTERNAL);

        assertEquals(AigRouteDecisionEnum.DENIED.getCode(), decision.getDecision(),
            "UNHEALTHY 是连通性测试真正写进去的失败值，必须被排除；实际=" + describe(decision));
        assertTrue(decision.getPolicyHits().stream().anyMatch(hit -> hit.contains("健康检查为 UNHEALTHY")),
            "排除原因必须写明是健康状态：" + decision.getPolicyHits());
    }

    @Test
    @DisplayName("★ 健康状态大小写与两侧空白不得影响判定（unhealthy / 带空格 / 混合大小写）")
    void unhealthyIsMatchedCaseInsensitively() {
        for (String raw : new String[]{"unhealthy", "  UNHEALTHY  ", "Unhealthy"}) {
            stubCapability();
            stubPolicy(AigDataLevelEnum.INTERNAL, "Y", "N");
            stubExternalOnlyCandidates(raw);

            AigRouteDecision decision = routeService.decide(CAPABILITY, AigDataLevelEnum.INTERNAL);

            assertEquals(AigRouteDecisionEnum.DENIED.getCode(), decision.getDecision(),
                "写法变体「" + raw + "」也必须被排除，否则数据录入格式一变机制就静默失效；实际="
                    + describe(decision));
        }
    }

    @Test
    @DisplayName("★ 反证：路由的「坏」词汇必须与实际写入端产出的值交集非空")
    void routerVocabularyIntersectsTheWriterVocabulary() {
        // 写入端（ModelConnectionTester#testConnection）只会产出这两个值之一
        stubCapability();
        stubPolicy(AigDataLevelEnum.INTERNAL, "Y", "N");
        stubExternalOnlyCandidates("HEALTHY");
        assertEquals(AigRouteDecisionEnum.MODEL.getCode(),
            routeService.decide(CAPABILITY, AigDataLevelEnum.INTERNAL).getDecision(),
            "写入端判成功（HEALTHY）时不能被排除");

        stubCapability();
        stubPolicy(AigDataLevelEnum.INTERNAL, "Y", "N");
        stubExternalOnlyCandidates("UNHEALTHY");
        assertEquals(AigRouteDecisionEnum.DENIED.getCode(),
            routeService.decide(CAPABILITY, AigDataLevelEnum.INTERNAL).getDecision(),
            "写入端判失败（UNHEALTHY）时必须被排除——若这里失败，说明路由的词汇与写入端脱节，"
                + "该机制等于从未生效（这正是 2026-10-08 修掉的问题）");
    }

}
