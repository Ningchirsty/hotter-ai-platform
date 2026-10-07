package org.dromara.aigov.service;

import org.dromara.aigov.domain.vo.AigRouteDecision;
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
        return decide(capabilityCode, dataLevel, null);
    }

    /**
     * 依据 能力 + 数据等级 + 场景 决定执行路径（设计 §4.4 步骤 4）。
     *
     * <p>{@code scenarioCode} 用来承载「场景强制绑定 Provider」：命中绑定后，
     * 候选集合会被收窄为「只保留指定供应商下的模型」。<b>收窄只会让可选项变少</b>——
     * 它不能让任何被策略/数据等级/生命周期/健康状态排除的模型重新变得可用。
     * 因此场景参数不参与权限与安全判定，只影响「在已允许的候选里挑谁」。</p>
     *
     * @param capabilityCode 能力编码
     * @param dataLevel      本次数据等级
     * @param scenarioCode   场景编码（可为 null/空，表示不做场景收窄）
     * @return 路由决策（含 policyHits 明细），不抛异常
     */
    AigRouteDecision decide(String capabilityCode, AigDataLevelEnum dataLevel, String scenarioCode);

    /**
     * 供预览/排障：返回决策依据明细。
     *
     * @param capabilityCode 能力编码
     * @param dataLevel      本次数据等级
     * @return 决策依据明细（第一行为结论摘要）
     */
    default List<String> explain(String capabilityCode, AigDataLevelEnum dataLevel) {
        return explain(capabilityCode, dataLevel, null);
    }

    /**
     * 供预览/排障：返回决策依据明细（带场景）。
     *
     * @param capabilityCode 能力编码
     * @param dataLevel      本次数据等级
     * @param scenarioCode   场景编码（可为 null/空）
     * @return 决策依据明细（第一行为结论摘要）
     */
    List<String> explain(String capabilityCode, AigDataLevelEnum dataLevel, String scenarioCode);

}
