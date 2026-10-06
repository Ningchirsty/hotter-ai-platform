package org.dromara.creative.helper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.content.domain.vo.CpBrandBriefVo;
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
 * <p><b>R7 起追加两类输入</b>（顺序即优先级，全部受长度上限约束）：</p>
 * <ol>
 *   <li><b>屏文案</b>：这一屏的画面自己要讲什么（画面独白优先，其次正文、标题）。
 *       此前屏文案完全不进提示词，出图只能按屏类型猜「这一张要讲哪件事」。</li>
 *   <li><b>品牌 Brief</b>：必显信息（逐行追加）→ 主推卖点（最多前 3 条，行首数字即优先级）；
 *       禁用词逐条追加到负向提示词。</li>
 * </ol>
 *
 * <p><b>超限纪律</b>：上限与页面表单一致（正向 1000、负向 500，见 {@code CreativeHeroBo}）。
 * 放不下时只追加「完整的条目」，绝不截半个词，并把未放入的条目写进 {@link Prompt#omitted()}
 * 供页面与日志排障——静默丢弃会让人误以为填了没生效。</p>
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
     * 正向提示词上限（与 {@code CreativeHeroBo.prompt} 的 {@code @Size(max = 1000)} 对齐）。
     *
     * <p>人写的提示词有表单校验，派生出来的却没有——同一个上限必须在派生器里也守一遍，
     * 否则「页面能填进去的」和「服务端会发出去的」是两条线。</p>
     */
    private static final int MAX_PROMPT = 1000;

    /**
     * 负向提示词上限（与 {@code CreativeHeroBo.negativePrompt} 的 {@code @Size(max = 500)} 对齐）
     */
    private static final int MAX_NEGATIVE = 500;

    /**
     * 屏文案进入提示词的字数上限：它是「这一屏讲什么」的一句话说明，不是正文全文。
     * 正文（bodyText 最长 1000 字）整段塞进提示词会把基因内容挤没，因此按 300 字截断并留痕。
     */
    private static final int MAX_SCREEN_TEXT = 300;

    /**
     * 主推卖点最多进提示词的条数（行首数字即优先级，页面已按优先级排好）
     */
    private static final int MAX_MAIN_PUSH = 3;

    /**
     * 未放入说明的长度上限（事件/日志里不能塞整段文本）
     */
    private static final int MAX_NOTE = 220;

    /**
     * 派生结果。
     *
     * @param prompt         正向提示词
     * @param negativePrompt 负向提示词
     * @param applied        实际用到的 DNA 维度与外部约束（供页面与事件留痕）
     * @param omitted        因长度上限未放入的条目说明（R7 起；没有则为空列表）
     */
    public record Prompt(String prompt, String negativePrompt, List<String> applied, List<String> omitted) {
    }

    /**
     * 派生提示词（不带屏文案与品牌 Brief；保留给不需要这两项输入的老调用点与测试）。
     *
     * @param dna        DNA 树（可为空树）
     * @param subject    主体（通常是产品名）
     * @param screenHint 画面用途提示（如「HERO 主图」「卖点图」），可空
     * @return 派生结果
     */
    public Prompt build(ObjectNode dna, String subject, String screenHint) {
        return build(dna, subject, screenHint, null, null, null);
    }

    /**
     * 派生提示词。
     *
     * @param dna        DNA 树（可为空树）
     * @param subject    主体（通常是产品名）
     * @param screenHint 画面用途提示（如「HERO 主图」「卖点图」），可空
     * @param brief      品牌 Brief（可空＝该项目还没填）
     * @param screenText 屏文案（画面独白/正文/标题，可空）
     * @return 派生结果
     */
    public Prompt build(ObjectNode dna, String subject, String screenHint,
                        CpBrandBriefVo brief, String screenText) {
        return build(dna, subject, screenHint, brief, screenText, null);
    }

    /**
     * 派生提示词（V0.2 R23：多一个「模块视觉表达」输入）。
     *
     * <p>R22 的模块规划里可以给每个模块写「视觉表达」（`visual_rules_json`）。那时它只落库不生效，
     * 属于"配了没用"；这里把它作为**该屏的额外视觉约束**接进提示词，并如实记进
     * {@link Prompt#applied()}——页面与事件都能看到"这版提示词用到了模块视觉表达"。</p>
     *
     * @param dna               DNA 树（可为空树）
     * @param subject           主体（通常是产品名）
     * @param screenHint        画面用途提示（如「HERO 主图」「卖点图」），可空
     * @param brief             品牌 Brief（可空＝该项目还没填）
     * @param screenText        屏文案（画面独白/正文/标题，可空）
     * @param moduleVisualRules 模块规划里的「视觉表达」（可空；原样是人填的文本或 JSON）
     * @return 派生结果
     */
    public Prompt build(ObjectNode dna, String subject, String screenHint,
                        CpBrandBriefVo brief, String screenText, String moduleVisualRules) {
        ObjectNode node = dna == null ? VisualDnaSchema.empty() : dna;

        // ③ 人工改写过的提示词（v1 人工测试反馈裁定 2026-10-06：「在框里改」**算新一版基因**）。
        //
        // 覆盖存在 `dna_json.promptOverride`，也就是**跟着版本走**：这一版带着覆盖，
        // 预览与真正下发给模型的提示词都以它为准；下一版没带覆盖就回到派生。
        //
        // 为什么放在 build() 里面而不是各个调用方各判一次：预览（promptPreview）与出图
        // （submitForScreen）都走这个派生器，放在这里才是"改完立刻生效"的唯一权威；
        // 放在调用方就得记住每处都判，漏一处就会出现"页面显示改了、出图还是老的"。
        JsonNode override = node.path("promptOverride");
        if (override.isObject()) {
            String overriddenPositive = override.path("positive").asText("");
            String overriddenNegative = override.path("negative").asText("");
            if (StringUtils.isNotBlank(overriddenPositive) || StringUtils.isNotBlank(overriddenNegative)) {
                return new Prompt(overriddenPositive, overriddenNegative,
                    List.of("promptOverride(人工改写)"), List.of());
            }
        }

        List<String> applied = new ArrayList<>();
        List<String> omitted = new ArrayList<>();
        StringBuilder sb = new StringBuilder();

        String purpose = StringUtils.isBlank(screenHint) ? "主图" : screenHint;
        sb.append("电商详情页").append(purpose).append("：")
            .append(StringUtils.blankToDefault(subject, "当前产品")).append("。");

        // 屏文案放在最前面：它决定「这一张图讲哪件事」，基因决定的是「怎么拍」。
        // 此前屏文案完全不进提示词（submitForScreen 的 prompt 传 null），批量出图只能按屏类型猜。
        String screen = singleLine(screenText);
        if (StringUtils.isNotBlank(screen)) {
            boolean cut = screen.length() > MAX_SCREEN_TEXT;
            sb.append("本屏画面要讲什么：")
                .append(cut ? screen.substring(0, MAX_SCREEN_TEXT) : screen).append("。");
            applied.add("screenText(屏文案)");
            if (cut) {
                omitted.add("屏文案超过 " + MAX_SCREEN_TEXT + " 字，已截断（原文 "
                    + screen.length() + " 字）；完整文案见分镜屏");
            }
        }

        // 模块视觉表达（R23）：紧跟屏文案之后，位置比基因更靠前——它是**这个模块**的专门约束，
        // 比全局风格更具体。整条追加，超长只截这一条并记进 omitted（不静默丢）。
        String rules = singleLine(moduleVisualRules);
        if (StringUtils.isNotBlank(rules)) {
            boolean cut = rules.length() > MAX_SCREEN_TEXT;
            sb.append("该屏的模块视觉表达：")
                .append(cut ? rules.substring(0, MAX_SCREEN_TEXT) : rules).append("。");
            applied.add("module.visualRules");
            if (cut) {
                omitted.add("模块视觉表达超过 " + MAX_SCREEN_TEXT + " 字，已截断（原文 "
                    + rules.length() + " 字）；完整内容见模块规划");
            }
        }

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

        // 品牌 Brief：必显 → 主推卖点（整条追加，放不下的记进 omitted）
        appendMustShow(sb, applied, omitted, brief);
        appendMainPush(sb, applied, omitted, brief);

        // 最后一道闸：正向提示词不能超过页面允许的长度（截断也要留痕）
        if (sb.length() > MAX_PROMPT) {
            omitted.add("正向提示词超过 " + MAX_PROMPT + " 字，已截断（原 "
                + sb.length() + " 字）：请精简屏文案或品牌 Brief");
            sb.setLength(MAX_PROMPT);
        }

        String avoid = joinArray(node.path("avoidKeywords"));
        StringBuilder negative = new StringBuilder(
            StringUtils.isBlank(avoid) ? FALLBACK_AVOID : avoid.replace(',', ','));
        if (StringUtils.isBlank(avoid)) {
            applied.add("avoidKeywords(默认)");
        } else {
            applied.add("avoidKeywords");
        }
        // 禁用词逐条追加到负向提示词（超出 500 字的条目如实记进 omitted，不静默丢）
        List<String> forbidden = forbiddenItems(brief == null ? null : brief.getForbiddenWords());
        if (!forbidden.isEmpty()) {
            List<String> dropped = appendWhole(negative, forbidden, MAX_NEGATIVE, ",", true);
            applied.add("brief.forbiddenWords(" + (forbidden.size() - dropped.size()) + ")");
            noteOmitted(omitted, "禁用词", "因负向提示词长度上限未放入", dropped);
        }
        return new Prompt(sb.toString(), negative.toString(), applied, omitted);
    }

    // ------------------------------------------------------------------
    // 品牌 Brief 接线
    // ------------------------------------------------------------------

    /**
     * 追加必显信息（品牌名/logo/口号/资质等必须出现在成品里的内容）。
     *
     * @param target  正向提示词
     * @param applied 已用维度
     * @param omitted 未放入说明
     * @param brief   品牌 Brief
     */
    private static void appendMustShow(StringBuilder target, List<String> applied,
                                       List<String> omitted, CpBrandBriefVo brief) {
        List<String> items = lines(brief == null ? null : brief.getMustShow());
        if (items.isEmpty()) {
            return;
        }
        String label = "必须出现：";
        // 逐条追加前先把「引导语 + 结尾句号」的位置留出来，避免留下「必须出现：。」这种半句话
        int capacity = MAX_PROMPT - target.length() - label.length() - 1;
        StringBuilder probe = new StringBuilder();
        List<String> dropped = capacity <= 0 ? new ArrayList<>(items)
            : appendWhole(probe, items, capacity, "、", false);
        if (probe.length() > 0) {
            target.append(label).append(probe).append("。");
            applied.add("brief.mustShow(" + (items.size() - dropped.size()) + ")");
        }
        noteOmitted(omitted, "必显信息", "因正向提示词长度上限未放入", dropped);
    }

    /**
     * 追加主推卖点（最多前 3 条：行首数字即优先级，取多了等于没优先级）。
     *
     * @param target  正向提示词
     * @param applied 已用维度
     * @param omitted 未放入说明
     * @param brief   品牌 Brief
     */
    private static void appendMainPush(StringBuilder target, List<String> applied,
                                       List<String> omitted, CpBrandBriefVo brief) {
        List<String> all = lines(brief == null ? null : brief.getMainPush());
        if (all.isEmpty()) {
            return;
        }
        List<String> items = all.size() > MAX_MAIN_PUSH ? all.subList(0, MAX_MAIN_PUSH) : all;
        String label = "主推卖点：";
        int capacity = MAX_PROMPT - target.length() - label.length() - 1;
        StringBuilder probe = new StringBuilder();
        List<String> dropped = capacity <= 0 ? new ArrayList<>(items)
            : appendWhole(probe, items, capacity, "、", false);
        if (probe.length() > 0) {
            target.append(label).append(probe).append("。");
            applied.add("brief.mainPush(" + (items.size() - dropped.size()) + ")");
        }
        List<String> rest = new ArrayList<>(dropped);
        if (all.size() > MAX_MAIN_PUSH) {
            rest.addAll(all.subList(MAX_MAIN_PUSH, all.size()));
        }
        noteOmitted(omitted, "主推卖点", "未进提示词（提示词只取前 " + MAX_MAIN_PUSH + " 条，且受长度上限）", rest);
    }

    // ------------------------------------------------------------------
    // 文本与长度工具
    // ------------------------------------------------------------------

    /**
     * 按整条追加，放不下就整条跳过（绝不截半个词）。
     *
     * @param target               目标
     * @param items                条目
     * @param maxLength            上限（含结尾标点留 1 字余量）
     * @param separator            分隔符
     * @param separatorBeforeFirst 第一条前是否也要分隔符（负向提示词是接着已有的词表往后排，需要；
     *                             正向提示词是接在「必须出现：」这类引导语后，不需要）
     * @return 未放入的条目（保持原顺序）
     */
    private static List<String> appendWhole(StringBuilder target, List<String> items, int maxLength,
                                            String separator, boolean separatorBeforeFirst) {
        List<String> dropped = new ArrayList<>();
        boolean first = true;
        for (String item : items) {
            if (StringUtils.isBlank(item)) {
                continue;
            }
            boolean withSeparator = separatorBeforeFirst || !first;
            int extra = (withSeparator ? separator.length() : 0) + item.length() + 1;
            if (target.length() + extra > maxLength) {
                dropped.add(item);
                continue;
            }
            if (withSeparator) {
                target.append(separator);
            }
            target.append(item);
            first = false;
        }
        return dropped;
    }

    /**
     * 记录「未放入」的条目（截长，避免事件里塞整段文本）。
     *
     * @param omitted 说明列表
     * @param label   类别名
     * @param reason  未放入的原因（长度上限 / 条数上限，两者含义不同，不能混写成一句话）
     * @param dropped 未放入的条目
     */
    private static void noteOmitted(List<String> omitted, String label, String reason,
                                    List<String> dropped) {
        if (dropped == null || dropped.isEmpty()) {
            return;
        }
        String joined = String.join("、", dropped);
        if (joined.length() > MAX_NOTE) {
            joined = joined.substring(0, MAX_NOTE) + "…";
        }
        omitted.add(label + " 有 " + dropped.size() + " 条" + reason + "：" + joined);
    }

    /**
     * 多行文本 → 逐行条目（按换行切，去空白行，不改变行内内容）。
     *
     * <p>契约里多行字段就是「一行一条」，服务端存储保持原字符串；
     * 这里只为拼提示词而临时拆行，不回写、不改存储格式。</p>
     *
     * @param value 多行文本
     * @return 条目列表
     */
    private static List<String> lines(String value) {
        List<String> items = new ArrayList<>();
        if (StringUtils.isBlank(value)) {
            return items;
        }
        for (String line : value.split("\\R")) {
            if (StringUtils.isNotBlank(line)) {
                items.add(line.trim());
            }
        }
        return items;
    }

    /**
     * 折成一行（屏文案可能带换行，提示词里换行没有意义）。
     *
     * @param value 原值
     * @return 单行文本；空白返回 null
     */
    private static String singleLine(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        return String.join(" ", lines(value));
    }

    /**
     * 禁用词切分：换行、逗号（中英文）、分号、顿号都算分隔符。
     *
     * <p><b>为什么比必显信息多切几种</b>：禁用词的最终去处是负向提示词，而负向提示词本身就是
     * 逗号分隔的词表。按「词」而不是按「行」计量，长度预算才算得准——若整行当一个词，
     * 用户一行写 20 个禁用词时会因为「这一条太长」而整行放不下，一个词都进不去。</p>
     *
     * @param value 禁用词文本
     * @return 逐词列表
     */
    private static List<String> forbiddenItems(String value) {
        List<String> items = new ArrayList<>();
        if (StringUtils.isBlank(value)) {
            return items;
        }
        for (String part : value.split("[\\r\\n,，;；、]+")) {
            if (StringUtils.isNotBlank(part)) {
                items.add(part.trim());
            }
        }
        return items;
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
