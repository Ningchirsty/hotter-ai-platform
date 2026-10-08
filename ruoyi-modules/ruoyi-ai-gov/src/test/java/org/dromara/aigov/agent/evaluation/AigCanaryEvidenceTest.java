package org.dromara.aigov.agent.evaluation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 灰度达标判据（a+b+c）的纯逻辑测试。
 *
 * <p><b>为什么单测这一段</b>：这是「能不能从灰度转正式」的<b>唯一</b>判据，
 * 而它的错误形态恰好都不会报错——阈值比较写反、边界写成 {@code <} 而不是 {@code ≤}、
 * 或者干脆漏掉某一项，表现为「某些版本被安静地放行」。这里逐条钉住边界值。</p>
 *
 * <p>阈值取 {@code 50 次 / 5% / 严重错误 0}（与 {@code AigCanaryProperties} 默认值一致）。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigCanaryEvidenceTest {

    private static final AigCanaryEvidence.Thresholds T =
        new AigCanaryEvidence.Thresholds(50, 0.05, 0);

    private static final LocalDateTime FROM = LocalDateTime.of(2026, 10, 8, 10, 0);
    private static final LocalDateTime TO = LocalDateTime.of(2026, 10, 8, 12, 0);

    private static AigCanaryEvidence evaluate(long total, long failed, Map<String, Long> byClass) {
        return AigCanaryEvidence.evaluate(FROM, TO, total, failed, byClass, T);
    }

    @Test
    @DisplayName("三项都满足 → 达标，且没有原因")
    void satisfiedWhenAllThreePass() {
        AigCanaryEvidence e = evaluate(100L, 3L, Map.of());

        assertTrue(e.satisfied());
        assertNull(e.reason(), "达标时不该有原因");
        assertEquals(100L, e.totalInvocations());
        assertEquals(3L, e.failedInvocations());
        assertEquals(0.03, e.failureRate(), 1e-9);
        assertEquals(0L, e.severeErrorCount());
        assertEquals(FROM, e.windowFrom(), "窗口起点要随证据返回：页面要能解释「统计的是哪一段时间」");
        assertEquals(TO, e.windowTo());
    }

    @Test
    @DisplayName("边界：调用次数恰好等于 N → 达标（判据是「≥」）")
    void exactlyMinInvocationsPasses() {
        assertTrue(evaluate(50L, 0L, Map.of()).satisfied(),
            "恰好 50 次应当达标；写成严格大于会把门槛悄悄抬高一次");
    }

    @Test
    @DisplayName("边界：少一次（49）→ 不达标，原因说明还差多少")
    void oneBelowMinInvocationsBlocks() {
        AigCanaryEvidence e = evaluate(49L, 0L, Map.of());

        assertFalse(e.satisfied());
        assertTrue(e.reason().contains("调用次数不足"), e.reason());
        assertTrue(e.reason().contains("50"), "要报出阈值：" + e.reason());
        assertTrue(e.reason().contains("49"), "要报出实测值：" + e.reason());
    }

    @Test
    @DisplayName("★ 零调用不能算达标：没有样本时「失败率 0%」是假象")
    void zeroInvocationsIsNotSatisfied() {
        AigCanaryEvidence e = evaluate(0L, 0L, Map.of());

        assertFalse(e.satisfied(), "零调用时失败率必然是 0，若不先看样本量，门槛等于不存在");
        assertEquals(0d, e.failureRate(), 1e-9, "零调用时失败率定义为 0，不能是 NaN");
        assertTrue(e.reason().contains("调用次数不足"), e.reason());
    }

    @Test
    @DisplayName("边界：失败率恰好等于上限 → 达标（判据是「≤」）")
    void exactlyFailureRateLimitPasses() {
        // 1/20 = 5%，阈值恰好 0.05；把 N 设为 20 以便单独验失败率边界
        AigCanaryEvidence e = AigCanaryEvidence.evaluate(FROM, TO, 20L, 1L, Map.of(),
            new AigCanaryEvidence.Thresholds(20, 0.05, 0));

        assertTrue(e.satisfied(), "恰好 5% 应当达标（≤），写成严格小于会与口径不符：" + e.reason());
        assertEquals(0.05, e.failureRate(), 1e-9);
    }

    @Test
    @DisplayName("失败率超上限 → 不达标，原因给出分子分母（便于对账）")
    void failureRateOverLimitBlocks() {
        AigCanaryEvidence e = AigCanaryEvidence.evaluate(FROM, TO, 20L, 2L, Map.of(),
            new AigCanaryEvidence.Thresholds(20, 0.05, 0));

        assertFalse(e.satisfied());
        assertTrue(e.reason().contains("失败率超标"), e.reason());
        assertTrue(e.reason().contains("2 / 共 20"), "要给出分子分母：" + e.reason());
    }

    @Test
    @DisplayName("★ 出现 1 个严重错误即不达标（允许 0 个），并给出分类明细")
    void oneSevereErrorBlocks() {
        AigCanaryEvidence e = evaluate(100L, 1L, Map.of("POLICY_DENIED", 1L));

        assertFalse(e.satisfied());
        assertEquals(1L, e.severeErrorCount());
        assertEquals(1L, e.severeByClass().get("POLICY_DENIED"));
        assertTrue(e.reason().contains("出现严重错误"), e.reason());
        assertTrue(e.reason().contains("POLICY_DENIED"),
            "只报总数，运维还得自己翻日志才知道是策略拒绝还是鉴权失败：" + e.reason());
    }

    @Test
    @DisplayName("非严重分类（限流/超时/不可用/未归类）不触发 (c)，只计入失败率 (b)")
    void transientErrorsAreNotSevere() {
        Map<String, Long> byClass = new LinkedHashMap<>();
        byClass.put("RATE_LIMITED", 2L);
        byClass.put("TIMEOUT", 1L);
        byClass.put("UNAVAILABLE", 1L);
        byClass.put("UNKNOWN", 1L);

        AigCanaryEvidence e = evaluate(100L, 5L, byClass);

        assertEquals(0L, e.severeErrorCount(), "上游临时状态不是「版本错了」，不该一次即否决");
        assertTrue(e.severeByClass().isEmpty());
        assertTrue(e.satisfied(), "5% 恰好在上限内，应当达标：" + e.reason());
    }

    @Test
    @DisplayName("★ 三项同时不满足 → 原因一次列全（不是只报第一条）")
    void allBlockersAreReportedTogether() {
        AigCanaryEvidence e = evaluate(40L, 8L, Map.of("AUTH_FAILED", 2L));

        assertFalse(e.satisfied());
        assertTrue(e.reason().contains("调用次数不足"), e.reason());
        assertTrue(e.reason().contains("失败率超标"), e.reason());
        assertTrue(e.reason().contains("出现严重错误"), e.reason());
    }

    @Test
    @DisplayName("严重错误只统计严重分类：同一次统计里混入的临时错误不计入")
    void severeCountIgnoresTransientClassesInSameMap() {
        Map<String, Long> byClass = new LinkedHashMap<>();
        byClass.put("TIMEOUT", 9L);
        byClass.put("OUTPUT_UNPARSABLE", 2L);
        byClass.put("QUOTA_EXCEEDED", 1L);

        AigCanaryEvidence e = evaluate(200L, 12L, byClass);

        assertEquals(3L, e.severeErrorCount(), "只有 OUTPUT_UNPARSABLE 与 QUOTA_EXCEEDED 算严重");
        assertFalse(e.severeByClass().containsKey("TIMEOUT"));
        assertEquals(2L, e.severeByClass().get("OUTPUT_UNPARSABLE"));
    }

    @Test
    @DisplayName("严重分类的判定：大小写不敏感、空值不算严重")
    void isSevereIsCaseInsensitiveAndNullSafe() {
        assertTrue(AigCanaryEvidence.isSevere("policy_denied"));
        assertTrue(AigCanaryEvidence.isSevere("  AUTH_FAILED  "));
        assertFalse(AigCanaryEvidence.isSevere("timeout"));
        assertFalse(AigCanaryEvidence.isSevere(null));
        assertFalse(AigCanaryEvidence.isSevere("   "));
        assertFalse(AigCanaryEvidence.isSevere("SOMETHING_ELSE"),
            "认不出的分类不算严重（它仍计入失败率），不能凭猜测否决整轮灰度");
    }

    @Test
    @DisplayName("证据不可用：不达标且保留原因（窗口都确定不了，谈不上达标）")
    void unavailableIsNotSatisfied() {
        AigCanaryEvidence e = AigCanaryEvidence.unavailable("没有进入 CANDIDATE 的记录", T);

        assertFalse(e.satisfied());
        assertEquals("没有进入 CANDIDATE 的记录", e.reason());
        assertEquals(0L, e.totalInvocations());
        assertEquals(T, e.thresholds());
    }

    @Test
    @DisplayName("摘要同时给出实测数字与门槛：看的人不必再去翻配置")
    void verdictSummaryCarriesNumbersAndThresholds() {
        String summary = evaluate(40L, 5L, Map.of()).verdictSummary();

        assertTrue(summary.contains("调用 40 次"), summary);
        assertTrue(summary.contains("失败 5 次"), summary);
        assertTrue(summary.contains("严重错误 0 个"), summary);
        assertTrue(summary.contains("≥50 次"), summary);
        assertTrue(summary.contains("≤5.00%"), summary);
    }

    @Test
    @DisplayName("无严重错误时明细摘要为「无」，不是空串")
    void severeSummarySaysNone() {
        assertEquals("无", evaluate(100L, 1L, Map.of()).severeSummary());
        assertEquals("AUTH_FAILED=1", evaluate(100L, 1L, Map.of("AUTH_FAILED", 1L)).severeSummary());
    }

}
