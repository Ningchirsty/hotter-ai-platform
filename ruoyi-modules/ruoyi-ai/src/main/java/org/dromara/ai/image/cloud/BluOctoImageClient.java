package org.dromara.ai.image.cloud;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.dromara.ai.image.exception.ImageTaskException;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/** 蓝章鱼图像生成适配器：一次付费请求，无自动重试；兼容 URL 和 base64 输出。 */
public class BluOctoImageClient {
    public static final List<String> MODELS = List.of("flux-2-pro", "gpt-image-2.5-flare",
        "gpt-image-2.5-sunburst", "qwen-image-3.0", "qwen-image-3.0-pro", "wan2.7-image", "wan2.7-image-pro");
    private static final int MAX_RESPONSE_BYTES = 64 * 1024 * 1024;
    private static final int MAX_IMAGE_BYTES = 20 * 1024 * 1024;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ImageCloudProperties properties;
    private final HttpClient client;
    private final URI base;

    public BluOctoImageClient(ImageCloudProperties properties) {
        this(properties, URI.create("https://bluocto.com"));
    }

    // 仅包内离线测试可替换地址；生产地址不接受浏览器或配置覆写。
    BluOctoImageClient(ImageCloudProperties properties, URI base) {
        this.properties = properties;
        this.base = base;
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    public void requireConfigured() {
        if (!properties.configured()) throw failure("CLOUD_NOT_CONFIGURED", "云端服务尚未配置 API Key");
    }

    public byte[] generate(String model, String prompt) {
        return generate(new CloudImageRequest(model, prompt, "T2I", List.of(), null), List.of());
    }

    public record InputImage(byte[] content, String contentType, boolean mask) {}

    public byte[] generate(CloudImageRequest input, List<InputImage> images) {
        if(input.output().n() != 1) throw failure("INVALID_CONTRACT","批量任务须使用多图结果入口");
        return generateBatch(input,images).get(0);
    }

    public List<byte[]> generateBatch(CloudImageRequest input, List<InputImage> images) {
        requireConfigured();
        String model = input.model();
        input.validateShape();
        if (!MODELS.contains(model)) throw failure("CLOUD_MODEL_UNSUPPORTED", "云端模型不在允许清单中");
        try {
            var fields = new java.util.LinkedHashMap<String, Object>();
            fields.put("model", model); fields.put("prompt", input.prompt());
            if ("TRANSPARENT".equals(input.capability())) {
                fields.put("background", "transparent"); fields.put("output_format", "png");
                fields.put("size", "1024x1024"); fields.put("quality", "low");
            }
            fields.putAll(input.output().vendorFields());
            HttpRequest request;
            if (List.of("T2I", "TRANSPARENT").contains(input.capability())) {
                request = authorized("/v1/images/generations").header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(fields))).build();
            } else {
                long references = images.stream().filter(image -> !image.mask()).count();
                long masks = images.stream().filter(InputImage::mask).count();
                if (references != input.referenceAssetIds().size() || masks != (input.maskAssetId() == null ? 0 : 1)) throw failure("INVALID_CONTRACT", "参考图内容与任务快照不一致");
                String boundary = "hotter-" + java.util.UUID.randomUUID();
                var body = new java.io.ByteArrayOutputStream();
                for (var field : fields.entrySet()) {
                    body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + field.getKey()
                        + "\"\r\n\r\n" + field.getValue() + "\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                int index = 0;
                for (var image : images) {
                    String name = image.mask() ? "mask" : references > 1 ? "image[]" : "image";
                    body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name
                        + "\"; filename=\"input-" + (++index) + "." + ("image/jpeg".equals(image.contentType()) ? "jpg" : "image/webp".equals(image.contentType()) ? "webp" : "png")
                        + "\"\r\nContent-Type: " + image.contentType() + "\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    body.write(image.content()); body.write("\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                body.write(("--" + boundary + "--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                request = authorized("/v1/images/edits").header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
            }
            JsonNode response = mapper.readTree(send(request, MAX_RESPONSE_BYTES));
            JsonNode data = response.path("data");
            if (!data.isArray() || data.size() != input.output().n()) {
                throw failure("CLOUD_OUTPUT_INVALID", "云端返回数量与请求数量不一致，请核对供应商响应契约");
            }
            var outputs = new java.util.ArrayList<byte[]>();
            for (JsonNode image : data) {
                String encoded = image.path("b64_json").asText("");
                byte[] content = !encoded.isEmpty() ? Base64.getDecoder().decode(encoded)
                    : fetchImage(URI.create(image.path("url").asText("")));
                if(content.length == 0 || content.length > MAX_IMAGE_BYTES) throw failure("CLOUD_OUTPUT_INVALID","云端图像为空或超过 20MB 上限");
                outputs.add(content);
            }
            return List.copyOf(outputs);
        } catch (ImageTaskException e) {
            throw e;
        } catch (Exception e) {
            // 供应商原始响应可能回显请求和密钥，禁止写进日志/错误消息。
            throw failure("CLOUD_OUTPUT_INVALID", "无法解析云端图像响应，请核对供应商响应契约");
        }
    }

    /** 只读鉴权与模型权限核验，不生成图片、不触发付费请求。 */
    public List<String> authorizedModels() {
        requireConfigured();
        try {
            JsonNode response = mapper.readTree(send(authorized("/v1/models").GET().build(), 1024 * 1024));
            JsonNode data = response.path("data");
            if (!data.isArray()) throw failure("CLOUD_RESPONSE_INVALID", "模型清单响应格式不符合接口契约");
            java.util.ArrayList<String> allowed = new java.util.ArrayList<>();
            for (JsonNode row : data) {
                String id = row.path("id").asText();
                if (MODELS.contains(id)) allowed.add(id);
            }
            return List.copyOf(allowed);
        } catch (ImageTaskException e) {
            throw e;
        } catch (Exception e) {
            throw failure("CLOUD_RESPONSE_INVALID", "无法读取云端模型清单");
        }
    }

    private HttpRequest.Builder authorized(String path) {
        return HttpRequest.newBuilder(base.resolve(path))
            .timeout(Duration.ofSeconds(Math.max(1, Math.min(900, properties.getTimeoutSeconds()))))
            .header("Authorization", "Bearer " + properties.readKey());
    }

    private byte[] fetchImage(URI uri) {
        assertOutputUrl(uri);
        // 输出下载永不带 API Key，并禁止跳转到未核准域名。
        return send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60)).GET().build(), MAX_IMAGE_BYTES);
    }

    void assertOutputUrl(URI uri) {
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || uri.getUserInfo() != null
            || (uri.getPort() != -1 && uri.getPort() != 443)
            || properties.getOutputHosts().stream().noneMatch(host::equalsIgnoreCase)) {
            throw failure("CLOUD_OUTPUT_HOST_DENIED", "云端输出域名未获准下载，请由管理员核准供应商的输出域名");
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                byte[] raw = address.getAddress();
                boolean reservedV4 = raw.length == 4 && ((raw[0] & 255) == 0 || (raw[0] & 255) >= 224
                    || ((raw[0] & 255) == 100 && (raw[1] & 255) >= 64 && (raw[1] & 255) <= 127)
                    || ((raw[0] & 255) == 169 && (raw[1] & 255) == 254));
                boolean uniqueLocalV6 = raw.length == 16 && (raw[0] & 254) == 252;
                if (reservedV4 || uniqueLocalV6 || address.isAnyLocalAddress() || address.isLoopbackAddress()
                    || address.isLinkLocalAddress() || address.isSiteLocalAddress() || address.isMulticastAddress()) {
                    throw failure("CLOUD_OUTPUT_HOST_DENIED", "云端输出地址指向受限网络");
                }
            }
        } catch (ImageTaskException e) {
            throw e;
        } catch (Exception e) {
            throw failure("CLOUD_OUTPUT_DOWNLOAD_FAILED", "云端输出域名无法解析");
        }
    }

    private byte[] send(HttpRequest request, int limit) {
        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream stream = response.body()) {
                int status = response.statusCode();
                if (status == 401 || status == 403) throw failure("CLOUD_AUTH_FAILED", "云端 API Key 无效或没有该模型权限");
                if (status == 429) throw failure("CLOUD_RATE_LIMITED", "云端限流或额度不足，请检查供应商控制台");
                if (status < 200 || status >= 300) throw failure("CLOUD_HTTP_ERROR", "云端接口返回 HTTP " + status);
                var read = new java.util.concurrent.FutureTask<byte[]>(() -> stream.readNBytes(limit + 1));
                Thread.startVirtualThread(read);
                byte[] body;
                try {
                    body = read.get(Math.max(1, request.timeout().orElse(Duration.ofSeconds(60)).toSeconds()),
                        java.util.concurrent.TimeUnit.SECONDS);
                } catch (java.util.concurrent.TimeoutException e) {
                    read.cancel(true);
                    throw new HttpTimeoutException("body timeout");
                }
                if (body.length > limit) throw failure("CLOUD_OUTPUT_INVALID", "云端响应超过大小上限");
                return body;
            }
        } catch (HttpTimeoutException e) {
            throw failure("CLOUD_RESULT_UNKNOWN", "请求超时，供应商可能仍在生成并计费；请先核对供应商任务记录，勿重复提交");
        } catch (ImageTaskException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw failure("CLOUD_RESULT_UNKNOWN", "请求被中断，供应商结果未知；请先核对供应商任务记录");
        } catch (Exception e) {
            throw failure("CLOUD_RESULT_UNKNOWN", "云端连接中断，供应商结果未知；请先核对供应商任务记录");
        }
    }

    private static ImageTaskException failure(String code, String message) {
        return new ImageTaskException(code, message);
    }
}
