package org.dromara.aigov.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 数据等级枚举
 * <p>等级由低到高：公开 &lt; 内部 &lt; 限制。路由判定时以 {@link #rank()} 比较，
 * 「模型允许的最高等级 &gt;= 本次数据等级」才允许调用。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigDataLevelEnum {

    /**
     * 公开数据
     */
    PUBLIC("PUBLIC", "公开", 0),
    /**
     * 内部数据
     */
    INTERNAL("INTERNAL", "内部", 1),
    /**
     * 限制级数据（个人信息、敏感资料）
     */
    RESTRICTED("RESTRICTED", "限制", 2);

    /**
     * 编码（入库值）
     */
    private final String code;
    /**
     * 描述
     */
    private final String desc;
    /**
     * 等级序号，用于比较高低
     */
    private final int rank;

    /**
     * 按 code 查找，找不到返回 null
     *
     * @param code 编码
     * @return 匹配的枚举，未命中返回 null
     */
    public static AigDataLevelEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AigDataLevelEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
