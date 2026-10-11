package org.dromara.aigov.agent.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.aigov.agent.domain.AigAgentResourceBinding;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工具/知识绑定视图（附件 07 §5）。
 *
 * <p>字段全带：绑定行的价值就在"这条资源给了谁"——少任何一个范围维度，
 * 运维都无法判断它到底收窄到了哪一级。</p>
 *
 * @author ai-gov
 */
@Data
@AutoMapper(target = AigAgentResourceBinding.class)
public class AigAgentResourceBindingVo implements Serializable {

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
     * 资源类型（TOOL / KNOWLEDGE）
     */
    private String resourceType;

    /**
     * 资源编码
     */
    private String resourceCode;

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
     * 限定角色（逗号分隔的 role_key，空=不限）
     */
    private String roleScope;

    /**
     * 是否启用（Y/N）
     */
    private String enabled;

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
