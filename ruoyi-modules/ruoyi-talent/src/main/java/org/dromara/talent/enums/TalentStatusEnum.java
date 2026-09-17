package org.dromara.talent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 人才状态枚举
 *
 * @author talent
 */
@Getter
@AllArgsConstructor
public enum TalentStatusEnum {

    /**
     * 新建
     */
    NEW("NEW", "新建"),
    /**
     * 跟进中
     */
    FOLLOWING("FOLLOWING", "跟进中"),
    /**
     * 面试中
     */
    INTERVIEW("INTERVIEW", "面试中"),
    /**
     * 已发offer
     */
    OFFER("OFFER", "已发offer"),
    /**
     * 已入职
     */
    ONBOARD("ONBOARD", "已入职"),
    /**
     * 已归档
     */
    ARCHIVED("ARCHIVED", "已归档");

    private final String code;
    private final String desc;

    /**
     * 按 code 查找，找不到返回 null
     */
    public static TalentStatusEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (TalentStatusEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
