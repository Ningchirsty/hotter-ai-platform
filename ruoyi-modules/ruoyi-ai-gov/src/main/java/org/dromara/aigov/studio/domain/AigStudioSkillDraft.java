package org.dromara.aigov.studio.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;

/**
 * 技能草案 {@code aig_studio_skill_draft}（附件 07 增量 7／ST-005）。
 *
 * <p><b>它是什么</b>：给某个 Agent 挂上一个技能（{@code skill_code}）以及这个技能声明了哪些能力。
 * 生成时它填的是训练台草稿内容里的 {@code allowedSkills} + 八分节里的
 * {@code tools_and_skills}，外加 Agent 的 {@code providerCapability} 与 {@code allowedTools}。</p>
 *
 * <p><b>它不是什么</b>：不是一条 Skill 记录，也不会去建 {@code aig_skill}。
 * 本仓的 Skill 注册表是 Package 安装/第三方带入的产物，Studio 没有它的写入口；
 * 草案生成器只产"一份可提交的 Agent 草稿内容"（附件 07 §286：只产草案，不等同可执行）。</p>
 *
 * <p><b>为什么必须有 {@code agentCategory}</b>：生成物是一份 Agent 内容，而类别决定它绑定哪个
 * 实现（见 {@code AigAgentCategoryEnum}）。服务层不替草案猜一个——猜出来的绑定与内容毫不相干，
 * 而且要到提交时才会暴露。缺它就拒绝生成，与训练台"提交时必须声明类别"同一口径。</p>
 *
 * @author ai-gov
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aig_studio_skill_draft")
public class AigStudioSkillDraft extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 草案ID
     */
    @TableId(value = "skill_draft_id")
    private Long skillDraftId;

    /**
     * 草案编码（同一租户内唯一；页面上靠它认出草案）
     */
    private String draftCode;

    /**
     * 草案名称
     */
    private String draftName;

    /**
     * 技能编码（生成后落到草稿内容的 {@code allowedSkills}）
     */
    private String skillCode;

    /**
     * 技能名称（展示用）
     */
    private String skillName;

    /**
     * 该技能声明的能力编码（逗号分隔；缺省时生成用 {@link #providerCapability}）
     */
    private String capabilities;

    /**
     * 技能做什么（生成后进 {@code tools_and_skills} 分节）
     */
    private String description;

    /**
     * 使用说明（生成后进 {@code workflow_rules} 分节；可空）
     */
    private String usageNotes;

    /**
     * Agent 类别（必填；见类注释"为什么必须有 agentCategory"）
     */
    private String agentCategory;

    /**
     * 生成的 Agent 声明的所需能力（路由只认能力；必填）
     */
    private String providerCapability;

    /**
     * 生成的 Agent 允许使用的工具（逗号分隔；可空）
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
     * 是否启用（Y/N；停用的草案不在生成入口里出现）
     */
    private String enabled;

    /**
     * 删除标志（0代表存在 1代表删除）
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注
     */
    private String remark;

}
