package org.dromara.aigov.agent.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 新增工具/知识绑定入参（附件 07 §5）。
 *
 * <p><b>空值的含义是「不限」</b>（公司/品牌/场景/角色），不是"都不匹配"——
 * 与 {@code aig_agent_binding} 同一语义，服务层的重复检查也按"空=不限"逐列比对。</p>
 *
 * <p><b>资源编码不能自报</b>：服务层会要求它命中该 Agent 版本已声明的项，
 * 否则绑定表就成了第二条声明通道。</p>
 *
 * @author ai-gov
 */
@Data
public class AigAgentResourceBindingBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 绑定的 Agent 版本ID
     */
    @NotNull(message = "Agent 版本ID不能为空")
    private Long agentVersionId;

    /**
     * 资源类型（{@code AigAgentResourceTypeEnum}：TOOL / KNOWLEDGE）
     */
    @NotBlank(message = "资源类型不能为空（TOOL / KNOWLEDGE）")
    @Size(max = 16, message = "资源类型长度不能超过 16")
    private String resourceType;

    /**
     * 资源编码（工具编码 / 知识范围编码；必须命中版本已声明的项）
     */
    @NotBlank(message = "资源编码不能为空")
    @Size(max = 128, message = "资源编码长度不能超过 128")
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
     * 是否启用（Y/N，留空默认 Y）
     */
    private String enabled;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;

}
