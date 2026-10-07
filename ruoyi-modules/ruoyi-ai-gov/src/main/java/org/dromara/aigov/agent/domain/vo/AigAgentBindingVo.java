package org.dromara.aigov.agent.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.aigov.agent.domain.AigAgentBinding;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Agent 版本绑定视图。字段全带（无长文本），因为绑定行的价值就在于「这几个范围」——
 * 裁剪掉任何一个范围维度，运维都无法判断这条绑定到底把版本给了谁。</p>
 *
 * @author ai-gov
 */
@Data
@AutoMapper(target = AigAgentBinding.class)
public class AigAgentBindingVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 绑定ID
     */
    private Long bindingId;

    /**
     * Agent 版本ID
     */
    private Long agentVersionId;

    /**
     * 限定公司（空=不限）
     */
    private Long companyId;

    /**
     * 限定品牌（空=不限）
     */
    private Long brandId;

    /**
     * 限定场景（空=不限）
     */
    private String scenarioCode;

    /**
     * 限定角色（空=不限）
     */
    private String roleScope;

    /**
     * 知识范围限定
     */
    private String knowledgeScope;

    /**
     * 绑定所属发布通道
     */
    private String releaseChannel;

    /**
     * 是否启用（Y/N）
     */
    private String enabled;

    /**
     * 生效开始
     */
    private LocalDateTime effectiveFrom;

    /**
     * 生效结束
     */
    private LocalDateTime effectiveTo;

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
