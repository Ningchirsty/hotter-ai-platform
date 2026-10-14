package org.dromara.aigov.task.service;

import org.dromara.aigov.execution.AigFlowNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 条件结论来源测试（设计 §6.5）。
 *
 * <p>这一条最容易写错的方向是"把取不到结论当成 false"——它不会报错，只会静默地
 * <b>跳过一个本该跑的节点</b>。所以这里逐条钉住：缺失、字符串 {@code "true"}、
 * 快照不是对象、无引用键，全都必须"没有结论"。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigFlowConditionsTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static AigFlowNode condition(String ref) {
        return new AigFlowNode("CONDITION", ref, ref);
    }

    private static AigFlowNode step(String ref) {
        return new AigFlowNode("STEP", ref, ref);
    }

    @Test
    @DisplayName("★ 快照里的布尔字段给出结论（true / false 都算结论）")
    void booleanFieldsGiveResults() {
        Map<String, Boolean> results = AigFlowConditions.fromSnapshotJson(
            "{\"needVideo\":true,\"needVoice\":false}",
            List.of(condition("needVideo"), condition("needVoice")), MAPPER);

        assertEquals(2, results.size());
        assertTrue(results.get("needVideo"));
        assertFalse(results.get("needVoice"));
    }

    @Test
    @DisplayName("★ 字段缺失 → 没有结论（不是 false：那会静默跳过本该跑的节点）")
    void missingFieldHasNoResult() {
        Map<String, Boolean> results = AigFlowConditions.fromSnapshotJson(
            "{\"other\":true}", List.of(condition("needVideo")), MAPPER);

        assertTrue(results.isEmpty());
        assertFalse(results.containsKey("needVideo"));
    }

    @Test
    @DisplayName("★ 字符串 \"true\" 不算结论（不猜：把字符串当布尔是另一种静默错判）")
    void stringValueHasNoResult() {
        Map<String, Boolean> results = AigFlowConditions.fromSnapshotJson(
            "{\"needVideo\":\"true\"}", List.of(condition("needVideo")), MAPPER);

        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("数字 / null / 对象都不算结论")
    void nonBooleanValuesHaveNoResult() {
        assertTrue(AigFlowConditions.fromSnapshotJson(
            "{\"a\":1}", List.of(condition("a")), MAPPER).isEmpty());
        assertTrue(AigFlowConditions.fromSnapshotJson(
            "{\"a\":null}", List.of(condition("a")), MAPPER).isEmpty());
        assertTrue(AigFlowConditions.fromSnapshotJson(
            "{\"a\":{\"b\":true}}", List.of(condition("a")), MAPPER).isEmpty());
    }

    @Test
    @DisplayName("★ 快照不是对象 / 不可解析 / 为空 → 一律没有结论（fail-closed）")
    void unusableSnapshotYieldsNoResult() {
        assertTrue(AigFlowConditions.fromSnapshotJson(
            "[1,2,3]", List.of(condition("a")), MAPPER).isEmpty());
        assertTrue(AigFlowConditions.fromSnapshotJson(
            "not-json-at-all", List.of(condition("a")), MAPPER).isEmpty());
        assertTrue(AigFlowConditions.fromSnapshotJson(
            "  ", List.of(condition("a")), MAPPER).isEmpty());
        assertTrue(AigFlowConditions.fromSnapshotJson(
            null, List.of(condition("a")), MAPPER).isEmpty());
    }

    @Test
    @DisplayName("★ 只查条件节点：没有条件节点时连快照都不解析（坏快照不该刷告警）")
    void onlyConditionNodesAreConsulted() {
        Map<String, Boolean> results = AigFlowConditions.fromSnapshotJson(
            "not-json", List.of(step("needVideo")), MAPPER);

        assertTrue(results.isEmpty());
        assertTrue(AigFlowConditions.fromSnapshotJson(
            "{\"a\":true}", List.of(), MAPPER).isEmpty());
        assertTrue(AigFlowConditions.fromSnapshotJson(
            "{\"a\":true}", null, MAPPER).isEmpty());
        assertTrue(AigFlowConditions.fromSnapshotJson(
            "{\"a\":true}", List.of(condition("a")), null).isEmpty());
    }

    @Test
    @DisplayName("★ 引用键为空的条件节点取不到结论（不知道它看哪个字段，就不猜）")
    void blankRefHasNoResult() {
        assertTrue(AigFlowConditions.fromSnapshotJson(
            "{\"a\":true}", List.of(condition(null)), MAPPER).isEmpty());
        assertTrue(AigFlowConditions.fromSnapshotJson(
            "{\"a\":true}", List.of(condition("   ")), MAPPER).isEmpty());
    }

    @Test
    @DisplayName("引用键两端空白按去空白后查（配置里多打了一个空格不该让流程卡住）")
    void refIsTrimmed() {
        Map<String, Boolean> results = AigFlowConditions.fromSnapshotJson(
            "{\"needVideo\":true}", List.of(condition(" needVideo ")), MAPPER);

        assertEquals(Boolean.TRUE, results.get("needVideo"));
    }

    @Test
    @DisplayName("只有取到结论的键会出现在结果里（其余保持「等结论」）")
    void partialResults() {
        Map<String, Boolean> results = AigFlowConditions.fromSnapshotJson(
            "{\"a\":true,\"b\":\"x\"}",
            List.of(condition("a"), condition("b"), condition("c")), MAPPER);

        assertEquals(1, results.size());
        assertEquals(Boolean.TRUE, results.get("a"));
    }

}
