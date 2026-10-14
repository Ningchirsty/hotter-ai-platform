package org.dromara.aigov.feedback.service;

import org.dromara.aigov.feedback.metrics.AigOperationsMetrics;

/**
 * 运营指标聚合服务（设计 §6.9）。
 *
 * <p>与 {@link AigOperationsMetrics}（纯计算）分工：本层负责<b>取数</b>，口径由纯函数固定。
 * 这样"数字怎么算"可以逐条单测，"从哪取"随表结构演进也不影响口径。</p>
 *
 * @author ai-gov
 */
public interface IAigOperationsMetricsService {

    /**
     * 计算运营指标。
     *
     * @return 指标
     */
    AigOperationsMetrics.Result compute();

}
