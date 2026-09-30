package org.dromara.creative.helper;

import org.dromara.common.core.utils.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 海报版面的 payload 组装（V0.2 R52）。
 *
 * <p><b>为什么是独立纯函数</b>：海报渲染最容易出错的地方不是"调渲染服务"，而是
 * <b>下发给模板的东西对不对</b>——画布尺寸是哪一档、主视觉用哪张图、缺图时是提示还是留白、
 * 主张文案从哪来。这些判定不依赖容器，抽出来就能直接单测；塞在渲染器里就只能靠真机试。</p>
 *
 * <p>payload 的形状与 {@code renderer/templates/poster/1.0.0/template.html} 一一对应；
 * 模板侧对每个字段都有兜底（缺了就不渲染那一块），因此这里**只负责如实下发**，
 * 不替模板做"补一个默认文案"这种事。</p>
 *
 * @author creative
 */
public final class CreativePosterLayout {

    private CreativePosterLayout() {
    }

    /**
     * 画布规格（一档输出规格 = 一张海报）。
     *
     * @param specCode 规格编码（如 BRAND_POSTER_3_4）
     * @param ratio    比例（如 3:4，可空）
     * @param width    宽（px）
     * @param height   高（px）
     */
    public record Canvas(String specCode, String ratio, int width, int height) {
    }

    /**
     * 一个模块的可用素材（来自分镜里"已选定"的那张产出图）。
     *
     * @param moduleCode   模块编码（如 MAIN_VISUAL / BRAND_LOCKUP）
     * @param screenNo     屏号
     * @param screenType   屏类型
     * @param title        屏标题（可作为主张的候选文案）
     * @param imageDataUri 图片（data URI；为空表示这一屏还没选定产出）
     * @param generationId 出图候选ID（留痕用）
     */
    public record PosterModule(String moduleCode, String screenNo, String screenType, String title,
                               String imageDataUri, Long generationId) {
    }

    /**
     * 组装下发给模板的 payload。
     *
     * @param canvas       画布规格
     * @param modules      模块素材（可空）
     * @param headline     海报主张（可空）
     * @param subline      副题（可空）
     * @param dna          视觉基因（background / accent 等，可空）
     * @param brandName    品牌名（页脚左侧）
     * @param productName  产品名（可空）
     * @param footerNote   页脚说明（生成信息，可空）
     * @return payload（有序 Map，便于断言与人工核对）
     */
    public static Map<String, Object> build(Canvas canvas, List<PosterModule> modules,
                                            String headline, String subline, Map<String, Object> dna,
                                            String brandName, String productName, String footerNote) {
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> canvasNode = new LinkedHashMap<>();
        if (canvas != null) {
            canvasNode.put("specCode", StringUtils.blankToDefault(canvas.specCode(), ""));
            canvasNode.put("ratio", StringUtils.blankToDefault(canvas.ratio(), ""));
            canvasNode.put("width", canvas.width());
            canvasNode.put("height", canvas.height());
        }
        root.put("canvas", canvasNode);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (PosterModule module : modules == null ? List.<PosterModule>of() : modules) {
            if (module == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("moduleCode", StringUtils.blankToDefault(module.moduleCode(), ""));
            row.put("screenNo", StringUtils.blankToDefault(module.screenNo(), ""));
            row.put("screenType", StringUtils.blankToDefault(module.screenType(), ""));
            row.put("title", StringUtils.blankToDefault(module.title(), ""));
            row.put("generationId", module.generationId());
            if (StringUtils.isNotBlank(module.imageDataUri())) {
                row.put("image", module.imageDataUri());
            }
            rows.add(row);
        }
        root.put("modules", rows);

        Map<String, Object> copy = new LinkedHashMap<>();
        copy.put("headline", StringUtils.blankToDefault(headline, ""));
        copy.put("subline", StringUtils.blankToDefault(subline, ""));
        root.put("copy", copy);

        Map<String, Object> brand = new LinkedHashMap<>();
        brand.put("brandName", StringUtils.blankToDefault(brandName, ""));
        brand.put("productName", StringUtils.blankToDefault(productName, ""));
        // 标识图缺失时的文字兜底：品牌名（没有就留空，模板会整块不渲染）
        brand.put("lockupText", StringUtils.blankToDefault(brandName, ""));
        brand.put("footerBrand", StringUtils.blankToDefault(productName, brandName));
        root.put("brand", brand);

        root.put("dna", dna == null ? Map.of() : dna);
        root.put("footerNote", StringUtils.blankToDefault(footerNote, ""));
        return root;
    }

    /**
     * 允许 null 的候选列表（**别用 {@code List.of}**）。
     *
     * <p>R52 真机第一跑就栽在这上面：{@code List.of(campaign==null?null:title, ...)} 遇到 null
     * 直接抛 NPE，接口只回一句"未知异常"，日志里才看得到栈。而"没有这一项"在文案候选里是
     * <b>正常情况</b>（没有主张屏、没有必显信息块），所以候选一律用这个工厂建。</p>
     *
     * @param values 候选（可含 null）
     * @return 可含 null 的列表
     */
    public static List<String> candidates(String... values) {
        return java.util.Arrays.asList(values);
    }

    /**
     * 取第一个非空文案（主张/副题的候选依次是：屏文案 → 文案块 → 项目名）。
     *
     * @param candidates 候选（按优先级，可含 null）
     * @return 文案；全空返回空串（模板会整块不渲染，不编造）
     */
    public static String pickFirst(List<String> candidates) {
        for (String candidate : candidates == null ? List.<String>of() : candidates) {
            String value = StringUtils.trimToNull(candidate);
            if (value != null) {
                return value;
            }
        }
        return "";
    }
}
