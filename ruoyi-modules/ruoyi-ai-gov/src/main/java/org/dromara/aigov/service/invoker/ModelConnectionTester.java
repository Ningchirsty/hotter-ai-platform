package org.dromara.aigov.service.invoker;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.domain.vo.AigModelTestTargetVo;
import org.dromara.aigov.domain.vo.AigModelTestVo;
import org.dromara.aigov.enums.AigDeploymentTypeEnum;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
     * 探测超时（毫秒）。刻意不做成配置项：这是运维动作的固定预算，
     * 与业务调用超时（aigov.snail-ai.timeout-ms）不是一回事，混在一起会互相牵制。
     */
    private static final int TEST_TIMEOUT_MS = 8000;

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
     * @param target 模型快照
     */
    private void probeHttp(AigModelTestVo vo, AigModelTestTargetVo target) {
        String url = buildChatUrl(target.getApiEndpoint());
        String body = JsonUtils.toJsonString(Map.of(
            "model", target.getModelKey(),
            "messages", List.of(Map.of("role", "user", "content", PROBE_PROMPT)),
            "max_tokens", 1,
            "stream", false
        ));
        long start = System.currentTimeMillis();
        try {
            HttpRequest request = HttpRequest.post(url)
                .header("Content-Type", "application/json")
                .body(body)
                .timeout(TEST_TIMEOUT_MS);
            if (StringUtils.isNotBlank(target.getApiKey())) {
                request.header("Authorization", "Bearer " + target.getApiKey());
            }
            try (HttpResponse response = request.execute()) {
                vo.setLatencyMs(System.currentTimeMillis() - start);
                int status = response.getStatus();
                String text = mask(response.body(), target.getApiKey());
                if (status >= 200 && status < 300) {
                    vo.setOk(true);
                    vo.setMessage("连接成功（HTTP " + status + "）");
                    vo.setDetail(truncate(text));
                    return;
                }
                vo.setOk(false);
                if (status == 401 || status == 403) {
                    vo.setMessage("鉴权失败（HTTP " + status + "）：密钥缺失或无效");
                } else if (status == 404) {
                    vo.setMessage("地址不存在（HTTP 404）：请确认访问地址是否包含 /v1 等路径前缀");
                } else {
                    vo.setMessage("连接失败（HTTP " + status + "）");
                }
                vo.setDetail(truncate(text));
            }
        } catch (Exception e) {
            vo.setLatencyMs(System.currentTimeMillis() - start);
            vo.setOk(false);
            vo.setMessage("连接异常：" + e.getClass().getSimpleName() + "（地址不可达或超时）");
            vo.setDetail(truncate(mask(e.getMessage(), target.getApiKey())));
        }
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
