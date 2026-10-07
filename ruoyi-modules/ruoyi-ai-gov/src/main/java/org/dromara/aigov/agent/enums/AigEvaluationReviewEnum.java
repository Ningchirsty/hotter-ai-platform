package org.dromara.aigov.agent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 人工复核结论（{@code aig_evaluation_run.review_result}）。
 *
 * <p><b>为什么 {@link #MANUAL} 必须存在，且必须挡住门槛</b>：设计 §13.2 的用例带
 * {@code rubric_json}（人工评分 Rubric），即「这条用例的最后判断在人手里」。如果平台在
 * 机器判据通过之后就当成「黄金用例已通过」，那 Rubric 就成了一条没人执行的装饰；
 * 反过来，如果 {@link #MANUAL} 因为是「待复核」而被忽略，结果就是<b>待复核 = 通过</b>。
 * 因此门槛的判据是：{@code review_result ∈ {null, PASS}}，{@link #MANUAL} 与
 * {@link #FAIL} 都不放行。</p>
 *
 * <p>{@code null} 表示「这条用例不要求人工复核」（用例没有 Rubric），与「要复核但还没复核」
 * 是两件事，刻意不合并。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigEvaluationReviewEnum {

    /**
     * 人工复核通过
     */
    PASS("PASS", "人工复核通过"),

    /**
     * 人工复核不通过
     */
    FAIL("FAIL", "人工复核不通过"),

    /**
     * 待人工复核（Rubric 用例的初始状态）
     */
    MANUAL("MANUAL", "待人工复核");

    /**
     * 编码（入库值）
     */
    private final String code;

    /**
     * 描述
     */
    private final String desc;

    /**
     * 按 code 查找，找不到返回 null。
     *
     * @param code 编码
     * @return 枚举；未命中返回 null
     */
    public static AigEvaluationReviewEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AigEvaluationReviewEnum item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return null;
    }

}
