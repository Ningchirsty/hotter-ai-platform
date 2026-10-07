package org.dromara.aigov.agent.domain.bo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Agent 版本绑定入参（设计 §10.2 + §6.3-6 灰度范围）。
 *
 * <p><b>空值的含义是「不限」</b>（公司/品牌/场景/角色），不是「都不匹配」——
 * 这一点必须与服务层的收窄逻辑一致，否则会出现「因为没填所以全都不可见」这种
 * 看起来像权限问题、实际是数据语义问题的故障。</p>
 *
 * @author ai-gov
 */
@Data
public class AigAgentBindingBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 绑定的 Agent 版本ID
     */
    @NotNull(message = "Agent 版本ID不能为空")
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
     * 限定业务场景（空=不限）
     */
    @Size(max = 32, message = "场景编码长度不能超过 32")
    private String scenarioCode;

    /**
     * 限定角色（逗号分隔的 role_key，空=不限）
     */
    @Size(max = 200, message = "角色范围长度不能超过 200")
    private String roleScope;

    /**
     * 知识范围限定（空=不做额外收窄）
     */
    @Size(max = 500, message = "知识范围长度不能超过 500")
    private String knowledgeScope;

    /**
     * 绑定所属发布通道（留空则由服务层取该版本当前的通道；填了必须与版本一致）
     */
    @Size(max = 16, message = "发布通道长度不能超过 16")
    private String releaseChannel;

    /**
     * 是否启用（Y/N，留空默认 Y）
     */
    private String enabled;

    /**
     * 生效开始（空=立即）
     */
    private LocalDateTime effectiveFrom;

    /**
     * 生效结束（空=长期）
     */
    private LocalDateTime effectiveTo;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;

}
