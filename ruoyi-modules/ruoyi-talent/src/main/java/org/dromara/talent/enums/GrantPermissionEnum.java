package org.dromara.talent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 单条授权动作枚举
 *
 * @author talent
 */
@Getter
@AllArgsConstructor
public enum GrantPermissionEnum {

    /**
     * 查看
     */
    VIEW("VIEW", "查看"),
    /**
     * 下载
     */
    DOWNLOAD("DOWNLOAD", "下载"),
    /**
     * 查看完整手机号
     */
    VIEW_FULL_PHONE("VIEW_FULL_PHONE", "查看完整手机号");

    private final String code;
    private final String desc;

    /**
     * 按 code 查找，找不到返回 null
     */
    public static GrantPermissionEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (GrantPermissionEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
