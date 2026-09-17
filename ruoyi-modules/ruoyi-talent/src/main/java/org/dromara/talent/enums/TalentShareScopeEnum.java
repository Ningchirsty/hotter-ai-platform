package org.dromara.talent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 人才共享范围枚举
 *
 * @author talent
 */
@Getter
@AllArgsConstructor
public enum TalentShareScopeEnum {

    /**
     * 区域共享
     */
    REGION("REGION", "区域共享"),
    /**
     * 全集团共享
     */
    GROUP("GROUP", "全集团共享"),
    /**
     * 仅授权可见
     */
    GRANT_ONLY("GRANT_ONLY", "仅授权可见");

    private final String code;
    private final String desc;

    /**
     * 按 code 查找，找不到返回 null
     */
    public static TalentShareScopeEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (TalentShareScopeEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
