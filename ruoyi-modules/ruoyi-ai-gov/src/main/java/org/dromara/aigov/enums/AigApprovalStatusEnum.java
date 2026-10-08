package org.dromara.aigov.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 调用授权审批单状态（C3：把 {@code aig_route_policy.require_approval} 做实）。
 *
 * <p>对应 {@code aig_call_approval.status}。只有 {@link #PENDING} 可以被处理，
 * 其余四个都是终态——<b>没有「退回待审批」这条边</b>：审批结论一旦给出就不该被重置，
 * 否则「批准过一次」会变成可以反复利用的东西。要重来就重新提交一张单子。</p>
 *
 * <p>{@link #EXPIRED} 与 {@link #REJECTED} 刻意分开：前者是"没人及时处理"，
 * 后者是"有人看过并否决了"。把它们并成一个会让「审批积压」与「方案本身不行」
 * 这两件事看起来一样，而该做的动作完全不同（前者催办、后者改方案）。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigApprovalStatusEnum {

    /**
     * 待审批（超过 expire_time 即视为超时，不得再批准）
     */
    PENDING("PENDING", "待审批"),

    /**
     * 已批准（valid_until 之前构成有效授权）
     */
    APPROVED("APPROVED", "已批准"),

    /**
     * 已驳回（有人明确否决）
     */
    REJECTED("REJECTED", "已驳回"),

    /**
     * 已超时（审批时限内没人处理）
     */
    EXPIRED("EXPIRED", "已超时"),

    /**
     * 已撤回（申请人自己收回）
     */
    CANCELLED("CANCELLED", "已撤回");

    /**
     * 编码（入库值）
     */
    private final String code;

    /**
     * 描述
     */
    private final String desc;

    /**
     * 是否终态（终态不能再被批准/驳回/撤回）。
     *
     * @return 终态返回 true
     */
    public boolean isTerminal() {
        return this != PENDING;
    }

    /**
     * 按 code 查找，找不到返回 null。
     *
     * @param code 编码
     * @return 匹配的枚举；未命中返回 null
     */
    public static AigApprovalStatusEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AigApprovalStatusEnum item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return null;
    }

}
