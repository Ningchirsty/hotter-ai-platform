package org.dromara.talent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 人才归属区域枚举
 *
 * @author talent
 */
@Getter
@AllArgsConstructor
public enum TalentRegionEnum {

    /**
     * 集团共享
     */
    GROUP("GROUP", "集团共享"),
    /**
     * 深圳
     */
    SZ("SZ", "深圳"),
    /**
     * 汕头
     */
    ST("ST", "汕头");

    private final String code;
    private final String desc;

    /**
     * 按 code 查找，找不到返回 null
     */
    public static TalentRegionEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (TalentRegionEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
