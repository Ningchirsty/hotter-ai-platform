package org.dromara.ai.image.cloud;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.dromara.ai.image.exception.ImageTaskException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class BluOctoImageClientTest {
    @TempDir Path temp;
    HttpServer server;
    ImageCloudProperties properties;
    BluOctoImageClient client;
    AtomicInteger requests;

    @BeforeEach void setup() throws Exception {
        properties = new ImageCloudProperties();
        properties.setEnabled(true);
        Path key = temp.resolve("test.key");
        Files.writeString(key, "test-only-key");
        properties.setApiKeyFile(key.toString());
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        client = new BluOctoImageClient(properties, URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
        requests = new AtomicInteger();
    }
    @AfterEach void stop() { server.stop(0); }

    void reply(int status, String body) {
        server.createContext("/v1/images/generations", exchange -> {
            requests.incrementAndGet();
            assertEquals("Bearer test-only-key", exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
    }

    @Test void usesExactModelAndMinimalContract() throws Exception {
        server.createContext("/v1/images/generations", exchange -> {
            requests.incrementAndGet();
            var payload = new ObjectMapper().readTree(exchange.getRequestBody());
            assertEquals(2, payload.size());
            assertEquals("qwen-image-3.0-pro", payload.path("model").asText());
            assertEquals("white flower", payload.path("prompt").asText());
            byte[] body = "{\"data\":[{\"b64_json\":\"AQID\"}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        assertArrayEquals(new byte[] {1, 2, 3}, client.generate("qwen-image-3.0-pro", "white flower"));
        assertEquals(1, requests.get());
    }

    @Test void rejectsUnknownModelWithoutPaidRequest() {
        reply(200, "{}");
        assertThrows(ImageTaskException.class, () -> client.generate("invented-model", "hello"));
        assertEquals(0, requests.get());
    }

    @Test void missingKeyFailsBeforeRequest() {
        properties.setApiKeyFile("");
        reply(200, "{}");
        assertEquals("CLOUD_NOT_CONFIGURED", assertThrows(ImageTaskException.class,
            () -> client.generate("flux-2-pro", "hello")).getErrorCode());
        assertEquals(0, requests.get());
    }

    @Test void authErrorsNeverEchoSupplierBodyAndNeverRetry() {
        reply(401, "test-only-key secret-reflected-provider-message");
        var error = assertThrows(ImageTaskException.class, () -> client.generate("flux-2-pro", "hello"));
        assertEquals("CLOUD_AUTH_FAILED", error.getErrorCode());
        assertFalse(error.getMessage().contains("secret"));
        assertFalse(error.getMessage().contains("test-only-key"));
        assertEquals(1, requests.get());
    }

    @Test void windowsUtf8KeyFileWorksWithoutLeakingBomIntoAuthorization() throws Exception {
        Files.writeString(Path.of(properties.getApiKeyFile()), "\uFEFFtest-only-key\r\n");
        reply(200, "{\"data\":[{\"b64_json\":\"AQID\"}]}");
        assertArrayEquals(new byte[] {1, 2, 3}, client.generate("flux-2-pro", "hello"));
        assertEquals(1, requests.get());
    }

    @Test void malformedKeyFileIsRejectedBeforeNetworkRequest() throws Exception {
        reply(200, "{}");
        for (String value : List.of("test key", "test\nkey", "测试", "")) {
            Files.writeString(Path.of(properties.getApiKeyFile()), value);
            assertEquals("CLOUD_NOT_CONFIGURED", assertThrows(ImageTaskException.class,
                () -> client.generate("flux-2-pro", "hello")).getErrorCode());
        }
        assertEquals(0, requests.get());
    }

    @Test void supplier503IsReportedWithoutRetryOrRawResponseLeak() {
        reply(503, "test-only-key reflected-secret");
        var error = assertThrows(ImageTaskException.class, () -> client.generate("flux-2-pro", "hello"));
        assertEquals("CLOUD_HTTP_ERROR", error.getErrorCode());
        assertTrue(error.getMessage().contains("503"));
        assertFalse(error.getMessage().contains("secret"));
        assertFalse(error.getMessage().contains("test-only-key"));
        assertEquals(1, requests.get());
    }

    @Test void rateLimitDoesNotRetry() {
        reply(429, "{}");
        assertEquals("CLOUD_RATE_LIMITED", assertThrows(ImageTaskException.class,
            () -> client.generate("flux-2-pro", "hello")).getErrorCode());
        assertEquals(1, requests.get());
    }

    @Test void rejectsAsyncEmptyAndMultipleOutputs() {
        reply(200, "{\"data\":[]}");
        assertEquals("CLOUD_OUTPUT_INVALID", assertThrows(ImageTaskException.class,
            () -> client.generate("flux-2-pro", "hello")).getErrorCode());
    }

    @Test void outputUrlsCannotReadPrivateNetworkOrRedirectArbitraryHosts() {
        properties.setOutputHosts(List.of("127.0.0.1", "localhost", "bluocto.com"));
        for (String url : List.of("https://127.0.0.1/a", "https://localhost/a", "http://bluocto.com/a",
            "https://bluocto.com:8188/a", "https://user:secret@bluocto.com/a", "https://evil.example/a")) {
            assertThrows(ImageTaskException.class, () -> client.assertOutputUrl(URI.create(url)), url);
        }
    }

    @Test void onlyReturnsAuthorizedKnownModelIds() {
        server.createContext("/v1/models", exchange -> {
            byte[] body = "{\"data\":[{\"id\":\"flux-2-pro\"},{\"id\":\"unrelated\"}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        assertEquals(List.of("flux-2-pro"), client.authorizedModels());
    }

    @Test void timeoutIsUnknownResultAndNoAutomaticSecondRequest() {
        properties.setTimeoutSeconds(1);
        server.createContext("/v1/images/generations", exchange -> {
            requests.incrementAndGet();
            try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            exchange.close();
        });
        assertEquals("CLOUD_RESULT_UNKNOWN", assertThrows(ImageTaskException.class,
            () -> client.generate("flux-2-pro", "hello")).getErrorCode());
        assertEquals(1, requests.get());
    }

    @Test void malformedOutputDoesNotExposeResponse() {
        reply(200, "test-only-key");
        assertEquals("CLOUD_OUTPUT_INVALID", assertThrows(ImageTaskException.class,
            () -> client.generate("flux-2-pro", "hello")).getErrorCode());
    }
    @Test void multipartUsesReferencesAndMaskWithoutSendingAssetIdsToSupplier() throws Exception {
        server.createContext("/v1/images/edits", exchange -> {
            requests.incrementAndGet();
            assertEquals("Bearer test-only-key", exchange.getRequestHeaders().getFirst("Authorization"));
            assertTrue(exchange.getRequestHeaders().getFirst("Content-Type").startsWith("multipart/form-data; boundary="));
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(body.contains("name=\"image\""));
            assertTrue(body.contains("name=\"mask\""));
            assertTrue(body.contains("gpt-image-2.5-flare"));
            assertFalse(body.contains("referenceAssetIds"));
            byte[] response = "{\"data\":[{\"b64_json\":\"AQID\"}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,response.length); exchange.getResponseBody().write(response); exchange.close();
        });
        var request = new CloudImageRequest("gpt-image-2.5-flare","flower","MASK",List.of(99L),100L);
        assertArrayEquals(new byte[]{1,2,3},client.generate(request,List.of(new BluOctoImageClient.InputImage(new byte[]{4},"image/png",false),new BluOctoImageClient.InputImage(new byte[]{5},"image/png",true))));
        assertEquals(1,requests.get());
    }
    @Test void multipleReferencesUseOpenAiArrayField() throws Exception {
        server.createContext("/v1/images/edits", exchange -> {
            requests.incrementAndGet();
            String body = new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
            assertEquals(2,body.split(java.util.regex.Pattern.quote("name=\"image[]\""),-1).length-1);
            byte[] response = "{\"data\":[{\"b64_json\":\"AQID\"}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,response.length); exchange.getResponseBody().write(response); exchange.close();
        });
        var request = new CloudImageRequest("gpt-image-2.5-sunburst","flower","MULTI",List.of(99L,100L),null);
        assertArrayEquals(new byte[]{1,2,3},client.generate(request,List.of(new BluOctoImageClient.InputImage(new byte[]{4},"image/png",false),new BluOctoImageClient.InputImage(new byte[]{5},"image/jpeg",false))));
    }
    @Test void transparentTemplateUsesJsonAndExactDocumentedOptions() throws Exception {
        server.createContext("/v1/images/generations", exchange -> {
            requests.incrementAndGet();
            var payload = new ObjectMapper().readTree(exchange.getRequestBody());
            assertEquals("transparent",payload.path("background").asText());
            assertEquals("png",payload.path("output_format").asText());
            assertEquals("1024x1024",payload.path("size").asText());
            assertEquals("low",payload.path("quality").asText());
            assertFalse(payload.has("input_fidelity"));
            byte[] response = "{\"data\":[{\"b64_json\":\"AQID\"}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,response.length); exchange.getResponseBody().write(response); exchange.close();
        });
        assertArrayEquals(new byte[]{1,2,3},client.generate(new CloudImageRequest("gpt-image-2.5-flare","flower","TRANSPARENT",List.of(),null),List.of()));
    }

}
