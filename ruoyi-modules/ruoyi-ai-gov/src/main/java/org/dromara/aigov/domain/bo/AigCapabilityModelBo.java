package org.dromara.aigov.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.dromara.aigov.domain.AigCapabilityModel;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;

import java.io.Serial;
import java.io.Serializable;

/**
 * AI 能力与模型绑定业务对象 aig_capability_model
 * <p>同一能力可绑定多个模型，用 {@code usageType} + {@code priority} 表达
 * 主选/灰度/备选与挑选顺序（PRIMARY → GRAY → FALLBACK，同级按 priority 升序）。</p>
 *
 * @author ai-gov
 */
@Data
@AutoMapper(target = AigCapabilityModel.class, reverseConvertGenerate = false)
public class AigCapabilityModelBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 绑定ID（编辑时必填）
     */
    @NotNull(message = "绑定ID不能为空", groups = {EditGroup.class})
    private Long bindId;

    /**
     * 能力编码
     */
    @NotBlank(message = "能力编码不能为空", groups = {AddGroup.class, EditGroup.class})
    @Size(max = 64, message = "能力编码长度不能超过 64", groups = {AddGroup.class, EditGroup.class})
    private String capabilityCode;

    /**
     * 关联 sai_model_config.id
     */
    @NotNull(message = "模型ID不能为空", groups = {AddGroup.class, EditGroup.class})
    private Long modelId;

    /**
     * 用途（PRIMARY主选 FALLBACK备选 GRAY灰度）
     */
    @NotBlank(message = "用途不能为空", groups = {AddGroup.class, EditGroup.class})
    @Pattern(regexp = "^(PRIMARY|FALLBACK|GRAY)$", message = "用途只能为 PRIMARY/FALLBACK/GRAY",
        groups = {AddGroup.class, EditGroup.class})
    private String usageType;

    /**
     * 优先级，数值越小越优先
     */
    @Min(value = 0, message = "优先级不能小于 0", groups = {AddGroup.class, EditGroup.class})
    @Max(value = 9999, message = "优先级不能大于 9999", groups = {AddGroup.class, EditGroup.class})
    private Integer priority;

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
