package org.dromara.creative.helper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 渲染服务客户端。
 *
 * <p><b>为什么用 JDK HttpClient 而不是引入 RestTemplate/Feign</b>：渲染服务只有一个 POST 与两个 GET，
 * 为它引入一套声明式客户端（还带上负载均衡、重试、编解码约定）不划算；JDK 自带的能力足够，
 * 也让「后端镜像里没有 Chromium」这件事保持简单——后端只是发一次 HTTP。</p>
 *
 * <p><b>失败要说人话</b>：渲染服务没起、超时、模板不存在、素材外链，都要转成可读原因往上抛。
 * 渲染是详情页交付的最后一公里，失败原因模糊会让人误以为是排版逻辑错了。</p>
 *
 * @author creative
 */
@Slf4j
@Component
public class RendererClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 渲染服务地址（内网容器名）。刻意做成配置项：换地址不用改代码。
     */
    @Value("${creative.renderer.base-url:http://creative-renderer:8090}")
    private String baseUrl;

    /**
     * 连接超时（毫秒）
     */
    @Value("${creative.renderer.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    /**
     * 渲染超时（毫秒）。长图渲染实测 300ms 级，给足余量但不无限等。
     */
    @Value("${creative.renderer.read-timeout-ms:120000}")
    private int readTimeoutMs;

    private HttpClient client() {
        return HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(connectTimeoutMs))
            .build();
    }

    /**
     * 渲染结果。
     *
     * @param png              PNG 字节
     * @param width            页宽（px）
     * @param height           页高（px）
     * @param renderMs         渲染耗时（毫秒，服务端实测）
     * @param sha256           渲染结果 sha256（服务端给出，用于可复现性核对）
     * @param templateChecksum 模板校验和
     * @param fontCjkWidth     中文字体探针宽度（缺字时服务端会直接失败）
     */
    public record RenderResult(byte[] png, int width, int height, long renderMs,
                               String sha256, String templateChecksum, int fontCjkWidth) {
    }

    /**
     * 内置模板信息。
     *
     * @param templateCode    模板编码
     * @param templateVersion 模板版本
     * @param checksum        模板文件 sha256
     * @param bytes           模板文件字节数
     */
    public record RendererTemplate(String templateCode, String templateVersion, String checksum, long bytes) {
    }

    /**
     * 渲染。
     *
     * @param templateCode    模板编码
     * @param templateVersion 模板版本
     * @param mode            screen / page / element
     * @param selector        element 模式下的选择器
     * @param layout          layout JSON（素材必须已内联为 data URI）
     * @return 渲染结果
     */
    public RenderResult render(String templateCode, String templateVersion, String mode,
                               String selector, Map<String, Object> layout) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("templateCode", templateCode);
        payload.put("templateVersion", templateVersion);
        payload.put("mode", mode);
        if (selector != null) {
            payload.put("selector", selector);
        }
        payload.put("layout", layout);

        HttpResponse<byte[]> response = send("/render", "POST", toJson(payload), true);
        if (response.statusCode() != 200) {
            throw new ServiceException("渲染服务返回 " + response.statusCode() + "："
                + new String(response.body(), java.nio.charset.StandardCharsets.UTF_8));
        }
        return new RenderResult(
            response.body(),
            intHeader(response, "x-page-width"),
            intHeader(response, "x-page-height"),
            longHeader(response, "x-render-ms"),
            header(response, "x-render-sha256"),
            header(response, "x-template-checksum"),
            intHeader(response, "x-font-cjk-width"));
    }

    /**
     * 取渲染服务内置模板清单（用于与库里的模板记录对账）。
     *
     * @return 模板清单
     */
    public List<RendererTemplate> templates() {
        HttpResponse<byte[]> response = send("/templates", "GET", null, false);
        if (response.statusCode() != 200) {
            throw new ServiceException("取渲染服务模板清单失败：HTTP " + response.statusCode());
        }
        try {
            JsonNode root = MAPPER.readTree(response.body());
            List<RendererTemplate> list = new ArrayList<>();
            for (JsonNode item : root.path("templates")) {
                list.add(new RendererTemplate(
                    item.path("templateCode").asText(),
                    item.path("templateVersion").asText(),
                    item.path("checksum").asText(),
                    item.path("bytes").asLong()));
            }
            return list;
        } catch (Exception e) {
            throw new ServiceException("解析渲染服务模板清单失败：" + e.getMessage());
        }
    }

    /**
     * 渲染服务是否可用（探活；失败不抛异常，供页面展示状态）。
     *
     * @return 版本信息；不可用返回 null
     */
    public String version() {
        try {
            HttpResponse<byte[]> response = send("/version", "GET", null, false);
            if (response.statusCode() != 200) {
                return null;
            }
            return new String(response.body(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("渲染服务探活失败：{}", e.getMessage());
            return null;
        }
    }

    private HttpResponse<byte[]> send(String path, String method, String body, boolean jsonBody) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofMillis(readTimeoutMs));
            if ("POST".equals(method)) {
                builder.header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body == null ? "{}" : body));
            } else {
                builder.GET();
            }
            return client().send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (java.net.ConnectException e) {
            throw new ServiceException("连不上渲染服务（" + baseUrl + "）：请确认 creative-renderer 容器已启动");
        } catch (java.net.http.HttpTimeoutException e) {
            throw new ServiceException("渲染服务超时（" + readTimeoutMs + "ms）：长图过大或服务过载");
        } catch (Exception e) {
            throw new ServiceException("调用渲染服务失败：" + e.getClass().getSimpleName() + " " + e.getMessage());
        }
    }

    private static String toJson(Map<String, Object> payload) {
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (Exception e) {
            throw new ServiceException("组装渲染请求失败：" + e.getMessage());
        }
    }

    private static String header(HttpResponse<?> response, String name) {
        return response.headers().firstValue(name).orElse(null);
    }

    private static int intHeader(HttpResponse<?> response, String name) {
        try {
            return Integer.parseInt(response.headers().firstValue(name).orElse("0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static long longHeader(HttpResponse<?> response, String name) {
        try {
            return Long.parseLong(response.headers().firstValue(name).orElse("0"));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

}
