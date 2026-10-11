package org.dromara.aigov.studio.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.aigov.studio.domain.AigStudioSkillDraft;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 技能草案视图。
 *
 * @author ai-gov
 */
@Data
@AutoMapper(target = AigStudioSkillDraft.class)
public class AigStudioSkillDraftVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 草案ID
     */
    private Long skillDraftId;

    /**
     * 草案编码
     */
    private String draftCode;

    /**
     * 草案名称
     */
    private String draftName;

    /**
     * 技能编码
     */
    private String skillCode;

    /**
     * 技能名称
     */
    private String skillName;

    /**
     * 技能声明的能力编码（逗号分隔）
     */
    private String capabilities;

    /**
     * 技能说明
     */
    private String description;

    /**
     * 使用说明
     */
    private String usageNotes;

    /**
     * Agent 类别
     */
    private String agentCategory;

    /**
     * 所需能力
     */
    private String providerCapability;

    /**
     * 允许工具（逗号分隔）
     */
    private String allowedTools;

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
