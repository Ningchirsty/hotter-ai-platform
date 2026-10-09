package org.dromara.aigov.task.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.dromara.aigov.enums.AigRouteDecisionEnum;

/**
 * 任务级策略结论（{@code aig_task.policy_result}）。
 *
 * <p><b>为什么它不等于路由结论（MODEL/MANUAL/DENIED）</b>：两者回答的不是同一个问题。
 * 路由结论说的是「引擎怎么判的」，任务级结论说的是「这条任务这次能不能往下走」——
 * 而 {@code aig_task.policy_result} 这一列在 DDL 里的取值就是 PASS/REJECT/MANUAL
 * （治理台的「策略结论」直接显示它）。把路由词表硬塞进任务列，页面上就会出现
 * 「MODEL」这种对业务读者无意义的词。</p>
 *
 * <p><b>映射必须唯一</b>：{@link #fromDecision} 是这条映射的唯一实现。
 * 以前这一列**无人写入**，页面上那格永远是「-」；若把映射散在调用点写，
 * 一旦有人把 MANUAL 也映射成 PASS，「转人工」这件事在任务列表里就消失了。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigTaskPolicyResultEnum {

    /**
     * 通过：路由命中候选模型
     */
    PASS("PASS", "通过"),

    /**
     * 拒绝：策略不允许这次调用（含未配置策略、无可用模型、外发禁令等）
     */
    REJECT("REJECT", "拒绝"),

    /**
     * 转人工：无可用模型且策略允许转人工
     */
    MANUAL("MANUAL", "转人工");

    /**
     * 编码
     */
    private final String code;

    /**
     * 描述
     */
    private final String desc;

    /**
     * 由路由结论映射出任务级结论。
     *
     * @param decision 路由结论（可为 null）
     * @return 任务级结论；入参为空时返回 null（调用方据此决定不写这一列）
     */
    public static AigTaskPolicyResultEnum fromDecision(AigRouteDecisionEnum decision) {
        if (decision == null) {
            return null;
        }
        return switch (decision) {
            case MODEL -> PASS;
            case MANUAL -> MANUAL;
            case DENIED -> REJECT;
        };
    }

    /**
     * 按 code 查找，找不到返回 null。
     *
     * @param code 编码
     * @return 匹配的枚举，未命中返回 null
     */
    public static AigTaskPolicyResultEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AigTaskPolicyResultEnum item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return null;
    }

}
