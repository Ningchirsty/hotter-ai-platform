package org.dromara.aigov.studio.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 训练台「测试调用」配置（{@code aigov.studio.test.*}）。
 *
 * <p><b>默认关闭是刻意的</b>：打开它就意味着训练台能发起**真实计费的模型调用**。
 * 训练台面向"反复调教"，若默认打开，一个人点几次测试就可能花掉真钱——
 * 这类开关的正确默认值是"关"，让部署方明确知道自己在开什么。</p>
 *
 * <p><b>与"逐策略开关"不同</b>：这里的开关只控制<b>训练台是否允许发起测试</b>，
 * 与路由策略（{@code aig_route_policy}）无关。测试调用本身仍要过网关的策略校验与配额——
 * 本开关<b>不是</b>"绕过策略"的开关，关着只是"训练台不给发"，开着也只是"允许发一次仍受治理的调用"。</p>
 *
 * <p>（注意：本仓没有 {@code @ConfigurationPropertiesScan}，配置类必须自己带 {@code @Component}，
 * 否则注入它的控制器会让整个后端启动失败。）</p>
 *
 * @author ai-gov
 */
@Data
@Component
@ConfigurationProperties(prefix = "aigov.studio.test")
public class AigStudioTestProperties {

    /**
     * 是否允许训练台发起测试调用（默认关闭）
     */
    private boolean enabled = false;

    /**
     * 测试输入的最大字符数（防止把整篇文档当输入塞进来）
     */
    private int maxInputChars = 4000;

    /**
     * 返回给页面的输出预览长度（完整输出仍按需从审计/链路查）
     */
    private int outputPreviewChars = 2000;

}
