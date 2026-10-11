package org.dromara.aigov.studio.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 新增快捷指令草案入参（附件 07 增量 7／ST-007）。
 *
 * @author ai-gov
 */
@Data
public class AigStudioActionDraftBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 草案编码（必填）
     */
    @NotBlank(message = "草案编码不能为空")
    @Size(max = 64, message = "草案编码长度不能超过 64")
    private String draftCode;

    /**
     * 草案名称
     */
    @Size(max = 128, message = "草案名称长度不能超过 128")
    private String draftName;

    /**
     * 指令编码
     */
    @Size(max = 64, message = "指令编码长度不能超过 64")
    private String actionCode;

    /**
     * 指令名称
     */
    @Size(max = 128, message = "指令名称长度不能超过 128")
    private String actionName;

    /**
     * 启动方式（{@code AigActionLaunchModeEnum}：QUICK/FORM/STUDIO/NAVIGATION）
     */
    @Size(max = 16, message = "启动方式长度不能超过 16")
    private String launchMode;

    /**
     * 启动目标类型（{@code AigLaunchTargetTypeEnum}）
     */
    @Size(max = 32, message = "启动目标类型长度不能超过 32")
    private String targetType;

    /**
     * 启动目标引用
     */
    @Size(max = 128, message = "启动目标引用长度不能超过 128")
    private String targetRef;

    /**
     * Agent 类别（必填）
     */
    @NotBlank(message = "Agent 类别不能为空（生成物是一份 Agent 内容）")
    @Size(max = 32, message = "Agent 类别长度不能超过 32")
    private String agentCategory;

    /**
     * 指令目标（进内容的 objective）
     */
    @Size(max = 1000, message = "指令目标长度不能超过 1000")
    private String objective;

    /**
     * 产出要求（进内容的 output_contract 分节）
     */
    @Size(max = 1000, message = "产出要求长度不能超过 1000")
    private String outputContract;

    /**
     * 所需能力（必填）
     */
    @NotBlank(message = "所需能力（providerCapability）不能为空")
    @Size(max = 64, message = "能力编码长度不能超过 64")
    private String providerCapability;

    /**
     * 允许工具（逗号分隔；可空）
     */
    @Size(max = 500, message = "工具清单长度不能超过 500")
    private String allowedTools;

    /**
     * 归属组织
     */
    private Long orgId;

    /**
     * 是否启用（Y/N，留空默认 Y）
     */
    private String enabled;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;

}
