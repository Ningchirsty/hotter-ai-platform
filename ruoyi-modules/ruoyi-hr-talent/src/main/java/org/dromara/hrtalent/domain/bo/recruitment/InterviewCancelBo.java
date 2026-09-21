package org.dromara.hrtalent.domain.bo.recruitment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 面试取消业务对象。
 * <p>对应接口 {@code POST /recruit/interviews/{id}/cancel}（§7.3、§8.6）。
 * 取消必须填写原因，原因写入 {@code cancel_reason} 并追加到面试记录备注，作为操作留痕。</p>
 *
 * @author hr-talent
 */
@Data
public class InterviewCancelBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 面试记录ID（必填）
     */
    @NotNull(message = "面试记录ID不能为空")
    private Long interviewId;

    /**
     * 取消原因（必填）
     */
    @NotBlank(message = "取消原因不能为空")
    @Size(max = 500, message = "取消原因长度不能超过 500")
    private String cancelReason;

}
