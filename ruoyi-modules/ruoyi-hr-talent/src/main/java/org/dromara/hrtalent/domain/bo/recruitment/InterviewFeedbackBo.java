package org.dromara.hrtalent.domain.bo.recruitment;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 面试反馈业务对象。
 * <p>对应接口 {@code POST /recruit/interviews/{id}/feedback}（§7.3、§8.6）。</p>
 *
 * <p><b>个人意见与汇总结论分离</b>：</p>
 * <ul>
 *     <li>{@link #score} / {@link #feedback} 是<b>当前登录面试官本人</b>的评分与意见，
 *     写入 {@code hr_recruit_interviewer} 的对应行；</li>
 *     <li>{@link #result} / {@link #overallScore} / {@link #conclusion} 是可选<b>汇总结论</b>，
 *     写入 {@code hr_recruit_interview} 的 {@code result}/{@code score}/{@code feedback}；
 *     提供 {@code result} 即视为提交面试结论，会发布面试结果变化事件。</li>
 * </ul>
 *
 * <p>不允许在入参中指定面试官用户ID：操作人一律取当前登录用户，
 * 非本场面试官提交时给出中文提示。</p>
 *
 * @author hr-talent
 */
@Data
public class InterviewFeedbackBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 面试记录ID（必填）
     */
    @NotNull(message = "面试记录ID不能为空")
    private Long interviewId;

    /**
     * 当前面试官个人评分
     */
    @DecimalMin(value = "0", message = "面试评分不能为负数")
    private BigDecimal score;

    /**
     * 当前面试官个人意见/结论（必填，多人面试分别保存）
     */
    @NotBlank(message = "面试意见不能为空")
    private String feedback;

    /**
     * 汇总结论（字典 recruit_interview_result 编码：pass/fail/reserve/absent；不填表示只提交个人意见）
     */
    @Size(max = 32, message = "面试结果长度不能超过 32")
    private String result;

    /**
     * 汇总评分（不填时取所有已反馈面试官评分的平均值）
     */
    @DecimalMin(value = "0", message = "汇总评分不能为负数")
    private BigDecimal overallScore;

    /**
     * 汇总结论文本（可选，写入面试记录的汇总意见）
     */
    private String conclusion;

}
