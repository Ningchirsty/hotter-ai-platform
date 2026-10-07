package org.dromara.aigov.agent.evaluation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.agent.domain.AigEvaluationCase;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 黄金用例判据求值器（设计 §13.2）。
 *
 * <p><b>判据是白名单的，和 Manifest 校验同一个理由</b>：平台不认识的判据<b>既不忽略也不放过</b>，
 * 而是当当当作不通过（定义期直接拒绝）。静默忽略一条判据会让用例看起来比实际更严——
 * 而实际上那条判据从未被执行过；反过来静默放过则等于用例形同虚设。</p>
 *
 * <p><b>判据集刻意很小</b>（{@code required_paths / equals / min_items / must_contain /
 * forbidden_contains}）。可判定的事情才写成判据，判不了的写进 {@code rubric_json} 交人工——
 * 与其发明一门"评分脚本语言"，不如明确「这台机器只能判定这五件事」。</p>
 *
 * <p><b>不做加权总分</b>：规则型判据是「全部通过才算通过」的与关系，把它压成一个百分数
 * （例如 4/5=80 分）会让人以为 80 分"还不错"，而平台只认全过。因此机器判据模式下
 * {@code total_score} 留空、明细进 {@code score_json}（{@code aig_evaluation_run} 的列注释：
 * 「算不出留空，禁止填 0 冒充」）。总分只由人工 Rubric 给出。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AigExpectedRuleChecker {

    /**
     * 判据：输出里必须存在且非空的路径（点号分隔，数组下标写 {@code a.b[0].c}）
     */
    public static final String RULE_REQUIRED_PATHS = "required_paths";

    /**
     * 判据：路径 → 期望值（字符串/数字/布尔）
     */
    public static final String RULE_EQUALS = "equals";

    /**
     * 判据：路径 → 最少元素个数（用于「至少给出 N 个方案」这类）
     */
    public static final String RULE_MIN_ITEMS = "min_items";

    /**
     * 判据：输出原文必须包含的片段
     */
    public static final String RULE_MUST_CONTAIN = "must_contain";

    /**
     * 判据：输出原文不得包含的片段（用于「不得出现违禁词」这类）
     */
    public static final String RULE_FORBIDDEN_CONTAINS = "forbidden_contains";

    /**
     * 认识的判据集合（有序，便于报错时稳定输出）
     */
    private static final Set<String> RULE_KEYS = new LinkedHashSet<>(List.of(
        RULE_REQUIRED_PATHS, RULE_EQUALS, RULE_MIN_ITEMS, RULE_MUST_CONTAIN, RULE_FORBIDDEN_CONTAINS));

    /**
     * 失败明细里回显输出原文的上限（避免把整份产出塞进 score_json）
     */
    private static final int OUTPUT_EXCERPT = 160;

    private final JsonMapper jsonMapper;

    /**
     * 定义期校验：判据写法是否可执行。
     *
     * <p>在<b>定义用例时</b>就拒绝，而不是等到跑评测时才炸：一条判据写错的用例如果被当成
     * 「跑不过」，改的会是 Prompt，而真正错的是用例。</p>
     *
     * @param expectedJson 判据 JSON（可空 = 该用例只有人工 Rubric）
     * @return 问题清单；空表示可执行
     */
    public List<String> validateExpectedJson(String expectedJson) {
        List<String> problems = new ArrayList<>();
        if (StringUtils.isBlank(expectedJson)) {
            return problems;
        }
        JsonNode node = parseQuietly(expectedJson);
        if (node == null) {
            problems.add("expected_json 不是合法 JSON");
            return problems;
        }
        if (!node.isObject()) {
            problems.add("expected_json 必须是 JSON 对象（判据集合）");
            return problems;
        }
        if (node.isEmpty()) {
            problems.add("expected_json 是空对象：没有声明任何判据。"
                + "要么补上判据，要么留空并改由 rubric_json 交人工复核——"
                + "空判据不是「无条件通过」，而是「这条用例没写清楚」");
            return problems;
        }
        for (Map.Entry<String, JsonNode> entry : node.properties()) {
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            if (!RULE_KEYS.contains(key)) {
                problems.add("未知判据：" + key + "（可选 " + String.join("/", RULE_KEYS) + "）");
                continue;
            }
            if (RULE_REQUIRED_PATHS.equals(key) || RULE_MUST_CONTAIN.equals(key)
                || RULE_FORBIDDEN_CONTAINS.equals(key)) {
                if (!value.isArray() || value.isEmpty()) {
                    problems.add(key + " 必须是非空字符串数组");
                } else {
                    for (JsonNode item : value) {
                        if (!item.isString() || StringUtils.isBlank(item.stringValue())) {
                            problems.add(key + " 里存在非字符串或空字符串元素");
                            break;
                        }
                    }
                }
            } else if (RULE_EQUALS.equals(key)) {
                if (!value.isObject() || value.isEmpty()) {
                    problems.add("equals 必须是非空对象（路径 → 期望值）");
                }
            } else if (RULE_MIN_ITEMS.equals(key)) {
                if (!value.isObject() || value.isEmpty()) {
                    problems.add("min_items 必须是非空对象（路径 → 最少条数）");
                } else {
                    for (Map.Entry<String, JsonNode> item : value.properties()) {
                        if (!item.getValue().isNumber() || item.getValue().asInt() < 0) {
                            problems.add("min_items 的 " + item.getKey() + " 必须是非负整数");
                        }
                    }
                }
            }
        }
        return problems;
    }

    /**
     * 运行期求值。
     *
     * @param evaluationCase 用例（判据 + 成本范围）
     * @param outcome        执行结果
     * @return 求值结论
     */
    public AigExpectedRuleCheck check(AigEvaluationCase evaluationCase, AigEvaluationOutcome outcome) {
        Map<String, Object> detail = new LinkedHashMap<>();
        List<String> failures = new ArrayList<>();

        // 「没判据」与「判据坏了」是两件事，不能合并：前者是合法形态（走人工 Rubric），
        // 后者是这条用例根本不可执行，必须判不通过——否则一条写坏的判据会因为「读不出来」
        // 而被当成「没有判据」，从而静默通过。
        if (StringUtils.isBlank(evaluationCase.getExpectedJson())) {
            detail.put("mode", "RUBRIC");
            detail.put("note", "用例未声明机器判据，结论由人工复核（review_result）给出");
            return new AigExpectedRuleCheck(false, true, failures, detail);
        }
        JsonNode expected = parseQuietly(evaluationCase.getExpectedJson());
        if (expected == null || !expected.isObject()) {
            return unusableRule(failures, "判据不是合法 JSON 对象，平台读不出来，无法执行");
        }
        if (expected.isEmpty()) {
            return unusableRule(failures, "判据是空对象：没有声明任何判据，"
                + "这不是「无条件通过」，而是这条用例没写清楚");
        }

        String actualText = outcome == null || outcome.outputJson() == null ? "" : outcome.outputJson();
        JsonNode actual = parseQuietly(actualText);
        boolean hasPathRules = expected.has(RULE_REQUIRED_PATHS) || expected.has(RULE_EQUALS)
            || expected.has(RULE_MIN_ITEMS);
        Map<String, Object> checks = new LinkedHashMap<>();

        if (hasPathRules && actual == null) {
            String problem = "实际输出不是合法 JSON，无法按路径校验判据（输出前 " + OUTPUT_EXCERPT
                + " 字：" + StringUtils.substring(actualText, 0, OUTPUT_EXCERPT) + "）";
            failures.add(problem);
            checks.put("actual_json", problem);
        } else {
            putRule(checks, RULE_REQUIRED_PATHS, evalRequiredPaths(expected, actual, failures));
            putRule(checks, RULE_EQUALS, evalEquals(expected, actual, failures));
            putRule(checks, RULE_MIN_ITEMS, evalMinItems(expected, actual, failures));
        }
        putRule(checks, RULE_MUST_CONTAIN,
            evalContains(expected, RULE_MUST_CONTAIN, actualText, failures));
        putRule(checks, RULE_FORBIDDEN_CONTAINS,
            evalContains(expected, RULE_FORBIDDEN_CONTAINS, actualText, failures));

        // 未知判据在定义期就被拒了，但库里可能有手工插入/历史遗留的判据：
        // 运行期若静默忽略它，这条用例会比它看起来更松
        List<String> unknownRules = new ArrayList<>();
        for (Map.Entry<String, JsonNode> entry : expected.properties()) {
            if (!RULE_KEYS.contains(entry.getKey())) {
                unknownRules.add(entry.getKey());
            }
        }
        if (!unknownRules.isEmpty()) {
            String problem = "未知判据：" + String.join("、", unknownRules)
                + "（平台认识的判据是 " + String.join("/", RULE_KEYS) + "）";
            failures.add("[expected_json] " + problem);
            checks.put("expected_json", problem);
        }

        detail.put("mode", "RULE");
        detail.put("checks", checks);
        detail.put("cost", evalCost(evaluationCase, outcome, failures));
        detail.put("failureCount", failures.size());
        detail.put("passed", failures.isEmpty());
        if (!failures.isEmpty()) {
            detail.put("failures", failures);
        }
        return new AigExpectedRuleCheck(true, failures.isEmpty(), failures, detail);
    }

    /**
     * 判据不可执行（读不出来/空对象）时的结论：判不通过，并说明这是判据自己的问题。
     *
     * @param failures 失败收集器
     * @param reason   原因
     * @return 结论
     */
    private AigExpectedRuleCheck unusableRule(List<String> failures, String reason) {
        String message = "[expected_json] " + reason;
        failures.add(message);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("mode", "RULE");
        detail.put("checks", Map.of("expected_json", reason));
        detail.put("cost", Map.of("rangeDeclared", false));
        detail.put("failureCount", failures.size());
        detail.put("passed", false);
        detail.put("failures", failures);
        return new AigExpectedRuleCheck(true, false, failures, detail);
    }

    /**
     * required_paths：每条路径必须存在且非空。
     *
     * @param expected 判据
     * @param actual   实际输出（可空）
     * @param failures 失败收集器
     * @return 该判据的明细（OK 或逐条失败）
     */
    private List<String> evalRequiredPaths(JsonNode expected, JsonNode actual, List<String> failures) {
        List<String> ruleFailures = new ArrayList<>();
        for (JsonNode item : arrayOf(expected, RULE_REQUIRED_PATHS)) {
            String path = item.stringValue();
            JsonNode value = actual == null ? null : resolve(actual, path);
            if (value == null || value.isMissingNode() || value.isNull()) {
                ruleFailures.add(path + " 缺失");
            } else if (value.isString() && StringUtils.isBlank(value.stringValue())) {
                ruleFailures.add(path + " 存在但为空");
            }
        }
        collect(RULE_REQUIRED_PATHS, ruleFailures, failures);
        return ruleFailures;
    }

    /**
     * equals：路径上的值必须等于期望值。
     *
     * @param expected 判据
     * @param actual   实际输出（可空）
     * @param failures 失败收集器
     * @return 该判据的明细
     */
    private List<String> evalEquals(JsonNode expected, JsonNode actual, List<String> failures) {
        List<String> ruleFailures = new ArrayList<>();
        JsonNode rules = expected.get(RULE_EQUALS);
        if (rules != null && rules.isObject()) {
            for (Map.Entry<String, JsonNode> rule : rules.properties()) {
                String path = rule.getKey();
                JsonNode want = rule.getValue();
                JsonNode got = actual == null ? null : resolve(actual, path);
                if (got == null || got.isMissingNode() || got.isNull()) {
                    ruleFailures.add(path + " 缺失（期望 " + text(want) + "）");
                } else if (!sameValue(want, got)) {
                    ruleFailures.add(path + " 实际 " + text(got) + "，期望 " + text(want));
                }
            }
        }
        collect(RULE_EQUALS, ruleFailures, failures);
        return ruleFailures;
    }

    /**
     * min_items：路径上的数组元素个数下限。
     *
     * @param expected 判据
     * @param actual   实际输出（可空）
     * @param failures 失败收集器
     * @return 该判据的明细
     */
    private List<String> evalMinItems(JsonNode expected, JsonNode actual, List<String> failures) {
        List<String> ruleFailures = new ArrayList<>();
        JsonNode rules = expected.get(RULE_MIN_ITEMS);
        if (rules != null && rules.isObject()) {
            for (Map.Entry<String, JsonNode> rule : rules.properties()) {
                String path = rule.getKey();
                int min = rule.getValue().asInt();
                JsonNode got = actual == null ? null : resolve(actual, path);
                if (got == null || !got.isArray()) {
                    ruleFailures.add(path + " 不是数组（期望至少 " + min + " 项）");
                } else if (got.size() < min) {
                    ruleFailures.add(path + " 只有 " + got.size() + " 项，少于 " + min + " 项");
                }
            }
        }
        collect(RULE_MIN_ITEMS, ruleFailures, failures);
        return ruleFailures;
    }

    /**
     * must_contain / forbidden_contains：按原文片段判定（不要求输出是 JSON）。
     *
     * @param expected 判据
     * @param rule     判据名
     * @param actual   实际输出原文
     * @param failures 失败收集器
     * @return 该判据的明细
     */
    private List<String> evalContains(JsonNode expected, String rule, String actual,
                                     List<String> failures) {
        List<String> ruleFailures = new ArrayList<>();
        for (JsonNode item : arrayOf(expected, rule)) {
            String fragment = item.stringValue();
            boolean present = actual.contains(fragment);
            if (RULE_MUST_CONTAIN.equals(rule) && !present) {
                ruleFailures.add("输出未包含「" + fragment + "」");
            } else if (RULE_FORBIDDEN_CONTAINS.equals(rule) && present) {
                ruleFailures.add("输出出现了禁止内容「" + fragment + "」");
            }
        }
        collect(rule, ruleFailures, failures);
        return ruleFailures;
    }

    /**
     * 把某条判据的结果记进明细。
     *
     * <p>没失败的判据也记 {@code OK}：让 {@code score_json} 里五条判据都出现，
     * 一眼看得出「哪条没过」，而不是从「哪条没出现」去反推。</p>
     *
     * @param checks       明细
     * @param rule         判据名
     * @param ruleFailures 该判据的失败
     */
    private static void putRule(Map<String, Object> checks, String rule, List<String> ruleFailures) {
        checks.put(rule, ruleFailures.isEmpty() ? "OK" : ruleFailures);
    }

    /**
     * 成本范围判定（§13.2「黄金用例通过（含成本范围）」）。
     *
     * <p>用例没声明 {@code cost_max} 就不判成本——但一旦声明了范围，未上报成本<b>不能</b>
     * 当作「在范围内」：那是拿未知当合规。</p>
     *
     * @param evaluationCase 用例
     * @param outcome        执行结果
     * @param failures       失败收集器
     * @return 成本明细
     */
    private Map<String, Object> evalCost(AigEvaluationCase evaluationCase, AigEvaluationOutcome outcome,
                                        List<String> failures) {
        Map<String, Object> cost = new LinkedHashMap<>();
        BigDecimal max = evaluationCase.getCostMax();
        BigDecimal min = evaluationCase.getCostMin();
        boolean known = outcome != null && outcome.costKnown();
        cost.put("known", known);
        if (max == null) {
            cost.put("rangeDeclared", false);
            return cost;
        }
        cost.put("rangeDeclared", true);
        cost.put("max", max);
        if (min != null) {
            cost.put("min", min);
        }
        if (!known) {
            cost.put("withinRange", "UNKNOWN");
            failures.add("成本无法判定：本次执行未上报成本，因此无法证明落在用例范围 ["
                + (min == null ? "-" : min) + ", " + max + "] 内（成本算不出应留空，不得用 0 冒充）");
            return cost;
        }
        BigDecimal actualCost = outcome.costAmount() == null ? BigDecimal.ZERO : outcome.costAmount();
        cost.put("actual", actualCost);
        boolean within = actualCost.compareTo(max) <= 0
            && (min == null || actualCost.compareTo(min) >= 0);
        cost.put("withinRange", within);
        if (!within) {
            failures.add("成本 " + actualCost.toPlainString() + " 超出用例声明的范围 ["
                + (min == null ? "-" : min.toPlainString()) + ", " + max.toPlainString() + "]");
        }
        return cost;
    }

    /**
     * 把某条判据的失败合并进总失败清单（带判据名前缀，便于一眼看出是哪条判据没过）。
     *
     * @param rule          判据名
     * @param ruleFailures  该判据的失败
     * @param failures      总失败清单
     */
    private void collect(String rule, List<String> ruleFailures, List<String> failures) {
        for (String failure : ruleFailures) {
            failures.add("[" + rule + "] " + failure);
        }
    }

    /**
     * 取判据里的字符串数组。
     *
     * @param expected 判据
     * @param key      判据名
     * @return 数组元素；缺失或类型不符时返回空清单
     */
    private static List<JsonNode> arrayOf(JsonNode expected, String key) {
        JsonNode node = expected.get(key);
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<JsonNode> items = new ArrayList<>();
        for (JsonNode item : node) {
            items.add(item);
        }
        return items;
    }

    /**
     * 按点号路径取值，支持 {@code a.b[0].c} 形式的数组下标。
     *
     * @param root 根节点
     * @param path 路径
     * @return 值；任一段缺失返回 null
     */
    private static JsonNode resolve(JsonNode root, String path) {
        if (StringUtils.isBlank(path)) {
            return null;
        }
        JsonNode current = root;
        for (String rawSegment : path.split("\\.")) {
            String segment = rawSegment.trim();
            if (segment.isEmpty()) {
                return null;
            }
            int bracket = segment.indexOf('[');
            String key = bracket < 0 ? segment : segment.substring(0, bracket);
            if (!key.isEmpty()) {
                if (current == null || !current.isObject()) {
                    return null;
                }
                current = current.get(key);
            }
            int cursor = bracket;
            while (cursor >= 0 && cursor < segment.length()) {
                int close = segment.indexOf(']', cursor);
                if (close < 0) {
                    return null;
                }
                String indexText = segment.substring(cursor + 1, close).trim();
                int index;
                try {
                    index = Integer.parseInt(indexText);
                } catch (NumberFormatException e) {
                    return null;
                }
                if (current == null || !current.isArray() || index < 0 || index >= current.size()) {
                    return null;
                }
                current = current.get(index);
                cursor = segment.indexOf('[', close);
            }
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    /**
     * 值是否相等（数字按数值比较：{@code 1} 与 {@code 1.0} 是同一个值）。
     *
     * @param want 期望
     * @param got  实际
     * @return 相等返回 true
     */
    private static boolean sameValue(JsonNode want, JsonNode got) {
        if (want.isNumber() && got.isNumber()) {
            return want.decimalValue().compareTo(got.decimalValue()) == 0;
        }
        if (want.isBoolean() || got.isBoolean()) {
            return want.isBoolean() && got.isBoolean() && want.booleanValue() == got.booleanValue();
        }
        return text(want).equals(text(got));
    }

    /**
     * 取值文本（数字不补格式化，避免 1 变 1.0）。
     *
     * @param node 节点
     * @return 文本
     */
    private static String text(JsonNode node) {
        if (node == null) {
            return "null";
        }
        if (node.isString()) {
            return node.stringValue();
        }
        return node.toString();
    }

    /**
     * 安静解析：解析不了返回 null（调用方负责报告"不是合法 JSON"）。
     *
     * @param text JSON 文本
     * @return 节点；无法解析返回 null
     */
    private JsonNode parseQuietly(String text) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        try {
            return jsonMapper.readTree(text);
        } catch (Exception e) {
            return null;
        }
    }

}
