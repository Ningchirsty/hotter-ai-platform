package org.dromara.aigov.task.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.aigov.task.domain.AigTask;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 任务列表/详情视图。
 *
 * <p><b>只读视图，不含快照原文与事件流</b>：那两样放进
 * {@link AigTaskDetailVo}，避免列表查询把长文本（快照 JSON 是 longtext）全捞出来——
 * 一页 20 条各带上几十 KB 的快照，列表就没法用了。</p>
 *
 * @author ai-gov
 */
@Data
@AutoMapper(target = AigTask.class)
public class AigTaskVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long taskId;

    /**
     * 任务号（不可猜测的业务编号）
     */
    private String taskNo;

    private String taskType;

    /**
     * 任务类型描述（服务层回填，界面不必自己维护枚举映射）
     */
    private String taskTypeLabel;

    private String capabilityCode;

    private String scenarioCode;

    private String projectType;

    private Long projectId;

    private Long agentVersionId;

    private String dataLevel;

    private String allowExternal;

    /**
     * 状态
     */
    private String status;

    /**
     * 状态描述
     */
    private String statusLabel;

    private Integer attemptNo;

    private Integer maxAttempt;

    private Long inputSnapshotId;

    private String policyResult;

    private String policyReason;

    private String providerCode;

    private String providerJobId;

    private Integer progress;

    private String resultType;

    private String externalCall;

    private BigDecimal costAmount;

    private Long latencyMs;

    private String traceId;

    private String errorCode;

    private String errorMessage;

    private String reviewStatus;

    private Long reviewedBy;

    private LocalDateTime reviewedAt;

    private String reviewComment;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    /**
     * 乐观锁版本（前端据此发起取消/重试等操作，避免无版本提交）
     */
    private Integer version;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private String remark;

}
