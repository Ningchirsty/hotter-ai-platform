package org.dromara.aigov.task.domain.bo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 人工复核入参（任务级：通过 / 拒绝）。
 *
 * <p><b>与「候选资产选定」是两件事</b>：任务级复核决定「这个任务算不算通过」，
 * 候选选定决定「用哪一张图」。两者都只能由人工做，但分开表达才不会出现
 * 「复核通过了但其实一张都没选」这种账实不符的状态。</p>
 *
 * @author ai-gov
 */
@Data
public class AigTaskReviewBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 任务ID
     */
    @NotNull(message = "任务ID不能为空")
    private Long taskId;

    /**
     * 期望的当前版本（乐观锁；必填）
     */
    @NotNull(message = "期望版本不能为空（复核必须带乐观锁版本）")
    private Integer expectedVersion;

    /**
     * 是否通过
     */
    @NotNull(message = "复核结论不能为空")
    private Boolean approved;

    /**
     * 复核意见（拒绝时必填）
     */
    @Size(max = 500, message = "复核意见长度不能超过 500")
    private String comment;

}
