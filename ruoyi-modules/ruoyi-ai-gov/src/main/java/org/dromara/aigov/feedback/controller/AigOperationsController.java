package org.dromara.aigov.feedback.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.aigov.constant.AigConstants;
import org.dromara.aigov.feedback.metrics.AigOperationsMetrics;
import org.dromara.aigov.feedback.service.IAigOperationsMetricsService;
import org.dromara.common.core.domain.R;
import org.dromara.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 运营指标接口（设计 §6.9）。
 *
 * <p>指标口径由 {@link AigOperationsMetrics}（纯函数）固定：<b>累计成果 = 经确认的结果记录</b>，
 * 不是模型输出条数；分母为 0 时返回 0。当前可算与不可算的项已在聚合服务里写明
 * （引用有效率、模型成本暂无可聚合数据源，如实置 0）。</p>
 *
 * @author ai-gov
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/aigov/operations")
public class AigOperationsController extends BaseController {

    private final IAigOperationsMetricsService metricsService;

    /**
     * 运营指标。
     *
     * @return 指标
     */
    @SaCheckPermission(AigConstants.PERM_OPS_METRICS)
    @GetMapping("/metrics")
    public R<AigOperationsMetrics.Result> metrics() {
        return R.ok(metricsService.compute());
    }

    /**
     * 故障分类计数。
     *
     * @return 分类 → 次数（次数降序）
     */
    @SaCheckPermission(AigConstants.PERM_OPS_METRICS)
    @GetMapping("/fault-classes")
    public R<Map<String, Long>> faultClasses() {
        return R.ok(metricsService.faultClassCounts());
    }

}
