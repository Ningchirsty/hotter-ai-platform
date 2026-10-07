package org.dromara.aigov.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 数据等级枚举
 * <p>等级由低到高：公开 &lt; 内部 &lt; 限制 &lt; 严格。路由判定时以 {@link #rank()} 比较，
 * 「模型允许的最高等级 &gt;= 本次数据等级」才允许调用。</p>
 *
 * <p><b>STRICT 与 RESTRICTED 的区别</b>：RESTRICTED 还可以由路由策略显式放行外发
 * （{@code aig_route_policy.allow_external='Y'}），STRICT 则**任何策略都不允许外发**——
 * 由 {@link #externalForbidden()} 在路由层硬拒绝，不依赖策略行是否存在。
 * 这是「严格敏感资料」的兜底：配置错一条策略不该造成数据泄露。</p>
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
    RESTRICTED("RESTRICTED", "限制", 2),
    /**
     * 严格级数据（HR、证件、客户名单等）：**一律禁止外发**
     */
    STRICT("STRICT", "严格", 3);

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
     * 是否禁止外发。STRICT 恒为 true，且不因任何路由策略而改变。
     *
     * @return 禁止外发返回 true
     */
    public boolean externalForbidden() {
        return this == STRICT;
    }

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
