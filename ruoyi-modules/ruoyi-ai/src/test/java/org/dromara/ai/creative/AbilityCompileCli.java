package org.dromara.ai.creative;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.dromara.ai.image.domain.ImageCapability;
import org.dromara.ai.image.service.ImageTemplatePreparer;
import org.dromara.ai.image.service.ImageWorkflowContractRegistry;
import org.dromara.ai.video.domain.VideoCapability;
import org.dromara.ai.video.service.H3TemplatePreparer;
import org.dromara.ai.video.service.WorkflowContractRegistry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Isolated acceptance tool. Uses production contract compiler and template preparers. */
public final class AbilityCompileCli {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]); Path output = Path.of(args[1]); Files.createDirectories(output);
        ObjectMapper mapper = new ObjectMapper();
        var images = new ImageWorkflowContractRegistry(root, mapper); images.load();
        var videos = new WorkflowContractRegistry(root, mapper); videos.load();
        List<Map<String,Object>> plans = new ArrayList<>();
        for (String media : List.of("image", "video")) {
            JsonNode definitions = media.equals("image") ? images.abilities() : videos.abilities();
            for (JsonNode ability : definitions) for (JsonNode binding : ability.path("workflows")) {
                String code = binding.path("workflowCode").asText();
                Map<String,Object> request = CreativeAbilityTest.example(ability, binding);
                @SuppressWarnings("unchecked") Map<String,Object> inputs = (Map<String,Object>)request.get("inputs");
                for (JsonNode field : ability.path("inputs")) if (!field.path("options").isArray()) {
                    String key = field.path("key").asText();
                    String value = field.path("placeholder").asText();
                    if (value.isBlank()) value = key.equals("title") ? "春日新品" : "青色陶瓷茶壶";
                    inputs.put(key, value);
                }
                Map<String,Object> normalized = media.equals("image") ? images.normalizeAbility(request) : videos.normalizeAbility(request);
                @SuppressWarnings("unchecked") Map<String,Object> fields = (Map<String,Object>)normalized.get("fields");
                String prompt = String.valueOf(fields.getOrDefault(media.equals("image") ? "prompt" : "desc", ""));
                JsonNode graph;
                if (media.equals("image")) {
                    var version=images.peek(code); var capability=ImageCapability.parse(version.capabilityCode());
                    graph=new ImageTemplatePreparer(mapper).prepare(images.templateOf(code),capability,version,
                        new ImageTemplatePreparer.ImageFields(prompt,"",(String)fields.get("size"),(String)fields.get("strength"),
                            capability.requiresImage() ? List.of("codex_validation_reference.png") : List.of(),42L));
                } else {
                    var version=videos.peek(code);
                    graph=new H3TemplatePreparer(mapper).prepare(videos.templateOf(code),VideoCapability.parse(version.capabilityCode()),version,
                        new H3TemplatePreparer.H3Fields(prompt,fields.containsKey("img")?"codex_validation_reference.png":null,
                            fields.containsKey("first")||fields.containsKey("reference1")?"codex_validation_reference.png":null,
                            fields.containsKey("last")||fields.containsKey("reference2")?"codex_validation_reference.png":null,
                            (String)fields.get("tier"),(String)fields.get("dur")));
                }
                mapper.writerWithDefaultPrettyPrinter().writeValue(output.resolve(code+".json").toFile(),graph);
                Map<String,Object> plan=new LinkedHashMap<>();
                plan.put("media",media);plan.put("abilityCode",ability.path("code").asText());plan.put("name",ability.path("name").asText());
                plan.put("workflowCode",code);plan.put("recommended",code.equals(ability.path("recommendedWorkflowCode").asText()));
                plan.put("request",request);plan.put("normalized",normalized);plan.put("hasAudio",binding.path("hasAudio").asBoolean());
                plans.add(plan);
            }
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(output.resolve("plans.json").toFile(),plans);
        System.out.println("ABILITY_BACKEND_PLANS_READY="+plans.size());
    }
}
