package org.dromara.aigov.helper;

import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.json.utils.JsonUtils;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/**
 * 模型输出结构校验器。
 * <p>能力模板 {@code output_schema} 形如：
 * <pre>{"fields":[{"name":"candidates","type":"array"},{"name":"score","type":"number"}]}</pre>
 * 校验规则：输出必须是合法 JSON，且包含 schema 声明的全部 {@code fields[].name}
 * （字段存在且不为 null）。输出为数组时，<b>至少一个元素</b>满足即可。</p>
 * <p>不符合 schema 的调用一律按失败处理（{@code result=1}）。</p>
 *
 * @author ai-gov
 */
public final class AigOutputSchemaValidator {

    /**
     * 输出不符合 Schema 的统一错误摘要。
     */
    public static final String MISMATCH_MESSAGE = "输出不符合Schema";

    private AigOutputSchemaValidator() {
    }

    /**
     * 校验模型输出是否符合能力输出模板。
     *
     * @param outputSchema 输出 Schema（JSON），为空表示不限结构
     * @param output       模型输出
     * @return 是否符合
     */
    public static boolean matches(String outputSchema, String output) {
        if (StringUtils.isBlank(outputSchema)) {
            return StringUtils.isNotBlank(output);
        }
        if (StringUtils.isBlank(output)) {
            return false;
        }
        try {
            Map<String, Object> schema = JsonUtils.parseMap(outputSchema);
            Object fieldsObj = schema == null ? null : schema.get("fields");
            if (!(fieldsObj instanceof List<?> fields) || fields.isEmpty()) {
                // Schema 未声明字段：只要求输出是合法 JSON
                return JsonUtils.isJson(output);
            }
            JsonNode node = JsonUtils.getJsonMapper().readTree(output);
            if (node == null || node.isNull()) {
                return false;
            }
            if (node.isArray()) {
                if (node.isEmpty()) {
                    return true;
                }
                for (JsonNode item : node) {
                    if (matchFields(fields, item)) {
                        return true;
                    }
                }
                return false;
            }
            return matchFields(fields, node);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 判断单个 JSON 对象是否包含 schema 声明的全部字段。
     *
     * @param fields schema 字段声明
     * @param node   输出节点
     * @return 是否全部命中
     */
    private static boolean matchFields(List<?> fields, JsonNode node) {
        if (node == null || !node.isObject()) {
            return false;
        }
        for (Object item : fields) {
            if (!(item instanceof Map<?, ?> field)) {
                continue;
            }
            Object name = field.get("name");
            if (name == null || StringUtils.isBlank(String.valueOf(name))) {
                continue;
            }
            JsonNode value = node.get(String.valueOf(name));
            if (value == null || value.isNull()) {
                return false;
            }
        }
        return true;
    }

}
