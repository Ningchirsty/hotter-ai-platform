package org.dromara.content.helper;

import org.dromara.common.core.utils.StringUtils;
import org.dromara.content.enums.ContentGateLevelEnum;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 资料预检引擎（本地规则）：检出**冲突**与**缺失**。
 *
 * <p><b>不做的事</b>：不判断冲突里哪个取值正确、不臆测补全缺失值。设计文档 §9.1 与
 * §3.4 都要求产品事实只能来自经确认的资料；系统的职责是把证据摆到人面前，
 * 由人一键裁定。因此本引擎只产出「有哪些冲突、各来自哪里」与「缺什么」。</p>
 *
 * @author content
 */
@Component
public class ContentPrecheckEngine {

    /**
     * 执行预检。
     *
     * @param candidates 候选/已确认事实行（来自 cp_fact_snapshot）
     * @param rules      该交付类型启用的闸门规则（来自 cp_gate_rule）
     * @return 预检结果
     */
    public Result precheck(List<Map<String, Object>> candidates, List<Map<String, Object>> rules) {
        Result result = new Result();

        // 一、冲突：同一 fieldCode 存在多个归一后不等的取值
        Map<String, List<Map<String, Object>>> byField = new LinkedHashMap<>();
        if (candidates != null) {
            for (Map<String, Object> c : candidates) {
                String fieldCode = str(c.get("fieldCode"));
                if (StringUtils.isBlank(fieldCode)) {
                    continue;
                }
                byField.computeIfAbsent(fieldCode, k -> new ArrayList<>()).add(c);
            }
        }
        for (Map.Entry<String, List<Map<String, Object>>> e : byField.entrySet()) {
            List<Map<String, Object>> rows = e.getValue();
            Set<String> distinct = new LinkedHashSet<>();
            for (Map<String, Object> r : rows) {
                distinct.add(normalizeValue(str(r.get("value"))));
            }
            distinct.remove("");
            if (distinct.size() > 1) {
                Map<String, Object> conflict = new LinkedHashMap<>();
                conflict.put("fieldCode", e.getKey());
                conflict.put("fieldName", firstNonBlank(rows, "fieldName"));
                List<Map<String, Object>> values = new ArrayList<>();
                for (Map<String, Object> r : rows) {
                    Map<String, Object> v = new LinkedHashMap<>();
                    v.put("value", str(r.get("value")));
                    v.put("sourceFileName", str(r.get("sourceFileName")));
                    v.put("locator", str(r.get("locator")));
                    v.put("excerpt", str(r.get("excerpt")));
                    v.put("snapshotId", r.get("snapshotId"));
                    values.add(v);
                }
                conflict.put("values", values);
                result.getConflicts().add(conflict);
            }
        }

        // 二、缺失：闸门规则要求存在、但没有任何候选值的字段
        if (rules != null) {
            for (Map<String, Object> rule : rules) {
                String fieldCode = str(rule.get("fieldCode"));
                if (StringUtils.isBlank(fieldCode)) {
                    continue;
                }
                // requirePresent='N' 的字段（如参考图）缺失不算问题，由条件项另行处理
                if (!"Y".equalsIgnoreCase(str(rule.get("requirePresent")))) {
                    continue;
                }
                if (byField.containsKey(fieldCode)) {
                    continue;
                }
                Map<String, Object> missing = new LinkedHashMap<>();
                missing.put("fieldCode", fieldCode);
                missing.put("fieldName", str(rule.get("fieldName")));
                missing.put("gateLevel", str(rule.get("gateLevel")));
                result.getMissings().add(missing);
            }
        }

        result.getPendingConfirm().add("冲突只呈现证据，需人工裁定正确取值，系统不代判");
        result.getPendingConfirm().add("缺失字段需补料或由责任人确认后手工录入");
        return result;
    }

    /**
     * 归一取值用于比较：去空白、全角转半角常见符号、大小写。
     * <p>「30cm」与「30 cm」应视为同一值，否则会产生假冲突。</p>
     *
     * @param value 原值
     * @return 归一值
     */
    private String normalizeValue(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", "")
            .replace('：', ':')
            .replace('（', '(')
            .replace('）', ')')
            .toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * 取该组行中第一个非空的字段名。
     *
     * @param rows 行
     * @param key  键
     * @return 字段名，找不到返回 null
     */
    private String firstNonBlank(List<Map<String, Object>> rows, String key) {
        for (Map<String, Object> r : rows) {
            String v = str(r.get(key));
            if (StringUtils.isNotBlank(v)) {
                return v;
            }
        }
        return null;
    }

    /**
     * 安全取字符串。
     *
     * @param v 值
     * @return 字符串，null 安全
     */
    private String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    /**
     * 预检结果。
     *
     * @author content
     */
    public static class Result {

        /**
         * 冲突项
         */
        private final List<Map<String, Object>> conflicts = new ArrayList<>();

        /**
         * 缺失项
         */
        private final List<Map<String, Object>> missings = new ArrayList<>();

        /**
         * 必须人工确认的点
         */
        private final List<String> pendingConfirm = new ArrayList<>();

        /**
         * @return 冲突项
         */
        public List<Map<String, Object>> getConflicts() {
            return conflicts;
        }

        /**
         * @return 缺失项
         */
        public List<Map<String, Object>> getMissings() {
            return missings;
        }

        /**
         * @return 人工确认点
         */
        public List<String> getPendingConfirm() {
            return pendingConfirm;
        }

        /**
         * 是否存在需要生成互动卡的问题。
         *
         * @return 有冲突或缺失返回 true
         */
        public boolean hasIssues() {
            return !conflicts.isEmpty() || !missings.isEmpty();
        }

    }

    /**
     * 判断闸门等级是否已知（供调用方校验规则数据）。
     *
     * @param gateLevel 等级编码
     * @return 是否已知
     */
    public static boolean isKnownGateLevel(String gateLevel) {
        return ContentGateLevelEnum.find(gateLevel) != null;
    }

}
