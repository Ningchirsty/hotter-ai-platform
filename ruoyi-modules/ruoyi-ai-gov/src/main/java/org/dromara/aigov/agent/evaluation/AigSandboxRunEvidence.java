package org.dromara.aigov.agent.evaluation;

import org.dromara.aigov.agent.domain.AigSandboxRun;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 「沙箱跑通了」的证据（发布门槛 {@code SANDBOX_RUN}：{@code VALIDATED → SANDBOX_TESTED}）。
 *
 * <p>与 {@link AigGoldenCaseEvidence} / {@link AigCanaryEvidence} 并列：三者都是发布门槛的
 * <b>证据提供方</b>，判据各自只实现一次，发布推进时只消费结论
 * （见 {@code AigAgentRegistryServiceImpl#assertSandboxRunEvidence}）。</p>
 *
 * <h3>判据（缺一不可）</h3>
 * <ol>
 *     <li><b>有运行记录</b>：该版本从来没跑过沙箱 ⇒ 没有任何东西可证明；</li>
 *     <li><b>{@code exitCode == 0}</b>：容器跑完且退出码为 0。非 0 不是"执行器故障"
 *         （那是 {@code executorRc}），而是这段代码自己没跑成；</li>
 *     <li><b>没有超时</b>：{@code timedOut=false}。超时被杀也算"跑起来了"，
 *         但"跑通了"显然不是——这两件事必须分开；</li>
 *     <li><b>{@code network == none}</b>：出网策略尚未冻结（F-09），
 *         所以「在无网条件下跑通」才是当前口径下可采信的证据。带网跑的结果也允许登记
 *         （登记是留痕，不是背书），但<b>不能据此过门槛</b>。</li>
 * </ol>
 *
 * <h3>只看最近一次，不看"历史上曾经通过"</h3>
 * <p>判据取该版本<b>最近一次</b>运行。理由：门槛问的是"这个版本现在能不能进沙箱测试通过"，
 * 而"上周跑通过、昨天改完跑不过"必须判不通过——若允许"挑一次成功的算数"，
 * 一个已经坏掉的版本可以拿着旧成绩单继续往发布链路上走。修好之后再跑一次并登记，
 * 证据自然恢复。</p>
 *
 * <p><b>产物个数不设为硬判据</b>：有的外部 Agent 冒烟运行就是没有产物。它出现在摘要里
 * 供人判断，但把它当门槛会让"没有产物的 Agent"永远过不了，而那是业务判断，不是隔离判据。</p>
 *
 * @param satisfied     是否满足（四项全过）
 * @param runId         运行记录ID（无记录时为 null）
 * @param jobId         作业ID
 * @param imageRef      实际运行的镜像 ref
 * @param exitCode      容器退出码
 * @param timedOut      是否超时被杀
 * @param network       网络模式
 * @param durationMs    执行耗时毫秒
 * @param artifactCount 产物个数
 * @param recordedAt    登记时间
 * @param recordedBy    登记人ID
 * @param reason        不满足时的可读原因（满足时为 null）
 * @author ai-gov
 */
public record AigSandboxRunEvidence(
    boolean satisfied,
    Long runId,
    String jobId,
    String imageRef,
    Integer exitCode,
    Boolean timedOut,
    String network,
    Long durationMs,
    Integer artifactCount,
    LocalDateTime recordedAt,
    Long recordedBy,
    String reason
) {

    /**
     * 网络模式：无网（唯一可采信的口径）
     */
    public static final String NETWORK_NONE = "none";

    /**
     * 按判据给出一条运行记录下结论。
     *
     * <p>刻意<b>不</b>"遇到第一条不满足就返回"：不满足的原因全部列出，
     * 否则运维修完一条再看下一条，要来回几轮才看清。</p>
     *
     * @param row 该版本最近一次运行记录（可为 null：从未运行）
     * @return 证据结论
     */
    public static AigSandboxRunEvidence evaluate(AigSandboxRun row) {
        if (row == null) {
            return unavailable("该版本没有任何沙箱运行记录");
        }
        List<String> blockers = new ArrayList<>();
        Integer exitCode = row.getExitCode();
        Boolean timedOut = row.getTimedOut();
        String network = row.getNetwork();
        if (exitCode == null) {
            blockers.add("运行结果里没有退出码（原文缺 exitCode）");
        } else if (exitCode != 0) {
            blockers.add("最近一次运行退出码为 " + exitCode + "（不是 0：这段代码自己没跑成）");
        }
        if (Boolean.TRUE.equals(timedOut)) {
            blockers.add("最近一次运行超时被杀（timedOut=true）：跑起来了但没跑通");
        }
        if (network == null || network.isBlank()) {
            blockers.add("网络模式缺失（无法确认是无网条件）");
        } else if (!NETWORK_NONE.equalsIgnoreCase(network.trim())) {
            blockers.add("最近一次运行网络模式为 " + network.trim() + "（当前只有 none 可采信；"
                + "出网策略属 F-09，尚未冻结）");
        }
        boolean ok = blockers.isEmpty();
        return new AigSandboxRunEvidence(ok, row.getSandboxRunId(), row.getJobId(), row.getImageRef(),
            exitCode, timedOut, network, row.getDurationMs(), row.getArtifactCount(),
            row.getCreateTime(), row.getRecordedBy(), ok ? null : String.join("；", blockers));
    }

    /**
     * 构造「证据不可用」的结论（压根没有记录，自然谈不上满足）。
     *
     * @param reason 原因
     * @return 结论（{@code satisfied=false}）
     */
    public static AigSandboxRunEvidence unavailable(String reason) {
        return new AigSandboxRunEvidence(false, null, null, null, null, null, null, null, null,
            null, null, reason);
    }

    /**
     * 实测口径的一句话摘要（落进错误消息，让人先看到实际数字而不是结论）。
     *
     * @return 例如 {@code 作业 job-1、镜像 nginx@sha256:...、退出码 0、耗时 256ms、产物 2 个、无网}
     */
    public String verdictSummary() {
        if (runId == null) {
            return "没有任何运行记录";
        }
        return "最近一次运行：作业 " + jobId + "、镜像 " + imageRef
            + "、退出码 " + exitCode + "、超时 " + timedOut
            + "、耗时 " + durationMs + "ms、产物 " + artifactCount + " 个、网络 " + network
            + "、登记时间 " + recordedAt;
    }

}
