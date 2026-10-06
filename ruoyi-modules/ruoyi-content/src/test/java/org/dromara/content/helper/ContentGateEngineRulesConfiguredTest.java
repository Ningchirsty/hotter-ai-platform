package org.dromara.content.helper;

import org.dromara.content.domain.CpGateRule;
import org.dromara.content.enums.ContentTaskStatusEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「没配规则」与「校验通过」必须能区分开（R38-5 / P1-3）。
 *
 * <p>修的是什么：{@link ContentGateEngine#evaluate} 在规则为空时直接判 {@code READY}，
 * 那是"没有可校验的东西"，不是"校验通过"。两者在页面上长得一模一样，
 * 于是「这个交付类型根本没配规则」从来没人知道。生产实测：种子只给 ECOM_DETAIL 与
 * EXHIBITION 灌了 {@code cp_gate_rule}，MAIN_IMAGE / BRAND_POSTER / MANUAL / PACKAGE / VIDEO
 * 的任务因此可以无闸门直接到「可开工」。</p>
 *
 * <p>这里钉住三条：
 * <ol>
 *   <li>规则为空 → 仍然判 READY（**判定行为一个字不改**，避免把存量项目的状态改掉），
 *       但必须带 {@code rulesConfigured=false} 与一句如实的 hint；</li>
 *   <li>规则非空 → {@code rulesConfigured=true}、没有 hint；</li>
 *   <li>规则为空且存在被显式阻断的卡片 → 仍然是 PENDING_CONFIRM（"人为阻断优先"不能被这条上报盖掉）。</li>
 * </ol>
 *
 * @author content
 */
class ContentGateEngineRulesConfiguredTest {

    private final ContentGateEngine engine = new ContentGateEngine();

    private static CpGateRule rule(String fieldCode, String level) {
        CpGateRule item = new CpGateRule();
        item.setFieldCode(fieldCode);
        item.setFieldName(fieldCode);
        item.setGateLevel(level);
        return item;
    }

    @Test
    @DisplayName("规则为空：仍是 READY，但如实标出「没有规则可校验」")
    void emptyRulesStillReadyButReported() {
        ContentGateEngine.GateResult result = engine.evaluate(List.of(), Set.of(), false);

        assertEquals(ContentTaskStatusEnum.READY.getCode(), result.getStatus());
        assertFalse(result.isRulesConfigured(), "空规则不能被当成「校验通过」");
        assertTrue(result.getRulesMissingHint() != null
                && result.getRulesMissingHint().contains("没有配置任何闸门规则"),
            "必须带一句如实的说明，实际：" + result.getRulesMissingHint());
    }

    @Test
    @DisplayName("rules 传 null：同样按「没配规则」上报（不能 NPE，也不能静默）")
    void nullRulesReported() {
        ContentGateEngine.GateResult result = engine.evaluate(null, Set.of(), false);

        assertEquals(ContentTaskStatusEnum.READY.getCode(), result.getStatus());
        assertFalse(result.isRulesConfigured());
        assertTrue(result.getRulesMissingHint() != null);
    }

    @Test
    @DisplayName("规则非空：rulesConfigured=true，且没有 hint（不要让提示常驻）")
    void configuredRulesHaveNoHint() {
        List<CpGateRule> rules = List.of(rule("product_name", "BLOCK"));

        ContentGateEngine.GateResult result = engine.evaluate(rules, Set.of(), false);

        assertEquals(ContentTaskStatusEnum.PENDING_CONFIRM.getCode(), result.getStatus());
        assertTrue(result.isRulesConfigured());
        assertNull(result.getRulesMissingHint());
    }

    @Test
    @DisplayName("规则非空且全满足：READY 且 rulesConfigured=true（这才是真的「校验通过」）")
    void configuredAndSatisfied() {
        List<CpGateRule> rules = List.of(rule("product_name", "BLOCK"));

        ContentGateEngine.GateResult result = engine.evaluate(rules, Set.of("product_name"), false);

        assertEquals(ContentTaskStatusEnum.READY.getCode(), result.getStatus());
        assertTrue(result.isRulesConfigured());
        assertNull(result.getRulesMissingHint());
    }

    @Test
    @DisplayName("人为阻断优先：规则为空也要停在待确认，不能被「没配规则」盖成可开工")
    void blockedCardWinsOverEmptyRules() {
        ContentGateEngine.GateResult result = engine.evaluate(List.of(), Set.of(), true);

        assertEquals(ContentTaskStatusEnum.PENDING_CONFIRM.getCode(), result.getStatus());
        assertTrue(result.getBlockReason() != null && result.getBlockReason().contains("显式阻断"));
        assertFalse(result.isRulesConfigured());
    }
}
