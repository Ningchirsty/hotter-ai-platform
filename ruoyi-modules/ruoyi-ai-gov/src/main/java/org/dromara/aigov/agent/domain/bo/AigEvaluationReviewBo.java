package org.dromara.aigov.agent.domain.bo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 评测人工复核入参（设计 §13.2 的 Rubric 评分）。
 *
 * <p>{@code totalScore} 的<b>量纲由 Rubric 自己定义</b>，平台不发明一套 0-100 分制：
 * 不同用例的 Rubric 可能用「5 档」也可能用「扣分制」，平台只负责如实存档并保证能落库
 * （{@code decimal(6,2)} 能表达的范围内）。</p>
 *
 * @author ai-gov
 */
@Data
public class AigEvaluationReviewBo {

    /**
     * 评测运行ID
     */
    private Long runId;

    /**
     * 复核结论（PASS/FAIL；MANUAL 是「待复核」状态，不能作为复核结论提交）
     */
    private String reviewResult;

    /**
     * 人工总分（可空；量纲由 Rubric 定义）
     */
    private BigDecimal totalScore;

    /**
     * 复核人
     */
    private Long reviewerId;

    /**
     * 复核说明
     */
    private String remark;

}
