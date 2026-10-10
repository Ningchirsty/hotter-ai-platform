package org.dromara.aigov.studio.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 创建训练草稿入参（专题 C §C9）。
 *
 * <p><b>为什么带 {@code agentCode} 而不只是 {@code agentId}</b>：从零创建时对象还不存在于
 * {@code aig_agent}（现有 Registry 没有在线创建 Agent 的接口），但它需要一个跨版本稳定的编码；
 * 而在"复制现有 Agent"的场景下两者都有。因此编码必填、ID 选填。</p>
 *
 * <p><b>{@code contentJson} 传字符串而不是对象</b>：与任务快照同理——内容哈希是对
 * "实际存进库的那串字节"算的。本层会把它**规范化**后再存（键排序/去空白/数值归一），
 * 因此入库内容的哈希永远可复算。</p>
 *
 * @author ai-gov
 */
@Data
public class AigStudioDraftCreateBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 训练对象编码（跨版本稳定）
     */
    @NotBlank(message = "训练对象编码不能为空")
    @Size(max = 64, message = "训练对象编码长度不能超过 64")
    private String agentCode;

    /**
     * 关联的 Agent 定义（从零创建时为空）
     */
    private Long agentId;

    /**
     * 归属组织（部门ID；空=集团级）
     */
    private Long orgId;

    /**
     * 草稿内容 JSON；为空时由服务端填一份含标准分节的骨架
     */
    private String contentJson;

    /**
     * 首个修订的说明
     */
    @Size(max = 200, message = "修订说明长度不能超过 200")
    private String summary;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;

}
