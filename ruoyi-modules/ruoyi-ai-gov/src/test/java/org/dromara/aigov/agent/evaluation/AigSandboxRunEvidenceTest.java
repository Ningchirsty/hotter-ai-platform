package org.dromara.aigov.agent.evaluation;

import org.dromara.aigov.agent.domain.AigSandboxRun;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「沙箱跑通了」判据的测试（发布门槛 {@code SANDBOX_RUN}）。
 *
 * <p>判据只有四条（有记录、退出码 0、未超时、无网），但每一条都是"少一条就放行了不该放行的东西"，
 * 所以逐条钉住，另外钉住"缺字段不算通过"——那是这类账本最容易悄悄放宽的地方。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigSandboxRunEvidenceTest {

    private static AigSandboxRun row(Integer exitCode, Boolean timedOut, String network) {
        AigSandboxRun row = new AigSandboxRun();
        row.setSandboxRunId(1L);
        row.setJobId("job-1");
        row.setImageRef("nginx@sha256:" + "a".repeat(64));
        row.setExitCode(exitCode);
        row.setTimedOut(timedOut);
        row.setNetwork(network);
        row.setDurationMs(256L);
        row.setArtifactCount(2);
        row.setCreateTime(LocalDateTime.now());
        row.setAttestation("UNATTESTED");
        return row;
    }

    @Test
    @DisplayName("可信度来源如实带出，且**不影响**判据：未验签的证据照样满足（本切片不阻断发布）")
    void attestationIsCarriedButDoesNotChangeSatisfaction() {
        AigSandboxRunEvidence evidence = AigSandboxRunEvidence.evaluate(row(0, false, "none"));

        assertEquals("UNATTESTED", evidence.attestation());
        assertFalse(evidence.attested());
        // 关键：不因为"没签名"就判不满足——否则在验签实现之前没有任何版本能过闸，
        // 那是把"没有签名"变成"发布停摆"，不是更安全。
        assertTrue(evidence.satisfied());
        // 没有记录的结论也不能被读成"已签名"
        assertFalse(AigSandboxRunEvidence.unavailable("x").attested());
    }

    @Test
    @DisplayName("只有 SIGNED 才算有密码学保证（未来验签实现后的口径）")
    void onlySignedCountsAsAttested() {
        AigSandboxRun signed = row(0, false, "none");
        signed.setAttestation("SIGNED");
        assertTrue(AigSandboxRunEvidence.evaluate(signed).attested());

        AigSandboxRun blank = row(0, false, "none");
        blank.setAttestation(null);
        assertFalse(AigSandboxRunEvidence.evaluate(blank).attested());
    }

    @Test
    @DisplayName("退出码 0 + 未超时 + 无网 → 满足，且摘要里带着实测数字")
    void satisfiedOnCleanRun() {
        AigSandboxRunEvidence evidence = AigSandboxRunEvidence.evaluate(row(0, false, "none"));

        assertTrue(evidence.satisfied());
        assertNull(evidence.reason());
        assertTrue(evidence.verdictSummary().contains("退出码 0"), evidence.verdictSummary());
        assertTrue(evidence.verdictSummary().contains("none"), evidence.verdictSummary());
    }

    @Test
    @DisplayName("非零退出码 → 不满足，原因里带实际退出码")
    void notSatisfiedOnNonZeroExit() {
        AigSandboxRunEvidence evidence = AigSandboxRunEvidence.evaluate(row(1, false, "none"));

        assertFalse(evidence.satisfied());
        assertTrue(evidence.reason().contains("1"), evidence.reason());
    }

    @Test
    @DisplayName("超时被杀 → 不满足（跑起来了 ≠ 跑通了）")
    void notSatisfiedOnTimeout() {
        AigSandboxRunEvidence evidence = AigSandboxRunEvidence.evaluate(row(137, true, "none"));

        assertFalse(evidence.satisfied());
        assertTrue(evidence.reason().contains("超时"), evidence.reason());
    }

    @Test
    @DisplayName("允许了出网 → 不满足（出网策略未冻结，只有 none 可采信）")
    void notSatisfiedOnNetwork() {
        AigSandboxRunEvidence evidence = AigSandboxRunEvidence.evaluate(row(0, false, "bridge"));

        assertFalse(evidence.satisfied());
        assertTrue(evidence.reason().contains("bridge"), evidence.reason());
    }

    @Test
    @DisplayName("字段缺失（退出码/网络为空）→ 不满足，不当作通过")
    void notSatisfiedOnMissingFields() {
        assertFalse(AigSandboxRunEvidence.evaluate(row(null, false, "none")).satisfied());
        assertFalse(AigSandboxRunEvidence.evaluate(row(0, null, null)).satisfied());
        // timedOut 缺失不会被当成 false：条件里的 timedOut=true 才算拦，
        // 但网络缺失已经足以判不满足——两条缺失原因都要出现在原因里
        AigSandboxRunEvidence evidence = AigSandboxRunEvidence.evaluate(row(0, null, null));
        assertTrue(evidence.reason().contains("网络"), evidence.reason());
    }

    @Test
    @DisplayName("从未运行 → 不满足，且摘要明说没有记录")
    void notSatisfiedWhenNoRun() {
        AigSandboxRunEvidence evidence = AigSandboxRunEvidence.evaluate(null);

        assertFalse(evidence.satisfied());
        assertNull(evidence.runId());
        assertEquals("没有任何运行记录", evidence.verdictSummary());
    }

}
