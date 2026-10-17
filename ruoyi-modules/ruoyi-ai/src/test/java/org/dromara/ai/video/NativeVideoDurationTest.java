package org.dromara.ai.video;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.dromara.ai.video.config.VideoTierResolutions;
import org.dromara.ai.video.domain.VideoCapability;
import org.dromara.ai.video.domain.WorkflowVersion;
import org.dromara.ai.video.exception.VideoTaskException;
import org.dromara.ai.video.service.H3TemplatePreparer;
import org.dromara.ai.video.service.WorkflowContractRegistry;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NativeVideoDurationTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void everyNativeBindingMapsAllDurationTiersToVideoAndAudioFrames() throws Exception {
        var root = Path.of("").toAbsolutePath().getParent().getParent().resolve("script");
        var registry = new WorkflowContractRegistry(root, mapper);
        registry.load();
        var preparer = new H3TemplatePreparer(mapper, new VideoTierResolutions());
        int checked = 0;
        for (WorkflowVersion version : registry.registeredVersions()) {
            if (!H3TemplatePreparer.isNative(version)) continue;
            assertEquals(List.of("5 秒", "10 秒", "20 秒"), version.fixedFieldValidation().allowedDurations());
            var rule = version.fixedFieldValidation().durationControl();
            assertNotNull(rule, version.workflowCode());
            for (int seconds : List.of(5, 10, 20)) {
                var fields = new H3TemplatePreparer.H3Fields("茶壶轻柔冒出蒸汽", "image.png", "first.png", "last.png",
                    version.fixedFieldValidation().tier(), seconds + " 秒");
                var graph = preparer.prepare(registry.templateOf(version.workflowCode()),
                    VideoCapability.valueOf(version.capabilityCode()), version, fields);
                int frames = rule.frames(seconds);
                assertTrue(frames >= seconds * rule.fps());
                assertTrue(frames - seconds * rule.fps() < rule.frameStep());
                assertEquals(rule.frameOffset(), frames % rule.frameStep());
                var targets = version.mapping().stream().filter(m -> "_durationFrames".equals(m.field())).toList();
                assertFalse(targets.isEmpty(), version.workflowCode());
                for (var target : targets) {
                    assertEquals(frames, graph.path(target.nodeId()).path("inputs").path(target.inputKey()).asInt(),
                        version.workflowCode() + " " + target.nodeId() + ":" + target.inputKey());
                }
                for (JsonNode node : graph) {
                    if ("LTXVEmptyLatentAudio".equals(node.path("class_type").asText())) {
                        assertEquals(frames, node.path("inputs").path("frames_number").asInt());
                    }
                }
                // Preparing a task must not mutate the cached source graph or invalidate its checksum.
                assertFalse(preparer.verifyChecksumException(registry.templateOf(version.workflowCode()), version));
                String exportDirectory = System.getProperty("duration.acceptance.exportDir");
                if (exportDirectory != null) {
                    var destination = Path.of(exportDirectory);
                    java.nio.file.Files.createDirectories(destination);
                    mapper.writeValue(destination.resolve(version.workflowCode() + "-" + seconds + ".json").toFile(), graph);
                }
                checked++;
            }
            var invalid = new H3TemplatePreparer.H3Fields("动态描述", "image.png", "first.png", "last.png",
                version.fixedFieldValidation().tier(), "30 秒");
            assertThrows(VideoTaskException.class, () -> preparer.validateFields(
                VideoCapability.valueOf(version.capabilityCode()), version, invalid));
        }
        assertEquals(105, checked);
    }

    @Test
    void durationFrameGridCoversEachRequestedSecond() {
        var h3 = new WorkflowVersion.DurationControl(24, 17, 5);
        assertEquals(124, h3.frames(5));
        assertEquals(243, h3.frames(10));
        assertEquals(481, h3.frames(20));
        assertEquals(121, new WorkflowVersion.DurationControl(24, 4, 1).frames(5));
        assertEquals(81, new WorkflowVersion.DurationControl(16, 4, 1).frames(5));
        assertEquals(481, new WorkflowVersion.DurationControl(24, 8, 1).frames(20));
        assertThrows(IllegalArgumentException.class, () -> h3.frames(0));
        assertThrows(IllegalArgumentException.class, () -> h3.frames(21));
    }
}
