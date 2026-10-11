package org.dromara.aigov.studio.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 变更提案的来源（增量 21／附件 07 增量 6）。
 *
 * <p><b>为什么来源必须落列，而不是塞进 remark</b>：采纳提案会写一条
 * {@code source=COPILOT_ACCEPTED} 的修订，而"这条修订是人想的还是模型提的"是事后追责
 * 与质量分析的基本维度。写进 remark 就会被人工复核追加、被改写，性质放在会变的地方等于没放
 * （同 ADR-014 给 {@code aig_evaluation_run.executed_by} 的理由）。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigStudioProposalSourceEnum {

    /**
     * 培训助手（Copilot）提出
     */
    COPILOT("COPILOT", "培训助手"),

    /**
     * 人工草拟（复用同一条提案通道，便于同一套审阅/采纳流程）
     */
    MANUAL("MANUAL", "人工草拟");

    /**
     * 编码（入库值）
     */
    private final String code;

    /**
     * 描述
     */
    private final String desc;

    /**
     * 按 code 查找，找不到返回 null。
     *
     * @param code 编码
     * @return 枚举；未命中或为空时返回 COPILOT（提案默认来自培训助手）
     */
    public static AigStudioProposalSourceEnum findOrDefault(String code) {
        if (code == null || code.isBlank()) {
            return COPILOT;
        }
        for (AigStudioProposalSourceEnum item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return null;
    }

}
