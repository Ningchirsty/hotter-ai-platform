package org.dromara.ai.video.config;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 输出档位（清晰度）与分辨率的映射。
 *
 * <p>为什么分辨率不能只写在契约里、必须有这段配置：H3 的分辨率并不是一个「参数」，
 * 而是<b>散落在模板的多个位置</b>，且各位置职责不同：</p>
 *
 * <ul>
 *   <li><b>导演阶段</b>（节点 5 {@code MiniMaxH3Director} 的 {@code width}/{@code height}，
 *       以及 timeline 的 {@code width}/{@code height}/{@code output.width}/{@code output.height}）
 *       —— 决定实际生成的潜在分辨率；</li>
 *   <li><b>编码阶段</b>（节点 14 {@code ImageScale} 的 {@code width}/{@code height}）
 *       —— 决定最终写入 mp4 的尺寸。</li>
 * </ul>
 *
 * <p>原 1080P 模板有意让两者错开一个 16 的台阶：导演阶段 {@code 1920×1088}，
 * 编码阶段 {@code 1920×1080}（节点 14 用 {@code crop=center} 裁掉多余的 8 像素）。
 * 这里<b>沿用同一策略</b>，只是把台阶按比例缩放，使最终成片落在标准的
 * 720 / 480 高度上，避免出现「标称 720 却输出 736」这种会被验收挑出的偏差。</p>
 *
 * <p>约束（来自 ComfyUI 自定义节点）：{@code resolve_output_dimensions} 会把宽高
 * 取整到 16 的倍数，且要求 ≥16；{@code MiniMaxH3Director} 的 widget step 是 32，
 * 但那只约束 UI 手输，API 提交走的是代码里的 16 对齐。因此这里所有取值都保证是
 * 16 的倍数。</p>
 */
@Data
public class VideoTierResolutions {

    /**
     * 高清 1080P。
     */
    public static final String TIER_1080P = "高清 · 1080P";

    /**
     * 流畅 720P。
     */
    public static final String TIER_720P = "流畅 · 720P";

    /**
     * 标清 480P。
     */
    public static final String TIER_480P = "标清 · 480P";

    /**
     * 单个档位的分辨率。
     *
     * @param width      导演阶段宽度（16 的倍数）
     * @param height     导演阶段高度（16 的倍数）
     * @param encodeWidth  编码阶段宽度（最终 mp4 宽度）
     * @param encodeHeight 编码阶段高度（最终 mp4 高度）
     * @param refMaxSize 参考图/参考视频的最大边，通常取导演阶段的长边
     */
    public record Resolution(int width, int height, int encodeWidth, int encodeHeight, int refMaxSize) {
    }

    private Map<String, Resolution> tiers = defaultTiers();

    /**
     * 默认档位表。
     *
     * <p>1080P 与原模板完全一致（1920×1088 → 1920×1080），保证已上线行为不变。</p>
     */
    public static Map<String, Resolution> defaultTiers() {
        Map<String, Resolution> map = new LinkedHashMap<>();
        map.put(TIER_1080P, new Resolution(1920, 1088, 1920, 1080, 1920));
        map.put(TIER_720P, new Resolution(1280, 736, 1280, 720, 1280));
        map.put(TIER_480P, new Resolution(864, 480, 864, 480, 864));
        return map;
    }

    /**
     * 取某档位的分辨率。
     *
     * @return 未知档位返回 null（由调用方决定报错文案）
     */
    public Resolution of(String tier) {
        if (tier == null || tiers == null) {
            return null;
        }
        return tiers.get(tier);
    }

    /**
     * 已配置的档位名集合（顺序与配置一致，便于前端稳定展示）。
     */
    public java.util.Set<String> tierNames() {
        return tiers == null ? java.util.Set.of() : java.util.Collections.unmodifiableSet(tiers.keySet());
    }
}
