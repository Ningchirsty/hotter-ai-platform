package org.dromara.aigov.feedback.metrics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 运营指标口径测试（设计 §6.9）。
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigOperationsMetricsTest {

    @Test
    @DisplayName("★ 累计成果 = 经确认的结果，采纳率以其为分子（不是模型输出条数）")
    void adoptionUsesConfirmedResults() {
        AigOperationsMetrics.Result result = AigOperationsMetrics.compute(
            new AigOperationsMetrics.Input(100, 60, 40, 20, 200, 190, 7, new BigDecimal("12.50")));

        assertEquals(100, result.completedTasks());
        assertEquals(60, result.confirmedResults());
        assertEquals(0.6, result.adoptionRate());
        assertEquals(0.4, result.firstPassRate());
        assertEquals(0.2, result.reworkRate());
        assertEquals(0.95, result.citationValidRate());
        assertEquals(7, result.policyBlocked());
        assertEquals(new BigDecimal("12.50"), result.modelCost());
    }

    @Test
    @DisplayName("★ 分母为 0 时返回 0（空周期是常态，不能出 NaN/异常）")
    void zeroDenominatorYieldsZero() {
        AigOperationsMetrics.Result result = AigOperationsMetrics.compute(
            new AigOperationsMetrics.Input(0, 5, 0, 0, 0, 0, 0, null));

        assertEquals(0d, result.adoptionRate());
        assertEquals(0d, result.firstPassRate());
        assertEquals(0d, result.reworkRate());
        assertEquals(0d, result.citationValidRate());
        assertEquals(BigDecimal.ZERO, result.modelCost());
    }

    @Test
    @DisplayName("null 输入不抛异常，返回全零")
    void nullInputIsSafe() {
        AigOperationsMetrics.Result result = AigOperationsMetrics.compute(null);
        assertEquals(0, result.completedTasks());
        assertEquals(0d, result.adoptionRate());
    }

    @Test
    @DisplayName("比率保留 4 位小数（避免长尾小数让报表读不清）")
    void rateIsRounded() {
        assertEquals(0.3333, AigOperationsMetrics.rate(1, 3));
        assertEquals(0d, AigOperationsMetrics.rate(1, 0));
        assertEquals(0d, AigOperationsMetrics.rate(1, -1));
    }

}
