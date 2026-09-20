package org.dromara.aigov.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 治理层业务配置
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
     * snail-ai 的 Agent ID。
     * <p>注意：snail-ai OpenAPI 聊天入口收的是 Agent，不是模型 ID；
     * {@code agentId} 为空或 &lt;= 0 时 {@code SnailAiChatInvoker.available()} 为 false。</p>
     */
    private Long agentId;

    /**
     * snail-ai OpenAPI 的 openId（外部用户标识）
     */
    private String openId;

    /**
     * 单次同步调用超时（毫秒）
     */
    private long timeoutMs = 60000L;

}
