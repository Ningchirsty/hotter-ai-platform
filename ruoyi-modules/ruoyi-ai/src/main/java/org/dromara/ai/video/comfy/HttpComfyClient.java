package org.dromara.ai.video.comfy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.dromara.ai.video.exception.VideoTaskException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于 ComfyUI HTTP API 的客户端实现。
 *
 * <p>网络约定（来自交接文档 §2/§4）：</p>
 * <ul>
 *   <li>ComfyUI 可能在另一台主机或容器；容器内的 {@code 127.0.0.1} 指容器自身，
 *       因此默认<b>拒绝</b>把回环地址当作 ComfyUI 地址，除非显式放开。</li>
 *   <li>可用性检查在提交前执行，避免把网络不可达误报为工作流执行失败。</li>
 *   <li>错误信息脱敏：不外泄节点图、模型路径与凭据。</li>
 * </ul>
 */
@Slf4j
public class HttpComfyClient implements ComfyClient {

    private final RestClient restClient;
    private final ObjectMapper mapper;
    private final String baseUrl;

    /**
     * @param baseUrl      ComfyUI 基础地址，例如 http://192.168.2.223:8188
     * @param mapper       JSON 解析器
     * @param allowLoopback 是否允许回环地址（仅同机进程直连的联调环境可放开）
     */
    public HttpComfyClient(String baseUrl, ObjectMapper mapper, boolean allowLoopback) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("comfyui.base-url 未配置");
        }
        URI uri;
        try {
            uri = URI.create(baseUrl.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("comfyui.base-url 不是合法 URL");
        }
        String host = uri.getHost();
        if (host == null) {
            throw new IllegalStateException("comfyui.base-url 缺少主机名");
        }
        boolean loopback = "localhost".equalsIgnoreCase(host)
            || "127.0.0.1".equals(host)
            || "::1".equals(host)
            || "0.0.0.0".equals(host);
        if (loopback && !allowLoopback) {
            throw new IllegalStateException(
                "comfyui.base-url 配置为回环地址 " + host
                    + "；容器内的 localhost 指向容器自身，请配置 ComfyUI 实际可达地址，"
                    + "确需本机直连时显式设置 comfyui.allow-loopback=true");
        }
        this.baseUrl = baseUrl.trim().replaceAll("/+$", "");
        this.mapper = mapper;
        this.restClient = RestClient.builder().baseUrl(this.baseUrl).build();
    }

    @Override
    public String uploadImage(String fileName, byte[] content, String mimeType) {
        if (content == null || content.length == 0) {
            throw VideoTaskException.assetNotFound("素材内容为空");
        }
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("image", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return fileName;
            }
        }).contentType(resolveMediaType(mimeType));
        builder.part("overwrite", "true");
        try {
            // 响应以 String 接收后用本模块的 ObjectMapper 解析。
            // 直接 body(JsonNode.class) 会在精简的 Jackson 转换器下抛
            // HttpMessageConversionException（JsonNode 是抽象类型，无法被构造）。
            String responseText = restClient.post()
                .uri("/upload/image")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(builder.build())
                .retrieve()
                .body(String.class);
            if (responseText == null || responseText.isBlank()) {
                throw VideoTaskException.comfyFailure("ComfyUI 上传返回为空", null);
            }
            JsonNode response = mapper.readTree(responseText);
            String name = response.path("name").asText(null);
            if (name == null || name.isBlank()) {
                throw VideoTaskException.comfyFailure("ComfyUI 上传未返回文件名", null);
            }
            String subfolder = response.path("subfolder").asText("");
            return subfolder.isBlank() ? name : subfolder + "/" + name;
        } catch (VideoTaskException e) {
            throw e;
        } catch (Exception e) {
            // 必须记录根因：只报异常类名（如 HttpMessageConversionException）
            // 无法定位问题，之前因此浪费了一轮排查。
            log.error("ComfyUI 图片上传失败: {}", describe(e));
            throw VideoTaskException.comfyFailure("ComfyUI 图片上传失败（" + describe(e) + "）", e);
        }
    }

    @Override
    public String submitPrompt(JsonNode graph) {
        try {
            // 必须以 String 提交：以 byte[] 作为 application/json 的 body 时
            // 没有匹配的 HttpMessageConverter，RestClient 会抛 HttpMessageConversionException。
            String body = mapper.writeValueAsString(java.util.Map.of(
                "prompt", graph,
                "client_id", "hotter-ai-platform"));
            String responseText = restClient.post()
                .uri("/prompt")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
            if (responseText == null || responseText.isBlank()) {
                throw VideoTaskException.comfyFailure("ComfyUI 提交返回为空", null);
            }
            JsonNode response = mapper.readTree(responseText);
            if (response.hasNonNull("error")) {
                throw VideoTaskException.comfyFailure(
                    "ComfyUI 拒绝该任务（" + truncate(response.path("error").toString()) + "）", null);
            }
            String promptId = response.path("prompt_id").asText(null);
            if (promptId == null || promptId.isBlank()) {
                throw VideoTaskException.comfyFailure("ComfyUI 未返回 prompt_id", null);
            }
            return promptId;
        } catch (VideoTaskException e) {
            throw e;
        } catch (Exception e) {
            log.error("ComfyUI 任务提交失败: {}", describe(e));
            throw VideoTaskException.comfyFailure("ComfyUI 任务提交失败（" + describe(e) + "）", e);
        }
    }

    @Override
    public PollResult poll(String promptId) {
        try {
            String historyText = restClient.get()
                .uri("/history/{id}", promptId)
                .retrieve()
                .body(String.class);
            if (historyText == null || historyText.isBlank()) {
                return PollResult.running();
            }
            JsonNode history = mapper.readTree(historyText);
            if (!history.has(promptId)) {
                return PollResult.running();
            }
            JsonNode entry = history.get(promptId);
            JsonNode status = entry.path("status");
            String statusStr = status.path("status_str").asText("");
            if ("error".equalsIgnoreCase(statusStr)) {
                return PollResult.failed(describeExecutionFailure(status.path("messages")));
            }
            List<ComfyOutput> outputs = extractOutputs(entry.path("outputs"));
            if (!outputs.isEmpty()) {
                return PollResult.succeeded(outputs);
            }
            if ("success".equalsIgnoreCase(statusStr)) {
                return PollResult.failed("ComfyUI 执行完成但没有产出视频");
            }
            return PollResult.running();
        } catch (Exception e) {
            log.error("ComfyUI 状态查询失败: {}", describe(e));
            throw VideoTaskException.comfyFailure("ComfyUI 状态查询失败（" + describe(e) + "）", e);
        }
    }

    /**
     * 把 ComfyUI 的失败记录翻译成可诊断的一句话。
     *
     * <p>此前无论什么原因都只记「ComfyUI 执行失败」，运维看不出是显存不足、
     * 节点抛异常还是被中断——实际排查时因此多花了一轮。这里从
     * {@code status.messages} 里提取：</p>
     * <ul>
     *   <li>{@code execution_error}：节点 id/类型 + 异常类型 + 异常信息；</li>
     *   <li>{@code execution_interrupted}：被中断时的节点 id/类型
     *       （显存不足时 ComfyUI 也会以此形式上报）。</li>
     * </ul>
     * 字段缺失时逐级降级，始终返回非空文案。
     */
    String describeExecutionFailure(JsonNode messages) {
        if (messages != null && messages.isArray()) {
            for (JsonNode message : messages) {
                if (!message.isArray() || message.size() < 2) {
                    continue;
                }
                String kind = message.get(0).asText("");
                JsonNode payload = message.get(1);
                if ("execution_error".equals(kind)) {
                    String nodeType = payload.path("node_type").asText("");
                    String nodeId = payload.path("node_id").asText("");
                    String exceptionType = payload.path("exception_type").asText("");
                    String exceptionMessage = payload.path("exception_message").asText("");
                    StringBuilder sb = new StringBuilder("ComfyUI 节点执行失败");
                    if (!nodeType.isBlank()) {
                        sb.append("：").append(nodeType);
                        if (!nodeId.isBlank()) {
                            sb.append("(#").append(nodeId).append(")");
                        }
                    }
                    if (!exceptionType.isBlank()) {
                        sb.append("，").append(exceptionType);
                    }
                    if (!exceptionMessage.isBlank()) {
                        sb.append("：").append(exceptionMessage);
                    }
                    return sb.toString();
                }
                if ("execution_interrupted".equals(kind)) {
                    String nodeType = payload.path("node_type").asText("");
                    String nodeId = payload.path("node_id").asText("");
                    StringBuilder sb = new StringBuilder("ComfyUI 执行被中断");
                    if (!nodeType.isBlank()) {
                        sb.append("：节点 ").append(nodeType);
                        if (!nodeId.isBlank()) {
                            sb.append("(#").append(nodeId).append(")");
                        }
                        sb.append(" 未完成，常见原因是显存不足或任务被取消");
                    }
                    return sb.toString();
                }
            }
        }
        return "ComfyUI 执行失败";
    }

    @Override
    public boolean freeMemory() {
        try {
            // /free 是 ComfyUI 官方接口：unload_models 卸载模型，free_memory 归还显存。
            restClient.post()
                .uri("/free")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"unload_models\":true,\"free_memory\":true}")
                .retrieve()
                .body(String.class);
            log.info("已请求 ComfyUI 释放显存与模型缓存");
            return true;
        } catch (Exception e) {
            // 释放失败不应影响主流程，只记录。
            log.warn("请求 ComfyUI 释放显存失败：{}", describe(e));
            return false;
        }
    }

    /**
     * 读取本实例所在 GPU 的空闲显存。
     *
     * <p>ComfyUI 用 {@code --cuda-device} 固定在一张卡上，因此 {@code devices[0]}
     * 就是它实际在用的那张（实测：8189 实例报的是 GPU0，8188 实例报的是 GPU1）。</p>
     */
    @Override
    public long freeVramMb() {
        try {
            String text = restClient.get().uri("/system_stats").retrieve().body(String.class);
            JsonNode devices = mapper.readTree(text).path("devices");
            if (devices.isArray() && !devices.isEmpty()) {
                long free = devices.get(0).path("vram_free").asLong(-1L);
                return free < 0 ? -1L : free / (1024L * 1024L);
            }
        } catch (Exception e) {
            // 取不到就返回 -1（未知），由调用方决定是否跳过闸门，不影响主流程。
            log.debug("读取 ComfyUI 显存失败：{}", describe(e));
        }
        return -1L;
    }

    /**
     * 从 ComfyUI 的 outputs 中提取成片。
     *
     * <p>实测发现：ComfyUI 的 {@code SaveVideo} 节点把视频放在 <b>images</b> 数组里，
     * 字段为 {@code filename}/{@code subfolder}/{@code type}（不是 videos 数组）。
     * 只解析 videos 会把成功执行误判为「没有产出视频」，这是实际踩过的缺陷。
     * 因此这里同时接受 images 与 videos，并按扩展名/格式字段判断是否为视频。</p>
     */
    List<ComfyOutput> extractOutputs(JsonNode outputsNode) {
        List<ComfyOutput> results = new ArrayList<>();
        if (outputsNode == null || !outputsNode.isObject()) {
            return results;
        }
        outputsNode.fields().forEachRemaining(nodeEntry -> {
            JsonNode node = nodeEntry.getValue();
            collect(node.path("videos"), results, true);
            collect(node.path("images"), results, false);
        });
        return results;
    }

    private void collect(JsonNode array, List<ComfyOutput> results, boolean forceVideo) {
        if (array == null || !array.isArray()) {
            return;
        }
        for (JsonNode item : array) {
            // 实测 filename 是字符串；个别节点会给出 {"filename": ...} 形态，两种都兼容。
            String fileName;
            JsonNode nameNode = item.path("filename");
            if (nameNode.isTextual()) {
                fileName = nameNode.asText();
            } else if (nameNode.isObject()) {
                fileName = nameNode.path("filename").asText(null);
            } else {
                continue;
            }
            if (fileName == null || fileName.isBlank()) {
                continue;
            }
            String format = item.path("format").asText("");
            if (!forceVideo && !looksLikeVideo(fileName, format)) {
                continue;
            }
            results.add(new ComfyOutput(
                fileName,
                item.path("subfolder").asText(""),
                item.path("type").asText("output"),
                item.hasNonNull("width") ? item.get("width").asInt() : null,
                item.hasNonNull("height") ? item.get("height").asInt() : null,
                item.hasNonNull("fps") ? item.get("fps").asDouble() : null,
                item.hasNonNull("duration_ms") ? item.get("duration_ms").asLong() : null,
                item.hasNonNull("size_bytes") ? item.get("size_bytes").asLong() : null));
        }
    }

    /**
     * 判断输出是否为视频。ComfyUI 的 SaveVideo 常带 {@code animated: true}，
     * 但没有该字段时按扩展名判断。
     */
    private static boolean looksLikeVideo(String fileName, String format) {
        String lower = fileName.toLowerCase();
        for (String ext : new String[] {".mp4", ".webm", ".mkv", ".mov", ".avi", ".gif"}) {
            if (lower.endsWith(ext)) {
                return true;
            }
        }
        String fmt = format == null ? "" : format.toLowerCase();
        return fmt.contains("video") || fmt.equals("mp4") || fmt.equals("auto");
    }

    @Override
    public byte[] fetchOutput(ComfyOutput output) {
        try {
            byte[] body = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/view")
                    .queryParam("filename", output.fileName())
                    .queryParam("subfolder", output.subfolder() == null ? "" : output.subfolder())
                    .queryParam("type", output.type() == null ? "output" : output.type())
                    .build())
                .retrieve()
                .body(byte[].class);
            if (body == null || body.length == 0) {
                throw VideoTaskException.outputInvalid("ComfyUI 输出文件为空");
            }
            return body;
        } catch (VideoTaskException e) {
            throw e;
        } catch (Exception e) {
            throw VideoTaskException.outputInvalid("ComfyUI 成片下载失败（" + e.getClass().getSimpleName() + "）");
        }
    }

    @Override
    public boolean isReachable() {
        try {
            restClient.get().uri("/system_stats").retrieve().toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.warn("ComfyUI 不可达：{}", e.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * 服务端配置的 ComfyUI 地址（脱敏日志可用）。
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    private static MediaType resolveMediaType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return MediaType.IMAGE_PNG;
        }
        try {
            return MediaType.parseMediaType(mimeType);
        } catch (Exception e) {
            return MediaType.IMAGE_PNG;
        }
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 200 ? value : value.substring(0, 200) + "…";
    }

    /**
     * 拼出「异常类名: 根因 message」的可诊断描述。
     *
     * <p>只带类名会丢失真正的失败原因（这是实际踩过的坑）；
     * 这里只取异常消息，不含响应体、凭据或模型路径。</p>
     */
    private static String describe(Throwable e) {
        StringBuilder sb = new StringBuilder(e.getClass().getSimpleName());
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String msg = root.getMessage();
        if (msg != null && !msg.isBlank()) {
            sb.append(": ").append(msg.length() > 300 ? msg.substring(0, 300) + "…" : msg);
        }
        return sb.toString();
    }
}
