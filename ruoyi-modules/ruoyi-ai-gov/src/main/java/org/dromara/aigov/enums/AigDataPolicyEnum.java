package org.dromara.aigov.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 数据策略枚举
 * <p>描述一个业务能力对数据出域的整体态度，与路由策略的
 * {@code allow_external} 共同构成外发判定。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigDataPolicyEnum {

    /**
     * 仅本地处理
     */
    LOCAL_ONLY("LOCAL_ONLY", "仅本地"),
    /**
     * 本地优先，允许在无本地可用时外发
     */
    LOCAL_FIRST("LOCAL_FIRST", "本地优先"),
    /**
     * 允许外部处理
     */
    EXTERNAL_ALLOWED("EXTERNAL_ALLOWED", "允许外部");

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
    public static AigDataPolicyEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AigDataPolicyEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
