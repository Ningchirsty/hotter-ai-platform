package org.dromara.creative.invoker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.enums.AigDeploymentTypeEnum;
import org.dromara.aigov.service.invoker.ModelInvokeRequest;
import org.dromara.aigov.service.invoker.ModelInvokeResult;
import org.dromara.aigov.service.invoker.ModelInvoker;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.creative.constant.CreativeConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 本地私有 LLM 调用器（Ollama 的 OpenAI 兼容端点）。
 *
 * <p><b>为什么需要它（而不是复用现成的两个调用器）</b>：治理层现有分工里
 * {@code LOCAL} 是「进程内规则引擎」（{@code LocalRuleModelInvoker} / {@code ContentLocalInvoker}），
 * 而 HTTP 直连只给 {@code EXTERNAL_API}——后者在枚举里 {@code external()=true}，
 * 会被路由策略的 {@code allow_external='N'} 拦住，并且在审计里被标成「数据外发」。
 * 公司自有 GPU 机器上的模型并不是外发，把它登记成 EXTERNAL_API 是**语义错误**：
 * 既会让 INTERNAL 任务的合法调用被拒，也会污染审计口径。</p>
 *
 * <p>所以这里按 {@code ModelInvoker} 文档既有的扩展点新增一个调用器：
 * <b>部署类型仍是 {@code LOCAL}（数据不出公司），但实现方式是访问内网 HTTP 端点</b>；
 * 并通过 {@link #supportsCapability(String)} 只认领视觉工厂自己的两个能力编码，
 * 不与 talent_match / document_parse 等既有本地能力抢路由。</p>
 *
 * <p>端点的来源与优先级：模型登记里的 {@code api_endpoint} → 配置 {@code creative.llm.endpoint}。
 * 两者都空则直接返回可读失败，不做静默兜底。</p>
 *
 * @author creative
 */
@Slf4j
@Component
public class CreativeLocalChatInvoker implements ModelInvoker {

    /**
     * 默认端点（内网自建 Ollama，OpenAI 兼容路径前缀为 /v1）
     */
    @Value("${creative.llm.endpoint:http://192.168.2.223:11434/v1}")
    private String defaultEndpoint;

    /**
     * 单次调用超时（秒）。本地 7B 模型在 A100 上通常数秒内返回；
     * 超时给足是为了避免把「机器忙」误判成「模型不可用」。
     */
    @Value("${creative.llm.timeout-seconds:180}")
    private int timeoutSeconds;

    /**
     * 采样温度：视觉方向与分镜属于创意草稿，需要一定发散；
     * 但输出被要求是 JSON，温度过高会明显增加格式失败率，故取中间值。
     */
    @Value("${creative.llm.temperature:0.7}")
    private double temperature;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public boolean supports(AigDeploymentTypeEnum deploymentType) {
        return deploymentType == AigDeploymentTypeEnum.LOCAL;
    }

    @Override
    public boolean supportsCapability(String capabilityCode) {
        return CreativeConstants.CAP_DIRECTION_DRAFT.equals(capabilityCode)
            || CreativeConstants.CAP_STORYBOARD_DRAFT.equals(capabilityCode);
    }

    @Override
    public boolean available() {
        // 端点是否真的活着由调用结果如实反映；这里不假装探活（探活会把「配置存在」当成「模型可用」）
        return true;
    }

    @Override
    public ModelInvokeResult invoke(ModelInvokeRequest request) {
        long start = System.currentTimeMillis();
        if (request == null || !supportsCapability(request.getCapabilityCode())) {
            return ModelInvokeResult.failure("视觉工厂本地调用器不处理该能力", 0L);
        }
        if (request.getDeploymentType() != AigDeploymentTypeEnum.LOCAL) {
            return ModelInvokeResult.failure("视觉工厂本地调用器仅支持 LOCAL 部署类型", elapsed(start));
        }
        String endpoint = StringUtils.isNotBlank(request.getEndpoint())
            ? request.getEndpoint().trim() : StringUtils.blankToDefault(defaultEndpoint, "").trim();
        if (StringUtils.isBlank(endpoint)) {
            return ModelInvokeResult.failure("未配置本地模型端点（sai_model_config.api_endpoint 与 creative.llm.endpoint 均为空）",
                elapsed(start));
        }
        String url = endpoint.endsWith("/") ? endpoint + "chat/completions" : endpoint + "/chat/completions";

        try {
            String body = MAPPER.writeValueAsString(buildBody(request));
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(Math.max(10, timeoutSeconds)))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            long cost = elapsed(start);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return ModelInvokeResult.failure("本地模型返回 HTTP " + response.statusCode()
                    + "：" + summarize(response.body()), cost);
            }
            String content = extractContent(response.body());
            if (StringUtils.isBlank(content)) {
                return ModelInvokeResult.failure("本地模型返回为空（原始响应：" + summarize(response.body()) + "）", cost);
            }
            ModelInvokeResult result = ModelInvokeResult.success(content, cost);
            result.setModelVersion(request.getModelKey());
            log.info("本地 LLM 调用完成, capability={}, model={}, cost={}ms, contentLength={}",
                request.getCapabilityCode(), request.getModelKey(), cost, content.length());
            return result;
        } catch (Exception e) {
            log.warn("本地 LLM 调用失败, capability={}, url={}, error={}",
                request.getCapabilityCode(), url, e.toString());
            return ModelInvokeResult.failure("本地模型调用异常：" + e.getClass().getSimpleName()
                + (StringUtils.isBlank(e.getMessage()) ? "" : "（" + summarize(e.getMessage()) + "）"), elapsed(start));
        }
    }

    /**
     * 组装 OpenAI 兼容的 chat/completions 请求体。
     *
     * <p>系统提示词强制「只输出 JSON」：不这么写，本地小模型很容易带上解释性文字，
     * 让整次调用白费（治理层的输出校验也会判不合格）。</p>
     *
     * @param request 调用请求
     * @return 请求体
     */
    private ObjectNode buildBody(ModelInvokeRequest request) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("model", StringUtils.blankToDefault(request.getModelKey(), "qwen2.5:7b-instruct"));
        root.put("stream", false);
        root.put("temperature", temperature);
        // Ollama 支持 response_format=json_object，能把「只输出 JSON」从提示词升级为解码约束
        root.putObject("response_format").put("type", "json_object");
        ArrayNode messages = root.putArray("messages");
        ObjectNode system = messages.addObject();
        system.put("role", "system");
        system.put("content", "你是电商详情页视觉方案助手。只输出一个 JSON 对象，不要输出任何解释文字，"
            + "不要使用 Markdown 代码块。字段名必须与要求完全一致；不确定的内容不要编造，宁可省略该字段。"
            + schemaHint(request.getOutputSchema()));
        ObjectNode user = messages.addObject();
        user.put("role", "user");
        user.put("content", StringUtils.blankToDefault(request.getPrompt(), ""));
        return root;
    }

    /**
     * 组装「输出模板」提示片段。
     *
     * <p><b>为什么未声明字段就一个字都不注入</b>：这里踩过一个真实的坑——
     * 治理台给视觉工厂能力登记的 {@code output_schema} 是 {@code {"fields":[],"note":"..."}}，
     * 意思是「只要求合法 JSON，具体字段由业务侧逐字段验收」。早期实现把整段 schema 原样塞进
     * 系统提示词当作「输出模板」，结果本地小模型**照着模板输出** {@code {"fields":[],"note":...}}，
     * 完全不理会业务提示词里的 {@code directions/screens} 契约，表现为「调用成功但 0 条被采纳」。
     * 所以只有 schema 真的声明了字段时才注入，并且这时的模板才是模型该照抄的东西。</p>
     *
     * @param outputSchema 能力的输出模板 JSON（可空）
     * @return 提示片段；不该注入时返回空串
     */
    static String schemaHint(String outputSchema) {
        if (StringUtils.isBlank(outputSchema)) {
            return "";
        }
        try {
            JsonNode fields = MAPPER.readTree(outputSchema).path("fields");
            if (!fields.isArray() || fields.isEmpty()) {
                return "";
            }
            return "\n输出模板（字段名必须一致）：" + outputSchema;
        } catch (Exception e) {
            // schema 坏了不该阻断调用：业务提示词里本来就有契约
            return "";
        }
    }

    /**
     * 从响应里取 choices[0].message.content。
     *
     * @param raw 响应体
     * @return 文本内容；取不到返回 null
     */
    private static String extractContent(String raw) {
        try {
            JsonNode node = MAPPER.readTree(raw);
            JsonNode choices = node.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return null;
            }
            JsonNode message = choices.get(0).path("message");
            JsonNode content = message.path("content");
            if (content.isTextual()) {
                return content.asText();
            }
            // 少数实现把内容放在 text 字段（completions 风格）
            JsonNode text = choices.get(0).path("text");
            return text.isTextual() ? text.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static long elapsed(long start) {
        return System.currentTimeMillis() - start;
    }

    /**
     * 摘要化长文本（日志与失败原因里不塞整段响应）。
     *
     * @param text 原文
     * @return 最多 200 字
     */
    private static String summarize(String text) {
        if (text == null) {
            return "";
        }
        String flat = text.replaceAll("\\s+", " ").trim();
        return flat.length() <= 200 ? flat : flat.substring(0, 200) + "…";
    }

}
