package org.dromara.aigov.agent.domain.bo;

import lombok.Data;

/**
 * Skill 版本清单查询条件。
 *
 * @author ai-gov
 */
@Data
public class AigSkillVersionQueryBo {

    /**
     * 所属 Skill
     */
    private Long skillId;

    /**
     * 发布状态
     */
    private String releaseStatus;

    /**
     * 发布通道
     */
    private String releaseChannel;

    /**
     * 需要的 Provider 能力编码
     */
    private String providerCapability;

    /**
     * 版本号（模糊）
     */
    private String version;

}
