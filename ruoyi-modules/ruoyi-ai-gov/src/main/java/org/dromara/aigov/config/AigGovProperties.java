package org.dromara.aigov.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 治理层业务配置
 *
 * <p><b>阶段2 移除的一项配置</b>：原先有一个静态的 {@code aigov.snail-ai.agent-id}，
 * 经 snail-ai 调用时固定用它。问题在于「那个 Agent 实际跑哪个模型」由 snail-ai 侧决定，
 * 于是路由选中模型 A、实际跑的却是该 Agent 绑定的模型 B，<b>而且不报错</b>——
 * 这是最难查的一类错配。现在改为按路由选中的模型反查 Agent
 * （{@code sai_agent.chat_model_id}），查不到就明确失败；
 * 这个静态入口因此被删除（仓库内没有任何 yml 配置过它）。</p>
 *
 * @author ai-gov
 */
@Data
@Component
@ConfigurationProperties(prefix = "aigov.snail-ai")
public class AigGovProperties {

    /**
     * 是否允许通过 snail-ai 发起模型调用。
     * <p>默认 <b>false</b>：snail-ai 未启用或未经审批时，治理层只走本地调用器。</p>
     */
    private boolean enabled = false;

    /**
     * 期望承接调用的 snail-ai 应用ID（{@code sai_agent.app_id} 的作用域约束），可空。
     *
     * <p>{@code sai_agent.app_id} 为 NULL 表示「在 snail-ai 本地执行」；有值表示该 Agent
     * 由那个应用承接。本项配置后，只接受 {@code app_id} 为空或与之相等的 Agent——
     * 否则请求可能被派给**别的应用**，结果是「发出去了但没人应答」。
     * 留空表示不做这台机器上的作用域约束（仍会在日志里记录选中 Agent 的 app_id）。</p>
     */
    private String appId;

    /**
     * snail-ai OpenAPI 的 openId（外部用户标识）
     */
    private String openId;

    /**
     * 单次同步调用超时（毫秒）
     */
    private long timeoutMs = 60000L;

}
