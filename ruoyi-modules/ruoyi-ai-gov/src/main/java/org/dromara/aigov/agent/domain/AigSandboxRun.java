package org.dromara.aigov.agent.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 沙箱运行证据（{@code aig_sandbox_run}，追加型账本）。
 *
 * <p><b>为什么不被 BaseEntity 收编</b>：与 {@code aig_package_rejection} /
 * {@code aig_policy_decision_log} 同口径——这是<b>证据</b>，不是可编辑的业务数据。
 * 带上 {@code del_flag}/{@code update_*} 会误导后来者以为可以改、可以删。</p>
 *
 * <p><b>它证明什么</b>：某段（外部的、不可信的）代码<b>真的</b>在隔离容器里跑过一次，
 * 镜像是什么、退出码几、有没有超时、有没有网、产物几个。它是发布门槛
 * {@code SANDBOX_RUN}（{@code VALIDATED → SANDBOX_TESTED}）的判据来源——
 * 在这张表之前，那道门槛只看调用方声明。</p>
 *
 * <p><b>摘要列与原文列的关系</b>：{@code exit_code}/{@code timed_out}/{@code network} 等列
 * 是为了能直接查询与索引；{@link #resultJson} 是执行器输出的原文，{@link #resultSha256}
 * 是服务端对原文实算的哈希。判据只看原文里那几个字段，摘要列由原文解析而来——
 * 原文改了，哈希就对不上。</p>
 *
 * @author ai-gov
 */
@Data
@TableName("aig_sandbox_run")
public class AigSandboxRun implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 沙箱运行记录ID
     */
    @TableId(value = "sandbox_run_id")
    private Long sandboxRunId;

    /**
     * 对象类型（AGENT_VERSION/SKILL_VERSION/PACKAGE_VERSION）
     */
    private String targetType;

    /**
     * 对象版本ID
     */
    private Long targetVersionId;

    /**
     * 作业ID（worker 队列目录名；唯一：同一作业只能登记一次）
     */
    private String jobId;

    /**
     * 作业里的 Agent 编码（可空）
     */
    private String agentCode;

    /**
     * 实际运行的镜像 ref（必须在白名单里，执行器已强制）
     */
    private String imageRef;

    /**
     * 容器退出码（0=跑通；124/137=超时或被杀）
     */
    private Integer exitCode;

    /**
     * 是否超时被杀
     */
    private Boolean timedOut;

    /**
     * 执行耗时毫秒
     */
    private Long durationMs;

    /**
     * 网络模式（none=无网；bridge=允许出网）
     */
    private String network;

    /**
     * 作业结束时 scratch 剩余 MB
     */
    private Long scratchFreeMb;

    /**
     * 产物个数
     */
    private Integer artifactCount;

    /**
     * result.json 原文 SHA-256（服务端实算）
     */
    private String resultSha256;

    /**
     * result.json 原文（证据本体）
     */
    private String resultJson;

    /**
     * 登记人ID
     */
    private Long recordedBy;

    /**
     * 登记时间
     */
    private LocalDateTime createTime;

}
