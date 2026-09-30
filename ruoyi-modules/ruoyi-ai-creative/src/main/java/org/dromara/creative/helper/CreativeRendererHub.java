package org.dromara.creative.helper;

import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 渲染器中枢（V0.2 R30，文档 §26 Renderer Hub）。
 *
 * <p><b>它只做三件事</b>：登记所有渲染器、按配置解析出该用哪一个、把"还不存在"的渲染器
 * 明确地拒掉。渲染逻辑一律在 {@link CreativeRenderer} 实现里——中枢自己不知道长图怎么排、
 * 主图怎么打包，这正是"加一种交付形态不用改中枢"的前提。</p>
 *
 * <p><b>解析判据来自配置</b>：{@code dp_delivery_type.render_mode}（LONGPAGE / MULTI_IMAGE …）→
 * 渲染器编码。不写"ECOM_DETAIL 就长图、MAIN_IMAGE 就打包"这种硬编码，
 * 否则每加一个交付类型都要改代码（文档 §27 的大意）。</p>
 *
 * <p><b>fail-closed</b>：请求一个没实现的渲染器（文档 §26 列了 Poster/Article/Print/Video），
 * 直接抛错并列出"已实现的是哪几个"，绝不返回一个空结果让人以为成功了。</p>
 *
 * @author creative
 */
@Component
public class CreativeRendererHub {

    /** 渲染模式 → 渲染器编码（配置层只写模式，映射留在代码里，改映射不动配置） */
    private static final Map<String, String> MODE_TO_RENDERER = Map.of(
        "LONGPAGE", "LONG_PAGE",
        "MULTI_IMAGE", "MULTI_IMAGE");

    /** 文档 §26 规划中、但还没有实现的渲染器（只出现在能力清单里，不能被执行） */
    private static final List<CreativeRenderer> PLANNED = List.of(
        new PlannedRenderer("POSTER", "海报渲染器", "POSTER_LAYOUT", "文档 §9.4 / §44（Sprint F）：等海报交付类型落地后实现"),
        new PlannedRenderer("ARTICLE", "图文文章渲染器", "ARTICLE_LAYOUT", "文档 §26 列出；尚无对应交付类型"),
        new PlannedRenderer("PRINT", "印刷渲染器", "PRINT_LAYOUT", "文档 §44（Sprint F2）：需要 CMYK/出血等印刷口径"),
        new PlannedRenderer("VIDEO", "视频渲染器", "VIDEO_EXPORT", "文档 §26 列为 Future"));

    private final Map<String, CreativeRenderer> byCode = new LinkedHashMap<>();

    /**
     * 由 Spring 注入全部渲染器实现 + 规划中的占位。
     *
     * @param renderers 已实现的渲染器（Spring 收集 {@link CreativeRenderer} 的所有 Bean）
     */
    public CreativeRendererHub(List<CreativeRenderer> renderers) {
        List<CreativeRenderer> all = new ArrayList<>();
        if (renderers != null) {
            all.addAll(renderers);
        }
        all.addAll(PLANNED);
        all.stream()
            .sorted(Comparator.comparing(renderer -> renderer.code().toUpperCase(Locale.ROOT)))
            .forEach(renderer -> byCode.put(renderer.code().toUpperCase(Locale.ROOT), renderer));
    }

    /**
     * 能力清单（页面/验收都能看到"有哪些渲染器、哪个能跑"）。
     *
     * @return 渲染器列表（按编码排序）
     */
    public List<CreativeRenderer> all() {
        return List.copyOf(byCode.values());
    }

    /**
     * 按编码取渲染器；未实现或不存在时**拒绝**。
     *
     * @param code 渲染器编码（大小写不敏感）
     * @return 可执行的渲染器
     */
    public CreativeRenderer require(String code) {
        String key = StringUtils.trimToNull(code);
        if (key == null) {
            throw new org.dromara.common.core.exception.ServiceException("渲染器编码不能为空。");
        }
        CreativeRenderer renderer = byCode.get(key.toUpperCase(Locale.ROOT));
        if (renderer == null) {
            throw new org.dromara.common.core.exception.ServiceException("没有这个渲染器：「" + code
                + "」。已登记的是 " + codes() + "。");
        }
        if (!renderer.implemented()) {
            throw new org.dromara.common.core.exception.ServiceException("渲染器「" + renderer.displayName()
                + "（" + renderer.code() + "）」还没有实现：" + renderer.note()
                + "。现在能跑的渲染器是 " + implementedCodes() + "。");
        }
        return renderer;
    }

    /**
     * 按交付类型的渲染模式解析渲染器（Hub 的正常入口）。
     *
     * @param renderMode {@code dp_delivery_type.render_mode}（如 LONGPAGE / MULTI_IMAGE）
     * @return 可执行的渲染器
     */
    public CreativeRenderer resolveFor(String renderMode) {
        String mode = StringUtils.trimToNull(renderMode);
        if (mode == null) {
            throw new org.dromara.common.core.exception.ServiceException(
                "这个交付类型没有配置渲染模式（dp_delivery_type.render_mode），无法决定用哪个渲染器。");
        }
        String code = MODE_TO_RENDERER.get(mode.toUpperCase(Locale.ROOT));
        if (code == null) {
            throw new org.dromara.common.core.exception.ServiceException("渲染模式「" + mode
                + "」还没有对应的渲染器（已登记的映射：" + MODE_TO_RENDERER + "）。");
        }
        return require(code);
    }

    /**
     * 已登记的全部编码（报错信息里用）。
     *
     * @return 形如 LONG_PAGE/MULTI_IMAGE/POSTER
     */
    public String codes() {
        return String.join("/", byCode.keySet());
    }

    /**
     * 已实现的编码（报错信息里用）。
     *
     * @return 形如 LONG_PAGE/MULTI_IMAGE
     */
    public String implementedCodes() {
        List<String> codes = new ArrayList<>();
        for (CreativeRenderer renderer : byCode.values()) {
            if (renderer.implemented()) {
                codes.add(renderer.code());
            }
        }
        return String.join("/", codes);
    }

    /**
     * 规划中但未实现的渲染器（能力清单里的占位，不参与执行）。
     */
    private static final class PlannedRenderer implements CreativeRenderer {

        private final String code;
        private final String name;
        private final String step;
        private final String note;

        private PlannedRenderer(String code, String name, String step, String note) {
            this.code = code;
            this.name = name;
            this.step = step;
            this.note = note;
        }

        @Override
        public String code() {
            return code;
        }

        @Override
        public String displayName() {
            return name;
        }

        @Override
        public String targetStep() {
            return step;
        }

        @Override
        public boolean implemented() {
            return false;
        }

        @Override
        public String note() {
            return note;
        }

        @Override
        public Outcome render(Context context) {
            throw new org.dromara.common.core.exception.ServiceException(
                "渲染器「" + name + "」还没有实现，不能执行。");
        }
    }
}
