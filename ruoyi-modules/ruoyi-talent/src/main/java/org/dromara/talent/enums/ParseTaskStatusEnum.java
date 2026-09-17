package org.dromara.talent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 简历解析任务状态枚举
 *
 * @author talent
 */
@Getter
@AllArgsConstructor
public enum ParseTaskStatusEnum {

    /**
     * 未启用（解析能力未获批）
     */
    DISABLED("DISABLED", "未启用"),
    /**
     * 待解析
     */
    PENDING("PENDING", "待解析"),
    /**
     * 解析中
     */
    PROCESSING("PROCESSING", "解析中"),
    /**
     * 解析成功
     */
    SUCCESS("SUCCESS", "解析成功"),
    /**
     * 解析失败
     */
    FAILED("FAILED", "解析失败");

    private final String code;
    private final String desc;

    /**
     * 按 code 查找，找不到返回 null
     */
    public static ParseTaskStatusEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (ParseTaskStatusEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
