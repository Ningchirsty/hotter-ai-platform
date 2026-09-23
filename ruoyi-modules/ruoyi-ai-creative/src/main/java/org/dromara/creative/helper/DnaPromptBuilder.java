package org.dromara.creative.helper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 由 Visual DNA 派生出图提示词。
 *
 * <p><b>为什么必须从 DNA 派生</b>：如果每一屏都靠人现写提示词，同一批图必然出现色彩、光线、
 * 留白各说各话——那正是「视觉基因」要解决的问题。派生后提示词里每一项都能指回 DNA 的某个字段。</p>
 *
 * <p>人类仍然在环内：派生结果只是<b>预填</b>到页面的提示词框，用户可改；改完的内容照原样下发，
 * 但 {@link Prompt#applied()} 会如实说明这版提示词用到了 DNA 的哪些维度。</p>
 *
 * @author creative
 */
@Component
public class DnaPromptBuilder {

    /**
     * 风格关键词缺省（DNA 里没写时用，避免提示词变成半句话）
     */
    private static final String FALLBACK_STYLE = "现代简约、清爽留白、商业摄影";

    /**
     * 默认禁忌词（与 DNA 种子同源）
     */
    private static final String FALLBACK_AVOID = "杂乱背景,强撞色,文字水印,产品变形,低分辨率,过曝";

    /**
     * 派生结果。
     *
     * @param prompt         正向提示词
     * @param negativePrompt 负向提示词
     * @param applied        实际用到的 DNA 维度（供页面与事件留痕）
     */
    public record Prompt(String prompt, String negativePrompt, List<String> applied) {
    }

    /**
     * 派生提示词。
     *
     * @param dna        DNA 树（可为空树）
     * @param subject    主体（通常是产品名）
     * @param screenHint 画面用途提示（如「HERO 主图」「卖点图」），可空
     * @return 派生结果
     */
    public Prompt build(ObjectNode dna, String subject, String screenHint) {
        ObjectNode node = dna == null ? VisualDnaSchema.empty() : dna;
        List<String> applied = new ArrayList<>();
        StringBuilder sb = new StringBuilder();

        String purpose = StringUtils.isBlank(screenHint) ? "主图" : screenHint;
        sb.append("电商详情页").append(purpose).append("：")
            .append(StringUtils.blankToDefault(subject, "当前产品")).append("。");

        String style = joinArray(node.path("styleKeywords"));
        sb.append("整体风格：").append(StringUtils.isBlank(style) ? FALLBACK_STYLE : style).append("；");
        applied.add("styleKeywords");

        JsonNode colors = node.path("colors");
        List<String> colorDesc = new ArrayList<>();
        appendColor(colorDesc, "主色", colors.path("primary").asText(null));
        appendColor(colorDesc, "辅色", colors.path("secondary").asText(null));
        appendColor(colorDesc, "点缀色", colors.path("accent").asText(null));
        appendColor(colorDesc, "背景", colors.path("background").asText(null));
        if (!colorDesc.isEmpty()) {
            sb.append("配色：").append(String.join("、", colorDesc)).append("；");
            applied.add("colors");
        }

        String lighting = lightingDesc(node.path("lighting"));
        if (StringUtils.isNotBlank(lighting)) {
            sb.append(lighting).append("；");
            applied.add("lighting");
        }

        String whitespace = levelDesc(node.path("whitespaceLevel").asText(null), "留白");
        if (StringUtils.isNotBlank(whitespace)) {
            sb.append(whitespace).append("；");
            applied.add("whitespaceLevel");
        }

        String ratio = ratioDesc(node.path("productRatio"));
        if (StringUtils.isNotBlank(ratio)) {
            sb.append(ratio).append("；");
            applied.add("productRatio");
        }

        String scene = node.path("sceneType").asText(null);
        if (StringUtils.isNotBlank(scene)) {
            sb.append("场景：").append(scene).append("；");
            applied.add("sceneType");
        }

        String saturation = levelDesc(node.path("saturation").asText(null), "饱和度");
        String contrast = levelDesc(node.path("contrastLevel").asText(null), "对比度");
        if (StringUtils.isNotBlank(saturation) || StringUtils.isNotBlank(contrast)) {
            sb.append(StringUtils.blankToDefault(saturation, ""))
                .append(StringUtils.isBlank(saturation) || StringUtils.isBlank(contrast) ? "" : "、")
                .append(StringUtils.blankToDefault(contrast, "")).append("；");
            applied.add("saturation/contrastLevel");
        }

        sb.append("产品结构、配色与细节保持与参考图一致，画面干净、主体清晰。");

        String avoid = joinArray(node.path("avoidKeywords"));
        String negative = StringUtils.isBlank(avoid) ? FALLBACK_AVOID : avoid.replace(',', ',');
        if (StringUtils.isBlank(avoid)) {
            applied.add("avoidKeywords(默认)");
        } else {
            applied.add("avoidKeywords");
        }
        return new Prompt(sb.toString(), negative, applied);
    }

    private static void appendColor(List<String> target, String label, String value) {
        if (StringUtils.isNotBlank(value)) {
            target.add(label + " " + value);
        }
    }

    private static String lightingDesc(JsonNode lighting) {
        String type = lighting.path("type").asText(null);
        String dir = lighting.path("direction").asText(null);
        if (StringUtils.isBlank(type) && StringUtils.isBlank(dir)) {
            return null;
        }
        StringBuilder sb = new StringBuilder("光线：");
        sb.append(switch (StringUtils.blankToDefault(type, "")) {
            case "SOFT" -> "柔和散射光";
            case "HARD" -> "硬质方向光";
            case "STUDIO" -> "影棚布光";
            case "NATURAL" -> "自然光";
            default -> "均匀布光";
        });
        String dirDesc = switch (StringUtils.blankToDefault(dir, "")) {
            case "FRONT" -> "正面光";
            case "SIDE" -> "侧光";
            case "TOP" -> "顶光";
            case "BACK" -> "背光/轮廓光";
            default -> "";
        };
        if (StringUtils.isNotBlank(dirDesc)) {
            sb.append("、").append(dirDesc);
        }
        return sb.toString();
    }

    private static String levelDesc(String value, String label) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        String desc = switch (value) {
            case "LOW" -> "低";
            case "HIGH" -> "高";
            case "MEDIUM" -> "中";
            default -> value;
        };
        return label + desc;
    }

    private static String ratioDesc(JsonNode ratio) {
        JsonNode min = ratio.path("min");
        JsonNode max = ratio.path("max");
        if (min.isNumber() && max.isNumber()) {
            return "产品占画面 " + min.asInt() + "%~" + max.asInt() + "%";
        }
        if (min.isNumber()) {
            return "产品占画面不低于 " + min.asInt() + "%";
        }
        if (max.isNumber()) {
            return "产品占画面不超过 " + max.asInt() + "%";
        }
        return null;
    }

    private static String joinArray(JsonNode array) {
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
        return items.isEmpty() ? null : String.join("、", items);
    }

}
