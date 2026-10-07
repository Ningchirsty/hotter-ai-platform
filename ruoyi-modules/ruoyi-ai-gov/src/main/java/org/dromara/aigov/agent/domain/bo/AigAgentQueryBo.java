package org.dromara.aigov.agent.domain.bo;

import lombok.Data;

/**
 * Agent 清单查询条件（设计 §5.1）。
 *
 * @author ai-gov
 */
@Data
public class AigAgentQueryBo {

    /**
     * Agent 编码（精确）
     */
    private String agentCode;

    /**
     * Agent 名称（模糊）
     */
    private String agentName;

    /**
     * 类别（PLANNING/VISUAL_DNA/GENERATION/QA）
     */
    private String category;

    /**
     * 是否平台内置（Y/N）
     */
    private String builtin;

    /**
     * 记录状态（0正常 1停用）
     */
    private String status;

}
