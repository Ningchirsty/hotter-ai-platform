package org.dromara.ai.video.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

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
@ConfigurationProperties(prefix = "video.tier-resolutions")
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
     * 横屏 16:9。
     */
    public static final String RATIO_LANDSCAPE = "16:9 横屏";

    /**
     * 竖屏 9:16。
     */
    public static final String RATIO_PORTRAIT = "9:16 竖屏";

    /**
     * 竖屏 1080×1920。
     */
    public static final String TIER_PORTRAIT_1080P = "竖屏 · 1080×1920";

    /**
     * 竖屏 720×1280。
     */
    public static final String TIER_PORTRAIT_720P = "竖屏 · 720×1280";

    /**
     * 竖屏 480×864。
     */
    public static final String TIER_PORTRAIT_480P = "竖屏 · 480×864";

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
     * 「画面比例 → 该比例下的档位（顺序即前端展示顺序）」。
     *
     * <p>为什么比例不做成独立字段、而是档位的分组：档位是这套模块里唯一的输出旋钮——
     * 它已经被契约声明、被服务端校验、随任务落库、并在成片尺寸断言里被使用。
     * 把竖屏做成"另一个比例的档位"，就不必改任务表、不必改提交流程，也不会出现
     * 「任务落库时丢了比例、重试时按横屏生成」这类不一致。</p>
     *
     * <p>协议与校验因此完全不变：前端先让用户选比例，再把该比例下的档位名提交上来，
     * 服务端仍旧只认 tier。</p>
     */
    private Map<String, java.util.List<String>> ratioTiers = defaultRatioTiers();

    /**
     * 各档位允许的时长档位。
     *
     * <p>为什么按时长做约束、而不是给所有档位开放全部时长：H3 的帧数必须落在
     * {@code 17k+5} 网格上，时长翻倍意味着帧数翻倍，显存与耗时显著上升。
     * 实测 1080P 5 秒（124 帧）需约 11.5 分钟，20 秒是 481 帧（约 4 倍帧数）。
     * 因此按档位分别声明，只开放实测可行的组合。</p>
     */
    private Map<String, java.util.List<String>> durations = defaultDurations();

    /**
     * 默认档位表（横屏 + 竖屏，键为档位名）。
     *
     * <p>1080P 横屏与原模板完全一致（1920×1088 → 1920×1080），保证已上线行为不变。</p>
     *
     * <p>竖屏是把宽高对调、并按 16 对齐取整：导演 1088×1920（16×68 / 16×120），
     * 成片 1080×1920；720P 与 480P 同理。三条竖屏档位同时都是 32 的倍数
     * （1088/32=34、1920/32=60、736/32=23、1280/32=40、480/32=15、864/32=27），
     * 因此不触碰 H3 导演节点 widget step=32 的约束。像素总数与同档横屏相同，
     * 显存与耗时不变，时长矩阵也照搬。</p>
     */
    public static Map<String, Resolution> defaultTiers() {
        Map<String, Resolution> map = new LinkedHashMap<>();
        map.put(TIER_1080P, new Resolution(1920, 1088, 1920, 1080, 1920));
        map.put(TIER_720P, new Resolution(1280, 736, 1280, 720, 1280));
        map.put(TIER_480P, new Resolution(864, 480, 864, 480, 864));
        map.put(TIER_PORTRAIT_1080P, new Resolution(1088, 1920, 1080, 1920, 1920));
        map.put(TIER_PORTRAIT_720P, new Resolution(736, 1280, 720, 1280, 1280));
        map.put(TIER_PORTRAIT_480P, new Resolution(480, 864, 480, 864, 864));
        return map;
    }

    /**
     * 默认的「比例 → 档位」分组，供前端渲染"先选比例、再选清晰度"。
     */
    public static Map<String, java.util.List<String>> defaultRatioTiers() {
        Map<String, java.util.List<String>> map = new LinkedHashMap<>();
        map.put(RATIO_LANDSCAPE, java.util.List.of(TIER_1080P, TIER_720P, TIER_480P));
        map.put(RATIO_PORTRAIT, java.util.List.of(TIER_PORTRAIT_1080P, TIER_PORTRAIT_720P, TIER_PORTRAIT_480P));
        return map;
    }

    /**
     * 默认时长矩阵，与前端 {@code optionsFor()} 的档位联动保持一致。
     *
     * <p>1080P 只给 5 秒：该档位帧数最多、耗时最长，放开长时长会让单次任务长时间占用 GPU。
     * 竖屏档位像素数与同档横屏相同，因此沿用同一矩阵。</p>
     */
    public static Map<String, java.util.List<String>> defaultDurations() {
        Map<String, java.util.List<String>> map = new LinkedHashMap<>();
        map.put(TIER_1080P, java.util.List.of("5 秒"));
        map.put(TIER_720P, java.util.List.of("5 秒", "10 秒"));
        map.put(TIER_480P, java.util.List.of("5 秒", "10 秒", "20 秒"));
        map.put(TIER_PORTRAIT_1080P, java.util.List.of("5 秒"));
        map.put(TIER_PORTRAIT_720P, java.util.List.of("5 秒", "10 秒"));
        map.put(TIER_PORTRAIT_480P, java.util.List.of("5 秒", "10 秒", "20 秒"));
        return map;
    }

    /**
     * 已配置的画面比例（顺序即前端展示顺序），例如 {@code [16:9 横屏, 9:16 竖屏]}。
     */
    public java.util.List<String> ratioNames() {
        return ratioTiers == null ? java.util.List.of() : java.util.List.copyOf(ratioTiers.keySet());
    }

    /**
     * 默认比例（第一个），用于前端初始选中。
     */
    public String defaultRatio() {
        java.util.List<String> names = ratioNames();
        return names.isEmpty() ? null : names.get(0);
    }

    /**
     * 某比例下的档位列表；未知比例返回空列表。
     */
    public java.util.List<String> tiersOf(String ratio) {
        if (ratioTiers == null || ratio == null) {
            return java.util.List.of();
        }
        java.util.List<String> list = ratioTiers.get(ratio);
        return list == null ? java.util.List.of() : java.util.List.copyOf(list);
    }

    /**
     * 档位所属的比例；未知档位返回 null。
     */
    public String ratioOf(String tier) {
        if (ratioTiers == null || tier == null) {
            return null;
        }
        for (Map.Entry<String, java.util.List<String>> entry : ratioTiers.entrySet()) {
            if (entry.getValue() != null && entry.getValue().contains(tier)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * 取某档位允许的时长档位；未知档位返回空列表。
     */
    public java.util.List<String> durationsOf(String tier) {
        if (tier == null || durations == null) {
            return java.util.List.of();
        }
        java.util.List<String> list = durations.get(tier);
        return list == null ? java.util.List.of() : java.util.Collections.unmodifiableList(list);
    }

    /**
     * 某「档位 + 时长」组合是否被允许。
     */
    public boolean supports(String tier, String duration) {
        return duration != null && durationsOf(tier).contains(duration);
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
