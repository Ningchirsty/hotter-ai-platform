package org.dromara.aigov.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 能力-模型绑定用途枚举
 * <p>路由挑选顺序：主选 → 灰度 → 备选，同用途内按 priority 升序。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigUsageTypeEnum {

    /**
     * 主选
     */
    PRIMARY("PRIMARY", "主选"),
    /**
     * 备选
     */
    FALLBACK("FALLBACK", "备选"),
    /**
     * 灰度
     */
    GRAY("GRAY", "灰度");

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
    public static AigUsageTypeEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AigUsageTypeEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
