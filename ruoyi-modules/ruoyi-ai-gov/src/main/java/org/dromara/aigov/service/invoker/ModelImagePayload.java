package org.dromara.aigov.service.invoker;

import org.dromara.common.core.utils.StringUtils;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 多模态图片载荷契约（SPI 调用器共用）。
 *
 * <p><b>为什么要单独定契约</b>：图片必须以 base64 随 {@code payload} 传进治理层，
 * 而 {@code payload} 同时又是「文本调用器要序列化进提示词」的对象。若各调用器各自
 * 从 payload 里抠图片，很容易出现两种事故：</p>
 * <ul>
 *     <li><b>图片被当文本发出去</b>——text-only 调用器把整段 base64 序列化进提示词，
 *     既污染提示词（模型看到的是一堵乱码）又把请求体撑到几十 MB；</li>
 *     <li><b>图片被悄悄丢掉</b>——调用器只发文本，模型在没见过图的情况下照样给出
 *     「一致」的结论，这是最危险的一种：错得毫无痕迹。</li>
 * </ul>
 * <p>因此统一约定：图片一律放在 {@value #KEY} 键下，形如
 * {@code [{"label":"参考图","mimeType":"image/png","base64":"..."}]}；
 * 文本调用器必须用 {@link #withoutImages(Map)} 取「去图片后的载荷」，
 * 支持图片的调用器用 {@link #extract(Map)} 取图片；
 * <b>不支持图片的调用器必须显式失败</b>（见 {@link #requireUnsupported(Map, String)}），
 * 不得静默忽略。</p>
 *
 * <p><b>审计安全</b>：{@code AigInputSanitizer} 只写字段名与值类型（本键记作 {@code array}），
 * 不写取值，因此 base64 不会进入 {@code aig_invocation_audit}，也不会撑大审计表。</p>
 *
 * @author ai-gov
 */
public final class ModelImagePayload {

    /**
     * 图片载荷键名。
     */
    public static final String KEY = "images";

    /**
     * 单次调用允许携带的图片数上限。
     */
    public static final int MAX_IMAGES = 6;

    /**
     * 单张图片解码后字节数上限（超过则整体拒绝，避免把请求体撑爆）。
     */
    public static final long MAX_BYTES_PER_IMAGE = 8L * 1024 * 1024;

    private ModelImagePayload() {
    }

    /**
     * 一张图片。
     *
     * @param label    业务标签（如「参考图」「成品图」），用于提示词与排障
     * @param mimeType MIME 类型（如 image/png）
     * @param base64   base64 编码的图片字节（不含 data URL 前缀）
     */
    public record ImagePart(String label, String mimeType, String base64) {

        /**
         * 是否字段齐全，可用于发请求。
         *
         * @return 齐全返回 true
         */
        public boolean usable() {
            return StringUtils.isNotBlank(base64);
        }

        /**
         * 归一化 MIME：缺省按 PNG 处理（调用方已按扩展名推断，这里是兜底）。
         *
         * @return MIME 类型
         */
        public String mimeOrDefault() {
            return StringUtils.isBlank(mimeType) ? "image/png" : mimeType;
        }

        /**
         * 组装 OpenAI 兼容的 data URL。
         *
         * @return {@code data:image/png;base64,...}
         */
        public String dataUrl() {
            return "data:" + mimeOrDefault() + ";base64," + base64;
        }
    }

    /**
     * 从载荷中提取图片（保持声明顺序，忽略不合格项）。
     *
     * @param payload 结构化载荷（可空）
     * @return 图片列表，无图片返回空列表
     */
    public static List<ImagePart> extract(Map<String, Object> payload) {
        List<ImagePart> parts = new ArrayList<>();
        if (payload == null || payload.isEmpty()) {
            return parts;
        }
        if (!(payload.get(KEY) instanceof Iterable<?> items)) {
            return parts;
        }
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            ImagePart part = new ImagePart(
                asText(map.get("label")),
                asText(map.get("mimeType")),
                asText(map.get("base64")));
            if (part.usable()) {
                parts.add(part);
                if (parts.size() >= MAX_IMAGES) {
                    break;
                }
            }
        }
        return parts;
    }

    /**
     * 取「去掉图片后的载荷」。
     * <p>文本调用器必须用它组装提示词/请求体：直接把带 base64 的原始载荷序列化出去，
     * 会把几十 MB 的乱码塞进提示词。</p>
     *
     * @param payload 结构化载荷（可空）
     * @return 不含 {@value #KEY} 的载荷副本；原载荷为空时返回空 Map
     */
    public static Map<String, Object> withoutImages(Map<String, Object> payload) {
        Map<String, Object> clean = new LinkedHashMap<>();
        if (payload == null || payload.isEmpty()) {
            return clean;
        }
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            if (KEY.equals(entry.getKey())) {
                continue;
            }
            clean.put(entry.getKey(), entry.getValue());
        }
        return clean;
    }

    /**
     * 判定载荷是否携带图片。
     *
     * @param payload 结构化载荷（可空）
     * @return 携带图片返回 true
     */
    public static boolean hasImages(Map<String, Object> payload) {
        return !extract(payload).isEmpty();
    }

    /**
     * 不支持图片的调用器用本方法显式失败。
     * <p><b>刻意不做「忽略图片继续调用」</b>：那样模型会在没看到图的情况下给出结论，
     * 调用方无法区分「真的比对过」与「只是没报错」。</p>
     *
     * @param payload     结构化载荷
     * @param invokerName 调用器名称（用于可读提示）
     * @return 载荷不含图片时返回 null；含图片时返回可直接返回给调用方的失败摘要
     */
    public static String requireUnsupported(Map<String, Object> payload, String invokerName) {
        if (!hasImages(payload)) {
            return null;
        }
        return invokerName + " 链路当前不支持图片（多模态）输入，"
            + "请为该能力绑定支持图片的直连视觉模型，或改用本地检查";
    }

    /**
     * 校验图片集合是否在体积与数量约束内。
     *
     * @param parts 图片列表
     * @return 违规原因；全部合规返回 null
     */
    public static String validate(List<ImagePart> parts) {
        if (parts == null || parts.isEmpty()) {
            return "未提供任何图片";
        }
        if (parts.size() > MAX_IMAGES) {
            return "图片数量超过上限 " + MAX_IMAGES + " 张";
        }
        for (ImagePart part : parts) {
            long bytes;
            try {
                bytes = Base64.getDecoder().decode(part.base64()).length;
            } catch (IllegalArgumentException e) {
                return "图片「" + StringUtils.blankToDefault(part.label(), "未命名") + "」不是合法的 base64";
            }
            if (bytes > MAX_BYTES_PER_IMAGE) {
                return "图片「" + StringUtils.blankToDefault(part.label(), "未命名") + "」超过 "
                    + (MAX_BYTES_PER_IMAGE / 1024 / 1024) + "MB 限制";
            }
        }
        return null;
    }

    /**
     * 按图片字节数与扩展名推断 MIME。
     *
     * @param ext 扩展名（可带点，可空）
     * @return MIME 类型，无法判定返回 {@code image/png}
     */
    public static String mimeOfExt(String ext) {
        if (StringUtils.isBlank(ext)) {
            return "image/png";
        }
        String e = ext.trim().toLowerCase(Locale.ROOT);
        if (e.startsWith(".")) {
            e = e.substring(1);
        }
        return switch (e) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "bmp" -> "image/bmp";
            case "webp" -> "image/webp";
            case "tif", "tiff" -> "image/tiff";
            default -> "image/png";
        };
    }

    /**
     * 安全取文本。
     *
     * @param value 值
     * @return 字符串，非字符串返回 null
     */
    private static String asText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

}
