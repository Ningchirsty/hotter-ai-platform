package org.dromara.ai.video.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.dromara.ai.video.domain.VideoCapability;
import org.dromara.ai.video.domain.WorkflowMapping;
import org.dromara.ai.video.domain.WorkflowVersion;
import org.dromara.ai.video.exception.VideoTaskException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * H3 API Format 模板的契约填充器（服务端）。
 *
 * <p>边界（与 {@code script/video/workflows/README.md} 一致）：</p>
 * <ul>
 *   <li>深拷贝模板；只覆写 mapping 白名单内的输入键，其余输入一律不动；</li>
 *   <li>节点 ID、模型路径、API Format JSON 不下发前端；</li>
 *   <li>固定档位只接受契约声明的取值（可多档位，见 {@code supportedTiers}）；</li>
 *   <li>输出分辨率由档位决定，本类负责把模板里散落的分辨率路径统一改写；</li>
 *   <li>发布前必须实测输出，超出时长上限由服务端截断并由调用方记录。</li>
 * </ul>
 *
 * <p>本类为纯函数实现，不依赖 Spring，便于用可控替身做离线单测。</p>
 */
public final class H3TemplatePreparer {

    /**
     * H3 导演节点 ID。契约三个模板均为 "5"。
     */
    public static final String DIRECTOR_NODE_ID = "5";

    /**
     * 编码节点 ID。模板为 {@code ImageScale}，决定最终 mp4 尺寸。
     */
    public static final String ENCODE_NODE_ID = "14";

    /**
     * 产品档位固定值（1080P）。
     */
    public static final String TIER_1080P = "高清 · 1080P";

    /**
     * 产品时长固定值。
     */
    public static final String DURATION_5S = "5 秒";

    private final ObjectMapper mapper;

    /**
     * 档位 → 分辨率映射。为 null 时只做校验、不改写分辨率（保持旧行为）。
     */
    private final org.dromara.ai.video.config.VideoTierResolutions tierResolutions;

    /**
     * 兼容构造器：不注入档位表，等价于「只支持契约声明的档位、不改写分辨率」。
     */
    public H3TemplatePreparer(ObjectMapper mapper) {
        this(mapper, null);
    }

    public H3TemplatePreparer(ObjectMapper mapper,
                              org.dromara.ai.video.config.VideoTierResolutions tierResolutions) {
        this.mapper = mapper;
        this.tierResolutions = tierResolutions;
    }

    /**
     * 计算模板内容 SHA-256，用于与契约 checksum 比对。
     */
    public static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * 校验模板内容与契约 checksum 一致，不一致即拒绝加载。
     */
    public void verifyChecksum(String templateContent, WorkflowVersion version) {
        String expected = version.checksum();
        if (expected == null || expected.isBlank() || "TBD".equalsIgnoreCase(expected)) {
            throw VideoTaskException.invalidContract(
                "工作流 " + version.workflowCode() + " 缺少有效校验值，禁止加载");
        }
        String actual = sha256(templateContent);
        if (!expected.equalsIgnoreCase(actual)) {
            throw VideoTaskException.invalidContract(
                "工作流 " + version.workflowCode() + " 模板校验值不匹配，禁止加载");
        }
    }

    /**
     * 校验模板 checksum 是否不匹配（供断言使用，避免测试依赖异常控制流）。
     *
     * @return true 表示校验值不匹配
     */
    public boolean verifyChecksumException(String templateContent, WorkflowVersion version) {
        try {
            verifyChecksum(templateContent, version);
            return false;
        } catch (VideoTaskException e) {
            return true;
        }
    }

    /**
     * 按能力与字段填充模板，返回可直接提交 ComfyUI 的节点图。
     *
     * @param templateContent 模板 JSON 原文
     * @param capability      能力
     * @param version         命中的契约版本
     * @param fields          已通过服务端校验的字段（text 为提示词，imageFile 为 ComfyUI 可用文件名）
     * @return 深拷贝并填充后的节点图
     */
    public ObjectNode prepare(String templateContent, VideoCapability capability,
                              WorkflowVersion version, H3Fields fields) {
        if (capability == null) {
            throw VideoTaskException.invalidContract("不支持的能力编码");
        }
        validateFields(capability, version, fields);

        JsonNode parsed;
        try {
            parsed = mapper.readTree(templateContent);
        } catch (Exception e) {
            throw VideoTaskException.invalidContract("工作流模板不是合法 JSON");
        }
        if (!parsed.isObject()) {
            throw VideoTaskException.invalidContract("工作流模板根节点必须是对象");
        }
        ObjectNode graph = ((ObjectNode) parsed).deepCopy();

        JsonNode directorNode = graph.get(DIRECTOR_NODE_ID);
        if (directorNode == null || !directorNode.isObject()) {
            throw VideoTaskException.invalidContract("模板缺少导演节点 " + DIRECTOR_NODE_ID);
        }
        ObjectNode director = (ObjectNode) directorNode;
        JsonNode inputsNode = director.get("inputs");
        if (inputsNode == null || !inputsNode.isObject()) {
            throw VideoTaskException.invalidContract("导演节点缺少 inputs");
        }
        ObjectNode inputs = (ObjectNode) inputsNode;

        if (!"MiniMaxH3Director".equals(director.path("class_type").asText())) {
            throw VideoTaskException.invalidContract("模板导演节点类型不是 MiniMaxH3Director");
        }
        String taskType = inputs.path("task_type").asText("");
        if (!taskType.startsWith(capability.taskTypeMarker())) {
            throw VideoTaskException.invalidContract(
                "模板 task_type 与能力 " + capability + " 不匹配");
        }

        String prompt = fields.text() == null ? "" : fields.text().trim();
        if (prompt.isEmpty()) {
            throw VideoTaskException.invalidContract("视频描述不能为空");
        }

        // 时间轴：全局/分段/分镜提示词同步写入，首尾帧写入 keyframes。
        JsonNode timelineNode = parseTimeline(inputs.path("timeline_data").asText(""));
        ObjectNode timeline = (ObjectNode) timelineNode;
        setNestedPrompt(timeline, prompt);
        if (capability != VideoCapability.T2V) {
            String firstFile = capability == VideoCapability.I2V ? fields.imageFile() : fields.firstFile();
            requireNonBlank(firstFile, "首帧图片");
            writeKeyframe(timeline, 0, firstFile);
            writeSegmentImage(timeline, "genImage", "imageFile", firstFile);
            writeSegmentImage(timeline, "startImage", "imageFile", firstFile);
        }
        if (capability == VideoCapability.FL2V) {
            requireNonBlank(fields.lastFile(), "尾帧图片");
            writeKeyframe(timeline, 1, fields.lastFile());
            writeSegmentImage(timeline, "endImage", "imageFile", fields.lastFile());
        }

        // 只覆写 mapping 白名单声明的键；其余输入保持模板原值。
        List<WorkflowMapping> mapping = version.mapping() == null ? List.of() : version.mapping();
        for (WorkflowMapping item : mapping) {
            if (!DIRECTOR_NODE_ID.equals(item.nodeId())) {
                // 当前契约三个 H3 模板的映射都落在导演节点；其他节点一律不写。
                continue;
            }
            switch (item.field()) {
                case "desc" -> inputs.put(item.inputKey(), prompt);
                case "timeline_data", "img", "first", "last" ->
                    inputs.put(item.inputKey(), mapper.getNodeFactory().textNode(timeline.toString()));
                default -> {
                    // tier/dur 为固定值校验，不是可覆写映射；其余字段不属于 H3 模板。
                }
            }
        }
        // 提示词即使未在 mapping 中声明，也必须随任务写入，否则模板仍是空提示词。
        if (mapping.stream().noneMatch(m -> "desc".equals(m.field()))) {
            inputs.put("global_prompt", prompt);
        }
        // 分辨率不在 mapping 白名单里，但它同样是「按档位决定的输出参数」，
        // 因此按档位统一改写模板中散落的各处分辨率。
        //
        // 必须放在把 timeline 序列化进节点之前：timeline 是 Jackson 对象，
        // 修改它不会自动更新已经写进 inputs.timeline_data 的那份字符串。
        // 早期版本把这一步放在序列化之后，结果节点上的 width/height 生效了、
        // 而 timeline 里的 width/height/output 仍是模板原值——两者不一致会让
        // i2v/fl2v（读 timeline.output）与 t2v（读节点 5）走出不同的分辨率。
        applyResolution(graph, inputs, timeline, fields.tier());

        // 确保时间轴落回节点，避免仅改 global_prompt 而分镜提示词为空。
        if (!inputs.has("timeline_data")) {
            inputs.set("timeline_data", mapper.getNodeFactory().textNode(timeline.toString()));
        } else {
            inputs.put("timeline_data", timeline.toString());
        }
        return graph;
    }

    /**
     * 按输出档位改写模板中的分辨率。
     *
     * <p>H3 模板的分辨率不是单一参数，而是散落在四处，必须一起改，否则会出现
     * 「导演阶段生成 1920×1088、编码阶段却按 1280×720 裁剪」这类畸形输出：</p>
     *
     * <ol>
     *   <li>节点 5 {@code width}/{@code height}：导演阶段的生成分辨率；</li>
     *   <li>节点 5 {@code ref_max_size}：参考图/参考视频的最大边；</li>
     *   <li>timeline 的 {@code width}/{@code height}/{@code refMaxSize} 与
     *       {@code output.width}/{@code output.height}/{@code output.longEdge}；
     *       t2v 走 fixed 分支只读节点 5，i2v/fl2v 走 timeline 的 output 分支；</li>
     *   <li>节点 {@value #ENCODE_NODE_ID} {@code ImageScale} 的 {@code width}/{@code height}：
     *       最终写入 mp4 的尺寸。</li>
     * </ol>
     *
     * <p>导演阶段与编码阶段刻意错开一个台阶（如 1088 → 1080），与原模板策略一致：
     * 编码节点用 {@code crop=center} 裁掉多余的像素，使成片落在标准档位上。</p>
     *
     * <p>未配置档位表、或档位不在表内时不做任何改写；档位合法性由
     * {@link #validateFields} 负责拦截。</p>
     */
    void applyResolution(ObjectNode graph, ObjectNode inputs, ObjectNode timeline, String tier) {
        if (tierResolutions == null || tier == null) {
            return;
        }
        org.dromara.ai.video.config.VideoTierResolutions.Resolution res = tierResolutions.of(tier);
        if (res == null) {
            return;
        }
        inputs.put("width", res.width());
        inputs.put("height", res.height());
        inputs.put("ref_max_size", res.refMaxSize());

        timeline.put("width", res.width());
        timeline.put("height", res.height());
        timeline.put("refMaxSize", res.refMaxSize());
        JsonNode outputNode = timeline.get("output");
        if (outputNode != null && outputNode.isObject()) {
            ObjectNode output = (ObjectNode) outputNode;
            output.put("width", res.width());
            output.put("height", res.height());
            output.put("longEdge", res.refMaxSize());
        }

        JsonNode encodeNode = graph.get(ENCODE_NODE_ID);
        if (encodeNode != null && encodeNode.isObject()) {
            JsonNode encodeInputs = encodeNode.get("inputs");
            if (encodeInputs != null && encodeInputs.isObject()) {
                ObjectNode scaleInputs = (ObjectNode) encodeInputs;
                scaleInputs.put("width", res.encodeWidth());
                scaleInputs.put("height", res.encodeHeight());
            }
        }
    }

    /**
     * 校验字段与固定档位、必需素材与提示词。
     *
     * <p>必须在任务入库<b>之前</b>调用；否则会产生不可执行的任务记录。</p>
     */
    public void validateFields(VideoCapability capability, WorkflowVersion version, H3Fields fields) {
        WorkflowVersion.FixedFieldValidation fixed = version.fixedFieldValidation();
        if (fixed != null) {
            java.util.Set<String> allowedTiers = fixed.allowedTiers();
            if (!allowedTiers.isEmpty() && !allowedTiers.contains(fields.tier())) {
                throw VideoTaskException.invalidContract(
                    "输出档位只支持 " + String.join(" / ", allowedTiers));
            }
            if (fixed.dur() != null && !fixed.dur().equals(fields.durationLabel())) {
                throw VideoTaskException.invalidContract(
                    "视频时长只支持 " + fixed.dur());
            }
        }
        // 提示词为空同样属于不可执行的任务，必须在这里拦住，
        // 否则会先入库、到执行阶段才失败。
        if (isBlank(fields.text())) {
            throw VideoTaskException.invalidContract("视频描述不能为空");
        }
        if (capability == VideoCapability.I2V && isBlank(fields.imageFile())) {
            throw VideoTaskException.invalidContract("图生视频必须提供图片素材");
        }
        if (capability == VideoCapability.FL2V
            && (isBlank(fields.firstFile()) || isBlank(fields.lastFile()))) {
            throw VideoTaskException.invalidContract("首尾帧生视频必须同时提供首帧和尾帧");
        }
    }

    /**
     * 校验字段取值是否落在契约 fields 白名单内。
     *
     * @param allowedFields 契约声明的字段名集合
     * @param provided      前端提交的字段名集合
     */
    public void validateFieldWhitelist(List<String> allowedFields, Map<String, ?> provided) {
        for (String key : provided.keySet()) {
            if (!allowedFields.contains(key)) {
                throw VideoTaskException.invalidContract("不支持的字段：" + key);
            }
        }
    }

    private JsonNode parseTimeline(String raw) {
        if (raw == null || raw.isBlank()) {
            throw VideoTaskException.invalidContract("模板缺少 timeline_data");
        }
        try {
            JsonNode node = mapper.readTree(raw);
            if (!node.isObject()) {
                throw VideoTaskException.invalidContract("timeline_data 必须是对象");
            }
            return node;
        } catch (VideoTaskException e) {
            throw e;
        } catch (Exception e) {
            throw VideoTaskException.invalidContract("timeline_data 不是合法 JSON");
        }
    }

    private void setNestedPrompt(ObjectNode timeline, String prompt) {
        ObjectNode global = childObject(timeline, "global");
        if (global != null) {
            global.put("prompt", prompt);
        }
        ObjectNode segment = firstArrayObject(timeline, "segments");
        if (segment != null) {
            segment.put("prompt", prompt);
        }
        ObjectNode shot = firstArrayObject(timeline, "shots");
        if (shot != null) {
            shot.put("prompt", prompt);
        }
    }

    private void writeKeyframe(ObjectNode timeline, int index, String fileName) {
        JsonNode keyframes = timeline.get("keyframes");
        if (keyframes == null || !keyframes.isArray() || keyframes.size() <= index) {
            throw VideoTaskException.invalidContract("模板 keyframes 数量不足，无法写入第 " + (index + 1) + " 帧");
        }
        JsonNode item = keyframes.get(index);
        if (!item.isObject()) {
            throw VideoTaskException.invalidContract("模板 keyframes 元素不是对象");
        }
        ((ObjectNode) item).put("imageFile", fileName);
    }

    private void writeSegmentImage(ObjectNode timeline, String objectKey, String fieldKey, String fileName) {
        ObjectNode segment = firstArrayObject(timeline, "segments");
        ObjectNode shot = firstArrayObject(timeline, "shots");
        if (segment != null) {
            ObjectNode holder = childObject(segment, objectKey);
            if (holder != null) {
                holder.put(fieldKey, fileName);
            }
        }
        if (shot != null) {
            ObjectNode holder = childObject(shot, objectKey);
            if (holder != null) {
                holder.put(fieldKey, fileName);
            }
        }
    }

    private ObjectNode childObject(ObjectNode parent, String key) {
        JsonNode node = parent.get(key);
        return node != null && node.isObject() ? (ObjectNode) node : null;
    }

    private ObjectNode firstArrayObject(ObjectNode parent, String key) {
        JsonNode node = parent.get(key);
        if (node == null || !node.isArray() || node.isEmpty()) {
            return null;
        }
        JsonNode first = node.get(0);
        return first.isObject() ? (ObjectNode) first : null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static void requireNonBlank(String value, String label) {
        if (isBlank(value)) {
            throw VideoTaskException.invalidContract(label + "不能为空");
        }
    }

    /**
     * 准备模板所需的字段。
     *
     * @param text      视频描述
     * @param imageFile I2V 的图片在 ComfyUI 输入目录中的文件名
     * @param firstFile FL2V 的首帧文件名
     * @param lastFile  FL2V 的尾帧文件名
     * @param tier      输出档位
     * @param durationLabel 时长档位
     */
    public record H3Fields(String text, String imageFile, String firstFile, String lastFile,
                           String tier, String durationLabel) {
    }
}
