package org.dromara.talent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 被授权主体类型枚举
 *
 * @author talent
 */
@Getter
@AllArgsConstructor
public enum GranteeTypeEnum {

    /**
     * 用户
     */
    USER("USER", "用户"),
    /**
     * 角色
     */
    ROLE("ROLE", "角色");

    private final String code;
    private final String desc;

    /**
     * 按 code 查找，找不到返回 null
     */
    public static GranteeTypeEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (GranteeTypeEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
