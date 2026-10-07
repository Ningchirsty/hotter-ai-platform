package org.dromara.aigov.service;

import org.dromara.aigov.domain.vo.AigRouteDecision;
import org.dromara.aigov.domain.vo.AigRouteHint;
import org.dromara.aigov.enums.AigDataLevelEnum;

import java.util.List;

/**
 * 路由引擎：依据「能力 + 数据等级（+ 可选场景）」决定执行路径。
 * <p><b>不抛异常</b>，一律用 {@link AigRouteDecision#getDecision()} 表达结果：
 * {@code MODEL} / {@code MANUAL} / {@code DENIED}。</p>
 * <p>核心口径：<b>无策略即拒绝</b>（默认拒绝，不默认放行）；
 * 模型治理等级不足、生命周期不可调用、被策略禁止外发的外部部署模型一律排除。</p>
 *
 * @author ai-gov
 */
public interface IAigRouteService {

    /**
     * 依据 能力 + 数据等级 决定执行路径（不带场景）。
     *
     * @param capabilityCode 能力编码
     * @param dataLevel      本次数据等级
     * @return 路由决策（含 policyHits 明细），不抛异常
     */
    default AigRouteDecision decide(String capabilityCode, AigDataLevelEnum dataLevel) {
        return decide(capabilityCode, dataLevel, (AigRouteHint) null);
    }

    /**
     * 依据 能力 + 数据等级 + 场景 决定执行路径（便捷重载）。
     *
     * @param capabilityCode 能力编码
     * @param dataLevel      本次数据等级
     * @param scenarioCode   场景编码（可为 null/空）
     * @return 路由决策
     */
    default AigRouteDecision decide(String capabilityCode, AigDataLevelEnum dataLevel, String scenarioCode) {
        return decide(capabilityCode, dataLevel, AigRouteHint.ofScenario(scenarioCode));
    }

    /**
     * 依据 能力 + 数据等级 + 路由提示 决定执行路径（设计 §4.4）。
     *
     * <p>提示承载「场景强制绑定」与「本次预算」，两者都<b>只收窄候选</b>：
     * 命中场景绑定时只保留指定供应商；声明了本次预算时排除单次成本上限高于该预算的模型。
     * 它们都不会让任何被策略/数据等级/生命周期/健康状态排除的模型重新变得可用，
     * 因此不参与权限与安全判定，只影响「在已允许的候选里挑谁」。</p>
     *
     * @param capabilityCode 能力编码
     * @param dataLevel      本次数据等级
     * @param hint           路由提示（可为 null，表示无任何场景/预算约束）
     * @return 路由决策（含 policyHits 明细），不抛异常
     */
    AigRouteDecision decide(String capabilityCode, AigDataLevelEnum dataLevel, AigRouteHint hint);

    /**
     * 供预览/排障：返回决策依据明细。
     *
     * @param capabilityCode 能力编码
     * @param dataLevel      本次数据等级
     * @return 决策依据明细（第一行为结论摘要）
     */
    default List<String> explain(String capabilityCode, AigDataLevelEnum dataLevel) {
        return explain(capabilityCode, dataLevel, (AigRouteHint) null);
    }

    /**
     * 供预览/排障：返回决策依据明细（带场景）。
     *
     * @param capabilityCode 能力编码
     * @param dataLevel      本次数据等级
     * @param scenarioCode   场景编码（可为 null/空）
     * @return 决策依据明细（第一行为结论摘要）
     */
    default List<String> explain(String capabilityCode, AigDataLevelEnum dataLevel, String scenarioCode) {
        return explain(capabilityCode, dataLevel, AigRouteHint.ofScenario(scenarioCode));
    }

    /**
     * 供预览/排障：返回决策依据明细（带路由提示）。
     *
     * @param capabilityCode 能力编码
     * @param dataLevel      本次数据等级
     * @param hint           路由提示（可为 null）
     * @return 决策依据明细（第一行为结论摘要）
     */
    List<String> explain(String capabilityCode, AigDataLevelEnum dataLevel, AigRouteHint hint);

}
