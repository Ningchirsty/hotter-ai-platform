package org.dromara.aigov.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 路由决策枚举
 * <p>路由引擎永不抛异常，用本枚举表达三种终态。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigRouteDecisionEnum {

    /**
     * 命中模型，可执行调用
     */
    MODEL("MODEL", "命中模型"),
    /**
     * 转人工处理
     */
    MANUAL("MANUAL", "转人工"),
    /**
     * 策略拒绝，不调用模型
     */
    DENIED("DENIED", "策略拒绝");

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
    public static AigRouteDecisionEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AigRouteDecisionEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
