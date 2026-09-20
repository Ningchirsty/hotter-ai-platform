package org.dromara.content.helper;

import lombok.Data;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.content.domain.CpGateRule;
import org.dromara.content.enums.ContentGateLevelEnum;
import org.dromara.content.enums.ContentTaskStatusEnum;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 闸门判定引擎。
 *
 * <p><b>这是唯一决定任务能否流转的规则入口</b>（SPEC §4.1），实现设计文档 §8.1 的三级闸门：</p>
 * <ul>
 *     <li>{@code BLOCK} 未满足 → {@code PENDING_CONFIRM}，并写入阻断原因；</li>
 *     <li>无 BLOCK 未满足、但有 {@code CONDITION} 未满足 → {@code CONDITIONAL_READY}；</li>
 *     <li>全部满足 → {@code READY}；{@code NOTICE} 不参与流转判定。</li>
 * </ul>
 *
 * <p><b>引擎只产出「规则结论」，不自己改状态</b>：状态变更由服务层在人工动作之后调用本引擎、
 * 再据结论落库。设计文档 §15 要求状态由「规则条件 + 人工权限」共同决定，
 * 因此这里不接权限、不写库，保持可单测。</p>
 *
 * @author content
 */
@Component
public class ContentGateEngine {

    /**
     * 执行闸门判定。
     *
     * @param rules             该交付类型下<b>已启用</b>的闸门规则
     * @param confirmedFields   已有「已确认」事实值的字段编码集合
     * @param hasBlockedCard    是否存在被显式选择「暂不确认并阻断」的卡片
     * @return 判定结论
     */
    public GateResult evaluate(List<CpGateRule> rules, Set<String> confirmedFields, boolean hasBlockedCard) {
        GateResult result = new GateResult();
        Set<String> confirmed = confirmedFields == null ? Set.of() : confirmedFields;

        List<CpGateRule> blockUnsatisfied = new ArrayList<>();
        List<CpGateRule> conditionUnsatisfied = new ArrayList<>();
        List<CpGateRule> notices = new ArrayList<>();

        if (rules != null) {
            for (CpGateRule rule : rules) {
                ContentGateLevelEnum level = ContentGateLevelEnum.find(rule.getGateLevel());
                if (level == null) {
                    continue;
                }
                boolean satisfied = confirmed.contains(rule.getFieldCode());
                switch (level) {
                    case BLOCK -> {
                        if (!satisfied) {
                            blockUnsatisfied.add(rule);
                        }
                    }
                    case CONDITION -> {
                        if (!satisfied) {
                            conditionUnsatisfied.add(rule);
                        }
                    }
                    case NOTICE -> notices.add(rule);
                }
            }
        }

        result.setBlockUnsatisfied(blockUnsatisfied);
        result.setConditionUnsatisfied(conditionUnsatisfied);
        result.setNotices(notices);

        // 人为显式阻断优先：即便闸门规则都满足了，只要有人选择「暂不确认并阻断」，
        // 任务就不得流转——这是设计文档 §7.2 给出的第四个处理选项。
        if (hasBlockedCard) {
            result.setStatus(ContentTaskStatusEnum.PENDING_CONFIRM.getCode());
            result.setBlockReason("存在被显式阻断的互动确认卡，需先处理该卡");
            return result;
        }

        if (!blockUnsatisfied.isEmpty()) {
            result.setStatus(ContentTaskStatusEnum.PENDING_CONFIRM.getCode());
            result.setBlockReason("以下强制项尚未确认：" + joinNames(blockUnsatisfied));
            return result;
        }
        if (!conditionUnsatisfied.isEmpty()) {
            result.setStatus(ContentTaskStatusEnum.CONDITIONAL_READY.getCode());
            result.setBlockReason(null);
            return result;
        }
        result.setStatus(ContentTaskStatusEnum.READY.getCode());
        result.setBlockReason(null);
        return result;
    }

    /**
     * 拼接未满足字段名。
     *
     * @param rules 规则
     * @return 顿号分隔的字段名
     */
    private String joinNames(List<CpGateRule> rules) {
        Set<String> names = new LinkedHashSet<>();
        for (CpGateRule r : rules) {
            names.add(StringUtils.blankToDefault(r.getFieldName(), r.getFieldCode()));
        }
        return String.join("、", names);
    }

    /**
     * 闸门判定结论。
     *
     * @author content
     */
    @Data
    public static class GateResult {

        /**
         * 结论状态（见 ContentTaskStatusEnum）
         */
        private String status;

        /**
         * 阻断原因（无阻断时为 null）
         */
        private String blockReason;

        /**
         * 未满足的强制项
         */
        private List<CpGateRule> blockUnsatisfied = new ArrayList<>();

        /**
         * 未满足的条件项
         */
        private List<CpGateRule> conditionUnsatisfied = new ArrayList<>();

        /**
         * 非阻断提醒项（不参与流转判定）
         */
        private List<CpGateRule> notices = new ArrayList<>();

        /**
         * 是否可用于生成开工包。
         *
         * @return 可开工或条件开工返回 true
         */
        public boolean readyForPackage() {
            return ContentTaskStatusEnum.READY.getCode().equals(status)
                || ContentTaskStatusEnum.CONDITIONAL_READY.getCode().equals(status);
        }

    }

}
