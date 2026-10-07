package org.dromara.aigov.agent.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;

/**
 * Skill 定义 aig_skill（设计 §5.1：Skill 是可复用原子能力）。
 *
 * <p><b>Agent 与 Skill 的分工</b>：Agent 是设计师可选的<b>工作流单元</b>（有输入输出 Schema、
 * 有 Prompt、有 Gate）；Skill 是<b>可被多个 Agent 复用的原子能力</b>（如「参考图配色提取」）。
 * 因此 Skill 不带 Prompt 与 Gate，只有能力清单与工具策略（在版本上）。</p>
 *
 * @author ai-gov
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aig_skill")
public class AigSkill extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Skill ID
     */
    @TableId(value = "skill_id")
    private Long skillId;

    /**
     * Skill 编码（唯一，跨版本稳定）
     */
    private String skillCode;

    /**
     * Skill 名称
     */
    private String skillName;

    /**
     * 能力清单（逗号分隔的 Provider 能力编码）
     */
    private String capabilities;

    /**
     * 是否平台内置（Y/N）
     */
    private String builtin;

    /**
     * 说明
     */
    private String description;

    /**
     * 记录状态（0正常 1停用）
     */
    private String status;

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
