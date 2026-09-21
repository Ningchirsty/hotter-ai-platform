package org.dromara.hrtalent.domain.bo.recruitment;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 月度计划任务动作入参（SPEC-P2 §3.2：{@code pause}/{@code resume}/{@code cancel}）。
 *
 * <p>「人工暂停或取消时同时显示控制原因」，因此 {@code pause} 与 {@code cancel}
 * 必须填写 {@code reason}，为空则给中文提示；{@code resume} 不需要原因。</p>
 *
 * <p>{@code carryoverEnabled} 允许业务管理员在结转前单独设置该任务的结转开关
 * （设计文档 §7.1.2：「业务管理员可以在结转前设置 {@code carryover_enabled}」）。</p>
 *
 * @author hr-talent
 */
@Data
public class RecruitPlanItemActionBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 计划任务ID（通常由路径回填）
     */
    private Long itemId;

    /**
     * 暂停或取消原因
     */
    @Size(max = 500, message = "控制原因长度不能超过 500")
    private String reason;

    /**
     * 是否允许自动结转（0否 1是）；为空表示不调整该开关
     */
    @Pattern(regexp = "^(0|1)?$", message = "是否允许自动结转只能为 0 或 1")
    private String carryoverEnabled;

}
