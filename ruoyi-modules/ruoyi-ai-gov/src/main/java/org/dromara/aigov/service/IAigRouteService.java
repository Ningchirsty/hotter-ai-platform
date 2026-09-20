package org.dromara.aigov.service;

import org.dromara.aigov.domain.vo.AigRouteDecision;
import org.dromara.aigov.enums.AigDataLevelEnum;

import java.util.List;

/**
 * 路由引擎：依据「能力 + 数据等级」决定执行路径。
 * <p><b>不抛异常</b>，一律用 {@link AigRouteDecision#getDecision()} 表达结果：
 * {@code MODEL} / {@code MANUAL} / {@code DENIED}。</p>
 * <p>核心口径：<b>无策略即拒绝</b>（默认拒绝，不默认放行）；
 * 模型治理等级不足、生命周期不可调用、被策略禁止外发的外部部署模型一律排除。</p>
 *
 * @author ai-gov
 */
public interface IAigRouteService {

    /**
     * 依据 能力 + 数据等级 决定执行路径。
     *
     * @param capabilityCode 能力编码
     * @param dataLevel      本次数据等级
     * @return 路由决策（含 policyHits 明细），不抛异常
     */
    AigRouteDecision decide(String capabilityCode, AigDataLevelEnum dataLevel);

    /**
     * 供预览/排障：返回决策依据明细。
     *
     * @param capabilityCode 能力编码
     * @param dataLevel      本次数据等级
     * @return 决策依据明细（第一行为结论摘要）
     */
    List<String> explain(String capabilityCode, AigDataLevelEnum dataLevel);

}
