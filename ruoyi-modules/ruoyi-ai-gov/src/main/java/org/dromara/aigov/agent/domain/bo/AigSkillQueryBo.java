package org.dromara.aigov.agent.domain.bo;

import lombok.Data;

/**
 * Skill 清单查询条件（设计 §5.1）。
 *
 * @author ai-gov
 */
@Data
public class AigSkillQueryBo {

    /**
     * Skill 编码（精确）
     */
    private String skillCode;

    /**
     * Skill 名称（模糊）
     */
    private String skillName;

    /**
     * 是否平台内置（Y/N）
     */
    private String builtin;

    /**
     * 记录状态（0正常 1停用）
     */
    private String status;

    /**
     * 能力清单（模糊匹配逗号分隔的能力编码）
     */
    private String capabilities;

}
