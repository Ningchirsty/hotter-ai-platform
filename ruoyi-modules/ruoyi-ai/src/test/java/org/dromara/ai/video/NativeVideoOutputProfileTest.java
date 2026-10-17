package org.dromara.ai.video;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dromara.ai.video.config.VideoTierResolutions;
import org.dromara.ai.video.domain.VideoCapability;
import org.dromara.ai.video.exception.VideoTaskException;
import org.dromara.ai.video.service.H3TemplatePreparer;
import org.dromara.ai.video.service.WorkflowContractRegistry;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NativeVideoOutputProfileTest {
    @Test
    void everyModelAndAbilityMapsAllRatiosSizesAndDurationsToRealNativeNodes() throws Exception {
        var mapper = new ObjectMapper();
        var root = Path.of("").toAbsolutePath().getParent().getParent().resolve("script");
        var registry = new WorkflowContractRegistry(root, mapper);
        registry.load();
        var preparer = new H3TemplatePreparer(mapper, new VideoTierResolutions());
        int count = 0;
        var manifest = mapper.createArrayNode();
        for (var version : registry.registeredVersions()) {
            if (!H3TemplatePreparer.isNative(version)) continue;
            var fixed = version.fixedFieldValidation();
            var profiles = fixed.profiles().stream().filter(p -> !p.legacy()).toList();
            assertEquals(9, profiles.size(), version.workflowCode());
            assertEquals(3, profiles.stream().map(p -> p.ratio()).distinct().count());
            assertEquals(fixed.allowedTiers(), new java.util.LinkedHashSet<>(profiles.stream().map(p -> p.tier()).toList()));
            var control = fixed.outputControl();
            var source = mapper.readTree(registry.templateOf(version.workflowCode()));
            for (int index = 0; index < profiles.size(); index++) {
                var profile = profiles.get(index);
                assertTrue(profile.durations().contains("5 秒"));
                if (!profile.durations().contains("20 秒")) {
                    assertThrows(VideoTaskException.class, () -> preparer.validateFields(VideoCapability.valueOf(version.capabilityCode()), version,
                        new H3TemplatePreparer.H3Fields("高分辨率需遵守时长矩阵", "image.png", "first.png", "last.png", profile.tier(), "20 秒")));
                }
                assertTrue(profile.tier().length() <= 32, "Must fit existing task tier column");
                assertArrayEquals(new int[]{profile.width(), profile.height()}, registry.nativeOutputSize(version.workflowCode(), profile.tier()));
                if (profile.ratio().startsWith("1:1")) assertEquals(profile.width(), profile.height());
                else if (profile.ratio().startsWith("16:9")) assertTrue(profile.width() > profile.height());
                else assertTrue(profile.height() > profile.width());
                for (String duration : profile.durations()) {
                    var fields = new H3TemplatePreparer.H3Fields("陶瓷茶壶轻柔冒出蒸汽", "image.png", "first.png", "last.png", profile.tier(), duration);
                    var graph = preparer.prepare(registry.templateOf(version.workflowCode()), VideoCapability.valueOf(version.capabilityCode()), version, fields);
                    for (var mapping : version.mapping()) {
                        int expected = switch (mapping.field()) {
                            case "_width" -> profile.generationWidth() / control.latentDivisor();
                            case "_height" -> profile.generationHeight() / control.latentDivisor();
                            case "_referenceWidth" -> profile.generationWidth();
                            case "_referenceHeight" -> profile.generationHeight();
                            case "_referenceMax" -> Math.max(profile.generationWidth(), profile.generationHeight());
                            default -> -1;
                        };
                        if (expected > 0) assertEquals(expected, graph.path(mapping.nodeId()).path("inputs").path(mapping.inputKey()).asInt(), version.workflowCode());
                    }
                    var resize = graph.path(control.resizeNodeId());
                    assertEquals("ImageScale", resize.path("class_type").asText());
                    assertEquals(profile.width(), resize.path("inputs").path("width").asInt());
                    assertEquals(profile.height(), resize.path("inputs").path("height").asInt());
                    if ("SUPER_RESOLUTION".equals(profile.mode())) {
                        var upscale = graph.path(control.resizeNodeId() + "_upscale");
                        assertEquals("ImageUpscaleWithModel", upscale.path("class_type").asText());
                        assertEquals(source.path(control.videoNodeId()).path("inputs").path("images"), upscale.path("inputs").path("image"));
                        assertEquals(control.resizeNodeId() + "_upscale", resize.path("inputs").path("image").path(0).asText());
                    } else assertEquals(source.path(control.videoNodeId()).path("inputs").path("images"), resize.path("inputs").path("image"));
                    assertEquals(control.resizeNodeId(), graph.path(control.videoNodeId()).path("inputs").path("images").path(0).asText());
                    assertEquals(source.path(control.videoNodeId()).path("inputs").path("audio"), graph.path(control.videoNodeId()).path("inputs").path("audio"));
                    assertFalse(preparer.verifyChecksumException(registry.templateOf(version.workflowCode()), version));
                    String fileName = version.workflowCode() + "-p" + index + "-" + H3TemplatePreparer.parseDurationSeconds(duration) + ".json";
                    var exported = manifest.addObject();
                    exported.put("workflowCode", version.workflowCode());
                    exported.put("modelCode", version.modelCode());
                    exported.put("capabilityCode", version.capabilityCode());
                    exported.put("file", fileName);
                    exported.put("seconds", H3TemplatePreparer.parseDurationSeconds(duration));
                    exported.set("profile", mapper.valueToTree(profile));
                    String directory = System.getProperty("output.acceptance.exportDir");
                    if (directory != null) {
                        var destination = Path.of(directory); Files.createDirectories(destination);
                        mapper.writeValue(destination.resolve(fileName).toFile(), graph);
                    }
                    count++;
                }
            }
            var legacy = fixed.profiles().stream().filter(p -> p.legacy()).findFirst().orElseThrow();
            var oldGraph = preparer.prepare(registry.templateOf(version.workflowCode()), VideoCapability.valueOf(version.capabilityCode()), version,
                new H3TemplatePreparer.H3Fields("旧任务继续使用原始尺寸", "image.png", "first.png", "last.png", legacy.tier(), "5 秒"));
            assertFalse(oldGraph.has(control.resizeNodeId()));
            assertEquals(source.path(control.videoNodeId()).path("inputs").path("images"), oldGraph.path(control.videoNodeId()).path("inputs").path("images"));
            assertThrows(VideoTaskException.class, () -> preparer.validateFields(VideoCapability.valueOf(version.capabilityCode()), version,
                new H3TemplatePreparer.H3Fields("非法尺寸", "image.png", "first.png", "last.png", "8K任意档位", "5 秒")));
        }
        assertEquals(630, count);
        String directory = System.getProperty("output.acceptance.exportDir");
        if (directory != null) mapper.writeValue(Path.of(directory).resolve("manifest.json").toFile(), Map.of("graphs", manifest, "count", count));
    }
}
