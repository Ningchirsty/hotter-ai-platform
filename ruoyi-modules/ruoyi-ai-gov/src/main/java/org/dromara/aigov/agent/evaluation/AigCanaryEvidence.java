package org.dromara.aigov.agent.evaluation;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 「灰度是否达标」的证据（发布门槛 {@code CANARY}：{@code CANDIDATE → STABLE}）。
 *
 * <p><b>为什么要有这样一个结论对象</b>：与
 * {@link AigGoldenCaseEvidence} 同样的理由——判据要被两个地方用到（页面查询、
 * 发布推进时的门槛校验），写两遍必然走样（一份放行、一份拦住）。因此判据只在这里算一次。</p>
 *
 * <h3>判据（a+b+c，缺一不可）</h3>
 * <ol>
 *     <li><b>调用次数 ≥ N</b>：没有样本，"失败率低"只是样本少，不是质量好；</li>
 *     <li><b>失败率 ≤ X%</b>：分母是灰度期内归属该版本的全部调用（含失败）；
 *         判据是「≤」，等于阈值算达标；</li>
 *     <li><b>严重错误 ≤ 允许值（默认 0）</b>：见 {@link #SEVERE_CLASS_CODES}。</li>
 * </ol>
 *
 * <h3>灰度窗口：从「进入 CANDIDATE 的那一刻」起</h3>
 * <p>统计的是<b>灰度期内</b>的调用，不是这个版本有史以来的全部调用——拿进入灰度之前
 * 的表现来证明灰度达标是两回事。窗口起点取发布事件账本里该对象
 * {@code to_status=CANDIDATE} 的那条事件时间（版本不可变、CANDIDATE 没有回边，
 * 因此至多一条）；终点是查询时刻。</p>
 *
 * <p><b>为什么严重错误的界定不交给调用方</b>：门槛的判据必须是平台口径。
 * 但"哪些分类算严重"是业务判断，因此做成一个显式常量而不是散在代码里 if-else。</p>
 *
 * @param satisfied            是否达标（三项全部满足）
 * @param windowFrom           灰度窗口起点（进入 CANDIDATE 的时间）
 * @param windowTo             灰度窗口终点（查询时刻）
 * @param totalInvocations     窗口内归属该版本的调用总数
 * @param failedInvocations    其中失败（{@code result='1'}）的次数
 * @param failureRate          失败率（无调用时为 0）
 * @param severeErrorCount     严重错误次数
 * @param severeByClass        严重错误按分类的明细（分类 → 次数），便于直接定位
 * @param thresholds           本次采用的阈值（随证据一起返回，页面据此解释「差在哪」）
 * @param reason               不达标时的可读原因（达标时为 null）
 * @author ai-gov
 */
public record AigCanaryEvidence(
    boolean satisfied,
    LocalDateTime windowFrom,
    LocalDateTime windowTo,
    long totalInvocations,
    long failedInvocations,
    double failureRate,
    long severeErrorCount,
    Map<String, Long> severeByClass,
    Thresholds thresholds,
    String reason
) {

    /**
     * 本次判定采用的阈值。
     *
     * @param minInvocations      最少调用次数
     * @param failureRateLimit    失败率上限
     * @param severeErrorsAllowed 允许的严重错误次数
     */
    public record Thresholds(int minInvocations, double failureRateLimit, int severeErrorsAllowed) {
    }

    /**
     * 判定为「严重错误」的分类编码。
     *
     * <p>共同的判据是：<b>这些失败说明「版本或平台配置错了」，而不是「当时环境不好」</b>。
     * 上游暂时忙可以等、可以换候选；而下面这些重试一万次还是同样的结论，因此一次
     * 就足以说明这个版本不该转正式。</p>
     *
     * <ul>
     *     <li>{@code POLICY_DENIED}：策略/外发拒绝——版本要的东西被治理规则禁止，
     *         说明它的配置与治理口径不符；</li>
     *     <li>{@code INVALID_REQUEST}：入参/输出 Schema 错——版本自己的 schema 或提示词有问题；</li>
     *     <li>{@code OUTPUT_UNPARSABLE}：结果不可解析——输出不符合能力模板，同样是版本自身问题；</li>
     *     <li>{@code AUTH_FAILED}：鉴权失败——平台凭据错，这段时间的调用都会失败，
     *         拿它当"灰度表现"是在评价一个坏掉的通道，不是评价这个版本；</li>
     *     <li>{@code QUOTA_EXCEEDED}：额度/余额不足——同上，外部资源断供期间的表现不可作证据。</li>
     * </ul>
     *
     * <p><b>刻意不算严重的：</b>{@code RATE_LIMITED} / {@code TIMEOUT} / {@code UNAVAILABLE}
     * 是上游临时状态，{@code UNKNOWN} 是未能归类——它们都<b>仍然计入失败率</b>（判据 b），
     * 只是不单独触发"一次即否决"。把 {@code UNKNOWN} 也算严重会让一个认不出的偶发抖动
     * 否决整轮灰度，而它已经有了失败率这道闸。</p>
     */
    public static final List<String> SEVERE_CLASS_CODES = List.of(
        "POLICY_DENIED", "INVALID_REQUEST", "OUTPUT_UNPARSABLE", "AUTH_FAILED", "QUOTA_EXCEEDED");

    /**
     * 某个错误分类是否算「严重」。
     *
     * @param errorClass 错误分类编码（可空）
     * @return 严重返回 true
     */
    public static boolean isSevere(String errorClass) {
        if (errorClass == null || errorClass.isBlank()) {
            return false;
        }
        String code = errorClass.trim();
        for (String severe : SEVERE_CLASS_CODES) {
            if (severe.equalsIgnoreCase(code)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 按 a+b+c 判定，并给出<b>全部</b>未达标的原因。
     *
     * <p>刻意不「遇到第一条不满足就返回」：运维看到「调用次数不足」调完再看，
     * 又冒出「失败率超标」，来回两轮才能把问题看全。</p>
     *
     * @param windowFrom    窗口起点
     * @param windowTo      窗口终点
     * @param total         调用总数
     * @param failed        失败次数
     * @param byClass       窗口内各错误分类的次数（可含非严重分类，由本方法筛）
     * @param thresholds    阈值
     * @return 证据结论
     */
    public static AigCanaryEvidence evaluate(LocalDateTime windowFrom, LocalDateTime windowTo,
                                            long total, long failed,
                                            Map<String, Long> byClass, Thresholds thresholds) {
        Map<String, Long> severe = new LinkedHashMap<>();
        long severeCount = 0L;
        if (byClass != null) {
            for (Map.Entry<String, Long> entry : byClass.entrySet()) {
                if (isSevere(entry.getKey())) {
                    long count = entry.getValue() == null ? 0L : entry.getValue();
                    severe.put(entry.getKey(), count);
                    severeCount += count;
                }
            }
        }
        double rate = total == 0L ? 0d : (double) failed / (double) total;

        List<String> blockers = new ArrayList<>();
        if (total < thresholds.minInvocations()) {
            blockers.add("调用次数不足：需要 ≥ " + thresholds.minInvocations()
                + " 次，灰度期内实际 " + total + " 次");
        }
        if (rate > thresholds.failureRateLimit()) {
            blockers.add("失败率超标：上限 " + percent(thresholds.failureRateLimit())
                + "，实际 " + percent(rate) + "（失败 " + failed + " / 共 " + total + "）");
        }
        if (severeCount > thresholds.severeErrorsAllowed()) {
            blockers.add("出现严重错误：允许 " + thresholds.severeErrorsAllowed()
                + " 个，实际 " + severeCount + " 个（" + describeMap(severe) + "）");
        }
        boolean ok = blockers.isEmpty();
        return new AigCanaryEvidence(ok, windowFrom, windowTo, total, failed, rate,
            severeCount, severe, thresholds, ok ? null : String.join("；", blockers));
    }

    /**
     * 构造「证据不可用」的结论（窗口都确定不了，自然谈不上达标）。
     *
     * @param reason     原因
     * @param thresholds 阈值
     * @return 结论（{@code satisfied=false}）
     */
    public static AigCanaryEvidence unavailable(String reason, Thresholds thresholds) {
        return new AigCanaryEvidence(false, null, null, 0L, 0L, 0d, 0L, Map.of(),
            thresholds, reason);
    }

    /**
     * 实测口径的一句话摘要（落进错误消息，让人先看到实际数字而不是结论）。
     *
     * @return 例如 {@code 调用 40 次、失败 5 次（12.50%）、严重错误 0 个；门槛：≥50 次 / ≤5.00% / 严重错误 ≤0}
     */
    public String verdictSummary() {
        return "调用 " + totalInvocations + " 次、失败 " + failedInvocations
            + " 次（" + percent(failureRate) + "）、严重错误 " + severeErrorCount
            + " 个；门槛：≥" + thresholds.minInvocations() + " 次 / ≤"
            + percent(thresholds.failureRateLimit()) + " / 严重错误 ≤"
            + thresholds.severeErrorsAllowed();
    }

    /**
     * 严重错误明细的可读形式。
     *
     * @return 例如 {@code POLICY_DENIED=1、AUTH_FAILED=2}；无内容时返回「无」
     */
    public String severeSummary() {
        if (severeByClass == null || severeByClass.isEmpty()) {
            return "无";
        }
        return describeMap(severeByClass);
    }

    /**
     * 百分比格式化（保留两位小数，与阈值同口径，避免 0.0499999 这类浮点尾巴影响阅读）。
     *
     * @param rate 比率
     * @return 例如 {@code 5.00%}
     */
    private static String percent(double rate) {
        return String.format(Locale.ROOT, "%.2f%%", rate * 100d);
    }

    /**
     * 把「分类 → 次数」拼成可读文本。
     *
     * @param counts 计数
     * @return 例如 {@code POLICY_DENIED=1、AUTH_FAILED=2}
     */
    private static String describeMap(Map<String, Long> counts) {
        if (counts == null || counts.isEmpty()) {
            return "无";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Long> entry : counts.entrySet()) {
            if (sb.length() > 0) {
                sb.append('、');
            }
            sb.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return sb.toString();
    }

}
