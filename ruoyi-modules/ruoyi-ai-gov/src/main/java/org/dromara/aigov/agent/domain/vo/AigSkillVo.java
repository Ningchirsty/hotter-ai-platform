package org.dromara.aigov.agent.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.aigov.agent.domain.AigSkill;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Skill 列表视图。
 *
 * @author ai-gov
 */
@Data
@AutoMapper(target = AigSkill.class)
public class AigSkillVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Skill ID
     */
    private Long skillId;

    /**
     * 编码
     */
    private String skillCode;

    /**
     * 名称
     */
    private String skillName;

    /**
     * 能力清单
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
