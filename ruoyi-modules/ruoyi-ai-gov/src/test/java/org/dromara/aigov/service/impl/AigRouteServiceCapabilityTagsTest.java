package org.dromara.aigov.service.impl;

import org.dromara.aigov.domain.AigModelGovernance;
import org.dromara.aigov.domain.vo.AigModelVo;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * 路由「能力标签匹配」行为锁定测试。
 *
 * <p><b>它守的是哪一类错误</b>：{@code aig_capability.required_tags} 此前一直被登记却从不与
 * 模型比对，于是「把纯文本模型绑到看图能力上」会被静默接受——路由判 {@code MODEL} 并成功返回，
 * 产出一份<b>没看过图</b>的结论，外形与真实结论一模一样。所以本测试的断言不只看
 * "选没选中"，还看<b>排除原因是否写明了缺哪个标签</b>：不写清原因，运维只看到一个模型
 * 莫名其妙不可用，最后只能靠猜。</p>
 *
 * <p>另一半是<b>不能误伤</b>：{@code capability_tags} 是新列，既有模型全是 NULL。
 * 若未声明就排除，一次上线会把所有既有模型同时干掉。因此未声明必须**放行并提示**，
 * 严格模式由开关显式打开——这两条都有用例。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigRouteServiceCapabilityTagsTest extends AigRouteServiceTestSupport {

    @Test
    @DisplayName("声明覆盖要求：required=IMAGE 且模型声明 IMAGE → 正常命中")
    void declaredTagCoveringRequirementPasses() {
        stubCapability("IMAGE");
        stubPolicy(AigDataLevelEnum.INTERNAL, "Y", "N");
        stubModelWithTags("IMAGE");

        AigRouteDecision decision = routeService.decide(CAPABILITY, AigDataLevelEnum.INTERNAL);

        assertEquals(AigRouteDecisionEnum.MODEL.getCode(), decision.getDecision(),
            "标签匹配却选不中，说明比对写错了方向；实际=" + describe(decision));
    }

    @Test
    @DisplayName("【核心修复】required=VISION 但模型只声明 TEXT → 必须排除，并写明缺哪个标签")
    void textModelIsExcludedFromVisionCapability() {
        stubCapability("VISION");
        stubPolicy(AigDataLevelEnum.INTERNAL, "Y", "N");
        stubModelWithTags("TEXT");

        AigRouteDecision decision = routeService.decide(CAPABILITY, AigDataLevelEnum.INTERNAL);

        assertEquals(AigRouteDecisionEnum.DENIED.getCode(), decision.getDecision(),
            "文本模型绑到看图能力上必须被拦下——这正是修复前会静默出错的场景；"
                + "实际=" + describe(decision));
        assertTrue(decision.getPolicyHits().stream().anyMatch(hit -> hit.contains("缺少 [VISION]")),
            "排除原因要写明缺哪个标签，否则运维只能猜：" + decision.getPolicyHits());
    }

    @Test
    @DisplayName("多标签要求：required=TEXT,VISION 而模型只有 TEXT → 排除（缺一即不满足）")
    void partialTagCoverageIsExcluded() {
        stubCapability("TEXT,VISION");
        stubPolicy(AigDataLevelEnum.INTERNAL, "Y", "N");
        stubModelWithTags("TEXT");

        AigRouteDecision decision = routeService.decide(CAPABILITY, AigDataLevelEnum.INTERNAL);

        assertEquals(AigRouteDecisionEnum.DENIED.getCode(), decision.getDecision(),
            "要求两个标签而只声明一个，必须排除；实际=" + describe(decision));
        assertTrue(decision.getPolicyHits().stream().anyMatch(hit -> hit.contains("缺少 [VISION]")),
            decision.getPolicyHits().toString());
    }

    @Test
    @DisplayName("未声明（新列上线时的既有模型）：必须放行，但写入可见提示——否则会一次性干掉所有既有路由")
    void undeclaredTagsPassWithVisibleNotice() {
        stubCapability("VISION");
        stubPolicy(AigDataLevelEnum.INTERNAL, "Y", "N");
        stubModelWithTags(null);

        AigRouteDecision decision = routeService.decide(CAPABILITY, AigDataLevelEnum.INTERNAL);

        assertEquals(AigRouteDecisionEnum.MODEL.getCode(), decision.getDecision(),
            "未声明不等于不支持：一律排除会让既有模型全部失效；实际=" + describe(decision));
        assertTrue(decision.getPolicyHits().stream().anyMatch(hit -> hit.contains("未声明")),
            "放行必须留痕，否则这个口子会被静默继续：" + decision.getPolicyHits());
        assertTrue(decision.getPolicyHits().stream().anyMatch(hit -> hit.contains("无法校验")),
            "提示要说清「无法校验」而不是「校验通过」：" + decision.getPolicyHits());
    }

    @Test
    @DisplayName("严格模式（aigov.route.require-model-tags=true）：未声明即排除，把口子彻底关掉")
    void strictModeExcludesUndeclared() {
        routeProperties.setRequireModelTags(true);
        stubCapability("VISION");
        stubPolicy(AigDataLevelEnum.INTERNAL, "Y", "N");
        stubModelWithTags(null);

        AigRouteDecision decision = routeService.decide(CAPABILITY, AigDataLevelEnum.INTERNAL);

        assertEquals(AigRouteDecisionEnum.DENIED.getCode(), decision.getDecision(),
            "严格模式下未声明必须排除；实际=" + describe(decision));
        assertTrue(decision.getPolicyHits().stream().anyMatch(hit -> hit.contains("严格模式")),
            decision.getPolicyHits().toString());
    }

    @Test
    @DisplayName("能力不要求标签时不产生任何标签提示（避免每行决策都刷无意义噪音）")
    void noRequirementMeansNoNoise() {
        stubCapability(null);
        stubPolicy(AigDataLevelEnum.INTERNAL, "Y", "N");
        stubModelWithTags(null);

        AigRouteDecision decision = routeService.decide(CAPABILITY, AigDataLevelEnum.INTERNAL);

        assertEquals(AigRouteDecisionEnum.MODEL.getCode(), decision.getDecision());
        assertFalse(decision.getPolicyHits().stream().anyMatch(hit -> hit.contains("未声明")),
            "能力没要求标签却提示「模型未声明」，是纯噪音：" + decision.getPolicyHits());
    }

    @Test
    @DisplayName("标签比对容错：大小写不同、中英文分隔符混用也算匹配")
    void tagComparisonIsTolerant() {
        stubCapability("image, vision");
        stubPolicy(AigDataLevelEnum.INTERNAL, "Y", "N");
        stubModelWithTags("VISION; IMAGE");

        AigRouteDecision decision = routeService.decide(CAPABILITY, AigDataLevelEnum.INTERNAL);

        assertEquals(AigRouteDecisionEnum.MODEL.getCode(), decision.getDecision(),
            "大小写与分隔符差异不该导致误排除；实际=" + describe(decision));
    }

    /**
     * 桩：候选模型声明指定的能力标签。
     *
     * @param capabilityTags 能力标签（null = 未声明）
     */
    private void stubModelWithTags(String capabilityTags) {
        when(capabilityModelMapper.selectList(any()))
            .thenReturn(List.of(binding(EXTERNAL_MODEL_ID, AigUsageTypeEnum.PRIMARY, 1)));
        AigModelVo vo = model(EXTERNAL_MODEL_ID, "vendor/model");
        vo.setModelType("CHAT");
        when(modelViewMapper.selectModelListByIds(anyList())).thenReturn(List.of(vo));
        AigModelGovernance governance = governance(EXTERNAL_MODEL_ID,
            AigDeploymentTypeEnum.EXTERNAL_API.getCode());
        governance.setCapabilityTags(capabilityTags);
        when(modelGovernanceMapper.selectList(any())).thenReturn(List.of(governance));
    }

}
