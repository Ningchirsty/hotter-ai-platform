package org.dromara.talent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 附件安全扫描状态枚举
 *
 * @author talent
 */
@Getter
@AllArgsConstructor
public enum ScanStatusEnum {

    /**
     * 待扫描
     */
    PENDING("PENDING", "待扫描"),
    /**
     * 扫描中
     */
    SCANNING("SCANNING", "扫描中"),
    /**
     * 扫描通过
     */
    CLEAN("CLEAN", "扫描通过"),
    /**
     * 已感染
     */
    INFECTED("INFECTED", "已感染"),
    /**
     * 扫描失败
     */
    FAILED("FAILED", "扫描失败");

    private final String code;
    private final String desc;

    /**
     * 按 code 查找，找不到返回 null
     */
    public static ScanStatusEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (ScanStatusEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
