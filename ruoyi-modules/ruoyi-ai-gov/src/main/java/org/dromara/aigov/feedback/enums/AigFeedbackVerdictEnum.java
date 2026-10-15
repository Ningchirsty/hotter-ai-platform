package org.dromara.aigov.feedback.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 反馈处理结论（设计 §6.9：训练负责人在问题队列中选择确认问题、补知识、改规则、加入用例或关闭）。
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigFeedbackVerdictEnum {

    /** 待处理（进入问题队列） */
    PENDING("PENDING", "待处理"),

    /** 已确认问题（会转成补知识/改规则/加用例之一） */
    CONFIRMED("CONFIRMED", "已确认问题"),

    /** 已驳回（不是问题、或已被其它反馈覆盖） */
    REJECTED("REJECTED", "已驳回");

    /** 编码 */
    private final String code;

    /** 描述 */
    private final String desc;

    /**
     * 按 code 查找。
     *
     * @param code 编码
     * @return 枚举；未命中返回 null
     */
    public static AigFeedbackVerdictEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AigFeedbackVerdictEnum item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return null;
    }

}
