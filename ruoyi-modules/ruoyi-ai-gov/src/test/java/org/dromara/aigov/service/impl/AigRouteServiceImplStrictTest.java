package org.dromara.aigov.service.impl;

import org.dromara.aigov.domain.vo.AigRouteDecision;
import org.dromara.aigov.enums.AigDataLevelEnum;
import org.dromara.aigov.enums.AigDeploymentTypeEnum;
import org.dromara.aigov.enums.AigRouteDecisionEnum;
import org.dromara.aigov.enums.AigUsageTypeEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * 路由引擎「数据等级硬约束」行为锁定测试。
 *
 * <p><b>为什么必须单独测这一段</b>：{@link AigDataLevelEnum#STRICT} 的语义是
 * 「任何路由策略都不允许外发」。它与 {@code RESTRICTED} 的区别只在
 * 「策略写成 {@code allowExternal='Y'} 时是否还拦得住」——而这恰好是最容易被
 * 一次「顺手放行」的改动破坏、且破坏后**从结果上看不出来**的地方：
 * 数据照样发出去，审计里 external_call=Y 看起来也"符合策略"。</p>
 *
 * <p>因此本测试用**同一套数据**跑两遍：数据等级换成 {@code RESTRICTED} 时必须
 * 能选中外部模型（证明测试不是恒真），换成 {@code STRICT} 时必须选不中。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigRouteServiceImplStrictTest extends AigRouteServiceTestSupport {

    @Test
    @DisplayName("STRICT：策略写成 allowExternal='Y' 也必须拦下外部模型，且说明里写明「被忽略」")
    void strictMustNotSelectExternalEvenWhenPolicyAllows() {
        // 策略显式允许外发——这正是需要被硬约束盖住的情形
        stubCapability();
        stubPolicy(AigDataLevelEnum.STRICT, "Y", "Y");
        stubExternalOnlyCandidates();

        AigRouteDecision decision = routeService.decide(CAPABILITY, AigDataLevelEnum.STRICT);

        assertNotEquals(AigRouteDecisionEnum.MODEL.getCode(), decision.getDecision(),
            "STRICT 级数据绝不能选中外部模型，哪怕策略 allowExternal='Y'；实际=" + describe(decision));
        // fallbackToManual='Y' → 无可用模型时转人工，而不是静默失败
        assertEquals(AigRouteDecisionEnum.MANUAL.getCode(), decision.getDecision(),
            "STRICT + fallbackToManual='Y' 应转人工；实际=" + describe(decision));
        assertTrue(decision.getPolicyHits().stream().anyMatch(hit -> hit.contains("严格级")),
            "策略被硬约束覆盖的原因必须出现在 policyHits 里，便于审计与排障：" + decision.getPolicyHits());
    }

    @Test
    @DisplayName("对照：同一套数据换成 RESTRICTED 且策略允许外发时，外部模型必须能被选中")
    void restrictedStillAllowsExternalWhenPolicySaysSo() {
        stubCapability();
        stubPolicy(AigDataLevelEnum.RESTRICTED, "Y", "N");
        stubExternalOnlyCandidates();

        AigRouteDecision decision = routeService.decide(CAPABILITY, AigDataLevelEnum.RESTRICTED);

        assertEquals(AigRouteDecisionEnum.MODEL.getCode(), decision.getDecision(),
            "RESTRICTED 与 STRICT 必须可区分：RESTRICTED 仍可由策略显式放行外发；实际=" + describe(decision));
        assertEquals(AigDeploymentTypeEnum.EXTERNAL_API.getCode(), decision.getDeploymentType());
    }

    @Test
    @DisplayName("STRICT：外部模型被排除后，必须顺延到非外部部署模型，而不是直接判定无模型")
    void strictFallsThroughToLocalModel() {
        stubCapability();
        stubPolicy(AigDataLevelEnum.STRICT, "Y", "N");
        // PRIMARY 是外部模型，FALLBACK 是本地模型
        when(capabilityModelMapper.selectList(any())).thenReturn(List.of(
            binding(EXTERNAL_MODEL_ID, AigUsageTypeEnum.PRIMARY, 1),
            binding(LOCAL_MODEL_ID, AigUsageTypeEnum.FALLBACK, 2)));
        when(modelViewMapper.selectModelListByIds(anyList())).thenReturn(List.of(
            model(EXTERNAL_MODEL_ID, "vendor/cloud-model"),
            model(LOCAL_MODEL_ID, "local-qwen")));
        when(modelGovernanceMapper.selectList(any())).thenReturn(List.of(
            governance(EXTERNAL_MODEL_ID, AigDeploymentTypeEnum.EXTERNAL_API.getCode()),
            governance(LOCAL_MODEL_ID, AigDeploymentTypeEnum.LOCAL.getCode())));

        AigRouteDecision decision = routeService.decide(CAPABILITY, AigDataLevelEnum.STRICT);

        assertEquals(AigRouteDecisionEnum.MODEL.getCode(), decision.getDecision(),
            "STRICT 下应仍能命中本地模型；实际=" + describe(decision));
        assertEquals("local-qwen", decision.getModelKey(),
            "外部模型被禁后应顺延到本地模型，而不是把整个能力判成不可用；实际=" + describe(decision));
        assertEquals(AigDeploymentTypeEnum.LOCAL.getCode(), decision.getDeploymentType());
    }

    @Test
    @DisplayName("STRICT + SELF：本平台自身部署不是外发，禁止外发不该拦它")
    void strictStillAllowsSelfDeployment() {
        stubCapability();
        // 策略允许外发会被 STRICT 强制覆盖为不允许——正是要看 SELF 在这种情形下是否仍可用
        stubPolicy(AigDataLevelEnum.STRICT, "Y", "N");
        stubSingleCandidate(AigDeploymentTypeEnum.SELF.getCode());

        AigRouteDecision decision = routeService.decide(CAPABILITY, AigDataLevelEnum.STRICT);

        assertEquals(AigRouteDecisionEnum.MODEL.getCode(), decision.getDecision(),
            "SELF 是本平台自身部署（数据不出环境），STRICT 的禁止外发不该把它一起拦掉；"
                + "实际=" + describe(decision));
        assertEquals(AigDeploymentTypeEnum.SELF.getCode(), decision.getDeploymentType());
    }

}
