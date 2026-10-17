package org.dromara.ai.video.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.dromara.ai.video.domain.WorkflowVersion;

import java.util.Map;

/** Applies only the dimension inputs and output edge explicitly declared by a reviewed contract. */
final class NativeVideoOutput {
    private NativeVideoOutput() { }

    static WorkflowVersion.OutputProfile inputs(WorkflowVersion version, String tier, Map<String, Object> values) {
        var fixed = version.fixedFieldValidation();
        if (fixed == null || fixed.profiles().isEmpty()) return null;
        var profile = fixed.profileOf(tier);
        var control = fixed.outputControl();
        if (profile == null || control == null) throw new IllegalArgumentException("所选输出规格未配置");
        int divisor = control.latentDivisor();
        if (profile.generationWidth() % control.alignment() != 0
            || profile.generationHeight() % control.alignment() != 0
            || profile.generationWidth() / divisor < 16 || profile.generationHeight() / divisor < 16) {
            throw new IllegalArgumentException("输出规格不符合模型尺寸约束");
        }
        values.put("_width", profile.generationWidth() / divisor);
        values.put("_height", profile.generationHeight() / divisor);
        values.put("_referenceWidth", profile.generationWidth());
        values.put("_referenceHeight", profile.generationHeight());
        values.put("_referenceMax", Math.max(profile.generationWidth(), profile.generationHeight()));
        return profile;
    }

    static ObjectNode finish(ObjectNode graph, WorkflowVersion version, WorkflowVersion.OutputProfile profile) {
        if (profile == null || profile.legacy()) return graph;
        var control = version.fixedFieldValidation().outputControl();
        JsonNode video = graph.path(control.videoNodeId());
        JsonNode source = video.path("inputs").path("images");
        if (!"CreateVideo".equals(video.path("class_type").asText()) || !source.isArray() || source.size() != 2
            || graph.has(control.resizeNodeId())) throw new IllegalArgumentException("视频尺寸输出边界无效");
        JsonNode resizeSource = source.deepCopy();
        if ("SUPER_RESOLUTION".equals(profile.mode())) {
            String loaderId = control.resizeNodeId() + "_model";
            String upscaleId = control.resizeNodeId() + "_upscale";
            if (control.upscaleModel() == null || control.upscaleModel().isBlank()
                || graph.has(loaderId) || graph.has(upscaleId)) throw new IllegalArgumentException("超分模型未配置或节点冲突");
            ObjectNode loader = graph.objectNode();
            loader.put("class_type", "UpscaleModelLoader");
            loader.putObject("inputs").put("model_name", control.upscaleModel());
            graph.set(loaderId, loader);
            ObjectNode upscale = graph.objectNode();
            upscale.put("class_type", "ImageUpscaleWithModel");
            var upscaleInputs = upscale.putObject("inputs");
            upscaleInputs.putArray("upscale_model").add(loaderId).add(0);
            upscaleInputs.set("image", source.deepCopy());
            graph.set(upscaleId, upscale);
            resizeSource = graph.arrayNode().add(upscaleId).add(0);
        }
        ObjectNode resize = graph.objectNode();
        resize.put("class_type", "ImageScale");
        var inputs = resize.putObject("inputs");
        inputs.set("image", resizeSource);
        inputs.put("upscale_method", "lanczos");
        inputs.put("width", profile.width());
        inputs.put("height", profile.height());
        inputs.put("crop", "center");
        graph.set(control.resizeNodeId(), resize);
        ((ObjectNode) video.get("inputs")).putArray("images").add(control.resizeNodeId()).add(0);
        return graph;
    }
}
