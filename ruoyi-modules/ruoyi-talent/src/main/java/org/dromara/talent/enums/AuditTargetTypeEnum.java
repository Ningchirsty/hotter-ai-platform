package org.dromara.talent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 审计对象类型枚举
 *
 * @author talent
 */
@Getter
@AllArgsConstructor
public enum AuditTargetTypeEnum {

    /**
     * 人才档案
     */
    TALENT("TALENT", "人才档案"),
    /**
     * 人才附件
     */
    ATTACHMENT("ATTACHMENT", "人才附件"),
    /**
     * 导出任务
     */
    EXPORT("EXPORT", "导出任务"),
    /**
     * 访问授权
     */
    GRANT("GRANT", "访问授权"),
    /**
     * 解析任务
     */
    PARSE_TASK("PARSE_TASK", "解析任务");

    private final String code;
    private final String desc;

    /**
     * 按 code 查找，找不到返回 null
     */
    public static AuditTargetTypeEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AuditTargetTypeEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
