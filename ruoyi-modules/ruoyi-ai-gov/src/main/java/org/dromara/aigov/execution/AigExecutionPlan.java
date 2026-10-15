package org.dromara.aigov.execution;

import lombok.Builder;
import lombok.Getter;
import org.dromara.common.core.utils.StringUtils;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 不可变执行计划（设计 §7.1「ImmutableExecutionPlan」、§7.2「冻结 Agent 版本与配置哈希、
 * 输入快照、知识版本与检索配置、工具版本、业务域适配器版本、预算和执行用途」）。
 *
 * <p><b>为什么必须不可变</b>：执行计划回答的是"这次运行依据的是哪一份配置"。
 * 如果它可以在运行中被人改（哪怕只是某处顺手 set 一下），事后看审计就无法区分
 * "当时确实用的是 v1.2 的 Prompt"和"有人把字段改了再执行"——那正是归因失败。</p>
 *
 * <p>因此：全字段 {@code final}、无 setter、由构造器一次性装配；集合字段以 {@link List#copyOf}
 * 复制，杜绝外部持有引用后原地修改。{@link #composePrompt} 的输入是运行时业务输入，
 * 它只影响当次拼装结果，不改变计划本身。</p>
 *
 * @author ai-gov
 */
@Getter
public class AigExecutionPlan implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 计划ID（字符串；用于日志与审计关联）
     */
    private final String planId;

    /**
     * 归属任务ID（训练台等非任务调用为空）
     */
    private final Long taskId;

    /**
     * 目标 Agent 版本ID
     */
    private final Long agentVersionId;

    /**
     * 运行用途
     */
    private final AigExecutionPurposeEnum purpose;

    /**
     * 配置哈希（冻结"这次用的是哪份配置"）
     */
    private final String configHash;

    /**
     * 冻结的系统 Prompt（来自版本，客户端不可覆盖）
     */
    private final String systemPrompt;

    /**
     * 输入快照哈希（无快照时为空）
     */
    private final String inputHash;

    /**
     * 实际用于路由的能力编码
     */
    private final String capabilityCode;

    /**
     * 本次数据等级
     */
    private final String dataLevel;

    /**
     * 业务侧外发许可（硬约束；{@code FALSE} 时路由必须排除外部候选）
     */
    private final Boolean allowExternal;

    /**
     * 本次预算（美元；可空=不设）
     */
    private final BigDecimal maxCost;

    /**
     * 一次运行允许的最大工具调用次数（可空=由配置决定，未配置则不限由运行时限流）
     */
    private final Integer maxToolCalls;

    /**
     * 允许的子 Agent 最大深度（可空）
     */
    private final Integer maxDepth;

    /**
     * 冻结的知识集合编码
     */
    private final List<String> knowledgeRefs;

    /**
     * 冻结的工具编码
     */
    private final List<String> toolRefs;

    /**
     * 冻结的子 Agent 版本调用顺序（已展开、已校验；空表示无子 Agent）
     */
    private final List<Long> subAgentVersionIds;

    /**
     * 冻结的授权上下文（身份 + 岗位卡片；可空表示本次没有岗位上下文）
     */
    private final AigAuthorizationContext authorizationContext;

    /**
     * 冻结的顺序流程节点（已校验；空表示没有声明流程）
     */
    private final List<AigFlowNode> flowNodes;

    /**
     * 一次运行允许的最大流程步骤数（可空=按平台默认）。
     *
     * <p>用户 2026-10-14 授权"顺序流程每步一次模型调用"之后，它多了一层含义：
     * <b>一次执行最多发起的流程调用次数</b>——每步一次调用，所以"步骤上限"就是"调用上限"。
     * 与 {@code maxToolCalls} 同属预算类约束，因此与它同层冻结。</p>
     */
    private final Integer maxFlowSteps;

    /**
     * 本次运行采用的知识资料节（已渲染；可为空表示没有检索资料节）。
     *
     * <p>在解析阶段（{@code AgentExecutionResolver}）由 {@code KnowledgePort} 检索并渲染，
     * 因此它同样是"冻结"的：执行中不再重新检索，避免"审计看到的资料"与"模型实际看到的资料"不一致。</p>
     */
    private final String retrievedKnowledgeBlock;

    /**
     * 被采用的引用（可核验的来源）
     */
    private final List<AigExecutionCitation> knowledgeCitations;

    /**
     * 全参构造（由解析器调用；外部请用解析器而非直接 new）。
     */
    @Builder
    public AigExecutionPlan(String planId, Long taskId, Long agentVersionId,
                            AigExecutionPurposeEnum purpose, String configHash, String systemPrompt,
                            String inputHash, String capabilityCode, String dataLevel,
                            Boolean allowExternal, BigDecimal maxCost, Integer maxToolCalls,
                            Integer maxDepth, List<String> knowledgeRefs, List<String> toolRefs,
                            List<Long> subAgentVersionIds,
                            AigAuthorizationContext authorizationContext,
                            List<AigFlowNode> flowNodes,
                            Integer maxFlowSteps,
                            String retrievedKnowledgeBlock, List<AigExecutionCitation> knowledgeCitations) {
        this.planId = planId;
        this.taskId = taskId;
        this.agentVersionId = agentVersionId;
        this.purpose = purpose;
        this.configHash = configHash;
        this.systemPrompt = systemPrompt;
        this.inputHash = inputHash;
        this.capabilityCode = capabilityCode;
        this.dataLevel = dataLevel;
        this.allowExternal = allowExternal;
        this.maxCost = maxCost;
        this.maxToolCalls = maxToolCalls;
        this.maxDepth = maxDepth;
        this.knowledgeRefs = knowledgeRefs == null ? List.of() : List.copyOf(knowledgeRefs);
        this.toolRefs = toolRefs == null ? List.of() : List.copyOf(toolRefs);
        this.subAgentVersionIds = subAgentVersionIds == null ? List.of() : List.copyOf(subAgentVersionIds);
        this.authorizationContext = authorizationContext;
        this.flowNodes = flowNodes == null ? List.of() : List.copyOf(flowNodes);
        this.maxFlowSteps = maxFlowSteps;
        this.retrievedKnowledgeBlock = retrievedKnowledgeBlock;
        this.knowledgeCitations = knowledgeCitations == null
            ? List.of() : List.copyOf(knowledgeCitations);
    }

    /**
     * 把冻结的系统 Prompt、检索资料与本次业务输入组装成真正发出去的 prompt。
     *
     * <p>资料永远处在系统指令<b>之后</b>的独立分节里：外部资料不能覆盖系统指令（设计 §7.3）；
     * 业务输入再排在最后。</p>
     *
     * @param businessInput 业务输入（可空）
     * @return 组装后的 prompt
     */
    public String composePrompt(String businessInput) {
        String system = systemPrompt == null ? "" : systemPrompt;
        StringBuilder text = new StringBuilder(system);
        if (StringUtils.isNotBlank(retrievedKnowledgeBlock)) {
            text.append("\n\n").append(retrievedKnowledgeBlock);
        }
        if (businessInput != null && !businessInput.isBlank()) {
            text.append("\n\n## 业务输入\n").append(businessInput);
        }
        return text.toString();
    }

}
