package org.dromara.aigov.task.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 统一任务 aig_task（设计 §9.1 任务类型 + §9.2 状态机 + §9.3 输入快照）。
 *
 * <p><b>字段级口径以 {@code script/sql/aig_ai_task.sql} 的列注释为准</b>，此处只补
 * 「为什么这么设计」的部分。</p>
 *
 * <p><b>{@link #version} 是状态迁移的正确性基础</b>：同一个任务可能被三路同时推进——
 * 用户点取消、Provider 回调完成、调度器超时置失败。没有乐观锁时三者互相覆盖，
 * 最终状态取决于写库的先后，而审计里会有三段互相矛盾的迁移记录。
 * 项目已注册 {@code OptimisticLockerInnerInterceptor}，因此这里只需标注 {@code @Version}，
 * 迁移语句会自动带上 {@code version} 条件并在冲突时返回 0 行（由服务层据此报「状态已被并发修改」）。</p>
 *
 * <p><b>{@link #attemptNo} 是幂等键的一半</b>：{@code task_id + attempt_no} 唯一确定一次调用，
 * 重试不重复计费（设计 §4.5）。因此重试时必须<b>递增</b>该值，而不是复用。</p>
 *
 * @author ai-gov
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aig_task")
public class AigTask extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 任务ID
     */
    @TableId(value = "task_id")
    private Long taskId;

    /**
     * 任务号（不可猜测的业务编号，对外不暴露连续ID）
     */
    private String taskNo;

    /**
     * 任务类型（{@code AigTaskTypeEnum}）
     */
    private String taskType;

    /**
     * 业务能力编码（业务侧只传能力，不传厂商参数）
     */
    private String capabilityCode;

    /**
     * 业务场景（LONG_PAGE/POSTER/MULTI_IMAGE/VIDEO…）
     */
    private String scenarioCode;

    /**
     * 所属业务域（CONTENT/CREATIVE/TALENT…）
     */
    private String projectType;

    /**
     * 业务对象ID（刻意不加外键：跨域引用，加了外键会让各域的清理互相牵制）
     */
    private Long projectId;

    /**
     * 发起该任务的 Agent 版本ID（人工直接发起时为空）
     */
    private Long agentVersionId;

    /**
     * 数据等级（PUBLIC/INTERNAL/RESTRICTED/STRICT）
     */
    private String dataLevel;

    /**
     * 业务侧是否允许外发（Y/N）；与路由策略<b>取与</b>，两者都允许才可能外发
     */
    private String allowExternal;

    /**
     * 状态（{@code AigTaskStatusEnum}）
     */
    private String status;

    /**
     * 已尝试次数（幂等键的一半）
     */
    private Integer attemptNo;

    /**
     * 最大自动尝试次数
     */
    private Integer maxAttempt;

    /**
     * 外部提交幂等键（同一提交人+键只建一个任务）
     */
    private String idempotencyKey;

    /**
     * 当前使用的不可变输入快照ID
     */
    private Long inputSnapshotId;

    /**
     * 路由快照（最终 Provider/模型/调用器/策略版本）。
     * <p>执行与排障的<b>唯一依据</b>：执行期间治理配置会变，
     * 只有快照能回答「当时是按什么决策执行的」。</p>
     */
    private String routeSnapshot;

    /**
     * 策略判定结果（PASS/REJECT/MANUAL）
     */
    private String policyResult;

    /**
     * 策略判定原因（拒绝/转人工的可读说明，不允许空泛文案）
     */
    private String policyReason;

    /**
     * 实际执行的外部 Provider 编码
     */
    private String providerCode;

    /**
     * 外部 Provider 异步作业ID（回调据此定位任务）
     */
    private String providerJobId;

    /**
     * 进度（0-100）
     */
    private Integer progress;

    /**
     * 结果类型（STRUCTURED/ASSET/SESSION）
     */
    private String resultType;

    /**
     * 本次是否发生外部调用（Y/N）
     */
    private String externalCall;

    /**
     * 成本金额。<b>算不出留空，禁止填 0 冒充</b>——0 是「确定免费」，空是「不知道」，
     * 混淆这两者会让费用汇总变成一个看起来精确的错数。
     */
    private BigDecimal costAmount;

    /**
     * 端到端耗时（毫秒）
     */
    private Long latencyMs;

    /**
     * 调用链追踪ID（关联 aig_invocation_audit.trace_id）
     */
    private String traceId;

    /**
     * 结构化错误码（驱动重试与转人工，取值同 {@code AigErrorClassEnum}）
     */
    private String errorCode;

    /**
     * 可读失败原因
     */
    private String errorMessage;

    /**
     * 人工复核状态（PENDING/APPROVED/REJECTED）
     */
    private String reviewStatus;

    /**
     * 复核人
     */
    private Long reviewedBy;

    /**
     * 复核时间
     */
    private LocalDateTime reviewedAt;

    /**
     * 复核意见
     */
    private String reviewComment;

    /**
     * 开始执行时间
     */
    private LocalDateTime startedAt;

    /**
     * 结束时间
     */
    private LocalDateTime finishedAt;

    /**
     * 乐观锁版本号（状态迁移防并发覆盖）
     */
    @Version
    private Integer version;

    /**
     * 删除标志（0代表存在 1代表删除）
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注
     */
    private String remark;

}
