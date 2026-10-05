package org.dromara.creative.helper;

import org.dromara.common.core.utils.StringUtils;

import java.util.Locale;
import java.util.Map;

/**
 * 「枚举值写成人话」的唯一口径（v1 人工测试反馈：卡片上印着 MEDIUM / HIGH / SOFT）。
 *
 * <p><b>为什么单独一个类</b>：同一件事原先散在三处、各写各的——
 * 方向内容里的档位（{@code CreativeDraftFactory} 自己一个 switch）、
 * 分镜屏规格里的留白（直接写 {@code dna.path("whitespaceLevel")} 原值 → 卡片上出现
 * 「留白 HIGH」）、没有方向时的光线兜底（拼出「光线：SOFT」）。
 * 一份口径三个实现，迟早有一个漏翻——v1 反馈里就是这么漏的。</p>
 *
 * <p><b>认不出就原样返回</b>：新加一个档位时页面上会露出英文（难看但可发现），
 * 而不是被悄悄改成"中"或变成空白。空值单独说「未设置」——**不填不等于中**。</p>
 *
 * @author creative
 */
public final class CreativeLevelText {

    /** 档位词表（与 {@code VisualDnaSchema} 的 LOW/MEDIUM/HIGH 一致） */
    private static final Map<String, String> LEVEL_WORDS = Map.of(
        "LOW", "低",
        "MEDIUM", "中",
        "HIGH", "高");

    /** 光型词表（与 {@code VisualDnaSchema.LIGHTING_TYPES} 取值一致） */
    private static final Map<String, String> LIGHT_WORDS = Map.of(
        "SOFT", "柔光",
        "HARD", "硬光",
        "STUDIO", "影棚均匀光",
        "NATURAL", "自然光");

    /** 光位词表（与 {@code VisualDnaSchema.LIGHTING_DIRS} 取值一致） */
    private static final Map<String, String> LIGHT_DIR_WORDS = Map.of(
        "FRONT", "正面光",
        "SIDE", "侧光",
        "TOP", "顶光",
        "BACK", "逆光轮廓");

    private CreativeLevelText() {
    }

    /**
     * 档位的中文名。
     *
     * @param level 档位码（LOW/MEDIUM/HIGH）
     * @return 低/中/高；空值「未设置」；认不出原样返回
     */
    public static String level(String level) {
        if (StringUtils.isBlank(level)) {
            return "未设置";
        }
        return LEVEL_WORDS.getOrDefault(up(level), level);
    }

    /**
     * 光型的中文名。
     *
     * @param type 光型码（SOFT/HARD/STUDIO/NATURAL）
     * @return 中文名；空值「未设置」；认不出原样返回
     */
    public static String lighting(String type) {
        if (StringUtils.isBlank(type)) {
            return "未设置";
        }
        return LIGHT_WORDS.getOrDefault(up(type), type);
    }

    /**
     * 光位的中文名。
     *
     * @param direction 光位码（FRONT/SIDE/TOP/BACK）
     * @return 中文名；空值返回空串（调用方通常据此省略这一段）；认不出原样返回
     */
    public static String lightingDirection(String direction) {
        if (StringUtils.isBlank(direction)) {
            return "";
        }
        return LIGHT_DIR_WORDS.getOrDefault(up(direction), direction);
    }

    private static String up(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
