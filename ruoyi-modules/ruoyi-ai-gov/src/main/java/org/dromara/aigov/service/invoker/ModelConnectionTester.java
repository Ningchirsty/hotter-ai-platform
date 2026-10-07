package org.dromara.aigov.service.invoker;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.config.AigModelTestProperties;
import org.dromara.aigov.domain.vo.AigModelTestTargetVo;
import org.dromara.aigov.domain.vo.AigModelTestVo;
import org.dromara.aigov.enums.AigDeploymentTypeEnum;
import org.dromara.aigov.enums.AigErrorClassEnum;
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
 *     <li>{@code model_type = IMAGE}：走 {@link #probeImage}——真实走一次
 *         {@code /images/generations}（复用 {@code OpenAiImageInvoker}）。<b>不能</b>用 chat 探针：
 *         图像端点要 {@code model + prompt + n + response_format}，发 chat 形态会被上游稳定拒掉
 *         （bluocto 实测 400 {@code Input should be a valid list: ….content}），
 *         而那个 400 与"通道是否可用"无关；</li>
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
     * 图像模型的 {@code model_type} 取值（与 {@code OpenAiImageInvoker} 认领的一致）
     */
    private static final String IMAGE_MODEL_TYPE = "IMAGE";

    /**
     * 图像模型探测用的最小提示词（英文、无品牌信息，避免污染上游内容审核）
     */
    private static final String IMAGE_PROBE_PROMPT = "a single small red square on a white background";

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
        } else if (StringUtils.isNotBlank(target.getApiEndpoint()) && IMAGE_MODEL_TYPE.equalsIgnoreCase(
            StringUtils.blankToDefault(target.getModelType(), "").trim())) {
            // 【2026-10-07 修】图像模型must走图像协议，不能走 chat。
            //
            // 修的是什么：原先所有 openai-compatible 都走 probeHttp，而它发的是
            // chat/completions 形态（messages[].content 是**字符串**）。bluocto（New API 系）
            // 对 Media 分组的模型做严格校验，稳定回
            //   400 Input should be a valid list: ***.***.***.content
            // 即"content 必须是数组"——与模型名无关。结果是：
            //   ① 一个**完全可用**的图像通道，点「测试连接」永远判 UNHEALTHY；
            //   ② 报错还会把人引向"模型名写错了"（见 describeHttpFailure），方向是错的。
            // 实测证据：同配置的 dryRun 一直是 decision=MODEL / invoker=OpenAiImageInvoker，
            // 只有这个探针失败——即"路由对了、探针协议错了"。
            vo.setProbe("OPENAI_IMAGE");
            probeImage(vo, target);
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
            vo.setMessage("snail-ai 链路不可用：请在应用配置中启用 aigov.snail-ai.enabled 并确认 OpenApiChatClient 已装配");
            return;
        }
        ModelInvokeRequest request = new ModelInvokeRequest();
        request.setCapabilityCode(org.dromara.aigov.constant.AigConstants.CAP_TALENT_MATCH);
        // modelId 必传：经 snail-ai 执行时按「模型 → Agent」映射（sai_agent.chat_model_id）选 Agent，
        // 没有 modelId 就无法确定实际执行哪个模型，调用器会明确拒绝而不是随便挑一个 Agent 跑
        request.setModelId(target.getModelId());
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
                    vo.setMessage(describeHttpFailure(status, text, target.getModelKey()));
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
     * 图像模型探测：走 {@code POST {endpoint}/images/generations}，**真实生成一张小图**。
     *
     * <p><b>为什么与 chat 探针分开</b>：chat 探针发的是 {@code messages[].content}（字符串），
     * 图像端点要的是 {@code model + prompt + n + response_format}；两者不能互相代替。
     * 用一个 chat 请求去测图像模型，得到一个稳定的 400，而那个 400 与"通道是否可用"无关
     * （见 {@link #test} 里那段实测说明）。</p>
     *
     * <p><b>为什么直接复用 {@code OpenAiImageInvoker} 而不是自己发一次 HTTP</b>：
     * 调用器已经承担了这条链路的全部要点——解密 SM4 密钥、拼 {@code /images/generations} 地址、
     * 下载返回的图、校验魔术字并重算 sha256。自己再写一份 HTTP 就等于把"能不能用"的判据
     * 分裂成两套：探针说能连、真实调用却可能失败。这里刻意让**探针与真实调用走同一条代码路径**。</p>
     *
     * <p><b>代价（要有预期）</b>：这是一次**真实生成**，会消耗上游额度、耗时为秒级
     * （受 {@code aigov.external-api.image-timeout-ms} 约束，默认 180s）。它只在人手动点
     * 「测试连接」时发生，换来的是"配好的这条通路确实能出图"这一条硬结论。
     * 为省带宽，探测模式下调用器**只确认上游接受请求并给出图，不下载图体**；
     * 因此它证明的是"上游接受 + 有产物"，不证明"那张图当时可下载"——后者由真实调用覆盖。</p>
     *
     * @param vo     结果（就地写 detail/latency/ok/message）
     * @param target 模型快照（端点与密钥由调用器自行从库中取，这里只需 modelId）
     */
    private void probeImage(AigModelTestVo vo, AigModelTestTargetVo target) {
        OpenAiImageInvoker invoker = null;
        for (ModelInvoker candidate : invokerProvider) {
            if (candidate instanceof OpenAiImageInvoker imageInvoker && imageInvoker.available()) {
                invoker = imageInvoker;
                break;
            }
        }
        if (invoker == null) {
            vo.setOk(false);
            vo.setMessage("图像调用器不可用（OpenAiImageInvoker 未装配或已被配置关闭）");
            return;
        }
        ModelInvokeRequest request = new ModelInvokeRequest();
        request.setModelId(target.getModelId());
        request.setModelKey(target.getModelKey());
        request.setModelType(target.getModelType());
        request.setDeploymentType(AigDeploymentTypeEnum.EXTERNAL_API);
        // 提示词刻意用一句与业务无关的英文：这次探测是"通道通不通"，不是"出图好不好"，
        // 不该把品牌/产品信息带进一次巡检性调用。
        request.setPrompt(IMAGE_PROBE_PROMPT);
        // 探测模式：调用器只确认"上游接受这次请求并给出了图"，不下载图体（省掉几 MB）。
        // 代价：它不证明那张图当时可下载——那是真实调用的职责。
        request.setProbeOnly(true);

        ModelInvokeResult result = invoker.invoke(request);
        vo.setLatencyMs(result.getLatencyMs());
        vo.setDetail(truncate(result.getOutput() == null ? result.getErrorSummary() : result.getOutput()));
        if (result.isSuccess()) {
            vo.setOk(true);
            vo.setMessage("连接成功（图像生成）：" + truncate(result.getOutput()));
            return;
        }
        vo.setOk(false);
        AigErrorClassEnum errorClass = result.getErrorCode() == null
            ? null : AigErrorClassEnum.find(result.getErrorCode());
        vo.setMessage("图像生成调用失败"
            + (errorClass == null ? "" : "（" + errorClass.getDesc() + "）")
            + (result.getHttpStatus() == null ? "" : "（HTTP " + result.getHttpStatus() + "）")
            + "：" + truncate(result.getErrorSummary()));
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
     * <p><b>为什么要回显「本次发送的模型标识」</b>：400/404 这一类失败里，上游只会说
     * 「某某 ID 不合法」，而运维看到这句话时并不知道我们究竟发了什么——只能回去翻库。
     * 之前实测就遇到「上游返回：orfree is not a valid model ID」这种只能靠猜的报错。
     * 把发出去的标识一并写进结论，这类问题一眼可判。</p>
     *
     * @param status   状态码
     * @param bodyText 响应体（已掩码）
     * @param modelKey 本次探测实际发送的模型标识
     * @return 可操作描述
     */
    private String describeHttpFailure(int status, String bodyText, String modelKey) {
        String upstream = extractUpstreamMessage(bodyText);
        String sent = "本次发送的模型标识为「" + StringUtils.blankToDefault(modelKey, "(空)") + "」";
        String base;
        if (status == 401 || status == 403) {
            base = "鉴权失败（HTTP " + status + "）：密钥缺失、无效，或该密钥无权访问此模型";
        } else if (status == 402) {
            base = "配额不足（HTTP 402）：上游账户额度/余额不足";
        } else if (status == 404) {
            base = "地址不存在（HTTP 404）：确认访问地址是否含 /v1 等路径前缀，且模型标识是上游有效模型 ID。"
                + sent;
        } else if (status == 429) {
            base = "触发限流（HTTP 429）：降低频率或稍后重试";
        } else if (status >= 500) {
            base = "上游服务错误（HTTP " + status + "）：对端异常，稍后重试通常可恢复";
        } else if (status == 400) {
            // 【2026-10-07 修】原先这里写"请与上游模型目录逐字核对（区分大小写；聚合网关常要求
            // vendor/model 前缀或 -free 后缀）"——那是**猜**，而且实测猜错过方向：
            // 真实失败原因是**请求体形态**（chat 探针把 content 发成字符串，而上游要数组），
            // 与模型标识无关。让人照着"改模型名"去试是白费功夫，正是我们要避免的那类误导。
            // 现在只陈述事实：发出去的是什么 + 上游原话，把判断留给看到两条信息的人。
            base = "请求被拒（HTTP 400）：上游不接受本次请求。" + sent
                + "。请以上游原文为准判断是「模型标识」还是「请求体形态」的问题"
                + "（本探针发的是 chat/completions 形态；图像模型应走 /images/generations）";
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
