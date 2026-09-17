package org.dromara.talent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 人才库角色标识枚举（code 为角色 roleKey 契约）
 *
 * @author talent
 */
@Getter
@AllArgsConstructor
public enum TalentRoleEnum {

    /**
     * 人才库管理员
     */
    ADMIN("talent_admin", "人才库管理员"),
    /**
     * 集团HR
     */
    HR_GROUP("talent_hr_group", "集团HR"),
    /**
     * 深圳HR
     */
    HR_SZ("talent_hr_sz", "深圳HR"),
    /**
     * 汕头HR
     */
    HR_ST("talent_hr_st", "汕头HR"),
    /**
     * 仅查阅者
     */
    VIEWER("talent_viewer", "仅查阅者"),
    /**
     * 审计员
     */
    AUDITOR("talent_auditor", "审计员");

    private final String code;
    private final String desc;

    /**
     * 按 code 查找，找不到返回 null
     */
    public static TalentRoleEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (TalentRoleEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
