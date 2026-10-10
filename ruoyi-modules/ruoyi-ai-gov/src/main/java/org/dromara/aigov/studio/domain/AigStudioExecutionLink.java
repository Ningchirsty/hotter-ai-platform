package org.dromara.aigov.studio.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;

/**
 * 训练台测试证据链 {@code aig_studio_execution_link}（专题 C §C9、§C11）。
 *
 * <p><b>为什么必须钉在 {@link #revisionId} + {@link #contentHash} 上</b>：测试的对象是
 * "某一刻的不可变内容"。只记 {@code draftId} 的话，测完又改了草稿，这条证据就指向了一段
 * **从未被测过的内容**——而发布门槛要的恰恰是"这份内容被真实跑通过"。</p>
 *
 * <p><b>{@link #contentHash} 是对 {@code revision.content_hash} 的冗余留档</b>：
 * 即使那条修订后来被逻辑删除，也能证明"当时测的就是这份内容"。</p>
 *
 * <p><b>本表不判定门槛</b>：它只如实记录"跑没跑、跑成什么样"。
 * 是否达到 {@code SANDBOX_RUN}/黄金用例门槛，由既有发布状态机判定——
 * 让证据表自己宣布"达标"，就又造了一个"看着生效"的字段。</p>
 *
 * @author ai-gov
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aig_studio_execution_link")
public class AigStudioExecutionLink extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 关联ID
     */
    @TableId(value = "link_id")
    private Long linkId;

    /**
     * 所属草稿（便于按草稿聚合）
     */
    private Long draftId;

    /**
     * 被测试的修订（测试对象是不可变快照）
     */
    private Long revisionId;

    /**
     * 测试时该修订的内容哈希（冗余留档，防修订被删后无法核对）
     */
    private String contentHash;

    /**
     * 本次测试使用的 Agent 版本（用草稿快照测试时为空）
     */
    private Long agentVersionId;

    /**
     * 真实一次执行的任务ID（{@code aig_task.task_id}）
     */
    private Long executionId;

    /**
     * 黄金用例运行ID（{@code aig_evaluation_run.eval_run_id}）
     */
    private Long evalRunId;

    /**
     * 调用链追踪ID（关联 {@code aig_invocation_audit.trace_id}）
     */
    private String traceId;

    /**
     * 测试状态（{@code AigStudioTestStatusEnum}）
     */
    private String testStatus;

    /**
     * 结果摘要哈希（对输出算 sha256）
     */
    private String resultDigest;

    /**
     * 失败原因（用户可读；不得含密钥与受限原文）
     */
    private String errorMessage;

    /**
     * 删除标志（0代表存在 1代表删除）
     */
    @TableLogic
    private String delFlag;

}
