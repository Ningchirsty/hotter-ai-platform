package org.dromara.aigov.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 外部 OpenAI 兼容端点直连配置（治理层 → 供应商，不经过 snail-ai）。
 *
 * <p><b>为什么需要它</b>：治理台的模型注册中心写的就是 snail-ai 的 {@code sai_model_config}，
 * 但此前全仓只有两个调用器——本地规则与 snail-ai。于是 {@code EXTERNAL_API} 类模型
 * （OpenRouter、各类 OpenAI 兼容网关）虽然能登记、能测连通，真正调用时却只能落到
 * {@code SnailAiChatInvoker}，而后者把请求转给 snail-ai 并携带 <b>agentId</b>，
 * 实际执行的模型由 Agent 决定，<b>与治理台填的 model_key / api_endpoint 无关</b>。
 * 结果就是「配好的外部模型用不上」。本配置配合
 * {@code OpenAiCompatibleInvoker} 补上直连这条路。</p>
 *
 * <p><b>默认开启</b>：与 {@code aigov.snail-ai.enabled} 的默认关闭不同。原因是走到本调用器
 * 需要已经越过好几道显式配置——模型必须是 {@code EXTERNAL_API} 部署类型、必须被绑定到某个能力、
 * 该「能力 × 数据等级」必须存在 {@code allow_external='Y'} 的路由策略、治理属性还必须是可调用状态。
 * 这几步本身就是有意为之的开关；再加一道默认关闭只会让「配好了却调不通」重现。
 * 保留本开关是为了出问题时能一键关停外呼。</p>
 *
 * @author ai-gov
 */
@Data
@Component
@ConfigurationProperties(prefix = "aigov.external-api")
public class AigExternalApiProperties {

    /**
     * 是否允许治理层直连外部 OpenAI 兼容端点。
     */
    private boolean enabled = true;

    /**
     * 单次调用超时（毫秒）。
     *
     * <p>与连通性测试的探测预算（{@code aigov.model-test.timeout-ms}）刻意分开：
     * 那是运维点一下就要出结果的固定预算，这里是真实业务调用的等待时间，两者不该互相牵制。</p>
     */
    private long timeoutMs = 60000L;

    /**
     * 是否在请求体里带上 {@code response_format={"type":"json_object"}}。
     *
     * <p>默认 <b>false</b>：这个字段是 OpenAI 的扩展，部分 OpenAI 兼容网关不支持，
     * 带上会被直接判 400。稳妥做法是在提示词里要求输出 JSON，再由调用器解析——
     * 遇到明确支持该字段的网关，可自行打开。</p>
     */
    private boolean jsonResponseFormat = false;

}
