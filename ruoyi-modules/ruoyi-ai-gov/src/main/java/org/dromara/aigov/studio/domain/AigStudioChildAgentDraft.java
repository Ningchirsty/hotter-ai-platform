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
 * 子 Agent 草案 {@code aig_studio_child_agent_draft}（附件 07 增量 7／ST-006）。
 *
 * <p><b>它是什么</b>：一份完整的 Agent 定义草稿——类别、角色/目标/禁止事项、主 Prompt 八分节、
 * 能力与工具/知识声明，外加"挂在哪个父 Agent 下"（{@link #parentAgentCode}）。</p>
 *
 * <p><b>它不是什么</b>：不是一条 {@code aig_agent}，也不建版本。生成物只是训练台草稿内容，
 * 提交仍走既有通道（产 DRAFT 版本 → 五道门槛）。</p>
 *
 * <p><b>为什么 {@code parentAgentCode} 不进生成物</b>：训练台草稿内容模型里没有"父 Agent"这个槽位
 * （那是 {@code aig_agent} 表上的归属关系，不是内容字段）。硬塞进内容只会让提交时丢掉。
 * 因此它留在草案表里，并由生成结果明确告知"这一项没有被写进内容"——
 * 静默丢失比不写更糟。</p>
 *
 * @author ai-gov
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aig_studio_child_agent_draft")
public class AigStudioChildAgentDraft extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 草案ID
     */
    @TableId(value = "child_agent_draft_id")
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
     * 生成物要绑定的 Agent 编码（提交时会以它查重/新建 Agent 定义）
     */
    private String agentCode;

    /**
     * Agent 展示名（生成后进内容的 {@code agentName}）
     */
    private String agentName;

    /**
     * Agent 类别（必填；决定绑定哪个实现）
     */
    private String agentCategory;

    /**
     * 父 Agent 编码（不在生成物里，见类注释）
     */
    private String parentAgentCode;

    /**
     * 角色定位（进内容的 {@code roleDescription}）
     */
    private String roleDescription;

    /**
     * 主要责任（进内容的 {@code objective}）
     */
    private String objective;

    /**
     * 禁止事项（进内容的 {@code prohibitions}）
     */
    private String prohibitions;

    /**
     * 输出成果说明（进内容的 {@code outputSummary}）
     */
    private String outputSummary;

    /**
     * 主 Prompt 八分节（JSON 对象：分节键 → 正文；缺的分节补空串，保证八节齐全）
     */
    private String promptSectionsJson;

    /**
     * 所需能力（路由只认能力；必填）
     */
    private String providerCapability;

    /**
     * 是否允许外发（Y/N；缺省 N——不允许外发是更安全的那一侧）
     */
    private String allowExternal;

    /**
     * 允许工具（逗号分隔；可空）
     */
    private String allowedTools;

    /**
     * 知识范围（逗号分隔；可空）
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
     * 删除标志（0代表存在 1代表删除）
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注
     */
    private String remark;

}
