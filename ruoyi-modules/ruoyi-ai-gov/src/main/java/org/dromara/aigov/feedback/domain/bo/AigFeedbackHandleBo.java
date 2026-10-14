package org.dromara.aigov.feedback.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 训练负责人处理反馈入参（设计 §6.9）。
 *
 * @author ai-gov
 */
@Data
public class AigFeedbackHandleBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 处理结论 */
    @NotBlank(message = "处理结论不能为空")
    @Size(max = 16, message = "处理结论长度不能超过 16")
    private String verdict;

    /** 处理动作（确认问题时必填） */
    @Size(max = 32, message = "处理动作长度不能超过 32")
    private String actionCode;

    /** 备注 */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;

}
