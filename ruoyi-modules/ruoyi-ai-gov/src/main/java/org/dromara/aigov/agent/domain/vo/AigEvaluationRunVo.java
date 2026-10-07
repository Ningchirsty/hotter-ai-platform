package org.dromara.aigov.agent.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.aigov.agent.domain.AigEvaluationRun;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 评测运行列表视图（裁掉 {@code scoreJson} 明细，要看逐项分数走详情）。
 *
 * @author ai-gov
 */
@Data
@AutoMapper(target = AigEvaluationRun.class)
public class AigEvaluationRunVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 运行ID
     */
    private Long runId;

    /**
     * 运行编号
     */
    private String runNo;

    /**
     * 评测对象类型
     */
    private String targetType;

    /**
     * 评测对象版本ID
     */
    private Long targetVersionId;

    /**
     * 黄金用例ID
     */
    private Long caseId;

    /**
     * 实际执行的 Provider
     */
    private Long providerId;

    /**
     * 实际使用的模型编码
     */
    private String modelCode;

    /**
     * 是否发生外部调用（Y/N）
     */
    private String externalCall;

    /**
     * 总分
     */
    private BigDecimal totalScore;

    /**
     * 人工复核结论
     */
    private String reviewResult;

    /**
     * 复核人
     */
    private Long reviewerId;

    /**
     * 复核时间
     */
    private LocalDateTime reviewedAt;

    /**
     * 实际成本
     */
    private BigDecimal costAmount;

    /**
     * 端到端耗时（毫秒）
     */
    private Long latencyMs;

    /**
     * 调用链ID
     */
    private String traceId;

    /**
     * 运行状态（RUNNING/PASS/FAIL/ERROR）
     */
    private String resultStatus;

    /**
     * 运行时间
     */
    private LocalDateTime operateTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 备注
     */
    private String remark;

}
