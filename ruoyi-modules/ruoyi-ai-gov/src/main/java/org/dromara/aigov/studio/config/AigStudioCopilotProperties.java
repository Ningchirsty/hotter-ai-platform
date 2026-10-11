package org.dromara.aigov.studio.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 训练台「对话式 Copilot」配置（{@code aigov.studio.copilot.*}）。
 *
 * <p><b>默认关闭是刻意的</b>：与「测试调用」同一理由——打开它就意味着训练台能发起
 * <b>真实计费</b>的模型调用。而且它是"对话式"的，一个人连着说几句就是几次调用，
 * 比点一次测试更容易不知不觉花掉钱。这类开关的正确默认值是"关"。</p>
 *
 * <p><b>它不是"绕过策略"的开关</b>：对话调用仍走网关（{@code IAigInvokeService}）的策略校验、
 * 配额与审计（ADR-006：所有模型/工具外发在网关执行点强制校验）。关着只是"训练台不给发"，
 * 开着也只是"允许发一次仍受治理的调用"。</p>
 *
 * <p>（注意：本仓没有 {@code @ConfigurationPropertiesScan}，配置类必须自己带 {@code @Component}，
 * 否则注入它的控制器会让整个后端启动失败。）</p>
 *
 * @author ai-gov
 */
@Data
@Component
@ConfigurationProperties(prefix = "aigov.studio.copilot")
public class AigStudioCopilotProperties {

    /**
     * 是否允许对话式 Copilot 发起模型调用（默认关闭）
     */
    private boolean enabled = false;

    /**
     * 单条用户消息的最大字符数（对话式入口最容易把整篇文档塞进来）
     */
    private int maxMessageChars = 2000;

    /**
     * 模型输出的最大字符数（超过就不解析：一份几百 KB 的"输出"多半不是我们要的 JSON）
     */
    private int maxOutputChars = 20000;

    /**
     * 落库并在页面上展示的模型原文长度（完整原文仍可从网关审计按 traceId 查）
     */
    private int outputPreviewChars = 4000;

    /**
     * 会话历史一次返回的条数（只回最近若干条：对话账本是追加型的，不需要全量翻页）
     */
    private int turnHistorySize = 50;

}
