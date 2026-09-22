package org.dromara.aigov.service.invoker;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.config.AigModelTestProperties;
import org.dromara.aigov.domain.vo.AigModelTestTargetVo;
import org.dromara.aigov.domain.vo.AigModelTestVo;
import org.dromara.aigov.enums.AigDeploymentTypeEnum;
import org.dromara.aigov.helper.AigModelSecretCipher;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.springframework.stereotype.Component;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 模型连通性探测器。
 * <p>
 * 治理页在「模型新增 / 属性登记」之后需要回答一个很实际的问题：<b>这套配置到底能不能用？</b>
 * 没有这一步，配错的 endpoint、失效的密钥、没装配的本地执行者，都要等到业务真的调用时才暴露，
 * 而且会以「路由没选中」这种间接现象出现，排查成本很高。
 * </p>
 * <p><b>探测方式按部署类型/适配器分流</b>：</p>
 * <ul>
 *     <li>{@code LOCAL}（进程内规则引擎）：只做「执行者是否装配可用」的自检，不发网络请求；</li>
 *     <li>{@code adapter_key} 含 snail-ai：走 snail-ai 调用器可用性 + 一次最小调用；</li>
 *     <li>其余（典型是 {@code openai-compatible}）：对 {@code api_endpoint} 发一次最小 chat/completions 请求。</li>
 * </ul>
 * <p><b>安全约定</b>：密钥只用于发请求；响应体里的密钥会被替换成掩码；日志与返回值都不含密钥与完整响应体。
 * </p>
 * <p><b>密钥必须先解密再使用</b>：{@code sai_model_config.api_key} 是 SM4 密文列，
 * 直接把列里的值当 Bearer 发出去，上游一定判鉴权失败——那会让「测试连接」在密钥完全正确时
 * 也报错，比没有这个功能更误导。故这里统一走 {@link AigModelSecretCipher#decrypt}。
 * 解密依赖 {@code aigov.model-crypto}，未启用时如实报「无法解密」，不做猜测。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModelConnectionTester {

    /**
     * 探测请求用的最小提示词（不含任何业务数据）
     */
    private static final String PROBE_PROMPT = "ping";

    /**
     * 细节字段最大长度（避免把上游长报文整段回显）
     */
    private static final int DETAIL_MAX = 300;

    /**
     * 从上游错误体里提取 {@code message} 字段。
     * <p>上游（OpenAI 兼容端点）在 4xx/5xx 时几乎都会给出可操作的原因，例如
     * {@code {"error":{"message":"orfree is not a valid model ID","code":400}}}。
     * 只回一句「连接失败（HTTP 400）」会把用户推向排查网络，方向是错的。</p>
     */
    private static final Pattern UPSTREAM_MESSAGE = Pattern.compile("\"message\"\\s*:\\s*\"([^\"]{0,200})\"");

    /**
     * 供应商/适配器里出现这些关键字就按 snail-ai 链路探测
     */
    private static final List<String> SNAIL_AI_HINTS = List.of("snail", "snailai");

    /**
     * 本地执行者关键字
     */
    private static final List<String> LOCAL_HINTS = List.of("local");

    private final org.springframework.beans.factory.ObjectProvider<ModelInvoker> invokerProvider;

    /**
     * 模型密钥加解密工具。库里存的是 SM4 密文，发请求前必须先解出明文。
     */
    private final AigModelSecretCipher secretCipher;

    /**
     * 探测的超时与重试预算（{@code aigov.model-test.*}）。
     */
    private final AigModelTestProperties testProperties;

    /**
     * 执行一次连通性测试。
     *
     * @param target 模型配置快照（含密钥，仅服务端内部使用）
     * @return 测试结果
     */
    public AigModelTestVo test(AigModelTestTargetVo target) {
        AigModelTestVo vo = new AigModelTestVo();
        vo.setCheckedAt(LocalDateTime.now());
        if (target == null) {
            vo.setOk(false);
            vo.setProbe("UNSUPPORTED");
            vo.setMessage("模型不存在，无法测试");
            return vo;
        }
        String adapter = StringUtils.blankToDefault(target.getAdapterKey(), "");
        String deployment = StringUtils.blankToDefault(target.getDeploymentType(), "");
        vo.setEndpointHost(hostOf(target.getApiEndpoint()));

        AigDeploymentTypeEnum deploymentType = AigDeploymentTypeEnum.find(deployment);
        boolean local = AigDeploymentTypeEnum.LOCAL.getCode().equals(deployment)
            || containsAny(adapter, LOCAL_HINTS);
        if (local) {
            vo.setProbe("LOCAL_ENGINE");
            probeLocalEngine(vo, adapter);
        } else if (containsAny(adapter, SNAIL_AI_HINTS) || containsAny(StringUtils.blankToDefault(target.getProviderKey(), ""), SNAIL_AI_HINTS)) {
            vo.setProbe("SNAIL_AI");
            probeSnailAi(vo, target, deploymentType);
        } else if (StringUtils.isNotBlank(target.getApiEndpoint())) {
            vo.setProbe("OPENAI_COMPATIBLE");
            probeHttp(vo, target);
        } else {
            vo.setProbe("UNSUPPORTED");
            vo.setOk(false);
            vo.setMessage("无法测试：未配置访问地址（api_endpoint），且适配器不是本地执行者或 snail-ai");
        }
        if (vo.getOk() == null) {
            vo.setOk(false);
        }
        vo.setHealthStatus(Boolean.TRUE.equals(vo.getOk()) ? "HEALTHY" : "UNHEALTHY");
        log.info("模型连通性测试完成, modelId={}, modelKey={}, probe={}, ok={}, latencyMs={}",
            target.getModelId(), target.getModelKey(), vo.getProbe(), vo.getOk(), vo.getLatencyMs());
        return vo;
    }

    /**
     * 本地执行者自检：确认进程内确实装配了支持 LOCAL 的调用器。
     *
     * @param vo      结果
     * @param adapter 适配器标识（用于提示）
     */
    private void probeLocalEngine(AigModelTestVo vo, String adapter) {
        long start = System.currentTimeMillis();
        ModelInvoker found = null;
        for (ModelInvoker invoker : invokerProvider) {
            if (invoker.supports(AigDeploymentTypeEnum.LOCAL)) {
                found = invoker;
                if (invoker.available()) {
                    break;
                }
            }
        }
        vo.setLatencyMs(System.currentTimeMillis() - start);
        if (found == null) {
            vo.setOk(false);
            vo.setMessage("本地执行者未装配：没有任何调用器声明支持 LOCAL 部署");
            return;
        }
        if (!found.available()) {
            vo.setOk(false);
            vo.setMessage("本地执行者不可用：" + found.getClass().getSimpleName() + " 报告未就绪");
            return;
        }
        vo.setOk(true);
        vo.setMessage("本地执行者已装配可用（" + found.getClass().getSimpleName() + "，进程内规则引擎，无需网络连通）");
        vo.setDetail("adapter=" + adapter);
    }

    /**
     * snail-ai 链路探测：先看可用性，可用则发一次最小调用。
     *
     * @param vo             结果
     * @param target         模型快照
     * @param deploymentType 部署类型
     */
    private void probeSnailAi(AigModelTestVo vo, AigModelTestTargetVo target, AigDeploymentTypeEnum deploymentType) {
        ModelInvoker snail = null;
        for (ModelInvoker invoker : invokerProvider) {
            if (invoker.getClass().getSimpleName().toLowerCase().contains("snail")) {
                snail = invoker;
                break;
            }
        }
        if (snail == null) {
            vo.setOk(false);
            vo.setMessage("snail-ai 调用器未装配");
            return;
        }
        if (!snail.available()) {
            vo.setOk(false);
            vo.setMessage("snail-ai 链路不可用：请在应用配置中启用 aigov.snail-ai.enabled 并配置 agent-id 与 OpenApiChatClient");
            return;
        }
        ModelInvokeRequest request = new ModelInvokeRequest();
        request.setCapabilityCode(org.dromara.aigov.constant.AigConstants.CAP_TALENT_MATCH);
        request.setModelKey(target.getModelKey());
        request.setPrompt(PROBE_PROMPT);
        ModelInvokeResult result = snail.invoke(request);
        vo.setLatencyMs(result.getLatencyMs());
        vo.setOk(result.isSuccess());
        vo.setMessage(result.isSuccess() ? "snail-ai 链路调用成功" : "snail-ai 链路调用失败：" + result.getErrorSummary());
    }

    /**
     * HTTP 探测：对 OpenAI 兼容端点发一次最小请求。
     *
     * @param vo     结果
     * @param target 模型快照（{@code apiKey} 是库中的密文，本方法负责解密后再使用）
     */
    private void probeHttp(AigModelTestVo vo, AigModelTestTargetVo target) {
        // 先解密钥：解不开就没必要发请求了，直接给出可操作的原因。
        // 注意不要抛出去——连通性测试的失败也是一条结论，要落 health_status 并展示给运维。
        String apiKey;
        try {
            apiKey = resolveApiKey(target);
        } catch (ServiceException e) {
            vo.setOk(false);
            vo.setMessage(e.getMessage());
            return;
        }
        String url = buildChatUrl(target.getApiEndpoint());
        String body = JsonUtils.toJsonString(Map.of(
            "model", target.getModelKey(),
            "messages", List.of(Map.of("role", "user", "content", PROBE_PROMPT)),
            "max_tokens", 1,
            "stream", false
        ));
        // 网络抖动（跨境/网关型端点常见）不该以「一次失败」定论：按配置重试，
        // 但只重试网络层失败——拿到任何 HTTP 状态码都是明确结论，重试无意义。
        int maxAttempts = Math.max(1, testProperties.getRetries() + 1);
        long start = System.currentTimeMillis();
        Exception lastError = null;
        int attemptsMade = 0;
        for (int i = 1; i <= maxAttempts; i++) {
            attemptsMade = i;
            try {
                HttpRequest request = HttpRequest.post(url)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .timeout(testProperties.getTimeoutMs());
                if (StringUtils.isNotBlank(apiKey)) {
                    request.header("Authorization", "Bearer " + apiKey);
                }
                try (HttpResponse response = request.execute()) {
                    vo.setLatencyMs(System.currentTimeMillis() - start);
                    int status = response.getStatus();
                    String text = mask(response.body(), apiKey);
                    vo.setDetail(truncate(text));
                    if (status >= 200 && status < 300) {
                        vo.setOk(true);
                        vo.setMessage("连接成功（HTTP " + status + "）");
                        return;
                    }
                    vo.setOk(false);
                    vo.setMessage(describeHttpFailure(status, text));
                    return;
                }
            } catch (Exception e) {
                lastError = e;
                if (i < maxAttempts && isTransientNetworkFailure(e)) {
                    log.warn("模型连通性测试第 {}/{} 次探测网络失败（{}），{}ms 后重试, modelId={}",
                        i, maxAttempts, rootCause(e).getClass().getSimpleName(),
                        testProperties.getRetryBackoffMs(), target.getModelId());
                    sleepQuietly(testProperties.getRetryBackoffMs());
                    continue;
                }
                break;
            }
        }
        vo.setLatencyMs(System.currentTimeMillis() - start);
        vo.setOk(false);
        vo.setMessage("连接异常：" + describeNetworkFailure(lastError)
            + (attemptsMade > 1 ? "（已尝试 " + attemptsMade + " 次）" : ""));
        vo.setDetail(truncate(mask(rawMessage(lastError), apiKey)));
    }

    /**
     * 沿 cause 链走到最内层异常。Hutool 会把底层 IOException 包成自己的异常，
     * 只看最外层永远只能得到一句「HttpException」，无法区分 DNS/连接/超时/TLS。
     *
     * @param e 异常
     * @return 最内层异常
     */
    private Throwable rootCause(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur;
    }

    /**
     * 取根因的可读消息（根因没消息时退回外层）。
     *
     * @param e 异常
     * @return 消息文本，永不为 null
     */
    private String rawMessage(Throwable e) {
        if (e == null) {
            return "未知错误";
        }
        Throwable root = rootCause(e);
        String msg = root.getMessage();
        if (StringUtils.isBlank(msg)) {
            msg = e.getMessage();
        }
        return StringUtils.blankToDefault(msg, root.getClass().getSimpleName());
    }

    /**
     * 是否属于「重试一次可能就好了」的网络层失败。
     *
     * @param e 异常
     * @return 是否值得重试
     */
    private boolean isTransientNetworkFailure(Throwable e) {
        Throwable root = rootCause(e);
        if (root instanceof UnknownHostException
            || root instanceof SocketTimeoutException
            || root instanceof ConnectException
            || root instanceof NoRouteToHostException) {
            return true;
        }
        String msg = rawMessage(e).toLowerCase();
        return msg.contains("connection reset") || msg.contains("broken pipe") || msg.contains("timed out");
    }

    /**
     * 把网络层失败翻译成可操作的结论。
     * <p>原实现统一回一句「地址不可达或超时」，把 DNS 失败、端口拒绝、TLS 失败、
     * 读取超时混为一谈——运维据此排查往往方向就是错的。</p>
     *
     * @param e 异常
     * @return 可操作描述
     */
    private String describeNetworkFailure(Throwable e) {
        if (e == null) {
            return "未知错误";
        }
        Throwable root = rootCause(e);
        String msg = rawMessage(e);
        String lower = msg.toLowerCase();
        if (root instanceof UnknownHostException) {
            return "DNS 解析失败，无法解析目标主机（核对访问地址拼写，以及容器/宿主 DNS 是否可用）";
        }
        if (root instanceof SocketTimeoutException) {
            if (lower.contains("connect")) {
                return "连接超时，TCP 握手未完成（目标被防火墙丢弃、需要走代理，或出口网络不可达）";
            }
            return "读取超时，已连上但对端未在 " + testProperties.getTimeoutMs() + "ms 内返回"
                + "（可调大 aigov.model-test.timeout-ms）";
        }
        if (root instanceof ConnectException) {
            return "连接被拒绝，目标端口未监听或被主动拒绝（核对地址与端口）";
        }
        if (root instanceof NoRouteToHostException) {
            return "网络不可达，本机没有到目标的路由（容器网络或出口策略受限）";
        }
        String cls = root.getClass().getSimpleName();
        if (cls.contains("SSL") || lower.contains("ssl") || lower.contains("certificate") || lower.contains("pkix")) {
            return "TLS 握手失败（证书不受信、SNI 不匹配，或链路被中间设备干扰）";
        }
        return cls + (StringUtils.isBlank(msg) ? "" : "：" + msg);
    }

    /**
     * 把「HTTP 非 2xx」翻译成可操作的结论，并带上上游原文。
     *
     * @param status   状态码
     * @param bodyText 响应体（已掩码）
     * @return 可操作描述
     */
    private String describeHttpFailure(int status, String bodyText) {
        String upstream = extractUpstreamMessage(bodyText);
        String base;
        if (status == 401 || status == 403) {
            base = "鉴权失败（HTTP " + status + "）：密钥缺失、无效，或该密钥无权访问此模型";
        } else if (status == 402) {
            base = "配额不足（HTTP 402）：上游账户额度/余额不足";
        } else if (status == 404) {
            base = "地址不存在（HTTP 404）：确认访问地址是否含 /v1 等路径前缀，且模型标识是上游有效模型 ID";
        } else if (status == 429) {
            base = "触发限流（HTTP 429）：降低频率或稍后重试";
        } else if (status >= 500) {
            base = "上游服务错误（HTTP " + status + "）：对端异常，稍后重试通常可恢复";
        } else if (status == 400) {
            base = "请求被拒（HTTP 400）：模型标识或请求参数不被上游接受";
        } else {
            base = "连接失败（HTTP " + status + "）";
        }
        return StringUtils.isBlank(upstream) ? base : base + "；上游返回：" + upstream;
    }

    /**
     * 从上游错误体里取 message 字段。
     *
     * @param bodyText 响应体
     * @return 上游消息，取不到返回 null
     */
    private String extractUpstreamMessage(String bodyText) {
        if (StringUtils.isBlank(bodyText)) {
            return null;
        }
        Matcher matcher = UPSTREAM_MESSAGE.matcher(bodyText);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * 重试等待，忽略中断并恢复中断标记。
     *
     * @param ms 等待毫秒
     */
    private void sleepQuietly(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 取出可用的明文密钥：库中是 SM4 密文，未配置密钥时返回 {@code null}（不带鉴权头）。
     *
     * @param target 模型快照
     * @return 明文密钥或 null
     * @throws ServiceException 未启用加解密、或密文解不开
     */
    private String resolveApiKey(AigModelTestTargetVo target) {
        String stored = target.getApiKey();
        if (StringUtils.isBlank(stored)) {
            return null;
        }
        return secretCipher.decrypt(stored);
    }

    /**
     * 组装 chat/completions 地址：兼容「配到 /v1」与「直接配到 /chat/completions」两种写法。
     *
     * @param endpoint 访问地址
     * @return 完整地址
     */
    private String buildChatUrl(String endpoint) {
        String base = endpoint.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.endsWith("/chat/completions")) {
            return base;
        }
        return base + "/chat/completions";
    }

    /**
     * 取出主机名（不含协议、路径、账号信息），便于判断打到了哪个地址。
     *
     * @param endpoint 访问地址
     * @return 主机名，解析失败返回原值截断
     */
    private String hostOf(String endpoint) {
        if (StringUtils.isBlank(endpoint)) {
            return null;
        }
        String raw = endpoint.trim();
        try {
            URI uri = URI.create(raw.contains("://") ? raw : "http://" + raw);
            String host = uri.getHost();
            if (host == null) {
                return truncate(raw);
            }
            return uri.getPort() > 0 ? host + ":" + uri.getPort() : host;
        } catch (Exception e) {
            return truncate(raw);
        }
    }

    /**
     * 把响应/异常里的密钥替换成掩码（防御上游回显密钥）。
     *
     * @param text 原文
     * @param key  密钥
     * @return 掩码后的文本
     */
    private String mask(String text, String key) {
        if (StringUtils.isBlank(text)) {
            return text;
        }
        if (StringUtils.isNotBlank(key)) {
            return text.replace(key, "***");
        }
        return text;
    }

    /**
     * 截断细节字段。
     *
     * @param text 原文
     * @return 截断后的文本
     */
    private String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= DETAIL_MAX ? text : text.substring(0, DETAIL_MAX) + "…";
    }

    /**
     * 关键字包含判断（忽略大小写）。
     *
     * @param text     被检查文本
     * @param keywords 关键字
     * @return 是否包含
     */
    private boolean containsAny(String text, List<String> keywords) {
        if (StringUtils.isBlank(text)) {
            return false;
        }
        String lower = text.toLowerCase();
        for (String keyword : keywords) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
