package org.dromara.aigov.agent.domain.bo;

import lombok.Data;

/**
 * Agent 版本绑定清单查询条件（设计 §10.2）。
 *
 * @author ai-gov
 */
@Data
public class AigAgentBindingQueryBo {

    /**
     * 绑定的 Agent 版本
     */
    private Long agentVersionId;

    /**
     * 限定公司
     */
    private Long companyId;

    /**
     * 限定品牌
     */
    private Long brandId;

    /**
     * 限定业务场景
     */
    private String scenarioCode;

    /**
     * 发布通道
     */
    private String releaseChannel;

    /**
     * 是否启用（Y/N）
     */
    private String enabled;

}
