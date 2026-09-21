package org.dromara.ai.image.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.dromara.ai.image.domain.ImageCapability;
import org.dromara.ai.image.domain.ImageWorkflowMapping;
import org.dromara.ai.image.domain.ImageWorkflowVersion;
import org.dromara.ai.image.exception.ImageTaskException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 图像模板填充器：深拷贝模板 + 只覆写 mapping 白名单 + 固定档位校验 + 参考图槽位裁剪。
 *
 * <p>与视频模块的 {@code H3TemplatePreparer} 同构，差异在于图像模板的「结构性改写」是
 * 画布宽高与 seed，而白名单里唯一允许覆写的采样参数是图生图的 {@code denoise}。</p>
 *
 * <p><b>不是 Spring 组件</b>（刻意去掉 {@code @Component}）：它的构造参数是
 * {@code ObjectMapper}，而视频模块已经注册了一个同类型 Bean；两个模块同时启用时按类型注入会因
 * 「找到 2 个候选」直接启动失败（真实生产事故，见 {@code ImageModuleConfiguration} 的注释）。
 * 因此改由 {@code ImageModuleConfiguration} 显式构造并注入。</p>
 *
 * <p><b>两个真实故障的防护</b>：</p>
 * <ol>
 *   <li>参考图槽位必须裁剪：模板里 {@code LoadImage.image} 为空串时 ComfyUI 会把 input 目录
 *       当文件打开并报 {@code Is a directory}，所以未提供的槽位要连节点一起删掉。</li>
 *   <li>校验和不匹配必须拒绝：模板被改动而契约没同步时，整条工作流不得加载或提交。</li>
 * </ol>
 */
public class ImageTemplatePreparer {

    private static final String ENCODER_TYPE = "TextEncodeQwenImage21";
    private static final String SAMPLER_TYPE = "KSampler";
    private static final String LATENT_TYPE = "EmptyLatentImage";
    private static final String LOAD_IMAGE_TYPE = "LoadImage";
    private static final Pattern IMAGE_SLOT = Pattern.compile("^images\\.image_(\\d+)$");
    private static final Pattern TRAILING_NUMBER = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*$");

    private final ObjectMapper mapper;

    public ImageTemplatePreparer(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 填充一次任务的节点图。
     *
     * @param templateContent 契约模板原文（不会被就地修改）
     * @param capability      能力
     * @param version         契约绑定
     * @param fields          本次任务的字段
     * @return 可提交的 API Format 节点图（深拷贝结果）
     */
    public ObjectNode prepare(String templateContent, ImageCapability capability,
                              ImageWorkflowVersion version, ImageFields fields) {
        if (capability == null) {
            throw ImageTaskException.invalidContract("不支持的能力编码");
        }
        validateFields(capability, version, fields);

        JsonNode parsed;
        try {
            parsed = mapper.readTree(templateContent);
        } catch (Exception e) {
            throw ImageTaskException.invalidContract("工作流模板不是合法 JSON");
        }
        if (!parsed.isObject()) {
            throw ImageTaskException.invalidContract("工作流模板根节点必须是对象");
        }
        ObjectNode graph = ((ObjectNode) parsed).deepCopy();

        // 1) 结构性改写：seed 由服务端按任务下发，模板里永远是 0
        ObjectNode sampler = findSingle(graph, SAMPLER_TYPE);
        ObjectNode samplerInputs = inputsOf(sampler, SAMPLER_TYPE);
        samplerInputs.put("seed", fields.seed());

        // 2) 结构性改写：文生图的画布尺寸来自 size 档位
        if (capability.requiresSize()) {
            int[] size = resolveSize(version, fields.sizeLabel());
            ObjectNode latent = findSingle(graph, LATENT_TYPE);
            ObjectNode latentInputs = inputsOf(latent, LATENT_TYPE);
            latentInputs.put("width", size[0]);
            latentInputs.put("height", size[1]);
            ObjectNode encoder = findSingle(graph, ENCODER_TYPE);
            if (encoder != null) {
                inputsOf(encoder, ENCODER_TYPE).put("resolution", Math.max(size[0], size[1]));
            }
        }

        // 3) 白名单覆写：只写 mapping 声明的节点输入键
        for (ImageWorkflowMapping item : version.mapping() == null ? List.<ImageWorkflowMapping>of() : version.mapping()) {
            JsonNode node = graph.get(item.nodeId());
            if (node == null || !node.isObject()) {
                throw ImageTaskException.invalidContract("模板缺少 mapping 指向的节点 " + item.nodeId());
            }
            ObjectNode inputs = inputsOf((ObjectNode) node, item.nodeId());
            switch (item.field()) {
                case "prompt" -> inputs.put(item.inputKey(), nullSafe(fields.prompt()));
                case "negative_prompt" -> inputs.put(item.inputKey(), nullSafe(fields.negativePrompt()));
                case "strength" -> inputs.put(item.inputKey(), strengthToDenoise(version, fields.strengthLabel()));
                case "img", "image1", "image2", "image3" -> {
                    String file = fileForSlot(fields, slotOfField(item.field()));
                    if (file != null) {
                        inputs.put(item.inputKey(), file);
                    }
                }
                default -> throw ImageTaskException.invalidContract("未知的字段映射：" + item.field());
            }
        }

        // 4) 裁剪未使用的参考图槽位（必须在白名单覆写之后）
        pruneUnusedReferenceSlots(graph, fields);

        return graph;
    }

    /**
     * 提交前的字段校验。控制器在入库前调用，避免「非法任务先入库再失败」。
     */
    public void validateFields(ImageCapability capability, ImageWorkflowVersion version, ImageFields fields) {
        if (capability == null) {
            throw ImageTaskException.invalidContract("不支持的能力编码");
        }
        if (version == null) {
            throw ImageTaskException.invalidContract("工作流未注册");
        }
        if (capability.allowsPrompt() && isBlank(fields.prompt())) {
            throw ImageTaskException.invalidContract("提示词不能为空");
        }
        if (capability.requiresImage() && fields.imageFiles().isEmpty()) {
            throw ImageTaskException.invalidContract(capability.label() + "必须提供输入图片");
        }
        if (capability.requiresSize()) {
            if (version.sizePresets().isEmpty()) {
                throw ImageTaskException.invalidContract("工作流未声明输出尺寸档位");
            }
            if (!version.sizePresets().containsKey(fields.sizeLabel())) {
                throw ImageTaskException.invalidContract(
                    "输出尺寸只支持：" + String.join(" / ", version.sizePresets().keySet()));
            }
        }
        if (capability == ImageCapability.I2I && !version.supportedStrengths().isEmpty()
            && !version.supportedStrengths().contains(fields.strengthLabel())) {
            throw ImageTaskException.invalidContract(
                "重绘幅度只支持：" + String.join(" / ", version.supportedStrengths()));
        }
        int maxImages = capability == ImageCapability.EDIT ? 3 : 1;
        if (fields.imageFiles().size() > maxImages) {
            throw ImageTaskException.invalidContract(capability.label() + "最多支持 " + maxImages + " 张图片");
        }
    }

    /**
     * 字段白名单校验：请求体里出现未声明字段一律拒绝。
     */
    public void validateFieldWhitelist(List<String> allowed, Map<String, ?> payload) {
        if (payload == null) {
            return;
        }
        for (String key : payload.keySet()) {
            if (!allowed.contains(key)) {
                throw ImageTaskException.invalidContract("不支持的字段：" + key);
            }
        }
    }

    /**
     * 模板字节的 SHA-256（小写 hex）。
     */
    public static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", e);
        }
    }

    /**
     * 校验模板与契约 checksum 一致，不一致禁止加载/提交。
     */
    public void verifyChecksum(String templateContent, ImageWorkflowVersion version) {
        String declared = version == null ? null : version.checksum();
        if (declared == null || declared.isBlank() || "TBD".equalsIgnoreCase(declared)) {
            throw ImageTaskException.invalidContract("工作流 " + version.workflowCode() + " 缺少有效校验值，禁止加载");
        }
        String actual = sha256(templateContent);
        if (!declared.equalsIgnoreCase(actual)) {
            throw ImageTaskException.invalidContract("工作流 " + version.workflowCode() + " 模板校验值不匹配，禁止加载");
        }
    }

    /**
     * size 档位 → {width, height}。
     */
    public int[] resolveSize(ImageWorkflowVersion version, String label) {
        int[] size = version.sizePresets().get(label);
        if (size == null) {
            throw ImageTaskException.invalidContract("未知的输出尺寸档位：" + label);
        }
        return size;
    }

    /**
     * strength 档位 → denoise。档位标签以数字结尾（如「标准重绘 · 0.75」）。
     */
    public double strengthToDenoise(ImageWorkflowVersion version, String label) {
        String effective = label;
        if (isBlank(effective)) {
            effective = version.defaultStrength();
        }
        if (isBlank(effective)) {
            throw ImageTaskException.invalidContract("缺少重绘幅度档位");
        }
        Matcher matcher = TRAILING_NUMBER.matcher(effective.trim());
        if (!matcher.find()) {
            throw ImageTaskException.invalidContract("无法从档位「" + effective + "」解析重绘幅度");
        }
        double denoise = Double.parseDouble(matcher.group(1));
        if (denoise <= 0 || denoise > 1) {
            throw ImageTaskException.invalidContract("重绘幅度必须在 (0, 1] 之间");
        }
        return denoise;
    }

    /**
     * 删除未提供图片的 LoadImage 节点及其 {@code images.image_N} 连线。
     *
     * <p>为什么必须做：{@code LoadImage.image} 为空串时 ComfyUI 会把 input 目录当文件打开并报
     * {@code [Errno 21] Is a directory}（真机复现过），所以模板预置的槽位必须在填充时收敛到实际张数。</p>
     */
    private void pruneUnusedReferenceSlots(ObjectNode graph, ImageFields fields) {
        ObjectNode encoder = findSingle(graph, ENCODER_TYPE);
        if (encoder == null) {
            return;
        }
        ObjectNode inputs = inputsOf(encoder, ENCODER_TYPE);
        List<String> slotKeys = new ArrayList<>();
        inputs.fieldNames().forEachRemaining(name -> {
            if (IMAGE_SLOT.matcher(name).matches()) {
                slotKeys.add(name);
            }
        });
        slotKeys.sort(Comparator.comparingInt(name -> slotNumber(name)));
        int used = fields.imageFiles().size();
        for (String key : slotKeys) {
            int slot = slotNumber(key);
            if (slot <= used) {
                continue;
            }
            JsonNode link = inputs.remove(key);
            if (link != null && link.isArray() && link.size() == 2) {
                String nodeId = link.get(0).asText();
                JsonNode node = graph.get(nodeId);
                if (node != null && LOAD_IMAGE_TYPE.equals(node.path("class_type").asText())) {
                    graph.remove(nodeId);
                }
            }
        }
    }

    private static int slotNumber(String key) {
        Matcher matcher = IMAGE_SLOT.matcher(key);
        if (!matcher.matches()) {
            throw ImageTaskException.invalidContract("非法的参考图槽位键：" + key);
        }
        return Integer.parseInt(matcher.group(1));
    }

    private static int slotOfField(String field) {
        return switch (field) {
            case "img", "image1" -> 1;
            case "image2" -> 2;
            case "image3" -> 3;
            default -> throw ImageTaskException.invalidContract("未知的图片字段：" + field);
        };
    }

    private static String fileForSlot(ImageFields fields, int slot) {
        List<String> files = fields.imageFiles();
        return slot <= files.size() ? files.get(slot - 1) : null;
    }

    private ObjectNode findSingle(ObjectNode graph, String classType) {
        ObjectNode found = null;
        var names = graph.fieldNames();
        while (names.hasNext()) {
            String id = names.next();
            JsonNode node = graph.get(id);
            if (node != null && classType.equals(node.path("class_type").asText())) {
                if (found != null) {
                    throw ImageTaskException.invalidContract("模板里存在多个 " + classType + " 节点，无法确定填充目标");
                }
                found = (ObjectNode) node;
            }
        }
        return found;
    }

    private ObjectNode inputsOf(ObjectNode node, String type) {
        if (node == null) {
            throw ImageTaskException.invalidContract("模板缺少 " + type + " 节点");
        }
        JsonNode inputs = node.get("inputs");
        if (inputs == null || !inputs.isObject()) {
            throw ImageTaskException.invalidContract(type + " 节点缺少 inputs");
        }
        return (ObjectNode) inputs;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    /**
     * 一次任务的字段集合。
     *
     * @param prompt        提示词（抠图能力忽略，使用模板固定提示词）
     * @param negativePrompt 负向提示词
     * @param sizeLabel     size 档位标签（仅文生图）
     * @param strengthLabel strength 档位标签（仅图生图）
     * @param imageFiles    已上传到 ComfyUI 的输入文件名，按槽位顺序（img / image1, image2, image3）
     * @param seed          本次任务的随机种子
     */
    public record ImageFields(
        String prompt,
        String negativePrompt,
        String sizeLabel,
        String strengthLabel,
        List<String> imageFiles,
        long seed) {

        public ImageFields {
            imageFiles = imageFiles == null ? List.of() : List.copyOf(imageFiles);
        }
    }
}
