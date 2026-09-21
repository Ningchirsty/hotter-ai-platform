package org.dromara.ai.image.comfy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.dromara.ai.image.exception.ImageTaskException;
import org.dromara.ai.video.comfy.ComfyClient;
import org.dromara.ai.video.comfy.ComfyOutput;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 图像创作模块的 ComfyUI HTTP 客户端。
 *
 * <p><b>为什么不直接复用 {@code HttpComfyClient}</b>：那个实现是视频专用的 ——
 * 它的输出解析只认可视频扩展名（{@code looksLikeVideo}），图像模板产出的 PNG 会被过滤掉，
 * 于是一次成功的执行会被记成「没有产出视频」。接口（{@link ComfyClient}）与输出记录
 * （{@link ComfyOutput}）是通用的，这里只重写 HTTP 细节与图像输出识别。</p>
 *
 * <p>与视频侧一致的两条经验：RuoYi 精简后的 Jackson 转换器无法直接构造 {@code JsonNode}，
 * 因此响应一律先按 {@code String} 接收再用模块自有 {@code ObjectMapper} 解析；
 * 另外默认拒绝把回环地址当作 ComfyUI 地址（容器里的 localhost 指向容器自身）。</p>
 */
@Slf4j
public class ImageComfyClient implements ComfyClient {

    private static final String CLIENT_ID = "hotter-ai-platform-image";
    private static final List<String> IMAGE_EXTENSIONS =
        List.of(".png", ".jpg", ".jpeg", ".webp", ".bmp", ".tif", ".tiff");

    private final RestClient restClient;
    private final ObjectMapper mapper;
    private final String baseUrl;

    public ImageComfyClient(String baseUrl, ObjectMapper mapper, boolean allowLoopback) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("image.comfy-base-url 未配置");
        }
        URI uri;
        try {
            uri = URI.create(baseUrl.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("image.comfy-base-url 不是合法 URL：" + baseUrl);
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalStateException("image.comfy-base-url 缺少主机名：" + baseUrl);
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        boolean loopback = "localhost".equals(normalized) || "127.0.0.1".equals(normalized)
            || "::1".equals(normalized) || "0.0.0.0".equals(normalized);
        if (loopback && !allowLoopback) {
            throw new IllegalStateException("image.comfy-base-url 配置为回环地址 " + host
                + "；容器内的 localhost 指向容器自身，请配置 ComfyUI 实际可达地址，"
                + "确需本机直连时显式设置 image.comfy-allow-loopback=true");
        }
        this.baseUrl = baseUrl.trim().replaceAll("/+$", "");
        this.mapper = mapper;
        this.restClient = RestClient.builder().baseUrl(this.baseUrl).build();
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    @Override
    public String uploadImage(String fileName, byte[] content, String mimeType) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("image", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });
        parts.add("overwrite", "true");
        String response;
        try {
            response = restClient.post()
                .uri("/upload/image")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(parts)
                .retrieve()
                .body(String.class);
        } catch (Exception e) {
            throw ImageTaskException.comfyFailure("素材上传到 ComfyUI 失败（" + describe(e) + "）");
        }
        JsonNode parsed = readJson(response, "素材上传");
        String name = parsed.path("name").asText("");
        if (name.isBlank()) {
            throw ImageTaskException.comfyFailure("ComfyUI 未返回上传文件名");
        }
        String subfolder = parsed.path("subfolder").asText("");
        return subfolder.isBlank() ? name : subfolder + "/" + name;
    }

    @Override
    public String submitPrompt(JsonNode graph) {
        String body;
        try {
            body = mapper.writeValueAsString(mapper.createObjectNode()
                .put("client_id", CLIENT_ID)
                .set("prompt", graph));
        } catch (Exception e) {
            throw ImageTaskException.invalidContract("节点图序列化失败");
        }
        String response;
        try {
            response = restClient.post()
                .uri("/prompt")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
        } catch (Exception e) {
            throw ImageTaskException.comfyFailure("提交 ComfyUI 失败（" + describe(e) + "）");
        }
        JsonNode parsed = readJson(response, "任务提交");
        String error = parsed.path("error").asText("");
        if (!error.isBlank()) {
            throw ImageTaskException.comfyFailure("ComfyUI 拒绝该任务（" + error + "）");
        }
        String promptId = parsed.path("prompt_id").asText("");
        if (promptId.isBlank()) {
            throw ImageTaskException.comfyFailure("ComfyUI 未返回 prompt_id");
        }
        return promptId;
    }

    @Override
    public PollResult poll(String promptId) {
        String response;
        try {
            response = restClient.get()
                .uri("/history/{id}", promptId)
                .retrieve()
                .body(String.class);
        } catch (Exception e) {
            throw ImageTaskException.comfyFailure("ComfyUI 状态查询失败（" + describe(e) + "）");
        }
        if (response == null || response.isBlank()) {
            return PollResult.running();
        }
        JsonNode root = readJson(response, "状态查询");
        JsonNode entry = root.get(promptId);
        if (entry == null || entry.isNull()) {
            return PollResult.running();
        }
        JsonNode status = entry.path("status");
        String statusStr = status.path("status_str").asText("");
        if ("error".equals(statusStr)) {
            return PollResult.failed(describeExecutionFailure(status.path("messages")));
        }
        List<ComfyOutput> outputs = extractOutputs(entry.path("outputs"));
        if (!outputs.isEmpty()) {
            return PollResult.succeeded(outputs);
        }
        if ("success".equals(statusStr)) {
            return PollResult.failed("ComfyUI 执行完成但没有产出图片");
        }
        return PollResult.running();
    }

    @Override
    public byte[] fetchOutput(ComfyOutput output) {
        try {
            byte[] bytes = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/view")
                    .queryParam("filename", output.fileName())
                    .queryParam("subfolder", output.subfolder() == null ? "" : output.subfolder())
                    .queryParam("type", output.type() == null ? "output" : output.type())
                    .build())
                .retrieve()
                .body(byte[].class);
            if (bytes == null || bytes.length == 0) {
                throw ImageTaskException.outputInvalid("ComfyUI 输出文件为空");
            }
            return bytes;
        } catch (ImageTaskException e) {
            throw e;
        } catch (Exception e) {
            throw ImageTaskException.outputInvalid("ComfyUI 输出下载失败（" + describe(e) + "）");
        }
    }

    @Override
    public boolean isReachable() {
        try {
            restClient.get().uri("/system_stats").retrieve().toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.warn("ComfyUI 不可达：{}", describe(e));
            return false;
        }
    }

    @Override
    public boolean freeMemory() {
        try {
            restClient.post()
                .uri("/free")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"unload_models\":true,\"free_memory\":true}")
                .retrieve()
                .toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.warn("请求 ComfyUI 释放显存失败：{}", describe(e));
            return false;
        }
    }

    @Override
    public long freeVramMb() {
        try {
            String response = restClient.get().uri("/system_stats").retrieve().body(String.class);
            JsonNode devices = readJson(response, "显存查询").path("devices");
            if (devices.isArray() && !devices.isEmpty()) {
                long bytes = devices.get(0).path("vram_free").asLong(-1);
                return bytes < 0 ? -1 : bytes / (1024 * 1024);
            }
        } catch (Exception e) {
            log.debug("读取 ComfyUI 空闲显存失败：{}", describe(e));
        }
        return -1;
    }

    /**
     * 从 {@code outputs} 收集图片输出。
     *
     * <p>同时接受 {@code images} 与 {@code videos}（不同保存节点放的位置不同），
     * 但只保留图片扩展名 —— 这正是与视频侧实现的关键差别。</p>
     */
    List<ComfyOutput> extractOutputs(JsonNode outputsNode) {
        List<ComfyOutput> outputs = new ArrayList<>();
        if (outputsNode == null || !outputsNode.isObject()) {
            return outputs;
        }
        var nodes = outputsNode.fields();
        while (nodes.hasNext()) {
            var node = nodes.next();
            for (String field : List.of("images", "videos")) {
                JsonNode arr = node.getValue().path(field);
                if (!arr.isArray()) {
                    continue;
                }
                for (JsonNode item : arr) {
                    ComfyOutput output = toOutput(item);
                    if (output != null) {
                        outputs.add(output);
                    }
                }
            }
        }
        return outputs;
    }

    private ComfyOutput toOutput(JsonNode item) {
        String fileName;
        JsonNode nameNode = item.path("filename");
        if (nameNode.isObject()) {
            fileName = nameNode.path("filename").asText("");
        } else {
            fileName = nameNode.asText("");
        }
        if (fileName.isBlank() || !looksLikeImage(fileName)) {
            return null;
        }
        return new ComfyOutput(
            fileName,
            item.path("subfolder").asText(""),
            item.path("type").asText("output"),
            item.path("width").isNumber() ? item.path("width").asInt() : null,
            item.path("height").isNumber() ? item.path("height").asInt() : null,
            item.path("fps").isNumber() ? item.path("fps").asDouble() : null,
            item.path("duration_ms").isNumber() ? item.path("duration_ms").asLong() : null,
            item.path("size_bytes").isNumber() ? item.path("size_bytes").asLong() : null);
    }

    private static boolean looksLikeImage(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        return IMAGE_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    /**
     * 把 ComfyUI 的 messages 数组转成一句可读的诊断信息（不泄露内部路径）。
     */
    String describeExecutionFailure(JsonNode messages) {
        if (messages != null && messages.isArray()) {
            for (JsonNode message : messages) {
                if (!message.isArray() || message.size() < 2) {
                    continue;
                }
                String type = message.get(0).asText("");
                JsonNode payload = message.get(1);
                if ("execution_error".equals(type)) {
                    return "ComfyUI 节点执行失败：" + payload.path("node_type").asText("未知节点")
                        + "(#" + payload.path("node_id").asText("?") + ")，"
                        + payload.path("exception_type").asText("异常") + "："
                        + truncate(payload.path("exception_message").asText(""), 300);
                }
                if ("execution_interrupted".equals(type)) {
                    return "ComfyUI 执行被中断：节点 " + payload.path("node_type").asText("未知节点")
                        + "(#" + payload.path("node_id").asText("?") + ") 未完成，常见原因是显存不足或任务被取消";
                }
            }
        }
        return "ComfyUI 执行失败";
    }

    private JsonNode readJson(String body, String what) {
        if (body == null || body.isBlank()) {
            throw ImageTaskException.comfyFailure("ComfyUI " + what + "返回空响应");
        }
        try {
            return mapper.readTree(body);
        } catch (Exception e) {
            throw ImageTaskException.comfyFailure("ComfyUI " + what + "响应解析失败");
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    /**
     * 递归取根因，输出「异常类名: 消息」，避免只留类名导致根因丢失。
     */
    static String describe(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        String text = root.getClass().getSimpleName() + (message == null ? "" : ": " + message);
        return truncate(text, 300);
    }
}
