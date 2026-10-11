package org.dromara.aigov.agent.domain.bo;

import lombok.Data;

/**
 * 工具/知识绑定清单查询条件（附件 07 §5）。
 *
 * @author ai-gov
 */
@Data
public class AigAgentResourceBindingQueryBo {

    /**
     * 绑定的 Agent 版本
     */
    private Long agentVersionId;

    /**
     * 资源类型（TOOL / KNOWLEDGE）
     */
    private String resourceType;

    /**
     * 资源编码
     */
    private String resourceCode;

    /**
     * 限定公司
     */
    private Long companyId;

    /**
     * 限定品牌
     */
    private Long brandId;

    /**
     * 是否启用（Y/N）
     */
    private String enabled;

}
