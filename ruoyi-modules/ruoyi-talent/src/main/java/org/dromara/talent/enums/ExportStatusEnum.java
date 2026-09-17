package org.dromara.talent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 导出任务状态枚举
 *
 * @author talent
 */
@Getter
@AllArgsConstructor
public enum ExportStatusEnum {

    /**
     * 待执行
     */
    PENDING("PENDING", "待执行"),
    /**
     * 执行中
     */
    RUNNING("RUNNING", "执行中"),
    /**
     * 成功
     */
    SUCCESS("SUCCESS", "成功"),
    /**
     * 失败
     */
    FAILED("FAILED", "失败"),
    /**
     * 已过期
     */
    EXPIRED("EXPIRED", "已过期");

    private final String code;
    private final String desc;

    /**
     * 按 code 查找，找不到返回 null
     */
    public static ExportStatusEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (ExportStatusEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
