package org.dromara.aigov.service.impl;

import org.dromara.aigov.domain.AigCapability;
import org.dromara.aigov.domain.vo.AigRouteDecision;
import org.dromara.aigov.domain.vo.AigRouteHint;
import org.dromara.aigov.enums.AigDataLevelEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Q2C：能力声明的最低数据等级在**路由引擎**里生效（只抬不降）。
 *
 * <p><b>为什么在路由层测而不是在 invoke 层</b>：所有入口（真实调用 / dryRun 预演 / 任务执行）
 * 都经过 {@code decide}。把合并口径放在这里，一处生效、处处一致；放在 invoke 里，
 * 预演就会给出与真实调用不同的结论——而预演正是我们刚做完的东西。</p>
 *
 * <p>用例刻意让「抬升」改变<strong>结论</strong>（而不只是那个字段）：声明 STRICT 的能力
 * 即使调用方按 INTERNAL 发、策略允许外发，也只能落到本地模型/被拒——否则这个测试
 * 只能证明"字段被赋值了"，证明不了"口径真的生效了"。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigRouteServiceDataLevelEscalationTest extends AigRouteServiceTestSupport {

    /**
     * 桩：能力存在且启用，并声明最低数据等级。
     *
     * @param minDataLevel 声明的最低数据等级（可空 = 不声明）
     */
    private void stubCapabilityDeclaring(String minDataLevel) {
        AigCapability capability = new AigCapability();
        capability.setCapabilityId(1L);
        capability.setCapabilityCode(CAPABILITY);
        capability.setCapabilityName("成品一致性检查");
        capability.setStatus("0");
        capability.setAuditLevel("SUMMARY");
        capability.setMinDataLevel(minDataLevel);
        when(capabilityMapper.selectOne(any())).thenReturn(capability);
    }

    @Test
    @DisplayName("★ 声明 STRICT 的能力：调用方按 INTERNAL 发也按 STRICT 路由（外发被硬拒）")
    void declaredStrictOverridesCallerLevel() {
        stubCapabilityDeclaring(AigDataLevelEnum.STRICT.getCode());
        // 策略本身允许外发，候选也只有外部模型：若等级没被抬上去，这里就会命中外部模型
        stubPolicy(AigDataLevelEnum.INTERNAL, "Y", "N");
        stubExternalOnlyCandidates();

        AigRouteDecision decision = routeService.decide(CAPABILITY, AigDataLevelEnum.INTERNAL, (AigRouteHint) null);

        assertEquals(AigDataLevelEnum.STRICT.getCode(), decision.getEffectiveDataLevel());
        assertNotEquals("MODEL", decision.getDecision(),
            "严格级一律禁止外发，只有外部候选时不能判 MODEL：" + describe(decision));
        assertTrue(decision.getPolicyHits().stream().anyMatch(hit -> hit.contains("能力声明最低数据等级")),
            "命中明细里要能看见等级被抬过：" + describe(decision));
    }

    @Test
    @DisplayName("声明 RESTRICTED + 调用方 STRICT → 保持 STRICT（只抬不降）")
    void callerHigherLevelIsKept() {
        stubCapabilityDeclaring(AigDataLevelEnum.RESTRICTED.getCode());
        stubPolicy(AigDataLevelEnum.STRICT, "N", "N");
        stubSingleCandidate("LOCAL");

        AigRouteDecision decision = routeService.decide(CAPABILITY, AigDataLevelEnum.STRICT, (AigRouteHint) null);

        assertEquals(AigDataLevelEnum.STRICT.getCode(), decision.getEffectiveDataLevel());
        assertTrue(decision.getPolicyHits().stream().noneMatch(hit -> hit.contains("能力声明最低数据等级")),
            "没抬升就不该刷一条明细：" + describe(decision));
    }

    @Test
    @DisplayName("不声明 → 生效等级恒等于调用方传入（存量能力行为不变）")
    void undeclaredKeepsCallerLevel() {
        stubCapability();
        stubPolicy(AigDataLevelEnum.INTERNAL, "Y", "N");
        stubExternalOnlyCandidates();

        AigRouteDecision decision = routeService.decide(CAPABILITY, AigDataLevelEnum.INTERNAL, (AigRouteHint) null);

        assertEquals(AigDataLevelEnum.INTERNAL.getCode(), decision.getEffectiveDataLevel());
        assertEquals("MODEL", decision.getDecision(), describe(decision));
    }

    @Test
    @DisplayName("★ 声明值无法识别：不静默失效——保持调用方等级，但明细里明确写出来")
    void unrecognizedDeclarationIsVisible() {
        stubCapabilityDeclaring("TOPSECRET");
        stubPolicy(AigDataLevelEnum.INTERNAL, "N", "N");
        stubSingleCandidate("LOCAL");

        AigRouteDecision decision = routeService.decide(CAPABILITY, AigDataLevelEnum.INTERNAL, (AigRouteHint) null);

        assertEquals(AigDataLevelEnum.INTERNAL.getCode(), decision.getEffectiveDataLevel());
        assertTrue(decision.getPolicyHits().stream().anyMatch(hit -> hit.contains("无法识别")),
            "非法声明必须可见，否则等于这道锁没上而没人知道：" + describe(decision));
    }
}
