package org.dromara.creative.helper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Visual DNA 的结构定义、派生与校验（唯一真相处）。
 *
 * <p><b>为什么要有这个类</b>：DNA 的 json 是权威内容，但表里还有一组用于索引/展示的镜像列。
 * 如果两处各写各的，迟早出现「页面显示主色是 A、提示词里用 B」。所以：
 * 写入时一律 {@link #toColumns(ObjectNode)} 从 json 派生镜像列；读取时若发现不一致，
 * 以 json 为准（并可在校验里报出来）。</p>
 *
 * <p><b>校验的意义</b>：锁定（LOCKED）前必须自洽——没有主色、占比区间颠倒、枚举值拼错的 DNA
 * 一旦锁上，后面所有分镜与出图都会带着这个错。因此 {@link #validate(ObjectNode)} 的结论
 * 直接决定能不能锁定。</p>
 *
 * @author creative
 */
public final class VisualDnaSchema {

    /**
     * Schema 标识（写进 json，便于以后演进时识别）
     */
    public static final String SCHEMA = "visual-dna/1";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 饱和度/对比度/留白允许的档位
     */
    private static final List<String> LEVELS = List.of("LOW", "MEDIUM", "HIGH");

    /**
     * 光线类型允许值
     */
    private static final List<String> LIGHTING_TYPES = List.of("SOFT", "HARD", "STUDIO", "NATURAL");

    /**
     * 光位允许值
     */
    private static final List<String> LIGHTING_DIRS = List.of("FRONT", "SIDE", "TOP", "BACK");

    private VisualDnaSchema() {
    }

    /**
     * 新建一个空的 DNA 树（带 schema）。
     *
     * @return 可写的 ObjectNode
     */
    public static ObjectNode empty() {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("schema", SCHEMA);
        node.putArray("styleKeywords");
        node.putArray("avoidKeywords");
        node.set("colors", MAPPER.createObjectNode());
        node.set("lighting", MAPPER.createObjectNode());
        node.set("productRatio", MAPPER.createObjectNode());
        node.putArray("evidence");
        return node;
    }

    /**
     * 解析 json 为树；空/非法时返回空树（调用方据此判「无基因」）。
     *
     * @param json json 文本
     * @return ObjectNode（永不为 null）
     */
    public static ObjectNode parse(String json) {
        ObjectNode node = readTreeOrNull(json);
        return node == null ? empty() : node;
    }

    /**
     * 解析 json 为树；解析失败返回 null（用于区分「空」与「格式坏」）。
     *
     * <p>本模块的 DNA json 处理统一走这里（Jackson 2 的 ObjectMapper）。
     * 工程里同时存在两套 Jackson：{@code JsonUtils} 基于 Jackson 3，两者类型不通用，
     * 混用会直接编译不过。DNA 的结构化读写全部集中在本类，避免散落两套。</p>
     *
     * @param json json 文本
     * @return ObjectNode；非法返回 null
     */
    public static ObjectNode readTreeOrNull(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(json);
            return node instanceof ObjectNode objectNode ? objectNode : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 序列化为 json 文本。
     *
     * @param node 树
     * @return json 文本
     */
    public static String toJson(ObjectNode node) {
        try {
            return MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * 从 json 派生镜像列（写入前调用，保证列与 json 永远同源）。
     *
     * @param node DNA 树
     * @return 列名 → 值（仅包含非空项）
     */
    public static Map<String, Object> toColumns(ObjectNode node) {
        Map<String, Object> columns = new LinkedHashMap<>();
        columns.put("styleKeywords", join(node.path("styleKeywords")));
        columns.put("avoidKeywords", join(node.path("avoidKeywords")));
        JsonNode colors = node.path("colors");
        columns.put("colorPrimary", text(colors, "primary"));
        columns.put("colorSecondary", text(colors, "secondary"));
        columns.put("colorAccent", text(colors, "accent"));
        columns.put("colorBg", text(colors, "background"));
        columns.put("saturation", text(node, "saturation"));
        columns.put("contrastLevel", text(node, "contrastLevel"));
        columns.put("lightingType", text(node.path("lighting"), "type"));
        columns.put("lightingDir", text(node.path("lighting"), "direction"));
        JsonNode ratio = node.path("productRatio");
        columns.put("productRatioMin", decimal(ratio, "min"));
        columns.put("productRatioMax", decimal(ratio, "max"));
        columns.put("whitespaceLevel", text(node, "whitespaceLevel"));
        columns.put("typographyStyle", text(node, "typographyStyle"));
        columns.put("sceneType", text(node, "sceneType"));
        return columns;
    }

    /**
     * 校验 DNA 是否自洽。
     *
     * @param node DNA 树
     * @return 问题清单；空表示可锁定
     */
    public static List<String> validate(ObjectNode node) {
        List<String> issues = new ArrayList<>();
        if (!SCHEMA.equals(node.path("schema").asText())) {
            issues.add("缺少 schema 标识（应为 " + SCHEMA + "）");
        }
        if (node.path("styleKeywords").isArray() && node.path("styleKeywords").isEmpty()) {
            issues.add("风格关键词为空：出图提示词将缺少统一语气");
        }
        JsonNode colors = node.path("colors");
        if (isBlank(text(colors, "primary"))) {
            issues.add("主色未设置");
        }
        if (isBlank(text(colors, "background"))) {
            issues.add("背景色未设置");
        }
        checkColor(issues, "主色", text(colors, "primary"));
        checkColor(issues, "辅色", text(colors, "secondary"));
        checkColor(issues, "点缀色", text(colors, "accent"));
        checkColor(issues, "背景色", text(colors, "background"));
        checkLevel(issues, "饱和度", text(node, "saturation"));
        checkLevel(issues, "对比度", text(node, "contrastLevel"));
        checkLevel(issues, "留白", text(node, "whitespaceLevel"));
        String lightingType = text(node.path("lighting"), "type");
        if (!isBlank(lightingType) && !LIGHTING_TYPES.contains(lightingType)) {
            issues.add("光线类型取值非法：" + lightingType);
        }
        String lightingDir = text(node.path("lighting"), "direction");
        if (!isBlank(lightingDir) && !LIGHTING_DIRS.contains(lightingDir)) {
            issues.add("光位取值非法：" + lightingDir);
        }
        JsonNode ratio = node.path("productRatio");
        BigDecimal min = decimal(ratio, "min");
        BigDecimal max = decimal(ratio, "max");
        if (min == null && max == null) {
            issues.add("产品占比区间未设置");
        }
        if (min != null && (min.compareTo(BigDecimal.ZERO) < 0 || min.compareTo(new BigDecimal("100")) > 0)) {
            issues.add("产品占比下限超出 0~100：" + min);
        }
        if (max != null && (max.compareTo(BigDecimal.ZERO) < 0 || max.compareTo(new BigDecimal("100")) > 0)) {
            issues.add("产品占比上限超出 0~100：" + max);
        }
        if (min != null && max != null && min.compareTo(max) > 0) {
            issues.add("产品占比区间颠倒：下限 " + min + " 大于上限 " + max);
        }
        return issues;
    }

    /**
     * 取出证据链（分析时用了哪些输入，供页面如实展示）。
     *
     * @param node DNA 树
     * @return 证据条目列表
     */
    public static List<Map<String, Object>> evidenceOf(ObjectNode node) {
        List<Map<String, Object>> list = new ArrayList<>();
        JsonNode evidence = node.path("evidence");
        if (evidence.isArray()) {
            for (JsonNode item : evidence) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("kind", item.path("kind").asText(""));
                row.put("label", item.path("label").asText(""));
                row.put("value", item.path("value").asText(""));
                row.put("source", item.path("source").asText(""));
                list.add(row);
            }
        }
        return list;
    }

    /**
     * 追加一条证据。
     *
     * @param node   DNA 树
     * @param kind   类型（FACT/REFERENCE/DEFAULT/MODEL）
     * @param label  名称
     * @param value  值
     * @param source 来源说明（如「产品事实 color（已确认）」）
     */
    public static void addEvidence(ObjectNode node, String kind, String label, String value, String source) {
        ArrayNode evidence = node.withArray("evidence");
        ObjectNode item = evidence.addObject();
        item.put("kind", kind);
        item.put("label", label);
        item.put("value", value);
        item.put("source", source);
    }

    /**
     * 设置产品占比区间。
     *
     * @param node DNA 树
     * @param min  下限
     * @param max  上限
     */
    public static void setProductRatio(ObjectNode node, Integer min, Integer max) {
        ObjectNode ratio = node.withObject("/productRatio");
        if (min != null) {
            ratio.put("min", min);
        }
        if (max != null) {
            ratio.put("max", max);
        }
    }

    /**
     * 设置灯光。
     *
     * @param node      DNA 树
     * @param type      光线类型
     * @param direction 光位
     */
    public static void setLighting(ObjectNode node, String type, String direction) {
        ObjectNode lighting = node.withObject("/lighting");
        if (type != null) {
            lighting.put("type", type);
        }
        if (direction != null) {
            lighting.put("direction", direction);
        }
    }

    private static void checkColor(List<String> issues, String label, String value) {
        if (isBlank(value)) {
            return;
        }
        if (!value.trim().toUpperCase(Locale.ROOT).matches("^#([0-9A-F]{6}|[0-9A-F]{3})$")) {
            issues.add(label + "不是合法的 #RRGGBB：" + value);
        }
    }

    private static void checkLevel(List<String> issues, String label, String value) {
        if (!isBlank(value) && !LEVELS.contains(value)) {
            issues.add(label + "取值非法（应为 LOW/MEDIUM/HIGH）：" + value);
        }
    }

    private static String join(JsonNode array) {
        if (!array.isArray() || array.isEmpty()) {
            return null;
        }
        List<String> items = new ArrayList<>();
        for (JsonNode item : array) {
            String text = item.asText("");
            if (!text.isBlank()) {
                items.add(text.trim());
            }
        }
        return items.isEmpty() ? null : String.join(",", items);
    }

    private static String text(JsonNode parent, String field) {
        JsonNode node = parent.path(field);
        return node.isMissingNode() || node.isNull() ? null : node.asText(null);
    }

    private static BigDecimal decimal(JsonNode parent, String field) {
        JsonNode node = parent.path(field);
        if (node.isMissingNode() || node.isNull() || !node.isNumber()) {
            return null;
        }
        return node.decimalValue();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

}
