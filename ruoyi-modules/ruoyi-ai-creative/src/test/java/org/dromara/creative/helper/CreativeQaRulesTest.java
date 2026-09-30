package org.dromara.creative.helper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 屏级质检规则的解析与等级（V0.2 R29，文档 §20 {@code qaRules}）。
 *
 * <p>这个类的核心不是"能解析 JSON"，而是三条口径：</p>
 * <ol>
 *   <li><b>没配就是没配</b>：空串/null/空对象/写坏 → {@link CreativeQaRules#NONE}，
 *       绝不用隐式默认值假装检查过；</li>
 *   <li><b>一项都没开 = 没配</b>（有 JSON 但所有开关都是 false 时按没配处理，这是合法意图）；</li>
 *   <li><b>等级默认值按"是不是平台客观要求"分</b>：比例/最小边/透明是 HARD，度量类是 SOFT。</li>
 * </ol>
 */
class CreativeQaRulesTest {

    private static final String MAIN_WHITE_BG_RULES = """
        {
          "schema": "screen-qa/1",
          "square": true,
          "minSide": 800,
          "alphaForbidden": true,
          "whiteBackground": {"enabled": true, "minEdgeWhiteness": 0.90},
          "subjectRatio": {"enabled": true, "min": 0.50},
          "edgeBleed": {"enabled": true, "maxRatio": 0.01}
        }
        """;

    @Test
    @DisplayName("主图白底规则解析正确（没写 levels 时按默认等级）")
    void parsesMainImageRules() {
        CreativeQaRules rules = CreativeQaRules.parse(MAIN_WHITE_BG_RULES);

        assertTrue(rules.configured());
        assertTrue(rules.square());
        assertEquals(800, rules.minSide());
        assertTrue(rules.alphaForbidden());
        assertTrue(rules.whiteBackground());
        assertEquals(0.90d, rules.minEdgeWhiteness(), 1e-6);
        assertTrue(rules.subjectRatio());
        assertEquals(0.50d, rules.subjectRatioMin(), 1e-6);
        assertTrue(rules.edgeBleed());
        assertEquals(0.01d, rules.maxBleedRatio(), 1e-6);
        // 默认等级：客观硬性项 HARD，度量项 SOFT
        assertEquals(CreativeQaRules.LEVEL_HARD, rules.levelOf("CANVAS_SQUARE"));
        assertEquals(CreativeQaRules.LEVEL_HARD, rules.levelOf("MIN_SIDE"));
        assertEquals(CreativeQaRules.LEVEL_HARD, rules.levelOf("NO_ALPHA"));
        assertEquals(CreativeQaRules.LEVEL_SOFT, rules.levelOf("WHITE_BACKGROUND"));
        assertEquals(CreativeQaRules.LEVEL_SOFT, rules.levelOf("SUBJECT_RATIO"));
    }

    @Test
    @DisplayName("levels 可以逐项覆盖（人写的等级优先于默认表）")
    void levelsCanBeOverridden() {
        CreativeQaRules rules = CreativeQaRules.parse(
            "{\"square\":true,\"levels\":{\"CANVAS_SQUARE\":\"soft\",\"WHITE_BACKGROUND\":\"HARD\"}}");

        assertTrue(rules.configured());
        assertEquals(CreativeQaRules.LEVEL_SOFT, rules.levelOf("CANVAS_SQUARE"), "写了小写也应认");
        assertEquals(CreativeQaRules.LEVEL_HARD, rules.levelOf("WHITE_BACKGROUND"));
        // 没在 levels 里写的项仍走默认表
        assertEquals(CreativeQaRules.LEVEL_HARD, rules.levelOf("MIN_SIDE"));
    }

    @Test
    @DisplayName("没配 / 空 / 写坏 / 一项都没开 → 都是「没配」（不伪造检查通过）")
    void unconfiguredIsHonest() {
        assertFalse(CreativeQaRules.parse(null).configured());
        assertFalse(CreativeQaRules.parse("").configured());
        assertFalse(CreativeQaRules.parse("   ").configured());
        assertFalse(CreativeQaRules.parse("not-json").configured());
        assertFalse(CreativeQaRules.parse("[1,2,3]").configured(), "不是对象就当没配");
        assertFalse(CreativeQaRules.parse("{\"schema\":\"screen-qa/1\"}").configured(),
            "有 JSON 但一项开关都没开 = 没配（合法意图，不是错误）");
        assertFalse(CreativeQaRules.parse("{\"whiteBackground\":{\"enabled\":false}}").configured());
        assertEquals(CreativeQaRules.NONE, CreativeQaRules.parse(null));
    }

    @Test
    @DisplayName("规则文本/对象两种写法都能读（模块库里存的是文本，屏上可能烙成对象）")
    void parsesFromScreenSpecBothShapes() {
        // 文本形态
        CreativeScreenModuleConfig asText = CreativeScreenModuleConfig.parse(
            "{\"moduleCode\":\"MAIN_WHITE_BG\",\"qaRules\":\"{\\\"square\\\":true,\\\"minSide\\\":800}\"}");
        assertEquals(800, CreativeQaRules.parse(asText.qaRules()).minSide());
        // 对象形态（被 Jackson 直接序列化成对象）
        CreativeScreenModuleConfig asObject = CreativeScreenModuleConfig.parse(
            "{\"moduleCode\":\"MAIN_WHITE_BG\",\"qaRules\":{\"square\":true,\"minSide\":800}}");
        assertEquals(800, CreativeQaRules.parse(asObject.qaRules()).minSide());
        // 没烙 → 没配
        assertFalse(CreativeQaRules.parse(
            CreativeScreenModuleConfig.parse("{\"moduleCode\":\"HERO\"}").qaRules()).configured());
        assertEquals(Map.of(), CreativeQaRules.NONE.levels());
    }
}
