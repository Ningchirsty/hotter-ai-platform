package org.dromara.ai.image.cloud;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.dromara.ai.image.exception.ImageTaskException;
import org.dromara.ai.image.service.ImageAssetStore;
import org.dromara.ai.image.service.JdbcImageTaskRepository;
import org.dromara.ai.video.service.LocalFileAssetStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.FileSystemResource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/** HTTP 响应解析、实际 SQL 和文件归档闭环；可回放真实响应，不再次请求付费供应商。 */
class BluOctoImageIntegrationTest {
    @TempDir Path temp;

    @Test void imageResponseBecomesOwnedTaskAssetAndThumbnailWithoutRepeatedRequest() throws Exception {
        String manifest = System.getProperty("cloud.responses.manifest", "");
        if (manifest.isBlank()) {
            replay(System.getProperty("cloud.response.model", "gpt-image-2.5-flare"),
                System.getProperty("cloud.response.fixture", ""), System.getProperty("cloud.evidence.file", ""), "T2I", List.of(), "", CloudImageOutput.DEFAULT);
        } else {
            var rows = new ObjectMapper().readTree(Files.readString(Path.of(manifest)));
            assertTrue(rows.isArray() && !rows.isEmpty());
            for (var row : rows) {
                var references = new java.util.ArrayList<String>();
                for (var ref : row.path("referenceFiles")) references.add(ref.asText());
                replay(row.path("model").asText(), row.path("responseFile").asText(), row.path("evidenceFile").asText(),
                    row.path("capability").asText("T2I"), references, row.path("maskFile").asText(), row.has("output") ? new ObjectMapper().treeToValue(row.path("output"),CloudImageOutput.class) : CloudImageOutput.DEFAULT);
            }
        }
    }

    private void replay(String modelId, String fixture, String evidence, String capability, List<String> referenceFiles, String maskFile, CloudImageOutput output) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        byte[] response;
        if (fixture.isBlank()) {
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(12, 18, BufferedImage.TYPE_INT_RGB), "png", png);
            response = mapper.writeValueAsBytes(Map.of("data", List.of(Map.of("b64_json",
                Base64.getEncoder().encodeToString(png.toByteArray())))));
        } else {
            // 只接收不含请求头和密钥的产出响应文件；不读取真实 API Key。
            String saved = Files.readString(Path.of(fixture), StandardCharsets.UTF_8);
            if (saved.startsWith("\uFEFF")) saved = saved.substring(1);
            response = saved.getBytes(StandardCharsets.UTF_8);
        }
        byte[] expected = Base64.getDecoder().decode(mapper.readTree(response).path("data").get(0).path("b64_json").asText());
        var expectedImage = ImageIO.read(new java.io.ByteArrayInputStream(expected));
        assertNotNull(expectedImage);
        String expectedMime;
        try (var input = ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(expected))) {
            var readers = ImageIO.getImageReaders(input);
            assertTrue(readers.hasNext());
            var reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(java.util.Locale.ROOT);
                expectedMime = "image/" + ("jpg".equals(format) ? "jpeg" : format);
            } finally { reader.dispose(); }
        }

        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
            + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "");
        try (var connection = source.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource("../../script/sql/ry_image_task.sql"));
        }
        var repository = new JdbcImageTaskRepository(new JdbcTemplate(source));
        var assets = new ImageAssetStore(new LocalFileAssetStorage(temp.resolve("assets")));
        var references = new java.util.ArrayList<Long>();
        long inputId = 1000;
        for (String file : referenceFiles) {
            long id = ++inputId; references.add(id);
            byte[] bytes = Files.readAllBytes(Path.of(file));
            String stored = assets.storeUpload("replay",42,"reference.png",bytes,"image/png");
            repository.insertAsset(new org.dromara.ai.image.service.ImageTaskRepository.AssetRow(id,"replay",42,null,
                "IMAGE","UPLOAD","reference.png",stored,"image/png",bytes.length,null,null,null,null,null));
        }
        Long maskId = null;
        if (!maskFile.isBlank()) {
            maskId = ++inputId; byte[] bytes = Files.readAllBytes(Path.of(maskFile));
            String stored = assets.storeUpload("replay",42,"mask.png",bytes,"image/png");
            repository.insertAsset(new org.dromara.ai.image.service.ImageTaskRepository.AssetRow(maskId,"replay",42,null,
                "IMAGE","UPLOAD","mask.png",stored,"image/png",bytes.length,null,null,null,null,null));
        }
        var request = new CloudImageRequest(modelId,"white flower",capability,references,maskId,output);
        var properties = new ImageCloudProperties();
        properties.setEnabled(true);
        Path key = temp.resolve("test.key");
        Files.writeString(key, "replay-only-key");
        properties.setApiKeyFile(key.toString());
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(List.of("T2I","TRANSPARENT").contains(capability) ? "/v1/images/generations" : "/v1/images/edits", exchange -> {
            requests.incrementAndGet();
            assertEquals("Bearer replay-only-key", exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] payloadBytes = exchange.getRequestBody().readAllBytes();
            if (List.of("T2I","TRANSPARENT").contains(capability)) {
                var payload = mapper.readTree(payloadBytes); assertEquals(modelId,payload.path("model").asText());
                if ("TRANSPARENT".equals(capability)) assertEquals("transparent",payload.path("background").asText());
                for (var entry : output.vendorFields().entrySet()) assertEquals(entry.getValue().toString(),payload.path(entry.getKey()).asText());
            } else {
                assertTrue(exchange.getRequestHeaders().getFirst("Content-Type").startsWith("multipart/form-data"));
                String payload = new String(payloadBytes,StandardCharsets.UTF_8);
                assertTrue(payload.contains(modelId));
                assertTrue(payload.contains(references.size() > 1 ? "name=\"image[]\"" : "name=\"image\""));
                if (!maskFile.isBlank()) assertTrue(payload.contains("name=\"mask\""));
                for (var entry : output.vendorFields().entrySet()) assertTrue(payload.contains("name=\""+entry.getKey()+"\"\r\n\r\n"+entry.getValue()+"\r\n"));
            }
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        var client = new BluOctoImageClient(properties, URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
        var service = new ImageCloudService(properties, client, repository, assets, new AtomicLong(100)::incrementAndGet, (model,cap) -> modelId.equals(model) && capability.equals(cap));
        try {
            var created = service.create("replay", 42, 7L, request, "API archive check", "replay-unique-key");
            long taskId = ((Number) created.get("taskId")).longValue();
            assertEquals("ACCEPTED", service.execute(taskId, "replay", 42));
            assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
                while ("RUNNING".equals(repository.requireOwnedTask(taskId, "replay", 42).get("status"))) Thread.sleep(25);
            });
            var completed = repository.requireOwnedTask(taskId, "replay", 42);
            assertEquals("SUCCEEDED", completed.get("status"), String.valueOf(completed.get("error_message")));
            var batch=repository.listTaskOutputs(taskId,"replay",42);
            var responseImages=mapper.readTree(response).path("data");
            assertEquals(output.n(),batch.size());
            assertEquals(responseImages.size(),batch.size());
            for(int i=0;i<batch.size();i++) {
                byte[] captured=Base64.getDecoder().decode(responseImages.get(i).path("b64_json").asText());
                var owned=repository.requireOwnedAsset(((Number)batch.get(i).get("id")).longValue(),"replay",42);
                assertArrayEquals(captured,assets.read(owned.storageKey()));
                assertEquals("replay",owned.tenantId());assertEquals(42,owned.userId());
            }
            Object rawSnapshot=completed.get("input_json");
            var snapshot=rawSnapshot instanceof byte[] jsonBytes ? mapper.readTree(jsonBytes) : mapper.readTree(rawSnapshot.toString());
            if(snapshot.isTextual()) snapshot=mapper.readTree(snapshot.asText());
            assertEquals(request,mapper.treeToValue(snapshot.path("request"),CloudImageRequest.class));
            long assetId = ((Number) completed.get("output_asset_id")).longValue();
            var asset = repository.requireOwnedAsset(assetId, "replay", 42);
            assertEquals("OUTPUT", asset.sourceKind());
            assertEquals(expectedMime, asset.contentType());
            assertEquals(expectedImage.getWidth(), asset.width());
            assertEquals(expectedImage.getHeight(), asset.height());
            assertArrayEquals(expected, assets.read(asset.storageKey()));
            var thumbnail = ImageIO.read(new java.io.ByteArrayInputStream(assets.thumbnail(asset.storageKey(), asset.contentType())));
            assertNotNull(thumbnail);
            assertTrue(thumbnail.getWidth() > 0 && thumbnail.getHeight() > 0);
            assertThrows(ImageTaskException.class, () -> repository.requireOwnedAsset(assetId, "other-tenant", 42));
            assertThrows(ImageTaskException.class, () -> repository.requireOwnedTask(taskId, "replay", 43));
            assertEquals(taskId, ((Number) service.create("replay", 42, 7L, request, "API archive check", "replay-unique-key").get("taskId")).longValue());
            assertThrows(ImageTaskException.class, () -> service.execute(taskId, "replay", 42));
            assertEquals(1, requests.get());
            assertEquals(List.of("CREATED", "SUBMITTED", "SUCCEEDED"), repository.listEvents(taskId, "replay")
                .stream().map(event -> event.get("event_type")).toList());
            if (!evidence.isBlank()) Files.writeString(Path.of(evidence), mapper.writeValueAsString(Map.ofEntries(
                Map.entry("mode","captured-response-replay"), Map.entry("model",modelId), Map.entry("capability",capability),
                Map.entry("status",completed.get("status")), Map.entry("width",asset.width()), Map.entry("height",asset.height()),
                Map.entry("bytes",asset.sizeBytes()), Map.entry("httpRequestsToReplayServer",requests.get()),
                Map.entry("outputCount",batch.size()), Map.entry("outputParameters",output), Map.entry("supplierRequests",0), Map.entry("sqlOwnershipChecked",true), Map.entry("thumbnailDecoded",true))), StandardCharsets.UTF_8);
        } finally {
            service.shutdown();
            server.stop(0);
        }
    }
}
