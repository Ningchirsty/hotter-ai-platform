package org.dromara.aigov.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 调用结果枚举
 * <p>编码与 {@code aig_invocation_audit.result}（char(1)）保持一致，
 * 与 {@code tl_sensitive_audit.result} 同口径。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigInvokeResultEnum {

    /**
     * 成功
     */
    SUCCESS("0", "成功"),
    /**
     * 失败
     */
    FAILED("1", "失败");

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
    public static AigInvokeResultEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AigInvokeResultEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
