package org.dromara.aigov.feedback.metrics;

import org.dromara.common.core.utils.StringUtils;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 运营指标计算（设计 §6.9）。
 *
 * <p><b>口径必须写清，否则数字不可复核</b>（这是本仓反复吃过的亏）：</p>
 * <ul>
 *     <li><b>累计成果 = 经确认的结果记录</b>，不是"模型输出条数"——把所有输出都算成果，
 *         指标会好看但没有任何业务含义（设计 §6.9 明确要求）；</li>
 *     <li>分母为 0 时一律返回 0，而不是 0/0 的 NaN 或抛错——空周期是常态。</li>
 * </ul>
 *
 * <p>纯函数：输入是已经数好的计数，不查库，因此可以逐条单测口径。</p>
 *
 * @author ai-gov
 */
public final class AigOperationsMetrics {

    private AigOperationsMetrics() {
    }

    /**
     * 指标输入（都是"已经数好的计数"）。
     *
     * @param completedTasks   完成任务数
     * @param confirmedResults 经确认的结果数（= 累计成果）
     * @param firstPassSuccess 首次通过数
     * @param reworkTasks      返工任务数
     * @param citationsTotal   引用总数
     * @param citationsValid   有效引用数
     * @param policyBlocked    策略阻断次数
     * @param modelCost        模型成本（美元）
     */
    public record Input(long completedTasks, long confirmedResults, long firstPassSuccess,
                        long reworkTasks, long citationsTotal, long citationsValid,
                        long policyBlocked, BigDecimal modelCost) {
    }

    /**
     * 指标结果。
     *
     * @param completedTasks   完成任务数
     * @param confirmedResults 经确认的结果数（累计成果）
     * @param adoptionRate     结果采纳率
     * @param firstPassRate    首次通过率
     * @param reworkRate       返工率
     * @param citationValidRate 引用有效率
     * @param policyBlocked    策略阻断次数
     * @param modelCost        模型成本
     */
    public record Result(long completedTasks, long confirmedResults, double adoptionRate,
                         double firstPassRate, double reworkRate, double citationValidRate,
                         long policyBlocked, BigDecimal modelCost) {
    }

    /**
     * 计算指标。
     *
     * @param input 输入
     * @return 结果
     */
    public static Result compute(Input input) {
        if (input == null) {
            return new Result(0, 0, 0, 0, 0, 0, 0, BigDecimal.ZERO);
        }
        return new Result(
            input.completedTasks(),
            input.confirmedResults(),
            rate(input.confirmedResults(), input.completedTasks()),
            rate(input.firstPassSuccess(), input.completedTasks()),
            rate(input.reworkTasks(), input.completedTasks()),
            rate(input.citationsValid(), input.citationsTotal()),
            input.policyBlocked(),
            input.modelCost() == null ? BigDecimal.ZERO : input.modelCost());
    }

    /**
     * 安全比率：分母 ≤ 0 时返回 0。
     *
     * @param numerator   分子
     * @param denominator 分母
     * @return 比率（保留 4 位小数）
     */
    public static double rate(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0d;
        }
        double raw = (double) numerator / (double) denominator;
        return Math.round(raw * 10000d) / 10000d;
    }

    /**
     * 故障分类计数（设计 §6.9「故障分类」）。
     *
     * <p>口径：按任务的错误分类编码计数；空编码统一记为 {@code UNKNOWN}
     * （把"没分类"混进某个具体分类会掩盖分类本身的问题）。排序：次数降序、编码升序，
     * 让报表输出稳定（同样的数据每次得到同样的顺序，才谈得上比对）。</p>
     *
     * @param errorCodes 错误分类编码（可空、可含空白/null）
     * @return 分类 → 次数（按次数降序）
     */
    public static Map<String, Long> faultClassCounts(List<String> errorCodes) {
        Map<String, Long> counts = new LinkedHashMap<>();
        if (errorCodes == null) {
            return counts;
        }
        for (String code : errorCodes) {
            String key = StringUtils.isBlank(code) ? "UNKNOWN" : code.trim();
            counts.merge(key, 1L, Long::sum);
        }
        return counts.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                .thenComparing(Map.Entry.comparingByKey()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a,
                LinkedHashMap::new));
    }

}
