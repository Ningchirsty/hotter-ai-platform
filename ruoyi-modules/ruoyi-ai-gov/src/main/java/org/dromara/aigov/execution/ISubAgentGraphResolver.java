package org.dromara.aigov.execution;

import java.util.List;

/**
 * 子 Agent 图解析端口（设计 §6.5）。
 *
 * <p>供 {@link AigSubAgentOrchestration} 展开与校验调用链：给定一个 Agent 版本，返回它
 * 声明的直接子 Agent 版本（按声明顺序）。抽成端口是为了让编排校验是纯函数、可单测，
 * 而"从哪里读声明"可以替换。</p>
 *
 * @author ai-gov
 */
public interface ISubAgentGraphResolver {

    /**
     * 取某版本的直接子 Agent 版本。
     *
     * @param agentVersionId 版本ID（可空）
     * @return 子版本ID（按声明顺序）；无子 Agent 返回空列表
     */
    List<Long> childrenOf(Long agentVersionId);

}
