package org.dromara.aigov.agent.evaluation;

import org.dromara.aigov.agent.domain.AigEvaluationCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 判据求值器测试（设计 §13.2）。
 *
 * <p>要钉住的三条：</p>
 * <ol>
 *     <li><b>「没判据」与「判据坏了」必须分开</b>：前者走人工 Rubric（合法），后者判不通过。
 *         否则一条写坏的判据会因为"读不出来"被当成"没有判据"从而静默通过；</li>
 *     <li><b>未知判据运行期也不能放过</b>：定义期拦得住新数据，拦不住库里已有的手工数据；</li>
 *     <li><b>成本算不出 ≠ 成本为 0</b>：声明了成本范围的用例，未上报成本判不通过。</li>
 * </ol>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigExpectedRuleCheckerTest {

    private AigExpectedRuleChecker checker;

    @BeforeEach
    void setUp() {
        checker = new AigExpectedRuleChecker(JsonMapper.builder().build());
    }

    /**
     * 造一个用例。
     *
     * @param expectedJson 机器判据
     * @param rubricJson   人工 Rubric
     * @return 用例
     */
    private static AigEvaluationCase evaluationCase(String expectedJson, String rubricJson) {
        AigEvaluationCase entity = new AigEvaluationCase();
        entity.setCaseCode("case-probe");
        entity.setCaseName("probe");
        entity.setExpectedJson(expectedJson);
        entity.setRubricJson(rubricJson);
        return entity;
    }

    /**
     * 造一个执行结果。
     *
     * @param outputJson 产出
     * @return 结果
     */
    private static AigEvaluationOutcome outcome(String outputJson) {
        return new AigEvaluationOutcome(outputJson, false, null, 12L, 3L, "model-x", false, "trace-x");
    }

    /**
     * 造一个带成本上报的执行结果。
     *
     * @param outputJson 产出
     * @param cost       成本
     * @return 结果
     */
    private static AigEvaluationOutcome outcomeWithCost(String outputJson, String cost) {
        return new AigEvaluationOutcome(outputJson, true, new BigDecimal(cost), 12L, 3L, "model-x",
            false, "trace-x");
    }

    @Test
    @DisplayName("没有机器判据 = 合法形态（走人工 Rubric），不是「不通过」")
    void rubricOnlyCaseIsNotMachineCheckable() {
        AigExpectedRuleCheck check = checker.check(evaluationCase(null, "{\"dimensions\":[]}"),
            outcome("{\"a\":1}"));

        assertFalse(check.machineCheckable());
        assertTrue(check.passed(), "没有机器判据时机器判据全部通过（结论交给人工）");
        assertEquals("RUBRIC", check.detail().get("mode"));
    }

    @Test
    @DisplayName("判据坏了 ≠ 没有判据：读不出来就判不通过，绝不静默通过")
    void brokenRulesAreNotTreatedAsRubric() {
        AigExpectedRuleCheck check = checker.check(evaluationCase("{oops", null), outcome("{\"a\":1}"));

        assertTrue(check.machineCheckable(), "声明了判据就必须按机器判据处理");
        assertFalse(check.passed(), "判据读不出来不能当作「无判据」而通过");
        assertTrue(check.failures().get(0).contains("不是合法 JSON"), check.failures().toString());
    }

    @Test
    @DisplayName("空判据对象：不是「无条件通过」，而是这条用例没写清楚")
    void emptyRulesObjectFails() {
        AigExpectedRuleCheck check = checker.check(evaluationCase("{}", null), outcome("{\"a\":1}"));

        assertFalse(check.passed());
        assertTrue(check.failures().get(0).contains("空对象"), check.failures().toString());
    }

    @Test
    @DisplayName("定义期校验：空对象/未知判据/类型写错/负数下限都在定义时拒绝")
    void validatesRuleShapeAtDefineTime() {
        assertTrue(checker.validateExpectedJson(null).isEmpty(), "留空是合法的（走 Rubric）");
        assertTrue(checker.validateExpectedJson("  ").isEmpty());
        assertTrue(checker.validateExpectedJson("{\"required_paths\":[\"a.b\"]}").isEmpty());

        assertEquals(1, checker.validateExpectedJson("{oops").size());
        assertEquals(1, checker.validateExpectedJson("{}").size());
        assertEquals(1, checker.validateExpectedJson("[1,2]").size());
        assertTrue(checker.validateExpectedJson("{\"nope\":1}").get(0).contains("未知判据"));
        assertTrue(checker.validateExpectedJson("{\"required_paths\":\"a\"}").get(0)
            .contains("非空字符串数组"));
        assertTrue(checker.validateExpectedJson("{\"required_paths\":[\"  \"]}").get(0)
            .contains("空字符串"));
        assertTrue(checker.validateExpectedJson("{\"equals\":{}}").get(0).contains("非空对象"));
        assertTrue(checker.validateExpectedJson("{\"min_items\":{\"a\":-1}}").get(0)
            .contains("非负整数"));
        assertTrue(checker.validateExpectedJson("{\"min_items\":{\"a\":2}}").isEmpty());
    }

    @Test
    @DisplayName("五条判据全过则通过，且明细里每条都记 OK（一眼看出哪条没过）")
    void passesWhenEveryRuleHolds() {
        String expected = "{\"required_paths\":[\"plan.title\",\"plan.days[0].text\"],"
            + "\"equals\":{\"plan.locale\":\"zh-CN\",\"plan.count\":2},"
            + "\"min_items\":{\"plan.days\":1},"
            + "\"must_contain\":[\"标题\"],\"forbidden_contains\":[\"违禁\"]}";
        String actual = "{\"plan\":{\"title\":\"标题\",\"locale\":\"zh-CN\",\"count\":2.0,"
            + "\"days\":[{\"text\":\"第一天\"}]}}";

        AigExpectedRuleCheck check = checker.check(evaluationCase(expected, null), outcome(actual));

        assertTrue(check.passed(), check.failures().toString());
        assertTrue(check.machineCheckable());
        @SuppressWarnings("unchecked")
        Map<String, Object> checks = (Map<String, Object>) check.detail().get("checks");
        assertEquals("OK", checks.get(AigExpectedRuleChecker.RULE_REQUIRED_PATHS));
        assertEquals("OK", checks.get(AigExpectedRuleChecker.RULE_EQUALS));
        assertEquals("OK", checks.get(AigExpectedRuleChecker.RULE_MIN_ITEMS));
        assertEquals("OK", checks.get(AigExpectedRuleChecker.RULE_MUST_CONTAIN));
        assertEquals("OK", checks.get(AigExpectedRuleChecker.RULE_FORBIDDEN_CONTAINS));
        assertTrue(check.failureSummary(500) == null, "全过时不该有失败摘要");
    }

    @Test
    @DisplayName("必填路径缺失、存在但为空，都要逐条报出（带判据名前缀）")
    void reportsRequiredPaths() {
        String expected = "{\"required_paths\":[\"plan.title\",\"plan.body\"]}";

        AigExpectedRuleCheck check = checker.check(evaluationCase(expected, null),
            outcome("{\"plan\":{\"title\":\"  \",\"other\":1}}"));

        assertFalse(check.passed());
        assertEquals(2, check.failures().size(), check.failures().toString());
        assertTrue(check.failures().get(0).startsWith("[required_paths]"), check.failures().toString());
        assertTrue(check.failures().get(0).contains("plan.title 存在但为空"), check.failures().toString());
        assertTrue(check.failures().get(1).contains("plan.body 缺失"), check.failures().toString());
    }

    @Test
    @DisplayName("equals 数字按数值比较（1 与 1.0 是同一个值），不等时两侧都回显")
    void equalsComparesNumerically() {
        assertTrue(checker.check(evaluationCase("{\"equals\":{\"n\":1}}", null),
            outcome("{\"n\":1.0}")).passed());
        AigExpectedRuleCheck check = checker.check(evaluationCase("{\"equals\":{\"n\":1}}", null),
            outcome("{\"n\":2}"));
        assertFalse(check.passed());
        assertTrue(check.failures().get(0).contains("实际 2"), check.failures().toString());
        assertTrue(check.failures().get(0).contains("期望 1"), check.failures().toString());
    }

    @Test
    @DisplayName("数组下标路径 a.b[1].c 能取到；min_items 不足时报实际条数")
    void resolvesArrayIndexAndMinItems() {
        assertTrue(checker.check(evaluationCase(
            "{\"required_paths\":[\"plan.days[1].text\"]}", null),
            outcome("{\"plan\":{\"days\":[{\"text\":\"a\"},{\"text\":\"b\"}]}}")).passed());

        AigExpectedRuleCheck check = checker.check(
            evaluationCase("{\"min_items\":{\"plan.days\":3}}", null),
            outcome("{\"plan\":{\"days\":[1,2]}}"));
        assertFalse(check.passed());
        assertTrue(check.failures().get(0).contains("只有 2 项"), check.failures().toString());
    }

    @Test
    @DisplayName("禁止内容出现即不通过；必须包含的片段缺失也不通过")
    void checksTextFragments() {
        AigExpectedRuleCheck forbidden = checker.check(
            evaluationCase("{\"forbidden_contains\":[\"内部代号\"]}", null),
            outcome("{\"text\":\"这里提到了内部代号\"}"));
        assertFalse(forbidden.passed());
        assertTrue(forbidden.failures().get(0).contains("禁止内容"), forbidden.failures().toString());

        AigExpectedRuleCheck missing = checker.check(
            evaluationCase("{\"must_contain\":[\"保证\"]}", null), outcome("{\"text\":\"别的\"}"));
        assertFalse(missing.passed());
        assertTrue(missing.failures().get(0).contains("未包含"), missing.failures().toString());
    }

    @Test
    @DisplayName("产出不是合法 JSON 时：报一次「无法按路径校验」，而不是把每条路径都报成缺失")
    void nonJsonOutputDoesNotSpamMissingPaths() {
        AigExpectedRuleCheck check = checker.check(
            evaluationCase("{\"required_paths\":[\"a\",\"b\",\"c\"]}", null), outcome("not json"));

        assertFalse(check.passed());
        assertEquals(1, check.failures().size(), "三条路径缺失是同一个根因，报三次会误导：" + check.failures());
        assertTrue(check.failures().get(0).contains("不是合法 JSON"), check.failures().toString());
    }

    @Test
    @DisplayName("产出不是 JSON 但只声明了文本判据时，仍按原文判定（不必强行 JSON）")
    void textRulesWorkOnPlainText() {
        AigExpectedRuleCheck check = checker.check(
            evaluationCase("{\"must_contain\":[\"abc\"]}", null), outcome("xxabcxx"));

        assertTrue(check.passed(), check.failures().toString());
    }

    @Test
    @DisplayName("未知判据在运行期也判不通过（手工插入库里的判据不能静默忽略）")
    void unknownRuleFailsAtRuntime() {
        AigExpectedRuleCheck check = checker.check(
            evaluationCase("{\"required_paths\":[\"a\"],\"weird_rule\":1}", null), outcome("{\"a\":1}"));

        assertFalse(check.passed(), "未知判据必须让这条用例判不通过");
        assertTrue(check.failures().get(0).contains("weird_rule"), check.failures().toString());
    }

    @Test
    @DisplayName("成本范围：未上报成本判不通过（未知不等于在范围内）")
    void unknownCostFailsWhenRangeDeclared() {
        AigEvaluationCase entity = evaluationCase("{\"required_paths\":[\"a\"]}", null);
        entity.setCostMin(new BigDecimal("0.5"));
        entity.setCostMax(new BigDecimal("2.0"));

        AigExpectedRuleCheck check = checker.check(entity, outcome("{\"a\":1}"));

        assertFalse(check.passed());
        assertTrue(check.failures().get(0).contains("成本无法判定"), check.failures().toString());
        @SuppressWarnings("unchecked")
        Map<String, Object> cost = (Map<String, Object>) check.detail().get("cost");
        assertEquals(false, cost.get("known"));
        assertEquals("UNKNOWN", cost.get("withinRange"));
    }

    @Test
    @DisplayName("成本范围：超上限、低于下限都不通过；范围内通过")
    void costRangeJudgedWhenKnown() {
        AigEvaluationCase entity = evaluationCase("{\"required_paths\":[\"a\"]}", null);
        entity.setCostMin(new BigDecimal("0.5"));
        entity.setCostMax(new BigDecimal("2.0"));

        assertTrue(checker.check(entity, outcomeWithCost("{\"a\":1}", "1.25")).passed());
        assertFalse(checker.check(entity, outcomeWithCost("{\"a\":1}", "2.01")).passed());
        assertFalse(checker.check(entity, outcomeWithCost("{\"a\":1}", "0.4")).passed());

        // 没声明范围就不判成本（哪怕算不出来）
        assertTrue(checker.check(evaluationCase("{\"required_paths\":[\"a\"]}", null),
            outcome("{\"a\":1}")).passed());
    }

    @Test
    @DisplayName("失败摘要有长度上限（落 remark 用），并说明还有多少项")
    void failureSummaryIsBounded() {
        StringBuilder paths = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            if (i > 0) {
                paths.append(',');
            }
            paths.append("\"plan.field").append(i).append('"');
        }
        AigExpectedRuleCheck check = checker.check(
            evaluationCase("{\"required_paths\":[" + paths + "]}", null), outcome("{}"));

        String summary = check.failureSummary(80);
        assertTrue(summary.length() <= 80, "摘要超长：" + summary.length());
        assertTrue(summary.contains("等 12 项不通过"), summary);
    }

}
