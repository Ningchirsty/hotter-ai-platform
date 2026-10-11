package org.dromara.ai.creative;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Business inputs are compiled on the server; clients never choose node mappings. */
public final class AbilityContract {
    private static final Pattern TOKEN = Pattern.compile("\\$\\{([a-zA-Z][a-zA-Z0-9_]*)}");
    private final String media;
    private final JsonNode definitions;

    public AbilityContract(Path root, String media, ObjectMapper mapper) {
        this.media = media;
        Path file = root.resolve(media + "/workflows/abilities.json");
        try {
            definitions = Files.isRegularFile(file) ? mapper.readTree(Files.readString(file)) : mapper.createArrayNode();
            if (!definitions.isArray()) throw new IllegalArgumentException("能力契约必须为数组");
            for (JsonNode definition : definitions) {
                if (!media.equals(definition.path("media").asText())) throw new IllegalArgumentException("能力媒体不匹配");
            }
        } catch (Exception e) {
            throw new IllegalStateException("无法加载 " + media + " 能力契约", e);
        }
    }

    public JsonNode definitions() { return definitions.deepCopy(); }

    public Map<String, Object> normalize(Map<String, Object> request) {
        String code = string(request.get("abilityCode"));
        String workflowCode = string(request.get("workflowCode"));
        if (code.isBlank()) {
            if (workflowCode.startsWith("wf-ability-")) throw new IllegalArgumentException("此工作流必须通过对应用途提交");
            return request;
        }
        JsonNode ability = null;
        for (JsonNode item : definitions) if (code.equals(item.path("code").asText())) ability = item;
        if (ability == null) throw new IllegalArgumentException("不支持的" + media + "用途");
        JsonNode binding = null;
        for (JsonNode item : ability.path("workflows")) if (workflowCode.equals(item.path("workflowCode").asText())) binding = item;
        if (binding == null) throw new IllegalArgumentException("用途与工作流不匹配");
        rejectUnknown(request, Set.of("abilityCode", "workflowCode", "inputs", "assets", "output", "idempotencyKey"));
        Map<String, Object> inputs = object(request.get("inputs"));
        Map<String, Object> assets = object(request.get("assets"));
        Map<String, Object> output = object(request.get("output"));
        List<String> keys = new ArrayList<>();
        Map<String, String> text = new LinkedHashMap<>();
        for (JsonNode field : ability.path("inputs")) {
            String key = field.path("key").asText(); keys.add(key);
            Object raw = inputs.get(key);
            if (raw != null && !(raw instanceof String)) throw new IllegalArgumentException("用途字段必须为文字：" + key);
            String value = string(raw).trim();
            if (field.path("required").asBoolean() && value.isBlank()) throw new IllegalArgumentException("请填写" + field.path("label").asText());
            if (value.length() > field.path("maxLength").asInt(300)) throw new IllegalArgumentException("用途字段过长：" + key);
            JsonNode choices = field.path("options");
            if (choices.isArray() && !value.isBlank()) {
                boolean found = false;
                for (JsonNode choice : choices) if (value.equals(choice.asText())) found = true;
                if (!found) throw new IllegalArgumentException("不支持的选项：" + key);
            }
            text.put(key, value);
        }
        rejectUnknown(inputs, Set.copyOf(keys));
        List<String> assetKeys = new ArrayList<>();
        for (JsonNode field : ability.path("assets")) {
            String key = field.path("key").asText(); assetKeys.add(key);
            Object id = assets.get(key);
            if (field.path("required").asBoolean() && id == null) throw new IllegalArgumentException("请上传" + field.path("label").asText());
            if (id != null && !string(id).matches("[1-9][0-9]{0,18}")) throw new IllegalArgumentException("无效素材编号");
        }
        rejectUnknown(assets, Set.copyOf(assetKeys));
        List<String> outputKeys = new ArrayList<>();
        for (JsonNode field : binding.path("fields")) if (Set.of("size", "strength", "tier", "dur").contains(field.asText())) outputKeys.add(field.asText());
        rejectUnknown(output, Set.copyOf(outputKeys));
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.putAll(assets); fields.putAll(output);
        String template = ability.path("promptTemplate").asText();
        Matcher matcher = TOKEN.matcher(template);
        StringBuilder prompt = new StringBuilder();
        while (matcher.find()) {
            if (!text.containsKey(matcher.group(1))) throw new IllegalArgumentException("用途提示词引用了未知字段");
            matcher.appendReplacement(prompt, Matcher.quoteReplacement(text.get(matcher.group(1))));
        }
        matcher.appendTail(prompt);
        if (prompt.length() > 1000) throw new IllegalArgumentException("组合后的创作描述超过 1000 字");
        if (!template.isBlank()) fields.put("image".equals(media) ? "prompt" : "desc", prompt.toString());
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("capabilityCode", ability.path("capabilityCode").asText());
        normalized.put("workflowCode", workflowCode);
        normalized.put("taskName", ability.path("name").asText());
        normalized.put("fields", fields);
        normalized.put("idempotencyKey", request.get("idempotencyKey"));
        return normalized;
    }

    private static void rejectUnknown(Map<String, Object> values, Set<String> allowed) {
        for (String key : values.keySet()) if (!allowed.contains(key)) throw new IllegalArgumentException("不允许的用途字段：" + key);
    }
    private static String string(Object value) { return value == null ? "" : value.toString(); }
    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value) {
        if (value == null) return Map.of();
        if (!(value instanceof Map<?, ?> map) || map.keySet().stream().anyMatch(key -> !(key instanceof String))) throw new IllegalArgumentException("用途参数必须为对象");
        return (Map<String, Object>) value;
    }
}
