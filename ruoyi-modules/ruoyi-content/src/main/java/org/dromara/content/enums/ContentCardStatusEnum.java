package org.dromara.content.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 互动确认卡状态枚举。
 *
 * @author content
 */
@Getter
@AllArgsConstructor
public enum ContentCardStatusEnum {

    /**
     * 待处理
     */
    PENDING("PENDING", "待处理"),
    /**
     * 已处理：已确认取值或选定选项
     */
    RESOLVED("RESOLVED", "已处理"),
    /**
     * 暂不确认并阻断：用户显式选择阻断，任务不得流转（设计文档 §7.2 的第四个选项）
     */
    BLOCKED("BLOCKED", "暂不确认并阻断"),
    /**
     * 已关闭
     */
    CLOSED("CLOSED", "已关闭");

    /**
     * 编码（入库值）
     */
    private final String code;
    /**
     * 描述
     */
    private final String desc;

    /**
     * 按 code 查找，找不到返回 null
     *
     * @param code 编码
     * @return 匹配的枚举，未命中返回 null
     */
    public static ContentCardStatusEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (ContentCardStatusEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
