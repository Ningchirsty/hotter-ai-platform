package org.dromara.aigov.domain.bo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 调用授权审批的<b>决定</b>入参（批准/驳回共用一次请求）。
 *
 * <p>批准与驳回走同一个接口与同一个入参对象，是因为它们是同一个动作的两面：
 * 都要「必须是 PENDING、不得超时、不得自审」这三道校验。分成两个接口只会让
 * 那三道校验写两遍——迟早有一边漏掉一条。</p>
 *
 * <p>{@code approved} 用包装类型 {@code Boolean} 而不是 {@code boolean}：
 * 需要区分「明确驳回」与「忘了传」——后者若按默认 false 处理，会把一次漏传
 * 静默变成一次驳回。</p>
 *
 * @author ai-gov
 */
@Data
public class AigCallApprovalDecideBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 是否批准（true 批准 / false 驳回；必填）
     */
    @NotNull(message = "请明确是批准还是驳回")
    private Boolean approved;

    /**
     * 审批意见（驳回时必填；批准时可选）
     */
    @Size(max = 500, message = "审批意见长度不能超过 500")
    private String remark;

}
