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

    /**
     * 图像生成单次调用超时（毫秒）。
     *
     * <p><b>为什么与 {@link #timeoutMs} 分开</b>：对话补全通常几秒返回，而图像生成
     * 常要几十秒到几分钟（尤其高分辨率/多模型排队）。若共用 60 秒的对话超时，
     * 图像任务会在正常出图前就被判超时——表现是「偶尔成功、多数超时」，
     * 而其根因只是预算给错了地方。</p>
     */
    private long imageTimeoutMs = 180000L;

    /**
     * 图像结果的最大字节数（默认 8MB）。
     *
     * <p>图像会以 base64 内联在返回结果里交给调用方落盘，因此必须设上限：
     * 单个响应体过大既会撑爆内存，也会让审计与日志链路变成事故现场。
     * 超限一律显式失败并报出实际大小，<b>不静默截断</b>（截断后的图会被当成正常产物）。</p>
     */
    private long imageMaxBytes = 8L * 1024 * 1024;

    /**
     * 图像生成的 {@code response_format} 取值（{@code url} 或 {@code b64_json}）。
     *
     * <p><b>默认 {@code url}</b>，依据是实测：bluocto（New API 网关）在
     * {@code POST /v1/images/generations} 上按 {@code url} 返回，
     * 官方给出的可用请求示例就是 {@code {"model","prompt","n":1,"response_format":"url"}}。
     * 曾经默认 {@code b64_json}，理由是「URL 会过期、会把资产留在第三方」——
     * 但那个理由<b>不该用请求字段去解</b>：网关不支持该字段时会直接 400，
     * 于是本该出图的一次调用什么都拿不到。正确的做法是「要它最可能给的形态，
     * 拿到 URL 后立刻下载成字节」——{@code OpenAiImageInvoker} 本来就是这么做的，
     * 下载之后资产不再留在外部，URL 过期与否也就无关了。</p>
     *
     * <p>调用方若在 payload 里显式给了 {@code response_format}，以调用方为准。</p>
     */
    private String imageResponseFormat = "url";

}
