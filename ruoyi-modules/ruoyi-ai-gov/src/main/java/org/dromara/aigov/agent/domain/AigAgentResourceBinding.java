package org.dromara.aigov.agent.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;

/**
 * 工具/知识绑定 aig_agent_resource_binding（附件 07 §5「工具/知识绑定：只有声明字段，无绑定表」）。
 *
 * <h3>这张表是什么，不是什么</h3>
 * <p><b>是</b>：版本声明的<b>收窄</b>——{@code aig_agent_version.allowed_tools} 说"这个版本可以碰哪些工具"，
 * 本表说"这些工具里，在什么范围（公司/品牌/场景/角色）下真的给它用、并且启用"。
 * 声明是一行逗号分隔的文本，无法索引、无法按范围收窄、无法逐条停用；绑定一行一条，三者都能做。</p>
 *
 * <p><b>不是</b>：Tool Gateway / Knowledge 引擎。附件 07 §10 明确「不建 Runtime / Tool Gateway /
 * Knowledge 引擎；P0 只读展示与如实标注"仅登记/不可测试"」。本表只登记与收窄，
 * <b>没有任何执行语义</b>——仓库里也没有任何调用代码会读它。（同 ADR-006：外发策略必须在网关执行点强制，
 * 不能靠一张表的声明。）</p>
 *
 * <h3>为什么绑定必须命中版本已声明的项</h3>
 * <p>如果允许绑一个版本没声明（甚至明确禁止）的工具，这张表就会变成<b>第二条声明通道</b>：
 * 治理台上说"这个版本不能用 shell"，绑定表里却给它绑上，而没人能说清以哪个为准。
 * 因此服务层对 TOOL/KNOWLEDGE 都要求命中声明，且 TOOL 明确拒绝 {@code forbidden_tools} 里的项。</p>
 *
 * <h3>为什么没有唯一索引</h3>
 * <p>范围列允许 {@code NULL} 表示"不限"，而 MySQL 的唯一索引<b>不约束 NULL</b>
 * （两行 scope 全为 NULL 的"重复"绑定照样能插进去）。加一个不生效的唯一索引只会给人虚假的安全感，
 * 所以重复检查由服务层显式做（用 {@code isNull} 逐列比对），与 {@code aig_agent_binding} 同一处理。</p>
 *
 * @author ai-gov
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aig_agent_resource_binding")
public class AigAgentResourceBinding extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 绑定ID
     */
    @TableId(value = "binding_id")
    private Long bindingId;

    /**
     * 绑定的 Agent 版本ID
     */
    private Long agentVersionId;

    /**
     * 资源类型（{@code AigAgentResourceTypeEnum}：TOOL / KNOWLEDGE）
     */
    private String resourceType;

    /**
     * 资源编码：TOOL 时是工具编码，KNOWLEDGE 时是知识范围编码
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
     * 限定业务场景（空=不限）
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
     * 删除标志（0代表存在 1代表删除）
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注
     */
    private String remark;

}
