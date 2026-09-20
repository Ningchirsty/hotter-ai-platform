package org.dromara.aigov.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 人工结论枚举
 * <p>对应 {@code aig_invocation_audit.manual_decision}：
 * 需要人工确认的能力默认 PENDING，最终由业务侧回写结论。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigManualDecisionEnum {

    /**
     * 待确认
     */
    PENDING("PENDING", "待确认"),
    /**
     * 已采纳
     */
    ACCEPTED("ACCEPTED", "已采纳"),
    /**
     * 已驳回
     */
    REJECTED("REJECTED", "已驳回"),
    /**
     * 无需确认
     */
    NOT_REQUIRED("NOT_REQUIRED", "无需确认");

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
    public static AigManualDecisionEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AigManualDecisionEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
