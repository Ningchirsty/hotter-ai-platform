package org.dromara.hrtalent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 数据分级枚举。
 * <p>对应数据字典 {@code recruit_data_level}（设计文档 §10）。
 * 分级由低到高：内部 &lt; 敏感 &lt; 高度敏感，敏感级操作需写入审计。</p>
 *
 * @author hr-talent
 */
@Getter
@AllArgsConstructor
public enum DataLevelEnum {

    /**
     * 内部
     */
    INTERNAL("internal", "内部"),
    /**
     * 敏感
     */
    SENSITIVE("sensitive", "敏感"),
    /**
     * 高度敏感
     */
    HIGHLY_SENSITIVE("highly_sensitive", "高敏感");

    /**
     * 编码（入库值，投入使用后不得随意变更）
     */
    private final String code;

    /**
     * 描述（中文名称，页面展示由字典转换）
     */
    private final String desc;

    /**
     * 按编码查找。
     *
     * @param code 编码
     * @return 匹配的枚举，未命中返回 null
     */
    public static DataLevelEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (DataLevelEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
