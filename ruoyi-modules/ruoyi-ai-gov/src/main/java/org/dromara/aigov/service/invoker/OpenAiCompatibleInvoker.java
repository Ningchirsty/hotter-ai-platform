package org.dromara.aigov.service.invoker;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.config.AigExternalApiProperties;
import org.dromara.aigov.domain.vo.AigModelTestTargetVo;
import org.dromara.aigov.enums.AigDeploymentTypeEnum;
import org.dromara.aigov.helper.AigModelSecretCipher;
import org.dromara.aigov.mapper.AigModelConfigMapper;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 外部 OpenAI 兼容端点直连调用器（治理层 → 供应商，不经过 snail-ai）。
 *
 * <p><b>它补的是什么</b>：此前 {@code EXTERNAL_API} 类模型（OpenRouter、各类 OpenAI 兼容网关）
 * 只能落到 {@code SnailAiChatInvoker}——那条路把请求转给 snail-ai 并携带 <b>agentId</b>，
 * 实际执行的模型由 Agent 决定，与治理台填的 {@code model_key} / {@code api_endpoint} 无关。
 * 于是「在治理台配好的外部模型用不上」。本调用器按治理台登记的信息直连端点，
 * 做到「配了什么就用什么」。</p>
 *
 * <p><b>凭据从哪来</b>：{@link ModelInvokeRequest} 里<b>不含密钥</b>（设计如此，避免明文在对象间流转）。
 * 本调用器按 {@code modelId} 取服务端内部快照并解密——复用
 * {@link AigModelConfigMapper#selectTestTarget(Long)}，这样「全仓只有一条语句读 api_key」
 * 的不变式仍然成立。</p>
 *
 * <p><b>安全约定</b>：密钥只用于发请求；上游响应体与异常文本在写入错误摘要前一律把密钥替换成掩码；
 * 日志不含密钥与完整响应体。SPI 约定：不向外抛异常，失败一律转成
 * {@link ModelInvokeResult#failure}。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiCompatibleInvoker implements ModelInvoker {

    /**
     * 错误摘要里保留的上游原文长度
     */
    private static final int DETAIL_MAX = 300;

    /**
     * 要求模型只输出 JSON 的系统提示词前缀
     */
    private static final String JSON_ONLY_PREFIX =
        "你被平台治理层以结构化方式调用。请只输出一个 JSON 对象，不要输出任何解释文字，"
            + "不要使用 Markdown 代码块。";

    /**
     * 外部 API 直连配置（开关与超时）
     */
    private final AigExternalApiProperties properties;

    /**
     * 服务端内部快照 Mapper（唯一读取 api_key 的语句）
     */
    private final AigModelConfigMapper modelConfigMapper;

    /**
     * 密钥加解密工具（与 snail-ai 口径一致）
     */
    private final AigModelSecretCipher secretCipher;

    @Override
    public boolean supports(AigDeploymentTypeEnum deploymentType) {
        // 只接管 EXTERNAL_API（直连端点）。
        // GROUP / EXTERNAL_ENTERPRISE 仍归 SnailAiChatInvoker 走集团 snail-ai 链路——
        // 那两个类型是「集团共享 / 外部企业服务」，由集团侧统一接入，不是让治理层各自直连。
        // 这样切分是确定的：同一种部署类型只会有一个调用器认领，不依赖 Spring Bean 装配顺序。
        return deploymentType == AigDeploymentTypeEnum.EXTERNAL_API;
    }

    @Override
    public boolean available() {
        return properties.isEnabled();
    }

    @Override
    public ModelInvokeResult invoke(ModelInvokeRequest request) {
        long start = System.currentTimeMillis();
        try {
            if (request == null) {
                return ModelInvokeResult.failure("调用请求为空", 0L);
            }
            if (request.getDeploymentType() != AigDeploymentTypeEnum.EXTERNAL_API) {
                return ModelInvokeResult.failure("外部 API 直连调用器仅支持 EXTERNAL_API 部署类型",
                    System.currentTimeMillis() - start);
            }
            if (request.getModelId() == null) {
                return ModelInvokeResult.failure("调用请求缺少模型ID", System.currentTimeMillis() - start);
            }
            if (StringUtils.isNotBlank(request.getModelType()) && !"CHAT".equalsIgnoreCase(request.getModelType())) {
                // 本实现只对接 /chat/completions；embedding / rerank 走的是别的端点，不能混用
                return ModelInvokeResult.failure("外部 API 直连调用器当前只支持 CHAT 类型模型，实际为 "
                    + request.getModelType(), System.currentTimeMillis() - start);
            }

            // 1. 取服务端内部快照：端点以库中为准（治理台登记的就是它），密钥是密文原值
            AigModelTestTargetVo target = modelConfigMapper.selectTestTarget(request.getModelId());
            String endpoint = target != null && StringUtils.isNotBlank(target.getApiEndpoint())
                ? target.getApiEndpoint() : request.getEndpoint();
            if (StringUtils.isBlank(endpoint)) {
                return ModelInvokeResult.failure("未配置访问地址（api_endpoint），无法直连外部 API",
                    System.currentTimeMillis() - start);
            }

            // 2. 解密密钥；解不开就如实报错，绝不把密文当凭据发出去
            String apiKey = null;
            if (target != null && StringUtils.isNotBlank(target.getApiKey())) {
                try {
                    apiKey = secretCipher.decrypt(target.getApiKey());
                } catch (Exception e) {
                    return ModelInvokeResult.failure(e.getMessage(), System.currentTimeMillis() - start);
                }
            }

            // 3. 组装请求。max_tokens 不设：那是业务语义，不该由调用器替业务方决定
            //    图片先校验：越界的 base64 会被上游以「请求体过大」拒绝，那种错误对调用方毫无指向性
            List<ModelImagePayload.ImagePart> images = ModelImagePayload.extract(request.getPayload());
            if (!images.isEmpty()) {
                String imageError = ModelImagePayload.validate(images);
                if (imageError != null) {
                    return ModelInvokeResult.failure(imageError, System.currentTimeMillis() - start);
                }
            }
            String url = buildChatUrl(endpoint);
            String body = buildBody(request);
            HttpRequest http = HttpRequest.post(url)
                .header("Content-Type", "application/json")
                .body(body)
                // hutool 的 timeout 收 int，配置是 long；钳一下避免溢出成负数
                .timeout((int) Math.min(Math.max(properties.getTimeoutMs(), 1L), Integer.MAX_VALUE));
            if (StringUtils.isNotBlank(apiKey)) {
                http.header("Authorization", "Bearer " + apiKey);
            }

            // 4. 执行并解析
            try (HttpResponse response = http.execute()) {
                int status = response.getStatus();
                String text = mask(response.body(), apiKey);
                if (status < 200 || status >= 300) {
                    log.warn("外部 API 调用失败, modelId={}, modelKey={}, status={}",
                        request.getModelId(), request.getModelKey(), status);
                    return ModelInvokeResult.failure(
                        "外部 API 返回 HTTP " + status + "：" + truncate(extractUpstreamMessage(text)),
                        System.currentTimeMillis() - start);
                }
                String content = extractContent(text);
                if (StringUtils.isBlank(content)) {
                    return ModelInvokeResult.failure("外部 API 响应里没有可用的 message.content",
                        System.currentTimeMillis() - start);
                }
                String json = extractJsonObject(content);
                if (json == null) {
                    return ModelInvokeResult.failure("模型输出不是合法 JSON 对象，无法通过能力的输出模板校验",
                        System.currentTimeMillis() - start);
                }
                ModelInvokeResult result = ModelInvokeResult.success(json, System.currentTimeMillis() - start);
                result.setModelVersion(request.getModelKey());
                result.setTokensUsed(extractTotalTokens(text));
                return result;
            }
        } catch (Exception e) {
            // SPI 约定：不向外抛异常
            log.error("外部 API 直连调用异常, capabilityCode={}, modelId={}",
                request == null ? null : request.getCapabilityCode(),
                request == null ? null : request.getModelId(), e);
            return ModelInvokeResult.failure("外部 API 直连调用异常：" + e.getClass().getSimpleName()
                + "（地址不可达或超时，可调大 aigov.external-api.timeout-ms）",
                System.currentTimeMillis() - start);
        }
    }

    /**
     * 组装请求体。
     *
     * <p>携带图片时走 OpenAI 兼容的多模态消息格式：{@code content} 由
     * {@code [{"type":"text"},{"type":"image_url"},...]} 组成。不带图片时仍用纯字符串
     * {@code content}——那是最通用的写法，没必要为文本调用套多模态外壳。</p>
     *
     * @param request 调用请求
     * @return JSON 字符串
     */
    private String buildBody(ModelInvokeRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.getModelKey());
        body.put("messages", List.of(
            Map.of("role", "system", "content", buildSystemPrompt(request.getOutputSchema())),
            Map.of("role", "user", "content", buildUserContent(request))
        ));
        body.put("stream", false);
        if (properties.isJsonResponseFormat()) {
            body.put("response_format", Map.of("type", "json_object"));
        }
        return JsonUtils.toJsonString(body);
    }

    /**
     * 组装 user 消息内容：有图片则输出多模态分片列表，否则输出纯文本。
     *
     * @param request 调用请求
     * @return 字符串或分片列表
     */
    private Object buildUserContent(ModelInvokeRequest request) {
        List<ModelImagePayload.ImagePart> images = ModelImagePayload.extract(request.getPayload());
        String text = buildUserPrompt(request);
        if (images.isEmpty()) {
            return text;
        }
        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(Map.of("type", "text", "text", text));
        for (ModelImagePayload.ImagePart image : images) {
            // 标签要显式写给模型：两张图谁是「参考」谁是「成品」决定了比对方向，
            // 靠图片顺序让模型猜是不可靠的。
            String label = StringUtils.isBlank(image.label()) ? "图片" : image.label();
            parts.add(Map.of("type", "text", "text", "【" + label + "】"));
            parts.add(Map.of("type", "image_url", "image_url", Map.of("url", image.dataUrl())));
        }
        return parts;
    }

    /**
     * 系统提示词：把输出模板翻译成「只输出这些字段的 JSON」。
     *
     * @param outputSchema 能力输出模板（可为空）
     * @return 系统提示词
     */
    private String buildSystemPrompt(String outputSchema) {
        StringBuilder sb = new StringBuilder(JSON_ONLY_PREFIX);
        List<String> fields = schemaFieldNames(outputSchema);
        if (!fields.isEmpty()) {
            sb.append("JSON 必须包含以下字段（缺一不可）：").append(String.join("、", fields)).append("。");
        }
        return sb.toString();
    }

    /**
     * 用户提示词：业务提示词 + 结构化载荷。
     *
     * <p><b>必须先剥掉图片</b>：{@link ModelImagePayload#withoutImages(Map)} 返回不含
     * base64 的载荷副本。若直接序列化原始 payload，几十 MB 的 base64 会被塞进提示词文本，
     * 既污染模型输入也把请求体撑到无法发送。</p>
     *
     * @param request 调用请求
     * @return 用户提示词
     */
    private String buildUserPrompt(ModelInvokeRequest request) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.isNotBlank(request.getPrompt())) {
            sb.append(request.getPrompt());
        }
        Map<String, Object> textPayload = ModelImagePayload.withoutImages(request.getPayload());
        if (!textPayload.isEmpty()) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append("输入数据（JSON）：").append(JsonUtils.toJsonString(textPayload));
        }
        if (sb.length() == 0) {
            sb.append("请按要求输出 JSON。");
        }
        return sb.toString();
    }

    /**
     * 从输出模板里取出字段名列表。
     *
     * @param outputSchema 形如 {@code {"fields":[{"name":"answer"}]}}
     * @return 字段名列表，取不到返回空列表
     */
    private List<String> schemaFieldNames(String outputSchema) {
        List<String> names = new ArrayList<>();
        if (StringUtils.isBlank(outputSchema)) {
            return names;
        }
        try {
            Map<String, Object> schema = JsonUtils.parseMap(outputSchema);
            Object fieldsObj = schema == null ? null : schema.get("fields");
            if (!(fieldsObj instanceof List<?> fields)) {
                return names;
            }
            for (Object item : fields) {
                if (item instanceof Map<?, ?> field) {
                    Object name = field.get("name");
                    if (name != null && StringUtils.isNotBlank(String.valueOf(name))) {
                        names.add(String.valueOf(name));
                    }
                }
            }
        } catch (Exception e) {
            // 模板解析不了不是调用失败的理由，退回「只要求 JSON」
            log.warn("能力输出模板解析失败，本次只要求模型输出 JSON：{}", e.getClass().getSimpleName());
        }
        return names;
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
        return base.endsWith("/chat/completions") ? base : base + "/chat/completions";
    }

    /**
     * 取 {@code choices[0].message.content}。
     *
     * @param bodyText 响应体（已掩码）
     * @return 内容，取不到返回 null
     */
    private String extractContent(String bodyText) {
        try {
            JsonNode root = JsonUtils.getJsonMapper().readTree(bodyText);
            if (root == null) {
                return null;
            }
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            return content.isMissingNode() || content.isNull() ? null : content.asText();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 取 {@code usage.total_tokens}；取不到返回 null（不得编造）。
     *
     * @param bodyText 响应体
     * @return token 数或 null
     */
    private Long extractTotalTokens(String bodyText) {
        try {
            JsonNode root = JsonUtils.getJsonMapper().readTree(bodyText);
            JsonNode usage = root == null ? null : root.path("usage");
            if (usage == null || !usage.has("total_tokens")) {
                return null;
            }
            return usage.get("total_tokens").asLong();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从模型输出里抠出 JSON 对象。
     *
     * <p>模型经常不听话：会包 Markdown 代码块、会在 JSON 前后加一句解释。这里做三层兜底，
     * 而不是直接把整段文本当 JSON——那样等于把「模型没按格式回」变成一次彻底失败。</p>
     *
     * @param content 模型输出的 content
     * @return JSON 对象字符串，抠不出返回 null
     */
    private String extractJsonObject(String content) {
        if (StringUtils.isBlank(content)) {
            return null;
        }
        String text = content.trim();
        // 去掉 ```json ... ``` / ``` ... ``` 围栏
        if (text.startsWith("```")) {
            int firstLineEnd = text.indexOf('\n');
            if (firstLineEnd > 0) {
                text = text.substring(firstLineEnd + 1);
            }
            int fenceEnd = text.lastIndexOf("```");
            if (fenceEnd >= 0) {
                text = text.substring(0, fenceEnd);
            }
            text = text.trim();
        }
        if (JsonUtils.isJson(text)) {
            return text;
        }
        // 前后带解释文字：取第一个 { 到最后一个 } 之间的片段
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            String candidate = text.substring(start, end + 1);
            if (JsonUtils.isJson(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * 从上游错误体里取 message 字段。
     *
     * @param bodyText 响应体（已掩码）
     * @return 上游消息，取不到返回空串
     */
    private String extractUpstreamMessage(String bodyText) {
        if (StringUtils.isBlank(bodyText)) {
            return "（无响应体）";
        }
        try {
            JsonNode root = JsonUtils.getJsonMapper().readTree(bodyText);
            JsonNode msg = root == null ? null : root.path("error").path("message");
            if (msg != null && !msg.isMissingNode() && !msg.isNull()) {
                return truncate(msg.asText());
            }
        } catch (Exception e) {
            // 非 JSON 错误体：退回原文
        }
        return truncate(bodyText);
    }

    /**
     * 把响应/异常里的密钥替换成掩码（防御上游回显密钥）。
     *
     * @param text 原文
     * @param key  明文密钥
     * @return 掩码后的文本
     */
    private String mask(String text, String key) {
        if (StringUtils.isBlank(text) || StringUtils.isBlank(key)) {
            return text;
        }
        return text.replace(key, "***");
    }

    /**
     * 截断文本。
     *
     * @param text 原文
     * @return 截断结果
     */
    private String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= DETAIL_MAX ? text : text.substring(0, DETAIL_MAX) + "…";
    }

}
