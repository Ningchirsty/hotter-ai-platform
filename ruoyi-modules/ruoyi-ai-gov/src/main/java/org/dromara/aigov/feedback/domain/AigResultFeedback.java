package org.dromara.aigov.feedback.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 结果反馈 aig_result_feedback（设计 §6.9、§8.2）。
 *
 * <p>必须关联 {@code taskId/resultId/agentVersionId}：缺一条就无法回答"这是哪次运行、哪个版本
 * 产出的哪条结果"——而反馈的价值正在于能定位到具体运行，否则只是一条感受。</p>
 *
 * @author ai-gov
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aig_result_feedback")
public class AigResultFeedback extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 反馈ID */
    @TableId(value = "feedback_id")
    private Long feedbackId;

    /** 关联任务 */
    private Long taskId;

    /** 关联结果 */
    private Long resultId;

    /** 关联 Agent 版本 */
    private Long agentVersionId;

    /** 关联知识版本 */
    private Long knowledgeVersionId;

    /** 反馈原因 */
    private String reasonCode;

    /** 处理结论 */
    private String verdict;

    /** 处理动作 */
    private String actionCode;

    /** 补充说明引用 */
    private String detailRef;

    /** 反馈人 */
    private Long reporterId;

    /** 处理人 */
    private Long handledBy;

    /** 处理时间 */
    private LocalDateTime handledAt;

    /** 记录状态 */
    private String status;

    /** 删除标志 */
    @TableLogic
    private String delFlag;

    /** 备注 */
    private String remark;

}
