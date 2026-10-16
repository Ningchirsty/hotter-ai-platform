package org.dromara.aigov.changerequest.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 变更类型（{@code aig_change_request.change_type}）。
 *
 * <p><b>本轮刻意只覆盖路由策略三件套</b>：那正是 Q1C 轻量门槛拦下来的东西
 * （放开外发 / 动限制级策略 / 删除策略行）。能力停用/删除、候选变更也能套这套，
 * 但它们各自的"目标值"形态不同，等有人真的要按单子批再扩——先把最常见的这条做扎实。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigChangeTypeEnum {

    /**
     * 新增策略行
     */
    POLICY_CREATE("POLICY_CREATE", "新增路由策略"),

    /**
     * 修改策略行
     */
    POLICY_UPDATE("POLICY_UPDATE", "修改路由策略"),

    /**
     * 删除策略行
     */
    POLICY_DELETE("POLICY_DELETE", "删除路由策略");

    private final String code;

    private final String desc;

    /**
     * 按 code 查找。
     *
     * @param code 编码
     * @return 枚举；未命中返回 null
     */
    public static AigChangeTypeEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AigChangeTypeEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }
}
