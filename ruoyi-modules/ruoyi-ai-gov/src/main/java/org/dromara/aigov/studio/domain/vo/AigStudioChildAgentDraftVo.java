package org.dromara.aigov.studio.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.aigov.studio.domain.AigStudioChildAgentDraft;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 子 Agent 草案视图。
 *
 * @author ai-gov
 */
@Data
@AutoMapper(target = AigStudioChildAgentDraft.class)
public class AigStudioChildAgentDraftVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 草案ID
     */
    private Long childAgentDraftId;

    /**
     * 草案编码
     */
    private String draftCode;

    /**
     * 草案名称
     */
    private String draftName;

    /**
     * Agent 编码
     */
    private String agentCode;

    /**
     * Agent 展示名
     */
    private String agentName;

    /**
     * Agent 类别
     */
    private String agentCategory;

    /**
     * 父 Agent 编码
     */
    private String parentAgentCode;

    /**
     * 角色定位
     */
    private String roleDescription;

    /**
     * 主要责任
     */
    private String objective;

    /**
     * 禁止事项
     */
    private String prohibitions;

    /**
     * 输出成果说明
     */
    private String outputSummary;

    /**
     * 主 Prompt 八分节（JSON 对象）
     */
    private String promptSectionsJson;

    /**
     * 所需能力
     */
    private String providerCapability;

    /**
     * 是否允许外发（Y/N）
     */
    private String allowExternal;

    /**
     * 允许工具（逗号分隔）
     */
    private String allowedTools;

    /**
     * 知识范围（逗号分隔）
     */
    private String knowledgeScope;

    /**
     * 责任人
     */
    private Long ownerId;

    /**
     * 归属组织
     */
    private Long orgId;

    /**
     * 是否启用（Y/N）
     */
    private String enabled;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 备注
     */
    private String remark;

}
