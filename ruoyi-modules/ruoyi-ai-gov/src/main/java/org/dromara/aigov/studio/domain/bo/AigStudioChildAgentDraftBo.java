package org.dromara.aigov.studio.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 新增子 Agent 草案入参（附件 07 增量 7／ST-006）。
 *
 * @author ai-gov
 */
@Data
public class AigStudioChildAgentDraftBo implements Serializable {

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
     * Agent 编码（生成物里没有它：提交时用的是训练台草稿自身的编码）
     */
    @Size(max = 64, message = "Agent 编码长度不能超过 64")
    private String agentCode;

    /**
     * Agent 展示名（生成后进内容的 agentName）
     */
    @Size(max = 128, message = "Agent 名称长度不能超过 128")
    private String agentName;

    /**
     * Agent 类别（必填）
     */
    @NotBlank(message = "Agent 类别不能为空（生成物是一份 Agent 内容）")
    @Size(max = 32, message = "Agent 类别长度不能超过 32")
    private String agentCategory;

    /**
     * 父 Agent 编码（不在生成物里：草稿内容没有这个槽位）
     */
    @Size(max = 64, message = "父 Agent 编码长度不能超过 64")
    private String parentAgentCode;

    /**
     * 角色定位
     */
    @Size(max = 1000, message = "角色定位长度不能超过 1000")
    private String roleDescription;

    /**
     * 主要责任
     */
    @Size(max = 1000, message = "主要责任长度不能超过 1000")
    private String objective;

    /**
     * 禁止事项
     */
    @Size(max = 1000, message = "禁止事项长度不能超过 1000")
    private String prohibitions;

    /**
     * 输出成果说明
     */
    @Size(max = 1000, message = "输出成果说明长度不能超过 1000")
    private String outputSummary;

    /**
     * 主 Prompt 八分节（JSON 对象：分节键 → 正文）
     */
    @Size(max = 20000, message = "Prompt 分节长度不能超过 20000")
    private String promptSectionsJson;

    /**
     * 所需能力（必填）
     */
    @NotBlank(message = "所需能力（providerCapability）不能为空")
    @Size(max = 64, message = "能力编码长度不能超过 64")
    private String providerCapability;

    /**
     * 是否允许外发（Y/N，留空按 N 生成）
     */
    @Size(max = 2, message = "allowExternal 只能是 Y 或 N")
    private String allowExternal;

    /**
     * 允许工具（逗号分隔；可空）
     */
    @Size(max = 500, message = "工具清单长度不能超过 500")
    private String allowedTools;

    /**
     * 知识范围（逗号分隔；可空）
     */
    @Size(max = 500, message = "知识范围长度不能超过 500")
    private String knowledgeScope;

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
