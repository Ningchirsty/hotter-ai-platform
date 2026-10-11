package org.dromara.ai.creative;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.dromara.ai.image.domain.ImageCapability;
import org.dromara.ai.image.service.ImageTemplatePreparer;
import org.dromara.ai.image.service.ImageWorkflowContractRegistry;
import org.dromara.ai.video.domain.VideoCapability;
import org.dromara.ai.video.service.H3TemplatePreparer;
import org.dromara.ai.video.service.WorkflowContractRegistry;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CreativeAbilityTest {
    static final ObjectMapper MAPPER = new ObjectMapper();
    static final Path ROOT = Path.of("..", "..", "script");

    static Map<String, Object> example(JsonNode ability, JsonNode binding) {
        Map<String, Object> input = new LinkedHashMap<>();
        for (JsonNode field : ability.path("inputs")) input.put(field.path("key").asText(),
            field.path("options").isArray() ? field.path("options").path(0).asText() : "青色陶瓷茶壶");
        Map<String, Object> assets = new LinkedHashMap<>();
        for (JsonNode field : ability.path("assets")) assets.put(field.path("key").asText(), "123456789012345678");
        Map<String, Object> output = new LinkedHashMap<>();
        if (binding.path("fields").toString().contains("\"size\"")) output.put("size", binding.path("sizes").path(0).path("label").asText());
        if (binding.path("fields").toString().contains("\"strength\"")) output.put("strength", binding.path("strengths").path(0).asText());
        if (binding.path("media").asText().equals("video")) {
            output.put("tier", binding.path("supportedTiers").path(0).asText());
            output.put("dur", binding.path("supportedDuration").asText());
        }
        return new LinkedHashMap<>(Map.of("abilityCode", ability.path("code").asText(), "workflowCode", binding.path("workflowCode").asText(),
            "inputs", input, "assets", assets, "output", output, "idempotencyKey", "contract-test"));
    }

    @Test void everyBusinessBindingCompilesUsingRealRegisteredTemplate() {
        var images = new ImageWorkflowContractRegistry(ROOT, MAPPER); images.load();
        var videos = new WorkflowContractRegistry(ROOT, MAPPER); videos.load();
        assertEquals(8, images.abilities().size()); assertEquals(6, videos.abilities().size());
        int bindings = 0;
        for (String media : List.of("image", "video")) {
            JsonNode abilities = media.equals("image") ? images.abilities() : videos.abilities();
            for (JsonNode ability : abilities) for (JsonNode binding : ability.path("workflows")) {
                String code = binding.path("workflowCode").asText(); bindings++;
                Map<String, Object> normalized = media.equals("image") ? images.normalizeAbility(example(ability, binding)) : videos.normalizeAbility(example(ability, binding));
                @SuppressWarnings("unchecked") Map<String, Object> fields = (Map<String, Object>) normalized.get("fields");
                String prompt = String.valueOf(fields.getOrDefault(media.equals("image") ? "prompt" : "desc", ""));
                assertFalse(prompt.contains("${"));
                JsonNode graph;
                if (media.equals("image")) {
                    var version = images.peek(code); assertNotNull(version); assertTrue(images.isTemplateLoaded(code));
                    assertFalse(version.isTestable()); assertThrows(RuntimeException.class, () -> images.require(code, true));
                    var capability = ImageCapability.parse(version.capabilityCode());
                    graph = new ImageTemplatePreparer(MAPPER).prepare(images.templateOf(code), capability, version,
                        new ImageTemplatePreparer.ImageFields(prompt, "", (String) fields.get("size"), (String) fields.get("strength"),
                            capability.requiresImage() ? List.of("reference.png") : List.of(), 42L));
                } else {
                    var version = videos.peek(code); assertNotNull(version); assertTrue(videos.isTemplateLoaded(code));
                    assertFalse(version.isTestable()); assertThrows(RuntimeException.class, () -> videos.require(code, true));
                    graph = new H3TemplatePreparer(MAPPER).prepare(videos.templateOf(code), VideoCapability.parse(version.capabilityCode()), version,
                        new H3TemplatePreparer.H3Fields(prompt, fields.containsKey("img") ? "reference.png" : null,
                            fields.containsKey("first") || fields.containsKey("reference1") ? "first.png" : null,
                            fields.containsKey("last") || fields.containsKey("reference2") ? "last.png" : null,
                            (String) fields.get("tier"), (String) fields.get("dur")));
                }
                assertTrue(graph.isObject());
            }
        }
        assertEquals(28, bindings);
    }

    @Test void rejectsCrossMediaWorkflowNodeOverridesAndMissingBusinessFields() {
        var image = new AbilityContract(ROOT, "image", MAPPER);
        var video = new AbilityContract(ROOT, "video", MAPPER);
        JsonNode poster = image.definitions().path(0);
        Map<String,Object> good = example(poster, poster.path("workflows").path(0));
        assertThrows(IllegalArgumentException.class, () -> video.normalize(good));
        var unknownNode = new LinkedHashMap<>(good); unknownNode.put("nodeId", "5");
        assertThrows(IllegalArgumentException.class, () -> image.normalize(unknownNode));
        var wrongWorkflow = new LinkedHashMap<>(good); wrongWorkflow.put("workflowCode", "wf-local-image-z-image-turbo");
        assertThrows(IllegalArgumentException.class, () -> image.normalize(wrongWorkflow));
        var missing = new LinkedHashMap<>(good); missing.put("inputs", Map.of());
        assertThrows(IllegalArgumentException.class, () -> image.normalize(missing));
        var bypass = new LinkedHashMap<>(good); bypass.remove("abilityCode");
        assertThrows(IllegalArgumentException.class, () -> image.normalize(bypass));
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="ability.validation.output", matches=".+")
    void compileAcceptancePlansUsingProductionPreparers() throws Exception {
        AbilityCompileCli.main(new String[]{ROOT.toAbsolutePath().normalize().toString(), System.getProperty("ability.validation.output")});
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="ability.validation.whiteInput", matches=".+")
    void realCutoutOutputCompositesToWhiteUsingProductionBackend() throws Exception {
        Path source = Path.of(System.getProperty("ability.validation.whiteInput"));
        byte[] finalBytes = new org.dromara.ai.image.service.ImageWhiteBackgroundCompositor().compositeOnWhite(java.nio.file.Files.readAllBytes(source));
        Path target = source.resolveSibling("white-final.png"); java.nio.file.Files.write(target, finalBytes);
        var original = javax.imageio.ImageIO.read(source.toFile());
        var result = javax.imageio.ImageIO.read(target.toFile());
        assertFalse(result.getColorModel().hasAlpha()); assertEquals(original.getWidth(), result.getWidth());
        int clear = 0, opaque = 0, mismatches = 0;
        for (int y=0; y<original.getHeight(); y++) for (int x=0; x<original.getWidth(); x++) {
            int pixel=original.getRGB(x,y), alpha=pixel>>>24;
            if (alpha==0) { clear++; if ((result.getRGB(x,y)&0xffffff)!=0xffffff) mismatches++; }
            if (alpha==255) { opaque++; if ((result.getRGB(x,y)&0xffffff)!=(pixel&0xffffff)) mismatches++; }
        }
        assertTrue(clear>0); assertTrue(opaque>0); assertEquals(0,mismatches);
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(source.resolveSibling("white-postprocess.json").toFile(),
            Map.of("compositor","ImageWhiteBackgroundCompositor","width",result.getWidth(),"height",result.getHeight(),
                "transparentSourcePixels",clear,"opaqueSourcePixels",opaque,"pixelMismatches",mismatches,"hasAlpha",false));
    }

    @Test void nativeVideoDurationRejectsOtherDurationsEvenWithDirectorTierConfig() {
        var registry = new WorkflowContractRegistry(ROOT, MAPPER); registry.load();
        var version = registry.peek("wf-local-video-ltx2-5-t2v");
        var preparer = new H3TemplatePreparer(MAPPER, new org.dromara.ai.video.config.VideoTierResolutions());
        assertThrows(RuntimeException.class, () -> preparer.validateFields(VideoCapability.T2V, version,
            new H3TemplatePreparer.H3Fields("scene", null, null, null, version.fixedFieldValidation().tier(), "20 秒")));
    }
}
