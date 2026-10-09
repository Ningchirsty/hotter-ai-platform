package org.dromara.aigov.agent.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 评测运行 aig_evaluation_run（设计 §13.2 + §5.4「进 STABLE 前的证据」）。
 *
 * <p><b>一次运行 = 一个版本 × 一个用例</b>：这样「哪条用例挂了」能直接定位，
 * 而不是从一份汇总报告里反推。{@link #runNo} 是不可猜测的编号，对外引用它
 * （评审、工单、审计）而不暴露连续 ID。</p>
 *
 * <p><b>{@link #reviewResult} 与 {@link #resultStatus} 是两个不同的问题</b>：
 * 前者是<b>人对这次评测结论的复核</b>（PASS/FAIL/MANUAL），后者是<b>这次运行本身</b>
 * 跑完了没有（RUNNING/PASS/FAIL/ERROR）。把两者合成一个字段，
 * 就会出现「评测跑完了、人工还没看」这种最常见的中间态无处表达。</p>
 *
 * <p><b>{@link #providerId}/{@link #modelCode} 跟着实际执行走</b>（与审计同口径）：
 * 评测结果必须能回答「是哪个供应商的哪个模型跑出来的」，否则同一条用例换供应商后
 * 分数变化，无法判断是版本改好了还是供应商变了。</p>
 *
 * @author ai-gov
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aig_evaluation_run")
public class AigEvaluationRun extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 评测运行ID
     */
    @TableId(value = "run_id")
    private Long runId;

    /**
     * 运行编号（不可猜测，对外引用用）
     */
    private String runNo;

    /**
     * 评测对象类型（AGENT_VERSION/SKILL_VERSION/PACKAGE_VERSION）
     */
    private String targetType;

    /**
     * 评测对象版本ID
     */
    private Long targetVersionId;

    /**
     * 所用黄金用例ID
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
     * 结论产出方（{@code PLATFORM}=平台执行器跑的；{@code ADMIN}=管理员人工评测后录入）。
     *
     * <p>见 {@link org.dromara.aigov.agent.enums.AigEvaluationExecutorEnum}：发布门槛只认
     * {@code result_status=PASS}，不区分谁产出的；两种来源都合法但可信度来源不同，
     * 因此必须能在库里分开，而不是把人工结论伪装成机器结论。</p>
     */
    private String executedBy;

    /**
     * 本次是否发生外部调用（Y/N）
     */
    private String externalCall;

    /**
     * 打分明细（逐项分数与依据）
     */
    private String scoreJson;

    /**
     * 总分（算不出留空）
     */
    private BigDecimal totalScore;

    /**
     * 人工复核结论（PASS/FAIL/MANUAL）
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
     * 实际成本（算不出留空）
     */
    private BigDecimal costAmount;

    /**
     * 端到端耗时（毫秒）
     */
    private Long latencyMs;

    /**
     * 调用链ID（关联 aig_invocation_audit.trace_id）
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
     * 删除标志（0代表存在 1代表删除）；评测报告是证据，正常情况不应删除
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注
     */
    private String remark;

}
