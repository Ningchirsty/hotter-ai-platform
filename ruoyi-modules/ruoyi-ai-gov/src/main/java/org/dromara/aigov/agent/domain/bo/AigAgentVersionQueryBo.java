package org.dromara.aigov.agent.domain.bo;

import lombok.Data;

/**
 * Agent 版本清单查询条件（设计 §5.3/§5.4）。
 *
 * @author ai-gov
 */
@Data
public class AigAgentVersionQueryBo {

    /**
     * 所属 Agent
     */
    private Long agentId;

    /**
     * 发布状态（DRAFT/VALIDATED/SANDBOX_TESTED/CANDIDATE/STABLE/DISABLED/ARCHIVED）
     */
    private String releaseStatus;

    /**
     * 发布通道（TESTING/BRAND/DEPT/GENERAL）
     */
    private String releaseChannel;

    /**
     * 需要的 Provider 能力编码
     */
    private String providerCapability;

    /**
     * 业务场景编码
     */
    private String scenarioCode;

    /**
     * 版本号（模糊）
     */
    private String version;

}
