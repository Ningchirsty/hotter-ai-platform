package org.dromara.aigov.execution;

import lombok.Getter;
import tools.jackson.databind.json.JsonMapper;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 版本配置 v2 的解析结果（设计 §8.3「配置结构示例」）。
 *
 * <p><b>为什么用 {@code Map} 解析而不是强类型反序列化</b>：{@code config_json} 里同时存在
 * 旧格式（Studio 草稿内容）与新格式（带 {@code schemaVersion} 的结构化配置），字段集在迁移期
 * 会持续增长。若用强类型反序列化并把未知字段当错误，任何新增字段都会让<b>旧版本</b>的解析
 * 直接失败；把未知字段当忽略，又会掩盖"我以为是 v2、其实是别的形状"。用 Map 读我们认识的键、
 * 用 {@code schemaVersion} 做显式识别，两种失败模式都避免了。</p>
 *
 * <p><b>{@code schemaVersion} 是识别的唯一依据</b>（设计 §8.3「配置迁移采用 schemaVersion
 * 显式识别」）：没有它、或小于 {@link #MIN_SCHEMA_VERSION}，都不是 v2 配置，
 * 调用方必须按旧业务适配器运行，而<b>不得</b>宣称已启用新知识/工具机制。</p>
 *
 * @author ai-gov
 */
@Getter
public class AigExecutionConfig implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 本类能识别的最低配置版本
     */
    public static final int MIN_SCHEMA_VERSION = 2;

    /**
     * 配置版本
     */
    private final int schemaVersion;

    /**
     * 业务分类（如 PRODUCT_KNOWLEDGE）
     */
    private final String businessCategory;

    /**
     * 执行器类型（如 GROUNDED_ANSWER）
     */
    private final String executorType;

    /**
     * 需要的 Provider 能力编码
     */
    private final String providerCapability;

    /**
     * 输出呈现方式（如 answer_with_sources）
     */
    private final String outputPresentation;

    /**
     * 知识集合编码（{@code knowledgeRefs[].collectionCode}）
     */
    private final List<String> knowledgeRefs;

    /**
     * 工具编码（{@code toolRefs[].toolCode}）
     */
    private final List<String> toolRefs;

    /**
     * 一次运行最大工具调用次数（可空）
     */
    private final Integer maxToolCalls;

    /**
     * 子 Agent 最大深度（可空）
     */
    private final Integer maxDepth;

    /**
     * 业务侧外发许可（v2 里 {@code dataPolicy.allowExternal}，可空）
     */
    private final Boolean allowExternal;

    /**
     * 最低数据等级（{@code dataPolicy.minimumLevel}，可空）
     */
    private final String minimumLevel;

    @SuppressWarnings("unchecked")
    private AigExecutionConfig(Map<String, Object> root) {
        this.schemaVersion = intValue(root.get("schemaVersion"), 0);
        this.businessCategory = str(root.get("businessCategory"));
        this.executorType = str(root.get("executorType"));
        this.providerCapability = str(root.get("providerCapability"));
        this.outputPresentation = str(root.get("outputPresentation"));
        this.knowledgeRefs = codes(root.get("knowledgeRefs"), "collectionCode");
        this.toolRefs = codes(root.get("toolRefs"), "toolCode");
        Map<String, Object> limits = root.get("limits") instanceof Map<?, ?> m
            ? (Map<String, Object>) m : Map.of();
        this.maxToolCalls = intOrNull(limits.get("maxToolCalls"));
        this.maxDepth = intOrNull(limits.get("maxDepth"));
        Map<String, Object> dataPolicy = root.get("dataPolicy") instanceof Map<?, ?> m
            ? (Map<String, Object>) m : Map.of();
        this.minimumLevel = str(dataPolicy.get("minimumLevel"));
        Object allow = dataPolicy.get("allowExternal");
        this.allowExternal = allow instanceof Boolean b ? b : null;
    }

    /**
     * 解析版本配置 JSON。
     *
     * <p>以下情形一律返回 {@code null}（表示"不是 v2 配置，按旧适配器运行"）：
     * 原文为空、不是 JSON 对象、没有 {@code schemaVersion}、版本号低于
     * {@link #MIN_SCHEMA_VERSION}。解析异常不抛出——{@code config_json} 是历史数据，
     * 一个旧版本里塞了非 JSON 内容不该让任务执行直接崩，而应自然退回旧路径。</p>
     *
     * @param configJson 版本 {@code config_json} 原文（可空）
     * @param jsonMapper 序列化器
     * @return 解析结果；非 v2 返回 null
     */
    @SuppressWarnings("unchecked")
    public static AigExecutionConfig parse(String configJson, JsonMapper jsonMapper) {
        if (configJson == null || configJson.isBlank() || jsonMapper == null) {
            return null;
        }
        try {
            Object parsed = jsonMapper.readValue(configJson, Map.class);
            if (!(parsed instanceof Map<?, ?> map)) {
                return null;
            }
            AigExecutionConfig config = new AigExecutionConfig((Map<String, Object>) map);
            return config.schemaVersion >= MIN_SCHEMA_VERSION ? config : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从任意对象里提取 {@code List<Map>} 的某个键。
     */
    @SuppressWarnings("unchecked")
    private static List<String> codes(Object value, String key) {
        List<String> out = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    String code = str(((Map<String, Object>) m).get(key));
                    if (code != null && !code.isBlank()) {
                        out.add(code);
                    }
                }
            }
        }
        return out;
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static int intValue(Object value, int fallback) {
        Integer parsed = intOrNull(value);
        return parsed == null ? fallback : parsed;
    }

    private static Integer intOrNull(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

}
