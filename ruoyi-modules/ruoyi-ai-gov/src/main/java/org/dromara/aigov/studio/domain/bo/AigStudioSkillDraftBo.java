package org.dromara.aigov.studio.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 新增技能草案入参（附件 07 增量 7／ST-005）。
 *
 * @author ai-gov
 */
@Data
public class AigStudioSkillDraftBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 草案编码（必填；页面上靠它认出草案）
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
     * 技能编码（必填；生成后落到草稿内容的 allowedSkills）
     */
    @NotBlank(message = "技能编码不能为空")
    @Size(max = 64, message = "技能编码长度不能超过 64")
    private String skillCode;

    /**
     * 技能名称
     */
    @Size(max = 128, message = "技能名称长度不能超过 128")
    private String skillName;

    /**
     * 技能声明的能力编码（逗号分隔；写进 coreCapabilities，不参与路由）
     */
    @Size(max = 500, message = "能力清单长度不能超过 500")
    private String capabilities;

    /**
     * 技能说明
     */
    @Size(max = 1000, message = "技能说明长度不能超过 1000")
    private String description;

    /**
     * 使用说明
     */
    @Size(max = 1000, message = "使用说明长度不能超过 1000")
    private String usageNotes;

    /**
     * Agent 类别（必填：决定绑定哪个实现，平台不替草案猜）
     */
    @NotBlank(message = "Agent 类别不能为空（生成物是一份 Agent 内容）")
    @Size(max = 32, message = "Agent 类别长度不能超过 32")
    private String agentCategory;

    /**
     * 生成的 Agent 声明的所需能力（必填：没有它无法路由）
     */
    @NotBlank(message = "所需能力（providerCapability）不能为空")
    @Size(max = 64, message = "能力编码长度不能超过 64")
    private String providerCapability;

    /**
     * 生成的 Agent 允许使用的工具（逗号分隔；可空）
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
