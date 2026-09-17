package org.dromara.talent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 重复人才匹配规则枚举（score 为命中分数 0-100）
 *
 * @author talent
 */
@Getter
@AllArgsConstructor
public enum DuplicateMatchRuleEnum {

    /**
     * 手机号哈希完全命中
     */
    PHONE_HASH("PHONE_HASH", "手机号哈希命中", 100),
    /**
     * 姓名 + 手机后四位命中
     */
    NAME_PHONE_TAIL4("NAME_PHONE_TAIL4", "姓名与手机后四位命中", 70),
    /**
     * 姓名 + 同区域命中
     */
    NAME_REGION("NAME_REGION", "姓名与区域命中", 40);

    private final String code;
    private final String desc;
    private final int score;

    /**
     * 按 code 查找，找不到返回 null
     */
    public static DuplicateMatchRuleEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (DuplicateMatchRuleEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
