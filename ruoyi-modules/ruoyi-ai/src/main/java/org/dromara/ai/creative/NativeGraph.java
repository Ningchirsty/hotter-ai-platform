package org.dromara.ai.creative;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;

/** Adapter for reviewed native ComfyUI graphs. All writes use contract node/input pairs. */
public final class NativeGraph {
    private NativeGraph() {}
    public record Input(String field, String nodeId, String inputKey) {}

    public static ObjectNode prepare(ObjectMapper mapper, String template, List<Input> mappings, Map<String, Object> fields) {
        try {
            JsonNode parsed = mapper.readTree(template);
            if (!parsed.isObject()) throw new IllegalArgumentException("原生模板必须为对象");
            ObjectNode graph = ((ObjectNode) parsed).deepCopy();
            for (Input mapping : mappings) {
                JsonNode node = graph.get(mapping.nodeId());
                if (node == null || !node.path("inputs").isObject() || !node.path("inputs").has(mapping.inputKey())) throw new IllegalArgumentException("原生模板映射无效");
                Object value = fields.get(mapping.field());
                if (value == null) throw new IllegalArgumentException("原生模板缺少字段：" + mapping.field());
                ((ObjectNode) node.get("inputs")).set(mapping.inputKey(), mapper.valueToTree(value));
            }
            return graph;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("原生模板填充失败", e);
        }
    }
}
