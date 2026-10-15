package org.dromara.aigov.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 路由提示（设计 §4.3 标准请求对象的 {@code routing_hint}）。
 *
 * <p><b>为什么聚合成一个对象，而不是继续给 decide() 加参数</b>：路由的可用输入会持续增加
 * （场景、本次预算，将来还有最大时延、指定供应商偏好）。每个都加一个参数，
 * 调用方很快就会写成 {@code decide(cap, level, null, null, 12, null)}——那种调用点
 * 谁也读不懂哪个 null 是谁，而多传错一个位置不会有任何编译错误。</p>
 *
 * <p><b>除 {@link #allowExternal} 外，这些字段都只影响「在已允许的候选里挑谁」，
 * 不参与权限与安全判定</b>：它们只能让候选变少，不能让任何被治理策略、数据等级、生命周期
 * 或健康状态排除的模型重新变得可用。因此传错一个提示的后果是「没挑到最合适的那家」，
 * 而不是「数据发错了地方」。</p>
 *
 * <p><b>{@link #allowExternal} 是这条规律的唯一例外，必须按硬约束对待</b>：它表达的是
 * 「业务侧（Agent 配置/草稿）已经判定本次不得外发」，路由必须把它与策略的 {@code allowExternal}
 * 取与。写成 {@code FALSE} 只会让外部候选被排除，永远不会放宽任何约束——这与本类「只能更严、
 * 不能更松」的总体性质一致（设计 §7.2）。</p>
 *
 * @author ai-gov
 */
@Data
public class AigRouteHint implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 场景编码（设计 §4.4 第 4 步的场景强制绑定）
     */
    private String scenarioCode;

    /**
     * 本次调用可接受的最高成本（单次；单位＝<b>美元 USD</b>，全平台唯一口径，见
     * {@link org.dromara.aigov.constant.AigConstants#COST_CURRENCY}）。
     *
     * <p>与模型治理里的 {@code cost_limit_amount}（该模型的单次成本上限）比对：
     * 声明值高于本次预算的候选会被排除。<b>为空表示调用方没有预算约束</b>，
     * 此时不做任何过滤、也不产生提示——把「没提要求」当成「预算为零」会把所有候选排除干净。</p>
     *
     * <p><b>不做换算</b>：全平台只有一个币种（美元），两侧天然可比；一旦出现按别的币种计价的
     * 供应商，要在<b>调用器</b>里换算成美元再回填，而不是让原币金额到这里「看起来也能比」——
     * 混用币种而不换算会让比较结果看起来正常却完全错误。</p>
     */
    private BigDecimal maxCost;

    /**
     * 业务侧声明是否允许外发（可空；{@code null}=不额外限制）。
     *
     * <p><b>与 {@link #maxCost} 不同，它是硬约束</b>：{@code FALSE} 时路由必须排除外部部署
     * 候选，即使策略允许外发（见类注释）。{@code TRUE} 不表示"一定外发"，只是"业务侧不禁止"，
     * 最终仍由策略与数据等级决定。</p>
     */
    private Boolean allowExternal;

    /**
     * 构造一个只含场景的路由提示。
     *
     * @param scenarioCode 场景编码（可空）
     * @return 路由提示；无任何内容时返回 {@code null}（调用方据此走「无提示」快路径）
     */
    public static AigRouteHint ofScenario(String scenarioCode) {
        return of(scenarioCode, null, null);
    }

    /**
     * 构造路由提示；字段全为空时返回 {@code null}。
     *
     * @param scenarioCode  场景编码（可空）
     * @param maxCost       本次预算（可空）
     * @param allowExternal 业务侧外发许可（可空）
     * @return 路由提示，或 null
     */
    public static AigRouteHint of(String scenarioCode, BigDecimal maxCost, Boolean allowExternal) {
        boolean hasScenario = scenarioCode != null && !scenarioCode.isBlank();
        if (!hasScenario && maxCost == null && allowExternal == null) {
            return null;
        }
        AigRouteHint hint = new AigRouteHint();
        hint.setScenarioCode(hasScenario ? scenarioCode : null);
        hint.setMaxCost(maxCost);
        hint.setAllowExternal(allowExternal);
        return hint;
    }

    /**
     * 构造路由提示；两个字段都为空时返回 {@code null}。
     *
     * @param scenarioCode 场景编码（可空）
     * @param maxCost      本次预算（可空）
     * @return 路由提示，或 null
     */
    public static AigRouteHint of(String scenarioCode, BigDecimal maxCost) {
        return of(scenarioCode, maxCost, null);
    }

    /**
     * 是否不含任何约束。
     *
     * @return 无约束返回 true
     */
    public boolean isEmpty() {
        return (scenarioCode == null || scenarioCode.isBlank()) && maxCost == null
            && allowExternal == null;
    }

}
