package org.dromara.aigov.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 审计等级枚举
 * <p>决定 {@code aig_invocation_audit} 能落库多少输入信息：
 * 仅哈希 / 哈希+摘要 / 摘要+完整输出引用。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigAuditLevelEnum {

    /**
     * 摘要（只记输入摘要与结果概要）
     */
    SUMMARY("SUMMARY", "摘要"),
    /**
     * 完整（记录完整输出引用，仍不落受限原文）
     */
    FULL("FULL", "完整"),
    /**
     * 仅哈希
     */
    HASH_ONLY("HASH_ONLY", "仅哈希");

    /**
     * 编码（入库值）
     */
    private final String code;
    /**
     * 描述
     */
    private final String desc;

    /**
     * 按 code 查找，找不到返回 null
     *
     * @param code 编码
     * @return 匹配的枚举，未命中返回 null
     */
    public static AigAuditLevelEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AigAuditLevelEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
