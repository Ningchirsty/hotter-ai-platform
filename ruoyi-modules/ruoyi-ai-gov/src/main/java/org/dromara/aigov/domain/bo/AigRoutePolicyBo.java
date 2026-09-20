package org.dromara.aigov.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.dromara.aigov.domain.AigRoutePolicy;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;

import java.io.Serial;
import java.io.Serializable;

/**
 * AI 路由策略业务对象 aig_route_policy
 * <p>按「能力 × 数据等级」配置，判定口径：<b>无策略即拒绝</b>，不默认放行。</p>
 *
 * @author ai-gov
 */
@Data
@AutoMapper(target = AigRoutePolicy.class, reverseConvertGenerate = false)
public class AigRoutePolicyBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 策略ID（编辑时必填）
     */
    @NotNull(message = "策略ID不能为空", groups = {EditGroup.class})
    private Long policyId;

    /**
     * 能力编码
     */
    @NotBlank(message = "能力编码不能为空", groups = {AddGroup.class, EditGroup.class})
    @Size(max = 64, message = "能力编码长度不能超过 64", groups = {AddGroup.class, EditGroup.class})
    private String capabilityCode;

    /**
     * 数据等级（PUBLIC/INTERNAL/RESTRICTED）
     */
    @NotBlank(message = "数据等级不能为空", groups = {AddGroup.class, EditGroup.class})
    @Pattern(regexp = "^(PUBLIC|INTERNAL|RESTRICTED)$", message = "数据等级只能为 PUBLIC/INTERNAL/RESTRICTED",
        groups = {AddGroup.class, EditGroup.class})
    private String dataLevel;

    /**
     * 优先部署类型（LOCAL/GROUP/EXTERNAL_ENTERPRISE/EXTERNAL_API）
     */
    @Pattern(regexp = "^(LOCAL|GROUP|EXTERNAL_ENTERPRISE|EXTERNAL_API)?$",
        message = "优先部署类型只能为 LOCAL/GROUP/EXTERNAL_ENTERPRISE/EXTERNAL_API",
        groups = {AddGroup.class, EditGroup.class})
    private String preferredDeployment;

    /**
     * 是否允许外发（Y允许 N禁止）
     * <p><b>风险项</b>：置为 Y 意味着该数据等级下的数据可送往外部模型。</p>
     */
    @NotBlank(message = "是否允许外发不能为空", groups = {AddGroup.class, EditGroup.class})
    @Pattern(regexp = "^(Y|N)$", message = "是否允许外发只能为 Y/N", groups = {AddGroup.class, EditGroup.class})
    private String allowExternal;

    /**
     * 调用前是否需要审批（Y是 N否；阶段1 仅作为路由判定，审批流二期）
     */
    @Pattern(regexp = "^(Y|N)?$", message = "是否需要审批只能为 Y/N", groups = {AddGroup.class, EditGroup.class})
    private String requireApproval;

    /**
     * 无可用模型时是否转人工待办（Y是 N否）
     */
    @Pattern(regexp = "^(Y|N)?$", message = "是否转人工只能为 Y/N", groups = {AddGroup.class, EditGroup.class})
    private String fallbackToManual;

    /**
     * 状态（0正常 1停用）
     */
    @Pattern(regexp = "^(0|1)?$", message = "状态只能为 0/1", groups = {AddGroup.class, EditGroup.class})
    private String status;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过 500", groups = {AddGroup.class, EditGroup.class})
    private String remark;

}
