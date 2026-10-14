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
     * 培训助手使用的<b>独立文本能力编码</b>（设计 §6.3、§7.3 的 D10）。
     *
     * <p><b>为什么不能复用被训练 Agent 的 {@code providerCapability}</b>：那个能力描述的是
     * 「这个 Agent 要跑什么」（可能是 {@code image_generation} / 视觉能力），而培训助手要做的是
     * <b>生成文本变更提案</b>。拿图像能力去路由文本提案，轻则路由不到合适模型、重则把一次
     * 文本请求发进图像链路。助手与 Agent 是两种不同用途的调用，能力编码必须各自独立。</p>
     *
     * <p>默认 {@code text_generation}：它是一个<b>已登记的能力编码</b>，路由策略、
     * 候选模型、配额与审计都与其它调用同一条链路，因此这里的"独立"只换取正确的路由语义，
     * 不会绕过任何治理（ADR-006）。</p>
     */
    private String capability = "text_generation";

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
