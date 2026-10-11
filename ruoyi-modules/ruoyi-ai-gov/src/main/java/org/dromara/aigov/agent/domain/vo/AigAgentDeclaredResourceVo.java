package org.dromara.aigov.agent.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 某个 Agent 版本「声明了什么」的只读视图（附件 07 §5）。
 *
 * <h3>它为什么必须由服务端算</h3>
 * <p>页面上的加法是：<b>先看版本声明了什么 → 再从声明里挑一个去绑定</b>。
 * 如果页面自己拿版本行上的逗号分隔字符串去 split，就会出现三份不一致的实现
 * （本类一份、页面一份、别处再一份）；而"声明里到底有没有这一项"正好是
 * 绑定校验的判据——两边算得不一样时，页面会让人挑一个服务端随后会拒绝的项。</p>
 *
 * <p>{@link Item#bound} 是"这个声明项已经有一条启用中的绑定了"——
 * 页面据此把已绑的项标出来（而不是让人重复点、被服务端拒绝）。</p>
 *
 * @author ai-gov
 */
@Data
public class AigAgentDeclaredResourceVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Agent 版本ID
     */
    private Long agentVersionId;

    /**
     * 版本号（展示用；读不到版本时为 null）
     */
    private String version;

    /**
     * 版本声明的工具（{@code allowed_tools}）
     */
    private List<Item> allowedTools;

    /**
     * 版本明确禁止的工具（{@code forbidden_tools}）
     *
     * <p>单独列出来是为了让页面能解释"为什么这个工具绑不上"——
     * 只给"不在允许清单里"，人还得去别的地方找原因。</p>
     */
    private List<String> forbiddenTools;

    /**
     * 版本声明的知识范围（{@code knowledge_scope_json}）
     */
    private List<Item> knowledgeScopes;

    /**
     * 一项声明。
     *
     * @param code  编码
     * @param bound 是否已有启用中的绑定
     */
    public record Item(String code, boolean bound) {
    }

}
