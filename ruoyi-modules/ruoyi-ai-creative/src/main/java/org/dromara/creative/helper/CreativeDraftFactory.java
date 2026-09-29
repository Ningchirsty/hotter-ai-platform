package org.dromara.creative.helper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.dromara.common.core.utils.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 视觉方向与分镜的「参数化草稿」工厂（R4）。
 *
 * <p><b>为什么要有这个类（问题 3 的正面回答）</b>：R3 之前，方向固定 3 条、分镜固定 7 句，
 * 生产库 27 行方向里只有 3 个不同名称、70 行分镜里只有 7 句不同独白——刷新永远一样。
 * 根因不是逻辑写错，而是**当时没有任何可用的创作模型能力**（治理台只注册了解析/预检类能力，
 * dryRun 一个 creative 能力直接 DENIED），只能吐固定模板。</p>
 *
 * <p>本类把「固定模板」升级为「参数化模板」：每一句文案都由
 * <b>锁定基因（含参考图实测值）+ 已确认事实 + 产品名</b> 推导出来。由此得到两条可验证的性质：</p>
 * <ul>
 *   <li><b>不同输入必然不同</b>：换产品、换参考图、改基因，文案随之改变（不再是同一句）；</li>
 *   <li><b>同输入完全可复现</b>：不引入随机数、不看时间、不看机器——同一份输入永远得到同一份草稿，
 *       便于人工对比「改了基因之后文案怎么变了」。</li>
 * </ul>
 *
 * <p><b>不猜的纪律</b>：参考图测不出来的东西（风格关键词、字体风格）绝不编造；
 * 事实里没有的字段就不写进文案，用中性表述替代。来源仍如实标 {@code TEMPLATE}——
 * 参数化模板的产物不是模型产物，页面徽标必须说实话。</p>
 *
 * @author creative
 */
public final class CreativeDraftFactory {

    /**
     * 光型词表（与 {@code VisualDnaSchema.LIGHTING_TYPES} 取值一致）
     */
    private static final Map<String, String> LIGHT_WORDS = Map.of(
        "SOFT", "柔光",
        "HARD", "硬光",
        "STUDIO", "影棚均匀光",
        "NATURAL", "自然光");

    /**
     * 光位词表（与 {@code VisualDnaSchema.LIGHTING_DIRS} 取值一致）
     */
    private static final Map<String, String> DIRECTION_WORDS = Map.of(
        "FRONT", "正面光",
        "SIDE", "侧光",
        "TOP", "顶光",
        "BACK", "逆光轮廓");

    private CreativeDraftFactory() {
    }

    /**
     * 视觉方向草稿。
     *
     * @param code     方向编码（A/B/C）
     * @param name     方向名（含输入驱动的参数标签）
     * @param concept  取舍说明（引用基因与实测值）
     * @param strategy 策略明细（background/scene/lighting/composition/mood/… 全部输入驱动）
     */
    public record DirectionDraft(String code, String name, String concept, Map<String, Object> strategy) {
    }

    /**
     * 分镜屏草稿。
     *
     * @param type             屏类型
     * @param label            展示名
     * @param productLockLevel 产品保真等级
     * @param title            标题
     * @param subtitle         副标题（可空）
     * @param bodyText         正文（可空）
     * @param soloStatement    画面独白（该屏画面自己要讲清的事，锁定前必填）
     */
    public record ScreenDraft(String type, String label, String productLockLevel,
                              String title, String subtitle, String bodyText, String soloStatement) {
    }

    /**
     * 文案块提示（R7：卖点块的标题与内容）。
     *
     * <p>用这个简单记录而不是直接依赖 VO：草稿工厂是纯函数式的文案推导器，
     * 入参越窄，越容易在单测里复现「同样输入必然同样输出」。</p>
     *
     * @param title   卖点标题（进副标题）
     * @param content 卖点说明（进正文）
     */
    public record CopyHint(String title, String content) {
    }

    // ------------------------------------------------------------------
    // 视觉方向
    // ------------------------------------------------------------------

    /**
     * 生成 3 条视觉方向草稿。
     *
     * <p>3 条方向代表 3 种**行业取舍**（克制/代入/质感），这是稳定的业务骨架；
     * 但方向名、取舍说明与策略明细全部由输入推导，因此不同项目看到的内容不同。</p>
     *
     * @param dna        锁定基因（含参考图实测镜像字段）
     * @param productName 产品名
     * @param facts      已确认事实（字段编码 → 值）
     * @return 3 条方向草稿
     */
    public static List<DirectionDraft> directions(ObjectNode dna, String productName, Map<String, String> facts) {
        String background = text(dna.path("colors"), "background", "#F5F5F3");
        String primary = text(dna.path("colors"), "primary", null);
        String lightingType = text(dna.path("lighting"), "type", "SOFT");
        String lightingDir = text(dna.path("lighting"), "direction", null);
        String saturation = text(dna, "saturation", null);
        String contrast = text(dna, "contrastLevel", null);
        String whitespace = text(dna, "whitespaceLevel", null);
        String sceneType = text(dna, "sceneType", null);
        String ratio = ratioOf(dna);
        String product = StringUtils.blankToDefault(productName, "该产品");

        String light = LIGHT_WORDS.getOrDefault(up(lightingType), "柔光");
        String dirWord = lightingDir == null ? null : DIRECTION_WORDS.get(up(lightingDir));
        String lightFull = dirWord == null ? light : light + "（" + dirWord + "）";
        String tone = toneOf(background);
        String density = densityOf(saturation, contrast, whitespace);

        // 基因摘要：让「这条方向是按哪份基因定的」在文案里可核对
        String dnaSummary = "主色 " + orDash(primary) + "、背景 " + background
            + "、饱和 " + orDash(saturation) + "、对比 " + orDash(contrast)
            + "、留白 " + orDash(whitespace) + "、产品占比 " + ratio;

        List<DirectionDraft> list = new ArrayList<>();
        list.add(new DirectionDraft("A", "克制影棚 · " + light + tone,
            "同一基因下信息最清楚的拍法：以「" + background + "」为底，"
                + lightFull + "均匀铺开，" + density + "，主体占比守在 " + ratio + "。"
                + "适合主图与参数屏——先把「这是什么」说清，再谈氛围。",
            strategy(background, "纯色底（沿用基因背景色 " + background + "）",
                lightFull + "，无环境光干扰",
                "产品居中、正投影，四周留白均等（留白 " + orDash(whitespace) + "）",
                "专业、克制、以产品为主",
                dnaSummary, sceneType, ratio)));

        list.add(new DirectionDraft("B", "生活代入 · " + sceneLabel(sceneType) + tone,
            "同一基因下最有代入感的拍法：把「" + product + "」放进真实使用环境"
                + (facts != null && StringUtils.isNotBlank(facts.get("color"))
                    ? "（画面里保留已确认的「" + facts.get("color") + "」配色特征）" : "")
                + "，构图走三分位、留白处承接文案。"
                + "参考图实测场景为「" + orDash(sceneType) + "」，本方向据此贴近而非另起一套。",
            strategy("#F7F1E8", "生活场景（暖白桌面/家居环境）",
                "自然光 + 侧光，带柔和投影",
                "产品偏左或偏右三分位，留白处置文案（留白 " + orDash(whitespace) + "）",
                "温暖、日常、可代入",
                dnaSummary, sceneType, ratio)));

        list.add(new DirectionDraft("C", "质感特写 · " + light + darkTone(background),
            "同一基因下最有质感的拍法：背景压到「" + darken(background) + "」，"
                + LIGHT_WORDS.getOrDefault(up(lightingType), "柔光") + "切小面积、强调材质反射。"
                + "事实里能支撑细节的字段"
                + (detailFact(facts) == null ? "暂缺，请先确认工艺/规格后再定这一屏"
                    : "为「" + detailFact(facts) + "」") + "。",
            strategy(darken(background), "主题暗场（深色渐变）",
                "硬质方向光 + 轮廓光，强调材质反射",
                "局部特写（结构/工艺/材质），大特写裁切",
                "精致、高级、强调质感",
                dnaSummary, sceneType, ratio)));

        return list;
    }

    // ------------------------------------------------------------------
    // 分镜
    // ------------------------------------------------------------------

    /**
     * 生成分镜草稿（按当前屏骨架契约）。
     *
     * <p>屏的类型与顺序来自骨架契约（默认：主图→卖点×2→场景→细节→尺寸→品牌），
     * 但每屏的标题、副标题、正文与<b>画面独白</b>都由事实与基因推导——
     * 这是「画面独白只有 7 句」这个问题的直接修复（当时独白是写死的句子，现在是按输入推导）。</p>
     *
     * @param dna         锁定基因
     * @param productName 产品名
     * @param facts       已确认事实
     * @return 与骨架一一对应的草稿
     */
    public static List<ScreenDraft> screens(ObjectNode dna, String productName, Map<String, String> facts) {
        return screens(dna, productName, facts, null, List.of());
    }

    /**
     * 生成分镜草稿（R7：接入品牌 Brief 与卖点块；C′：改为按屏骨架迭代）。
     *
     * <p><b>接入原则</b>：有数据就用数据，<b>没数据保持原有骨架</b>——
     * 没有填品牌 Brief 的项目，分镜必须与 R7 之前完全一致，否则「没填」会变成「分镜变空」，
     * 把可用性倒退成阻塞。卖点只取骨架里卖点屏的数量（默认 2 条）：多出来的先留在文案块里等人决定。</p>
     *
     * <p><b>C′ 改造点</b>：改造前这里是第二份写死的 7 屏清单（一串 {@code list.add}），
     * 与建屏服务里的 TEMPLATES 靠"数量都是 7"的约定对齐；现在两份都改为遍历同一份骨架契约。</p>
     *
     * @param dna              锁定基因
     * @param productName      产品名
     * @param facts            已确认事实
     * @param mustShowFirstLine 品牌 Brief 必显信息的第一行（可空；进品牌收尾屏）
     * @param sellingPoints    卖点块（按 sortNo 升序，可空）
     * @return 与骨架一一对应的草稿
     */
    public static List<ScreenDraft> screens(ObjectNode dna, String productName, Map<String, String> facts,
                                            String mustShowFirstLine, List<CopyHint> sellingPoints) {
        return screens(CreativeScreenSkeletonRegistry.skeleton(), dna, productName, facts,
            mustShowFirstLine, sellingPoints);
    }

    /**
     * 生成分镜草稿（**按调用方给的骨架**；R21 起模块引擎的场景走这里）。
     *
     * <p>为什么要有这个 public 重载：R21 之前骨架只能来自 {@link CreativeScreenSkeletonRegistry}
     * （进程级一份契约）；现在骨架可以来自**项目模块计划**（`dp_project_module`），
     * 而分镜服务与草稿工厂不同包，拿不到包级可见的那个重载。</p>
     *
     * @param skeleton          屏骨架（模块计划或契约文件）
     * @param dna               锁定基因
     * @param productName       产品名
     * @param facts             已确认事实
     * @param mustShowFirstLine 品牌 Brief 必显信息的第一行（可空）
     * @param sellingPoints     卖点块（按 sortNo 升序，可空）
     * @return 与骨架一一对应的草稿
     */
    public static List<ScreenDraft> screensOf(CreativeScreenSkeleton skeleton, ObjectNode dna, String productName,
                                              Map<String, String> facts, String mustShowFirstLine,
                                              List<CopyHint> sellingPoints) {
        return screens(skeleton, dna, productName, facts, mustShowFirstLine, sellingPoints);
    }

    /**
     * 生成分镜草稿（按指定骨架；包级可见，供单测验证 3 屏 / 9 屏等非默认骨架）。
     *
     * @param skeleton          屏骨架
     * @param dna               锁定基因
     * @param productName       产品名
     * @param facts             已确认事实
     * @param mustShowFirstLine 品牌 Brief 必显信息的第一行（可空；进品牌收尾屏）
     * @param sellingPoints     卖点块（按 sortNo 升序，可空）
     * @return 与骨架一一对应的草稿
     */
    static List<ScreenDraft> screens(CreativeScreenSkeleton skeleton, ObjectNode dna, String productName,
                                     Map<String, String> facts, String mustShowFirstLine,
                                     List<CopyHint> sellingPoints) {
        Map<String, String> f = facts == null ? Map.of() : facts;
        List<CopyHint> points = sellingPoints == null ? List.of() : sellingPoints;
        String product = StringUtils.blankToDefault(
            firstNonBlank(f.get("product_name"), productName), "该产品");
        String color = blank(f.get("color"));
        String craft = firstNonBlank(f.get("craft"), f.get("main_version"));
        String spec = blank(f.get("spec_params"));
        String quantity = blank(f.get("quantity"));
        String packing = blank(f.get("package_version"));
        String brandTone = blank(f.get("brand_tone"));
        String sceneType = text(dna, "sceneType", null);
        String ratio = ratioOf(dna);
        String background = text(dna.path("colors"), "background", "#F5F5F3");
        String lightingType = text(dna.path("lighting"), "type", "SOFT");
        String light = LIGHT_WORDS.getOrDefault(up(lightingType), "柔光");

        ScreenContext ctx = new ScreenContext(product, color, craft, spec, quantity, packing, brandTone,
            sceneType, ratio, background, light, f.get("product_name"), f.get("main_version"),
            orDash(text(dna, "whitespaceLevel", null)), blank(mustShowFirstLine));

        List<ScreenDraft> list = new ArrayList<>();
        // 同类型第几次出现：两个卖点屏要拿到不同的卖点块，第 N 个卖点屏拿第 N 条
        Map<String, Integer> occurrence = new HashMap<>();
        Map<String, String> lastSoloByType = new HashMap<>();
        for (CreativeScreenSkeleton.ScreenSpec screen : skeleton.screens()) {
            int index = occurrence.merge(screen.type(), 1, Integer::sum);
            ScreenDraft draft = screenDraft(screen, index, points, ctx);
            // 兜底：同类型多屏的"画面独白"不许撞车。默认契约只有两个卖点屏、文案天然不同，
            // 但契约是可配置的——一旦有人在契约里放 3 个同类型屏，通用文案就会重复，
            // 那正是「70 行分镜只有 7 句不同独白」那个老问题的翻版。这里当场消解，且不改默认输出。
            String previous = lastSoloByType.get(screen.type());
            if (index > 1 && previous != null && previous.equals(draft.soloStatement())) {
                draft = new ScreenDraft(draft.type(), draft.label(), draft.productLockLevel(), draft.title(),
                    draft.subtitle(), draft.bodyText(),
                    draft.soloStatement() + "（该类型第 " + index + " 屏：机位、景别或背景要与前一屏明显不同）");
            }
            lastSoloByType.put(screen.type(), draft.soloStatement());
            list.add(draft);
        }
        return list;
    }

    /**
     * 单屏文案上下文（把 {@link #screens} 里算好的一堆取值收成一个不可变参数，避免 12 个形参）。
     */
    private record ScreenContext(String product, String color, String craft, String spec, String quantity,
                                 String packing, String brandTone, String sceneType, String ratio,
                                 String background, String light, String productNameFact, String mainVersion,
                                 String whitespace, String mustShow) {
    }

    /**
     * 单屏文案策略：按屏类型分派；未知类型给可用的兜底草稿。
     *
     * <p>兜底为什么重要：骨架契约是可配置的，运营完全可能加一个代码里没有专用策略的新屏类型。
     * 这时宁可给一句"按这一屏的展示名把画面讲清楚"的通用草稿（保真等级照用契约里的），
     * 也不能抛异常或少一屏——那会让整次生成失败。</p>
     *
     * @param screen 屏定义（类型/展示名/保真等级）
     * @param index  同类型屏的第几次出现（从 1 开始）
     * @param points 卖点块
     * @param ctx    文案上下文
     * @return 草稿
     */
    private static ScreenDraft screenDraft(CreativeScreenSkeleton.ScreenSpec screen, int index,
                                           List<CopyHint> points, ScreenContext ctx) {
        CopyHint point = index <= points.size() ? points.get(index - 1) : null;
        return switch (screen.type()) {
            case "HERO" -> new ScreenDraft(screen.type(), screen.label(), screen.productLockLevel(),
                ctx.product() + " · 主图",
                firstNonBlank(ctx.spec(), "整体形态"),
                joinNonBlank("，", ctx.productNameFact(), ctx.color(), ctx.mainVersion()),
                "一眼认出这是「" + ctx.product() + "」："
                    + (ctx.color() == null ? "形态、配色、材质" : "已确认的" + ctx.color() + "配色")
                    + "与材质都在画面上讲清，不靠一行文案解释；留白 " + ctx.whitespace()
                    + "，产品占比 " + ctx.ratio() + "。");
            case "SELLING_POINT" -> new ScreenDraft(screen.type(), screen.label(), screen.productLockLevel(),
                ctx.product() + " · " + sellingLabel(index == 1 ? ctx.color() : ctx.craft(),
                    index == 1 ? "配色" : "工艺", screen.label()),
                point == null ? null : blank(point.title()),
                point == null ? null : blank(point.content()),
                index == 1
                    ? (ctx.color() == null
                        ? "把第一个卖点用画面讲清楚：用「" + ctx.product() + "」身上最直观的那个特征当主角，而不是写一行字"
                        : "把「" + ctx.color() + "」这个已确认的配色特征拍成画面主角——让人先看到颜色，再读文字")
                    : index == 2
                        ? (ctx.craft() == null
                            ? "第二个卖点要与第一个在画面上有区分：换机位、换景别，别让两屏看起来是同一张图"
                            : "把「" + ctx.craft() + "」讲成画面：换机位与景别，与上一屏的卖点在视觉上明确区分开")
                        : ("第 " + index + " 个卖点要再换一次视觉表达：机位、景别与背景都要和前面几屏明显不同"
                            + (point == null || blank(point.content()) == null
                                ? "" : "，把「" + blank(point.content()) + "」讲成画面")));
            case "SCENE" -> new ScreenDraft(screen.type(), screen.label(), screen.productLockLevel(),
                ctx.product() + " · " + sceneLabel(ctx.sceneType()),
                null,
                null,
                "展示它在真实环境里的样子："
                    + (StringUtils.isBlank(ctx.sceneType()) ? "放在哪、和什么在一起、什么氛围"
                        : "参考图实测场景为「" + ctx.sceneType() + "」，本屏贴近该场景")
                    + "；光线沿用基因的" + ctx.light() + "，背景色以 " + ctx.background() + " 为基调");
            case "DETAIL" -> new ScreenDraft(screen.type(), screen.label(), screen.productLockLevel(),
                ctx.product() + " · 细节",
                firstNonBlank(ctx.craft(), "工艺与结构细节"),
                joinNonBlank("；", ctx.craft()),
                "让人相信做工："
                    + (ctx.craft() == null
                        ? "把材质纹理、结构接缝、表面处理拍清楚（具体工艺字段尚未确认，先按画面可辨识为准）"
                        : "把「" + ctx.craft() + "」拍清楚：材质纹理、结构接缝、表面处理经得起看"));
            case "SIZE" -> new ScreenDraft(screen.type(), screen.label(), screen.productLockLevel(),
                ctx.product() + " · 尺寸",
                firstNonBlank(ctx.spec(), ctx.quantity(), "尺寸与构成"),
                joinNonBlank("；", ctx.spec(), ctx.quantity()),
                "不靠文案也能感知大小与构成：加上可对照的参照物，比例必须真实"
                    + (ctx.spec() == null ? "（尺寸字段尚未确认，请先确认规格再定这一屏）"
                        : "，已确认规格「" + ctx.spec() + "」直接上图"));
            case "BRAND" -> new ScreenDraft(screen.type(), screen.label(), screen.productLockLevel(),
                "品牌收尾",
                // R7：品牌 Brief 的必显信息第一行进副标题（它是品牌方要求必须出现的内容，
                // 放在收尾屏最自然）；没填 Brief 时与 R7 之前完全一致
                firstNonBlank(ctx.mustShow(), ctx.packing(), ctx.brandTone(), "品牌与包装"),
                joinNonBlank("；", ctx.mustShow(), ctx.packing(), ctx.brandTone()),
                "留下品牌印象并收尾：画面克制、不抢产品"
                    + (ctx.brandTone() == null ? "" : "，调性落在已确认的「" + ctx.brandTone() + "」上"));
            default -> genericDraft(screen, ctx, point);
        };
    }

    /**
     * 未知屏类型的兜底草稿：用契约里的展示名与保真等级，文案保证"这一屏讲得清"的最低可用要求。
     *
     * @param screen 屏定义
     * @param ctx    文案上下文
     * @param point  若该屏对应到某条卖点块则带上
     * @return 草稿
     */
    private static ScreenDraft genericDraft(CreativeScreenSkeleton.ScreenSpec screen, ScreenContext ctx, CopyHint point) {
        return new ScreenDraft(screen.type(), screen.label(), screen.productLockLevel(),
            ctx.product() + " · " + screen.label(),
            point == null ? null : blank(point.title()),
            point == null ? null : blank(point.content()),
            "这一屏（" + screen.label() + "）在骨架契约里没有专用文案策略，先按展示名把画面要讲的事说清楚："
                + "「" + ctx.product() + "」在这一屏最该被看到的是什么，用画面而不是文字表达。");
    }

    // ------------------------------------------------------------------
    // 词法（全部由输入判定，不做随机）
    // ------------------------------------------------------------------

    private static Map<String, Object> strategy(String background, String scene, String lighting,
                                                String composition, String mood,
                                                String dnaSummary, String sceneType, String ratio) {
        Map<String, Object> strategy = new LinkedHashMap<>();
        strategy.put("schema", "visual-direction/1");
        strategy.put("background", background);
        strategy.put("scene", scene);
        strategy.put("lighting", lighting);
        strategy.put("composition", composition);
        strategy.put("mood", mood);
        strategy.put("productRatio", ratio);
        strategy.put("referenceSceneType", orDash(sceneType));
        strategy.put("dnaBasis", dnaSummary);
        strategy.put("differences", List.of("background", "scene", "lighting", "composition", "mood"));
        return strategy;
    }

    /**
     * 背景色的明度定性：暗场/亮场/中间调。
     *
     * @param hex 背景色（#RRGGBB）
     * @return 色调词
     */
    private static String toneOf(String hex) {
        double lum = luminance(hex);
        if (lum < 0.35) {
            return "暗场";
        }
        if (lum > 0.75) {
            return "亮场";
        }
        return "中间调";
    }

    /**
     * 方向 C 用的暗场词：底色本来就暗时不必再说「压暗」。
     *
     * @param hex 背景色
     * @return 词
     */
    private static String darkTone(String hex) {
        return luminance(hex) < 0.35 ? "暗场" : "压暗";
    }

    /**
     * 密度短语：由饱和度/对比度/留白组合而成。
     *
     * @param saturation 饱和度档
     * @param contrast   对比度档
     * @param whitespace 留白档
     * @return 短语
     */
    private static String densityOf(String saturation, String contrast, String whitespace) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.isNotBlank(saturation)) {
            parts.add(switch (up(saturation)) {
                case "LOW" -> "低饱和";
                case "HIGH" -> "高饱和";
                default -> "中饱和";
            });
        }
        if (StringUtils.isNotBlank(contrast)) {
            parts.add(switch (up(contrast)) {
                case "LOW" -> "低对比";
                case "HIGH" -> "高对比";
                default -> "中对比";
            });
        }
        if (StringUtils.isNotBlank(whitespace)) {
            parts.add(switch (up(whitespace)) {
                case "LOW" -> "紧凑构图";
                case "HIGH" -> "大留白";
                default -> "常规留白";
            });
        }
        return parts.isEmpty() ? "按基因的密度设定" : String.join("、", parts);
    }

    /**
     * 场景词：参考图实测场景优先，测不出来时不猜。
     *
     * @param sceneType DNA 里的实测场景
     * @return 场景词
     */
    private static String sceneLabel(String sceneType) {
        return StringUtils.isBlank(sceneType) ? "场景自定" : sceneType;
    }

    /**
     * 卖点标签：有事实就用事实说清是哪一类卖点，没有就退回序号。
     *
     * @param fact      事实值
     * @param label     事实对应的卖点类别
     * @param fallback  兜底标签
     * @return 标签
     */
    private static String sellingLabel(String fact, String label, String fallback) {
        return StringUtils.isBlank(fact) ? fallback : label;
    }

    /**
     * 取第一条可用于「细节屏」的事实（工艺优先，其次款式）。
     *
     * @param facts 事实
     * @return 事实值；都没有返回 null
     */
    private static String detailFact(Map<String, String> facts) {
        if (facts == null) {
            return null;
        }
        return firstNonBlank(facts.get("craft"), facts.get("main_version"));
    }

    /**
     * 产品占比区间文本。
     *
     * @param dna 基因
     * @return 形如 45%~65%
     */
    private static String ratioOf(ObjectNode dna) {
        int min = dna.path("productRatio").path("min").asInt(45);
        int max = dna.path("productRatio").path("max").asInt(65);
        return min + "%~" + max + "%";
    }

    private static String text(JsonNode parent, String field, String fallback) {
        JsonNode node = parent.path(field);
        if (node.isMissingNode() || node.isNull()) {
            return fallback;
        }
        String value = node.asText("");
        return StringUtils.isBlank(value) ? fallback : value;
    }

    private static String orDash(String value) {
        return StringUtils.isBlank(value) ? "未测" : value;
    }

    private static String up(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String blank(String value) {
        return StringUtils.isBlank(value) ? null : value;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private static String joinNonBlank(String sep, String... values) {
        List<String> parts = new ArrayList<>();
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                parts.add(value);
            }
        }
        return parts.isEmpty() ? null : String.join(sep, parts);
    }

    /**
     * 相对亮度（0~1，sRGB 线性化前的加权）——只用于「暗场/亮场」定性。
     *
     * @param hex 颜色
     * @return 亮度；非法色值返回 1（按亮处理，不猜暗）
     */
    private static double luminance(String hex) {
        if (hex == null || !hex.trim().matches("^#([0-9A-Fa-f]{6})$")) {
            return 1;
        }
        String h = hex.trim().substring(1);
        int r = Integer.parseInt(h.substring(0, 2), 16);
        int g = Integer.parseInt(h.substring(2, 4), 16);
        int b = Integer.parseInt(h.substring(4, 6), 16);
        return (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0;
    }

    /**
     * 把背景色压暗一档；非合法色值原样返回（不猜）。
     *
     * @param hex 颜色
     * @return 压暗后的颜色
     */
    private static String darken(String hex) {
        if (hex == null || !hex.trim().matches("^#([0-9A-Fa-f]{6})$")) {
            return "#1C1C1C";
        }
        String h = hex.trim().substring(1);
        int r = Integer.parseInt(h.substring(0, 2), 16);
        int g = Integer.parseInt(h.substring(2, 4), 16);
        int b = Integer.parseInt(h.substring(4, 6), 16);
        int factor = 45;
        r = Math.max(12, r * factor / 100);
        g = Math.max(12, g * factor / 100);
        b = Math.max(12, b * factor / 100);
        return String.format("#%02X%02X%02X", r, g, b);
    }

}
