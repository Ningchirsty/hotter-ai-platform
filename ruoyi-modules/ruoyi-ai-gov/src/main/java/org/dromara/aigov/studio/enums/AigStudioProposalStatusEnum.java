package org.dromara.aigov.studio.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 训练草稿「变更提案」的状态（增量 21／附件 07 增量 6）。
 *
 * <p><b>为什么只有三态、没有 DRAFT</b>：提案从产生那一刻就是"待审"对象，
 * 它是草稿内容的<b>建议</b>而不是草稿本身；"还没写完"的提案没有存在意义
 * （想改就重新提一份），多做一态只会让"哪些提案在等人处理"这个问题变难回答。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigStudioProposalStatusEnum {

    /**
     * 待人工处理（未采纳也未拒绝）
     */
    PENDING("PENDING", "待处理"),

    /**
     * 已采纳（已产生一条新修订，来源 COPILOT_ACCEPTED）
     */
    APPLIED("APPLIED", "已采纳"),

    /**
     * 已拒绝（内容未被改动）
     */
    REJECTED("REJECTED", "已拒绝");

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
     * @return 枚举；未命中返回 null
     */
    public static AigStudioProposalStatusEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AigStudioProposalStatusEnum item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return null;
    }

}
