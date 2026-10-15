package org.dromara.aigov.feedback.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 员工提交结果反馈入参（设计 §6.9）。
 *
 * @author ai-gov
 */
@Data
public class AigResultFeedbackBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 关联任务（可空：直接成果也可反馈） */
    private Long taskId;

    /** 关联结果 */
    private Long resultId;

    /** 关联 Agent 版本 */
    private Long agentVersionId;

    /** 关联知识版本 */
    private Long knowledgeVersionId;

    /** 反馈原因 */
    @NotBlank(message = "反馈原因不能为空")
    @Size(max = 32, message = "反馈原因长度不能超过 32")
    private String reasonCode;

    /** 补充说明引用（受限原文走受控存储，不直接落库） */
    @Size(max = 500, message = "补充说明长度不能超过 500")
    private String detailRef;

}
