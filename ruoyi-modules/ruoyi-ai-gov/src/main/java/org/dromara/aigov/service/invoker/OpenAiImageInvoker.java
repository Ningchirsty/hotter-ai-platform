package org.dromara.aigov.service.invoker;

import cn.hutool.core.codec.Base64;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.config.AigExternalApiProperties;
import org.dromara.aigov.domain.vo.AigModelTestTargetVo;
import org.dromara.aigov.enums.AigDeploymentTypeEnum;
import org.dromara.aigov.enums.AigErrorClassEnum;
import org.dromara.aigov.enums.AigProviderTypeEnum;
import org.dromara.aigov.helper.AigModelSecretCipher;
import org.dromara.aigov.mapper.AigModelConfigMapper;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 外部 OpenAI 兼容<b>图像生成</b>调用器（治理层 → 供应商，不经过 snail-ai）。
 *
 * <p><b>它补的是哪条路</b>：本仓的图像生产此前只有 ComfyUI 一条路（{@code ruoyi-ai} 内核）。
 * 引入外部聚合网关后，{@code EXTERNAL_API} 下会同时存在对话模型与图像模型，而
 * {@link OpenAiCompatibleInvoker} 只对接 {@code /chat/completions}——两类模型挤在同一个
 * 部署类型里，仅按部署类型派发必然出不确定的结果。本调用器通过
 * {@link #supportsModelType(String)} 只认领 {@code IMAGE}，把不变式放宽为
 * 「每种 (部署类型, 模型类型) 只有一个认领者」。</p>
 *
 * <p><b>只做文生图，图生图明确拒绝</b>：OpenAI 兼容的图生图/局部编辑走
 * {@code /images/edits}（multipart 上传），与 {@code /images/generations}（JSON）不是同一套
 * 请求形态。检测到输入图片时本调用器<b>显式失败</b>而不是丢掉图片——静默丢图会让模型在
 * 没看过原图的情况下出图，却以「已完成」的样子返回。这与 snail-ai 调用器遇图片载荷的
 * 处理同口径。</p>
 *
 * <p><b>产出怎么交付</b>：结果以 JSON 信封返回（含 base64、字节数、SHA-256、MIME），
 * 由<b>调用方</b>负责落盘到资产库——治理层不持有资产存储，也不该为此反向依赖业务模块。
 * 上游给 {@code url} 还是 {@code b64_json} 都能收：给 URL 时<b>立刻下载成字节</b>，
 * 因此资产不会留在第三方，URL 过期也不再影响我们（这也是默认用 {@code url}
 * 请求字段却依然不留资产在外部的原因）。</p>
 *
 * <p>SPI 约定：不向外抛异常，失败一律转成 {@code ModelInvokeResult.failure}，
 * 并把 HTTP 状态码带上，让错误分类（{@code AigErrorClassEnum}）能据此决定
 * 「退避重试 / 转人工 / 熔断」。</p>
 *
 * <p><b>为什么注入 {@link JsonMapper} 而不是用 {@code JsonUtils}</b>：{@code JsonUtils} 的内部
 * mapper 是 {@code SpringUtils.getBean(JsonMapper.class)} 静态字段，类一加载就需要 Spring 环境。
 * 这会让本类的纯函数（请求体组装、响应解析）在单元测试里直接抛
 * {@code ExceptionInInitializerError}——等于这些逻辑没有测试可覆盖。注入 Spring 里本就存在的
 * {@code JsonMapper} Bean 后，运行期拿到的是平台统一配置的 mapper，测试里也能直接构造。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiImageInvoker implements ModelInvoker {

    /**
     * 错误摘要里保留的上游原文长度
     */
    private static final int DETAIL_MAX = 300;

    /**
     * 图像生成端点路径（相对 api_endpoint）
     */
    private static final String IMAGES_PATH = "/images/generations";

    /**
     * 外部 API 直连配置（开关、超时、结果体积上限）
     */
    private final AigExternalApiProperties properties;

    /**
     * 平台统一配置的 JSON 映射器（由 Spring 注入，见类注释）
     */
    private final JsonMapper jsonMapper;

    /**
     * 服务端内部快照 Mapper（唯一读取 api_key 的语句）
     */
    private final AigModelConfigMapper modelConfigMapper;

    /**
     * 密钥解密工具（与 snail-ai 口径一致）
     */
    private final AigModelSecretCipher secretCipher;

    @Override
    public boolean supports(AigDeploymentTypeEnum deploymentType) {
        return deploymentType == AigDeploymentTypeEnum.EXTERNAL_API;
    }

    @Override
    public boolean supportsModelType(String modelType) {
        // 必须显式声明 IMAGE，空值**不放行**。
        // 与对话调用器相反（那里空值放行是为兼容历史数据）：这里若放行空值，
        // 一个没填 model_type 的对话模型可能被派到图像端点，错得更隐蔽。
        return "IMAGE".equalsIgnoreCase(modelType == null ? null : modelType.trim());
    }

    @Override
    public AigProviderTypeEnum providerType() {
        return AigProviderTypeEnum.IMAGE;
    }

    @Override
    public boolean available() {
        return properties.isEnabled();
    }

    @Override
    public ModelInvokeResult invoke(ModelInvokeRequest request) {
        long start = System.currentTimeMillis();
        try {
            // 1. 前置校验：这些错误重试多少次都一样，必须归类为不可重试
            if (request == null) {
                return failure("调用请求为空", start, AigErrorClassEnum.INVALID_REQUEST, null);
            }
            if (request.getDeploymentType() != AigDeploymentTypeEnum.EXTERNAL_API) {
                return failure("外部图像调用器仅支持 EXTERNAL_API 部署类型", start,
                    AigErrorClassEnum.INVALID_REQUEST, null);
            }
            if (request.getModelId() == null) {
                return failure("调用请求缺少模型ID", start, AigErrorClassEnum.INVALID_REQUEST, null);
            }
            if (StringUtils.isBlank(request.getPrompt())) {
                return failure("图像生成缺少提示词（prompt）", start, AigErrorClassEnum.INVALID_REQUEST, null);
            }
            // 图生图/局部编辑走 /images/edits，形态不同；显式拒绝，绝不静默丢图。
            // 刻意不复用 ModelImagePayload.requireUnsupported：那条提示是给「视觉理解」能力写的
            // （建议另绑视觉模型），而这里的真正出路是图生图端点或 ComfyUI——提示必须指向真正的出路，
            // 否则使用者会照着提示去绑一个视觉模型，而问题根本不在那里。
            List<ModelImagePayload.ImagePart> images = ModelImagePayload.extract(request.getPayload());
            if (!images.isEmpty()) {
                return failure("本调用器只支持文生图；检测到 " + images.size()
                    + " 张输入图片（图生图/局部编辑需 /images/edits，暂未实现）。"
                    + "请先移除图片，或改用 ComfyUI 的图像能力", start,
                    AigErrorClassEnum.INVALID_REQUEST, null);
            }

            // 2. 取服务端内部快照：端点以库中为准，密钥是密文原值
            AigModelTestTargetVo target = modelConfigMapper.selectTestTarget(request.getModelId());
            String endpoint = target != null && StringUtils.isNotBlank(target.getApiEndpoint())
                ? target.getApiEndpoint() : request.getEndpoint();
            if (StringUtils.isBlank(endpoint)) {
                return failure("未配置访问地址（api_endpoint），无法直连外部图像 API", start,
                    AigErrorClassEnum.INVALID_REQUEST, null);
            }
            String apiKey = null;
            if (target != null && StringUtils.isNotBlank(target.getApiKey())) {
                try {
                    apiKey = secretCipher.decrypt(target.getApiKey());
                } catch (Exception e) {
                    // 解密失败属配置错误，重试无用
                    return failure(e.getMessage(), start, AigErrorClassEnum.INVALID_REQUEST, null);
                }
            }

            // 3. 组装并执行
            String url = buildImageUrl(endpoint);
            String body = jsonMapper.writeValueAsString(buildImageBody(request));
            HttpRequest http = HttpRequest.post(url)
                .header("Content-Type", "application/json")
                .body(body)
                .timeout(intClamp(properties.getImageTimeoutMs()));
            if (StringUtils.isNotBlank(apiKey)) {
                http.header("Authorization", "Bearer " + apiKey);
            }

            try (HttpResponse response = http.execute()) {
                int status = response.getStatus();
                String text = mask(response.body(), apiKey);
                if (status < 200 || status >= 300) {
                    log.warn("外部图像 API 调用失败, modelId={}, modelKey={}, status={}",
                        request.getModelId(), request.getModelKey(), status);
                    // 带上 httpStatus：错误分类据此判定限流(429)/鉴权(401)/不可用(5xx)
                    return failure("外部图像 API 返回 HTTP " + status + "："
                        + truncate(extractUpstreamMessage(text)), start, null, status);
                }
                return extractImage(text, request, start);
            }
        } catch (Exception e) {
            log.error("外部图像 API 调用异常, capabilityCode={}, modelId={}",
                request == null ? null : request.getCapabilityCode(),
                request == null ? null : request.getModelId(), e);
            return failure("外部图像 API 调用异常：" + e.getClass().getSimpleName()
                + "（地址不可达或超时，可调大 aigov.external-api.image-timeout-ms）",
                start, AigErrorClassEnum.UNAVAILABLE, null);
        }
    }

    /**
     * 从上游响应里取出图像字节、度量并以 JSON 信封返回。
     *
     * @param bodyText 响应体（已掩码）
     * @param request  调用请求
     * @param start    起始时间（毫秒）
     * @return 调用结果
     */
    private ModelInvokeResult extractImage(String bodyText, ModelInvokeRequest request, long start) {
        // 探测模式（连通性测试用）：只证明「上游接受这次请求并给出了图」，**不下载图体**。
        //
        // 为什么单独留一条路，而不是让探针也走完整链路：真实出图会把整张图 base64 进 JSON 信封，
        // 一张 2048×2048 的 PNG 就是数 MB；连通性测试只回答"这条通路能不能用"，
        // 没必要为此把几 MB 搬一遍。代价是**它不证明那张图当时可下载**——
        // 交付型产品的探针只需回答"能不能用"，真要证明可下载是真实调用的职责。
        if (request.isProbeOnly()) {
            String probeUrl = extractUrl(bodyText);
            String probeB64 = extractB64Json(bodyText);
            if (StringUtils.isBlank(probeUrl) && StringUtils.isBlank(probeB64)) {
                return failure("上游响应里既没有 data[0].b64_json 也没有 data[0].url", start,
                    AigErrorClassEnum.OUTPUT_UNPARSABLE, null);
            }
            Map<String, Object> probeEnvelope = new LinkedHashMap<>();
            probeEnvelope.put("model", request.getModelKey());
            probeEnvelope.put("providerType", AigProviderTypeEnum.IMAGE.getCode());
            probeEnvelope.put("probeOnly", true);
            probeEnvelope.put("hasUrl", StringUtils.isNotBlank(probeUrl));
            probeEnvelope.put("hasB64Json", StringUtils.isNotBlank(probeB64));
            probeEnvelope.put("responseBytes", bodyText == null ? 0 : bodyText.length());
            ModelInvokeResult probeResult = ModelInvokeResult.success(
                jsonMapper.writeValueAsString(probeEnvelope), System.currentTimeMillis() - start);
            probeResult.setModelVersion(request.getModelKey());
            return probeResult;
        }
        byte[] bytes;
        String mimeType;
        String b64 = extractB64Json(bodyText);
        if (StringUtils.isNotBlank(b64)) {
            try {
                bytes = Base64.decode(b64);
            } catch (Exception e) {
                return failure("上游返回的 data[0].b64_json 不是合法 base64", start,
                    AigErrorClassEnum.OUTPUT_UNPARSABLE, null);
            }
            // OpenAI 图像接口 b64_json 默认 png
            mimeType = "image/png";
        } else {
            String remoteUrl = extractUrl(bodyText);
            if (StringUtils.isBlank(remoteUrl)) {
                return failure("上游响应里既没有 data[0].b64_json 也没有 data[0].url", start,
                    AigErrorClassEnum.OUTPUT_UNPARSABLE, null);
            }
            // 不回传第三方 URL 当产物：它会过期，也会把资产留在外部。
            // 这里下载成字节，契约与 b64_json 一致。
            try (HttpResponse download = HttpRequest.get(remoteUrl)
                .timeout(intClamp(properties.getImageTimeoutMs())).execute()) {
                if (download.getStatus() < 200 || download.getStatus() >= 300) {
                    return failure("下载上游图像失败，HTTP " + download.getStatus(), start,
                        AigErrorClassEnum.UNAVAILABLE, download.getStatus());
                }
                bytes = download.bodyBytes();
                mimeType = guessImageMimeType(download.header("Content-Type"), remoteUrl);
            }
        }

        long maxBytes = properties.getImageMaxBytes();
        if (bytes.length > maxBytes) {
            // 显式失败而不是截断：截断后的图会被当成正常产物继续流转
            return failure("上游图像 " + bytes.length + " 字节，超过上限 " + maxBytes
                + " 字节（可调大 aigov.external-api.image-max-bytes）", start,
                AigErrorClassEnum.INVALID_REQUEST, null);
        }

        // JSON 信封：调用方据此落盘。带上 sha256——资产入库与后续去重都要它。
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("model", request.getModelKey());
        envelope.put("providerType", AigProviderTypeEnum.IMAGE.getCode());
        envelope.put("imageCount", 1);
        envelope.put("mimeType", mimeType);
        envelope.put("sizeBytes", bytes.length);
        envelope.put("sha256", DigestUtil.sha256Hex(bytes));
        envelope.put("b64", Base64.encode(bytes));

        ModelInvokeResult result = ModelInvokeResult.success(jsonMapper.writeValueAsString(envelope),
            System.currentTimeMillis() - start);
        result.setModelVersion(request.getModelKey());
        return result;
    }

    /**
     * 组装请求体。
     *
     * <p>字段与实测可用的请求逐一对齐（bluocto / New API 网关）：
     * {@code {"model":"…","prompt":"…","n":1,"response_format":"url"}}。
     * 其中 {@code response_format} 默认 {@code url} 而不是 {@code b64_json}——
     * 理由见 {@link AigExternalApiProperties#getImageResponseFormat()}：
     * 拿不到图比「资产留在第三方」严重得多，而拿到 URL 后立刻下载就同时解决了后者。</p>
     *
     * <p>刻意<b>不</b>带默认 {@code size}：不同上游支持的可选尺寸集合不同，
     * 我们臆测一个默认值会把本可成功的请求判成 400。只有调用方明确给了才带上。</p>
     *
     * @param request 调用请求
     * @return 请求体
     */
    Map<String, Object> buildImageBody(ModelInvokeRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.getModelKey());
        body.put("prompt", buildPrompt(request));
        body.put("n", 1);
        Object size = request.getPayload() == null ? null : request.getPayload().get("size");
        if (size instanceof String sizeText && StringUtils.isNotBlank(sizeText)) {
            body.put("size", sizeText.trim());
        }
        body.put("response_format", resolveResponseFormat(request));
        return body;
    }

    /**
     * 解析 {@code response_format}：调用方显式指定优先，否则用配置值。
     *
     * <p>只接受 {@code url} 与 {@code b64_json} 两个已知取值；其它值原样透传也没有意义
     * （上游只会 400），而静默改写成默认值会让人以为「我填的生效了」。因此非法值一律<b>回落默认</b>
     * 并记一条 warn——这里不抛错是因为它属于调用参数问题，应由归类为 INVALID_REQUEST 的路径处理，
     * 而当前方法没有那个上下文。</p>
     *
     * @param request 调用请求
     * @return 生效的 response_format
     */
    private String resolveResponseFormat(ModelInvokeRequest request) {
        Object fromPayload = request.getPayload() == null ? null : request.getPayload().get("response_format");
        if (fromPayload instanceof String text && StringUtils.isNotBlank(text)) {
            String value = text.trim();
            if ("url".equalsIgnoreCase(value) || "b64_json".equalsIgnoreCase(value)) {
                return value.toLowerCase();
            }
            log.warn("忽略无法识别的 response_format={}，回落到默认值 {}（只支持 url / b64_json）",
                value, properties.getImageResponseFormat());
        }
        String configured = properties.getImageResponseFormat();
        return StringUtils.isBlank(configured) ? "url" : configured.trim().toLowerCase();
    }

    /**
     * 提示词 = 业务提示词 + 不含图片的结构化载荷。
     *
     * <p>剥掉图片是必须的：{@link ModelImagePayload#withoutImages(Map)} 返回不含 base64 的副本，
     * 否则几十 MB 的 base64 会被塞进 prompt 文本，既污染输入也把请求体撑到发不出去。</p>
     *
     * @param request 调用请求
     * @return 提示词
     */
    String buildPrompt(ModelInvokeRequest request) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.isNotBlank(request.getPrompt())) {
            sb.append(request.getPrompt());
        }
        Map<String, Object> textPayload = ModelImagePayload.withoutImages(request.getPayload());
        if (!textPayload.isEmpty()) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append("输入数据（JSON）：").append(jsonMapper.writeValueAsString(textPayload));
        }
        return sb.toString();
    }

    /**
     * 组装图像生成地址：兼容「配到 /v1」与「直接配到 /images/generations」两种写法。
     *
     * @param endpoint 访问地址
     * @return 完整地址
     */
    static String buildImageUrl(String endpoint) {
        String base = endpoint.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base.endsWith(IMAGES_PATH) ? base : base + IMAGES_PATH;
    }

    /**
     * 取 {@code data[0].b64_json}。
     *
     * @param bodyText 响应体
     * @return base64 文本，取不到返回 null
     */
    String extractB64Json(String bodyText) {
        return firstDataField(bodyText, "b64_json");
    }

    /**
     * 取 {@code data[0].url}。
     *
     * @param bodyText 响应体
     * @return 地址，取不到返回 null
     */
    String extractUrl(String bodyText) {
        return firstDataField(bodyText, "url");
    }

    /**
     * 取 {@code data[0]} 下的某个字符串字段。
     *
     * @param bodyText 响应体
     * @param field    字段名
     * @return 值，取不到返回 null
     */
    private String firstDataField(String bodyText, String field) {
        if (StringUtils.isBlank(bodyText)) {
            return null;
        }
        try {
            // readTree 对空输入可能返回 null，必须先判空再取 path
            JsonNode root = jsonMapper.readTree(bodyText);
            if (root == null) {
                return null;
            }
            JsonNode node = root.path("data").path(0).path(field);
            return node.isMissingNode() || node.isNull() ? null : node.asText();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从上游错误体里取 message 字段。
     *
     * @param bodyText 响应体（已掩码）
     * @return 上游消息，取不到返回原文截断
     */
    private String extractUpstreamMessage(String bodyText) {
        if (StringUtils.isBlank(bodyText)) {
            return "（无响应体）";
        }
        try {
            JsonNode root = jsonMapper.readTree(bodyText);
            JsonNode node = root == null ? null : root.path("error").path("message");
            if (node != null && !node.isMissingNode() && !node.isNull()) {
                return truncate(node.asText());
            }
        } catch (Exception e) {
            // 非 JSON 错误体：退回原文
        }
        return truncate(bodyText);
    }

    /**
     * 推断图像 MIME 类型：优先响应头，其次 URL 扩展名，最后退回 png。
     *
     * @param contentType 下载响应的 Content-Type（可为空）
     * @param url         图像地址（可为空）
     * @return MIME 类型
     */
    static String guessImageMimeType(String contentType, String url) {
        String type = contentType == null ? "" : contentType.toLowerCase();
        if (type.contains("jpeg") || type.contains("jpg")) {
            return "image/jpeg";
        }
        if (type.contains("webp")) {
            return "image/webp";
        }
        if (type.contains("png")) {
            return "image/png";
        }
        String path = url == null ? "" : url.toLowerCase();
        if (path.contains(".jpg") || path.contains(".jpeg")) {
            return "image/jpeg";
        }
        if (path.contains(".webp")) {
            return "image/webp";
        }
        return "image/png";
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

    /**
     * long 转 int 并钳制，避免溢出成负数（hutool 的 timeout 收 int）。
     *
     * @param millis 毫秒
     * @return 钳制后的 int
     */
    private int intClamp(long millis) {
        return (int) Math.min(Math.max(millis, 1L), Integer.MAX_VALUE);
    }

    /**
     * 构造失败结果，错误分类与 HTTP 状态码按需带上。
     *
     * @param summary    错误摘要
     * @param start      起始时间（毫秒）
     * @param errorClass 错误分类（会以其 code 作为 errorCode，让分类判定精确而不靠文案猜）
     * @param httpStatus HTTP 状态码（可为 null）
     * @return 失败结果
     */
    private ModelInvokeResult failure(String summary, long start,
                                     AigErrorClassEnum errorClass, Integer httpStatus) {
        return ModelInvokeResult.failure(
            errorClass == null ? null : errorClass.getCode(),
            httpStatus,
            summary,
            System.currentTimeMillis() - start);
    }

}
