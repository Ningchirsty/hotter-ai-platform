package org.dromara.talent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 重复人才确认结论枚举
 *
 * @author talent
 */
@Getter
@AllArgsConstructor
public enum DuplicateConclusionEnum {

    /**
     * 待确认
     */
    PENDING("PENDING", "待确认"),
    /**
     * 非同一人
     */
    DIFFERENT("DIFFERENT", "非同一人"),
    /**
     * 同一人
     */
    SAME("SAME", "同一人");

    private final String code;
    private final String desc;

    /**
     * 按 code 查找，找不到返回 null
     */
    public static DuplicateConclusionEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (DuplicateConclusionEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
