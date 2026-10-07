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
 * <p><b>这些字段都只影响「在已允许的候选里挑谁」，不参与权限与安全判定</b>：
 * 它们只能让候选变少，不能让任何被治理策略、数据等级、生命周期或健康状态排除的模型
 * 重新变得可用。因此传错一个提示的后果是「没挑到最合适的那家」，而不是「数据发错了地方」。</p>
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
     * 本次调用可接受的最高成本（单次）。
     *
     * <p>与模型治理里的 {@code cost_limit_amount}（该模型的单次成本上限）比对：
     * 声明值高于本次预算的候选会被排除。<b>为空表示调用方没有预算约束</b>，
     * 此时不做任何过滤、也不产生提示——把「没提要求」当成「预算为零」会把所有候选排除干净。</p>
     *
     * <p>金额单位由部署方统一（本平台按人民币元），此处不做换算：混用币种而不换算
     * 会让比较结果看起来正常却完全错误，宁可让调用方统一口径。</p>
     */
    private BigDecimal maxCost;

    /**
     * 构造一个只含场景的路由提示。
     *
     * @param scenarioCode 场景编码（可空）
     * @return 路由提示；无任何内容时返回 {@code null}（调用方据此走「无提示」快路径）
     */
    public static AigRouteHint ofScenario(String scenarioCode) {
        if (scenarioCode == null || scenarioCode.isBlank()) {
            return null;
        }
        AigRouteHint hint = new AigRouteHint();
        hint.setScenarioCode(scenarioCode);
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
        boolean hasScenario = scenarioCode != null && !scenarioCode.isBlank();
        if (!hasScenario && maxCost == null) {
            return null;
        }
        AigRouteHint hint = new AigRouteHint();
        hint.setScenarioCode(hasScenario ? scenarioCode : null);
        hint.setMaxCost(maxCost);
        return hint;
    }

    /**
     * 是否不含任何约束。
     *
     * @return 无约束返回 true
     */
    public boolean isEmpty() {
        return (scenarioCode == null || scenarioCode.isBlank()) && maxCost == null;
    }

}
