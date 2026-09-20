package org.dromara.content.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 资料解析状态枚举。
 *
 * @author content
 */
@Getter
@AllArgsConstructor
public enum ContentParseStatusEnum {

    /**
     * 待解析
     */
    PENDING("PENDING", "待解析"),
    /**
     * 解析中
     */
    PARSING("PARSING", "解析中"),
    /**
     * 已完成
     */
    DONE("DONE", "已完成"),
    /**
     * 解析失败（异常，带可读原因）
     */
    FAILED("FAILED", "失败"),
    /**
     * 已跳过（类型不支持，非错误，须带可读原因）
     */
    SKIPPED("SKIPPED", "已跳过");

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
    public static ContentParseStatusEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (ContentParseStatusEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
