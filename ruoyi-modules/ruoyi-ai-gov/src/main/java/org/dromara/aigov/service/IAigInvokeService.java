package org.dromara.aigov.service;

import org.dromara.aigov.domain.bo.AigInvokeBo;
import org.dromara.aigov.domain.vo.AigInvokeVo;

/**
 * AI 能力调用编排（唯一对外调用入口）。
 * <p>所有模型调用都必须经过本服务：先路由决策，再选调用器，<b>失败也必须落审计</b>。</p>
 *
 * @author ai-gov
 */
public interface IAigInvokeService {

    /**
     * 同步调用一次能力。
     * <p>流程：生成 traceId → 路由决策 → 写审计（无论成败）→ 按决策执行：
     * {@code DENIED} 直接拒绝；{@code MANUAL} 转人工待确认；
     * {@code MODEL} 选调用器执行并校验 {@code output_schema}。</p>
     *
     * @param bo 调用入参
     * @return 调用结果
     */
    AigInvokeVo invoke(AigInvokeBo bo);

    /**
     * 只做路由决策，不真调用，不写审计。
     *
     * @param bo 调用入参
     * @return 决策结果
     */
    AigInvokeVo dryRun(AigInvokeBo bo);

}
