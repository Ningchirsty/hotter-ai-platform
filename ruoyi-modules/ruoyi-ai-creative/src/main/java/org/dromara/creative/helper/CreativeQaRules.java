package org.dromara.creative.helper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.dromara.common.core.utils.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一屏的质检规则（文档 §20 的 {@code qaRules}，V0.2 R29 起真正被消费）。
 *
 * <p><b>为什么要有这个类</b>：{@code dp_module_definition.qa_rules_json} 从 R21 建表起就存在，
 * 模块库界面也能编辑，但**从来没有代码读它**——存着而已。R29 把它接上：分镜生成时按模块库把规则
 * 冻进屏的 {@code spec_json}，选定候选时按这套规则对交付图做一次确定性体检。</p>
 *
 * <p><b>规则 schema（{@code screen-qa/1}）</b>：</p>
 * <pre>
 * {
 *   "schema": "screen-qa/1",
 *   "square": true,                       // 要求正方形画布（平台硬性：主图 1:1）
 *   "minSide": 800,                       // 最短边下限（像素）
 *   "alphaForbidden": true,               // 不许带透明通道（主图交付不允许）
 *   "whiteBackground": {"enabled": true, "minEdgeWhiteness": 0.90},
 *   "subjectRatio":   {"enabled": true, "min": 0.50},
 *   "edgeBleed":      {"enabled": true, "maxRatio": 0.01},
 *   "levels": {"CANVAS_SQUARE": "HARD", "WHITE_BACKGROUND": "SOFT", ...}
 * }
 * </pre>
 *
 * <p><b>没配就是没配</b>：{@code parse(null)} 得到 {@link #NONE}，体检直接返回"未配置规则"，
 * 绝不用一套隐式默认值假装检查过——那会让页面出现"通过"，而其实什么都没看。</p>
 *
 * <p><b>HARD / SOFT 的含义</b>：HARD = 平台客观硬性项（如不是 1:1、边小于 800），
 * SOFT = 参考项（白底度、主体占比、边缘裁切这类度量）。两者在本轮都**只报告不判决**：
 * 是否让 HARD 项自动筛除候选属于产品决策（与文档 §25 第 3 步同类），不由代码替人拍板。</p>
 *
 * @param configured        是否有可用规则（false 时其余字段无意义）
 * @param square            是否要求正方形
 * @param minSide           最短边下限（像素）
 * @param alphaForbidden    是否禁止透明通道
 * @param whiteBackground   是否检查白底
 * @param minEdgeWhiteness  边缘白度下限（0~1）
 * @param subjectRatio      是否检查主体占比
 * @param subjectRatioMin   主体占比下限（0~1）
 * @param edgeBleed         是否检查边缘裁切
 * @param maxBleedRatio     允许贴边的"主体像素"占比上限（0~1）
 * @param levels            逐项等级（HARD/SOFT）
 * @author creative
 */
public record CreativeQaRules(boolean configured, boolean square, int minSide, boolean alphaForbidden,
                             boolean whiteBackground, double minEdgeWhiteness,
                             boolean subjectRatio, double subjectRatioMin,
                             boolean edgeBleed, double maxBleedRatio,
                             Map<String, String> levels) {

    /** 等级：平台客观硬性项（报告为主，不自动筛除） */
    public static final String LEVEL_HARD = "HARD";
    /** 等级：参考项 */
    public static final String LEVEL_SOFT = "SOFT";

    /**
     * 各检查项在规则没写 {@code levels} 时的默认等级。
     *
     * <p>取值的依据是"这一项是不是平台客观要求"：画布比例、最小边、透明通道是客观硬性；
     * 白底度、主体占比、边缘裁切是度量值（不同品类阈值不同），默认 SOFT。</p>
     */
    private static final Map<String, String> DEFAULT_LEVELS = Map.of(
        "CANVAS_SQUARE", LEVEL_HARD,
        "MIN_SIDE", LEVEL_HARD,
        "NO_ALPHA", LEVEL_HARD,
        "WHITE_BACKGROUND", LEVEL_SOFT,
        "SUBJECT_RATIO", LEVEL_SOFT,
        "EDGE_BLEED", LEVEL_SOFT);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 没有配置规则（屏上没有 qaRules，或解析失败） */
    public static final CreativeQaRules NONE = new CreativeQaRules(false, false, 0, false,
        false, 0d, false, 0d, false, 0d, Map.of());

    /**
     * 从模块库/屏规格里的 qaRules JSON 解析规则。
     *
     * @param json 规则 JSON（可空）
     * @return 规则；空或解析不出有效内容时返回 {@link #NONE}
     */
    public static CreativeQaRules parse(String json) {
        if (StringUtils.isBlank(json)) {
            return NONE;
        }
        try {
            JsonNode node = MAPPER.readTree(json);
            if (node == null || !node.isObject()) {
                return NONE;
            }
            boolean square = node.path("square").asBoolean(false);
            int minSide = node.path("minSide").asInt(0);
            boolean alphaForbidden = node.path("alphaForbidden").asBoolean(false);
            JsonNode white = node.path("whiteBackground");
            boolean whiteEnabled = white.path("enabled").asBoolean(false);
            double minEdgeWhiteness = white.path("minEdgeWhiteness").asDouble(0.9d);
            JsonNode subject = node.path("subjectRatio");
            boolean subjectEnabled = subject.path("enabled").asBoolean(false);
            double subjectMin = subject.path("min").asDouble(0d);
            JsonNode bleed = node.path("edgeBleed");
            boolean bleedEnabled = bleed.path("enabled").asBoolean(false);
            double maxBleed = bleed.path("maxRatio").asDouble(0.01d);
            Map<String, String> levels = new LinkedHashMap<>();
            JsonNode levelNode = node.path("levels");
            if (levelNode.isObject()) {
                levelNode.fields().forEachRemaining(entry -> {
                    String value = StringUtils.trimToNull(entry.getValue().asText(null));
                    if (value != null) {
                        levels.put(entry.getKey(), value.toUpperCase(java.util.Locale.ROOT));
                    }
                });
            }
            boolean any = square || minSide > 0 || alphaForbidden || whiteEnabled || subjectEnabled
                || bleedEnabled;
            if (!any) {
                // 有 JSON 但一项都没开：等同没配。这里不抛异常——规则是人写的，写空是合法意图。
                return NONE;
            }
            return new CreativeQaRules(true, square, minSide, alphaForbidden, whiteEnabled,
                minEdgeWhiteness, subjectEnabled, subjectMin, bleedEnabled, maxBleed, levels);
        } catch (Exception e) {
            // 规则写坏不该让"选定候选"失败：按没配处理，页面上会如实显示未配置
            return NONE;
        }
    }

    /**
     * 取某个检查项的等级。
     *
     * @param code 检查项编码（如 {@code CANVAS_SQUARE}）
     * @return HARD / SOFT
     */
    public String levelOf(String code) {
        String level = levels.get(code);
        if (level == null) {
            level = DEFAULT_LEVELS.get(code);
        }
        return LEVEL_HARD.equals(level) ? LEVEL_HARD : LEVEL_SOFT;
    }
}
