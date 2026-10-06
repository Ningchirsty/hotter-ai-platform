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
import java.util.Set;

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
     * 画面独白是**平台口径载体**的屏类型（V0.2 R29）。
     *
     * <p>主图（MAIN_IMAGE，文档 §9.2）的验收口径来自平台客观要求：1:1 方图、纯色白底、
     * 产品完整不裁不遮、单图不拼版、不叠促销文字。这些要求写在**画面独白**里——它是喂给出图的
     * 画面描述。所以这几种屏的独白不是"创意文案"，而是**生产约束**：让模型润色时把口径改掉，
     * 等于出图提示词里完全没有主图规范（R29 真机第一次验收就是这么翻车的：
     * 模型把独白改写成"展示产品真实质感…"，白底/不裁不遮/不叠字全丢了）。</p>
     *
     * <p>调用方（分镜生成）据此对这几类屏**只采纳模型给的标题/副标题/正文，独白保留代码推导的那句**，
     * 并把未采纳的原因如实记进事件。</p>
     */
    private static final Set<String> PLATFORM_RULE_SCREENS = Set.of(
        "MAIN_WHITE_BG", "MAIN_SELLING_POINT", "MAIN_SCENE", "MAIN_DETAIL", "MAIN_SIZE");

    /**
     * 这一屏的独白是不是平台口径载体（见 {@link #PLATFORM_RULE_SCREENS}）。
     *
     * @param screenType 屏类型（可空）
     * @return true 表示独白由代码保证、不接受模型改写
     */
    public static boolean hasPlatformRules(String screenType) {
        return screenType != null && PLATFORM_RULE_SCREENS.contains(screenType.trim().toUpperCase(Locale.ROOT));
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
     * 生成 3 条视觉方向草稿（不带差异种子，等价于种子 0）。
     *
     * <p>保留这个重载：不关心轮次差异的调用方（含单测）拿到的是**改造前逐字相同**的输出
     * （每个方向的第 0 号变体就是原来的文案）。真要按轮次差异化，请用带种子的重载。</p>
     *
     * @param dna         锁定基因（含参考图实测镜像字段）
     * @param productName 产品名
     * @param facts       已确认事实（字段编码 → 值）
     * @return 3 条方向草稿
     */
    public static List<DirectionDraft> directions(ObjectNode dna, String productName, Map<String, String> facts) {
        return directions(dna, productName, facts, 0L);
    }

    /**
     * 生成 3 条视觉方向草稿（按差异种子取拍法变体）。
     *
     * <p>3 条方向代表 3 种**行业取舍**（克制/代入/质感），这是稳定的业务骨架，任何种子都不改变它；
     * 种子只决定每条取舍**这一次具体怎么拍**（机位、光比、场景处理、氛围），
     * 并且只在"同样忠于锁定基因"的若干种拍法之间选。</p>
     *
     * <p><b>为什么要有种子（v1 裁定 ⑤「可以复现，但每次生成都要有差异化」）</b>：
     * 改造前这里是纯函数——同一份输入永远同一份输出，于是"重新生成方向"看到的还是那三句话，
     * 人无法判断这次重生成到底有没有变化；但直接塞随机数又会毁掉"可复现"
     * （出问题时要能回到当时那一版）。折中是：<b>把差异来源存下来</b>——
     * 种子相同则逐字相同（可复现、可回归测试），种子不同则必然不同（每次生成都有差异）。
     * 种子怎么来见 {@link #variantSeed(Long, int)}。</p>
     *
     * <p><b>种子不改变的事实</b>：背景色/产品占比/档位/已确认事实仍然只来自基因与事实，
     * 变体只在"怎么拍"上做选择；参考图实测场景依旧如实引用，测不出来时依旧说"不猜"。</p>
     *
     * @param dna         锁定基因（含参考图实测镜像字段）
     * @param productName 产品名
     * @param facts       已确认事实（字段编码 → 值）
     * @param variantSeed 差异种子（同种子同输出）
     * @return 3 条方向草稿
     */
    public static List<DirectionDraft> directions(ObjectNode dna, String productName, Map<String, String> facts,
                                                  long variantSeed) {
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

        // 这一次的拍法变体：由种子决定，但**每条取舍各自在自己的变体表里选**，
        // 所以 A 永远是"克制影棚"、B 永远是"生活代入"、C 永远是"质感特写"，不会被种子换掉骨架。
        DirectionVariant va = pick(VARIANT_A, variantSeed, 0);
        DirectionVariant vb = pick(VARIANT_B, variantSeed, 1);
        DirectionVariant vc = pick(VARIANT_C, variantSeed, 2);

        // 基因摘要：让「这条方向是按哪份基因定的」在文案里可核对
        // v1 人工测试反馈：这里原先把档位原样拼进去（「饱和 MEDIUM、对比 MEDIUM、留白 HIGH」），
        // 卡片上于是出现给代码看的枚举值。档位是给人读的，就该说「中/高」。
        String dnaSummary = "主色 " + orDash(primary) + "、背景 " + background
            + "、饱和度 " + levelCn(saturation) + "、对比度 " + levelCn(contrast)
            + "、留白 " + levelCn(whitespace) + "、产品占比 " + ratio;

        List<DirectionDraft> list = new ArrayList<>();
        // 方向 A 的背景处理：变体只说"这块底怎么处理"，主色永远引用基因里的背景色（不另起一套）
        String sceneA = va.scene() == null
            ? "纯色底（沿用基因背景色 " + background + "）"
            : va.scene() + "（主色仍为基因背景色 " + background + "）";
        list.add(new DirectionDraft("A", "克制影棚 · " + light + tone,
            "同一基因下信息最清楚的拍法：以「" + background + "」为底，"
                + lightFull + "均匀铺开，" + density + "，主体占比守在 " + ratio + "。"
                + "适合主图与参数屏——先把「这是什么」说清，再谈氛围。" + note(va),
            strategy(background, sceneA,
                lightFull + "，" + va.lighting(),
                va.composition() + "（留白 " + levelCn(whitespace) + "）",
                va.mood(),
                dnaSummary, sceneType, ratio)));

        list.add(new DirectionDraft("B", "生活代入 · " + sceneLabel(sceneType) + tone,
            "同一基因下最有代入感的拍法：把「" + product + "」放进真实使用环境"
                + (facts != null && StringUtils.isNotBlank(facts.get("color"))
                    ? "（画面里保留已确认的「" + facts.get("color") + "」配色特征）" : "")
                + "，构图走三分位、留白处承接文案。"
                + "参考图实测场景为「" + orDash(sceneType) + "」，本方向据此贴近而非另起一套。" + note(vb),
            strategy("#F7F1E8", sceneB(vb, sceneType),
                vb.lighting(),
                vb.composition() + "（留白 " + levelCn(whitespace) + "）",
                vb.mood(),
                dnaSummary, sceneType, ratio)));

        list.add(new DirectionDraft("C", "质感特写 · " + light + darkTone(background),
            "同一基因下最有质感的拍法：背景压到「" + darken(background) + "」，"
                + LIGHT_WORDS.getOrDefault(up(lightingType), "柔光") + "切小面积、强调材质反射。"
                + "事实里能支撑细节的字段"
                + (detailFact(facts) == null ? "暂缺，请先确认工艺/规格后再定这一屏"
                    : "为「" + detailFact(facts) + "」") + "。" + note(vc),
            strategy(darken(background), vc.scene(),
                vc.lighting(),
                vc.composition(),
                vc.mood(),
                dnaSummary, sceneType, ratio)));

        return list;
    }

    // ------------------------------------------------------------------
    // ⑤ 差异种子与「拍法变体」
    //
    // v1 裁定 ⑤：「重新生成可以复现，但每次生成都要有差异化」。这两件事看似矛盾，
    // 解法只有一条——把"这次为什么长这样"存下来，而不要引入真正的随机：
    //   种子相同 → 逐字相同（可复现、可写回归测试、可回到当时那一版）
    //   种子不同 → 必然不同（"重新生成方向"一定看得到变化）
    //
    // 变体的边界（这也是"不猜"的一部分）：种子只换"怎么拍"（机位/光比/场景处理/氛围），
    // 不换"拍什么"。背景色、产品占比、档位、已确认事实、参考图实测场景仍然是基因与事实说了算；
    // 参考图没测出场景时，变体也不许编一个场景出来（见 sceneB）。
    // ------------------------------------------------------------------

    /** 每条取舍各有多少种拍法变体（4×4×4＝64 种组合，够 64 轮不重样） */
    public static final int VARIANTS_PER_DIRECTION = 4;

    /**
     * 一次「拍法变体」：只描述怎么拍，不含任何产品事实。
     *
     * @param scene       背景/场景的处理方式；{@code null} 表示沿用第 0 号变体的原始口径
     * @param lighting    光线处理（不改变基因的光型，只改变投影与光比的处理）
     * @param composition 构图与机位（后面会自动补上"留白 X"）
     * @param mood        情绪/调性
     * @param note        追加在概念末尾的一句说明；{@code null} 表示不加（第 0 号变体保持逐字不变）
     */
    private record DirectionVariant(String scene, String lighting, String composition,
                                    String mood, String note) {
    }

    /**
     * 方向 A「克制影棚」的拍法变体；第 0 号是改造前的原文案（保证默认输出逐字不变）。
     */
    private static final List<DirectionVariant> VARIANT_A = List.of(
        new DirectionVariant(null, "无环境光干扰",
            "产品居中、正投影，四周留白均等", "专业、克制、以产品为主", null),
        new DirectionVariant("同色系渐变底", "加同色系反光板补暗部，光比压平",
            "45° 微俯，产品居中，留白收在左右两侧", "理性、干净、偏目录感",
            "机位改为 45° 微俯，接近翻目录时的视角。"),
        new DirectionVariant(null, "顶部硬边投影，光比略高",
            "90° 正俯视，产品居中，投影当构图元素", "现代、利落、秩序感",
            "正俯视把投影变成画面里唯一的装饰，信息依旧最清楚。"),
        new DirectionVariant("同色系浅底（主色不变，只提亮明度）", "背面加冷白轮廓光勾边，正面光比不变",
            "侧向平视，产品居中略偏右三分位", "冷静、精致、留白充裕",
            "侧向平视加轮廓光勾边，体块与边缘更分明。"));

    /**
     * 方向 B「生活代入」的拍法变体；第 0 号是改造前的原文案。
     *
     * <p>注意 B 的场景写法（见 {@link #sceneB}）：变体只说"怎么处理场景"，
     * 参考图实测到的场景永远如实引用——原样搬一个家居场景进来会与"贴近参考图"自相矛盾。</p>
     */
    private static final List<DirectionVariant> VARIANT_B = List.of(
        new DirectionVariant(null, "自然光 + 侧光，带柔和投影",
            "产品偏左或偏右三分位，留白处置文案", "温暖、日常、可代入", null),
        new DirectionVariant("窗边家居角落 + 织物", "侧窗自然光作主光，带窗棂投影",
            "俯拍 45°，产品落在左下三分位，右上留白置文案", "松弛、有生活痕迹",
            "改用俯拍 45° 与窗光，画面更像日常随手记录。"),
        new DirectionVariant("晨间桌面（早餐场景）", "自然光 + 逆光轮廓，前景略虚",
            "平视中景，产品居中偏左，右侧留白置文案", "清爽、明亮、有早晨的温度",
            "换成平视中景，把使用状态与场景一起交代。"),
        new DirectionVariant("傍晚居家（暖色台灯环境）", "暖色环境光 + 侧逆光，暗部保留细节",
            "低机位平视，产品落在右三分位，左侧留白置文案", "安静、有归属感、偏夜色",
            "低机位配暖色环境光，把氛围往傍晚推。"));

    /**
     * 方向 C「质感特写」的拍法变体；第 0 号是改造前的原文案。
     */
    private static final List<DirectionVariant> VARIANT_C = List.of(
        new DirectionVariant("主题暗场（深色渐变）", "硬质方向光 + 轮廓光，强调材质反射",
            "局部特写（结构/工艺/材质），大特写裁切", "精致、高级、强调质感", null),
        new DirectionVariant("纯暗底（无渐变）", "单侧硬光，明暗交界线压在结构上",
            "微距特写，取材质最密的一段", "冷峻、克制、像产品摄影棚样张",
            "换成单侧硬光微距，明暗交界线压在结构上。"),
        new DirectionVariant("暗底 + 反光板（局部提亮）", "顶部硬光 + 底部反光板补暗部",
            "中特写，带一点环境交代", "扎实、可信、工艺感强",
            "用顶部硬光配反光板，先把做工讲扎实。"),
        new DirectionVariant("暗底渐变 + 背景光晕", "逆光轮廓 + 前方柔光补面",
            "特写，浅景深虚化背景", "通透、轻盈、材质感清透",
            "逆光轮廓加浅景深，画面更通透。"));

    /**
     * 差异种子的组合空间（A/B/C 三条取舍的变体数之积）。
     *
     * @return 组合数（64）
     */
    public static int directionVariantSpace() {
        int space = 1;
        for (int i = 0; i < 3; i++) {
            space *= VARIANTS_PER_DIRECTION;
        }
        return space;
    }

    /**
     * 由「任务 + 第几轮」推出这一次的差异种子。
     *
     * <p><b>为什么是纯函数</b>：种子必须可复现，而"任务的第 N 轮"是库里已有的事实
     * （{@code batch_no}），所以种子可以由它算出来，不需要额外记一张表；
     * 服务层仍会把算出来的种子写进 {@code dp_visual_direction.variant_seed}，
     * 这样"当时用的是哪个种子"是可查的，不依赖任何人重算。</p>
     *
     * <p><b>为什么相邻轮次一定不同</b>：种子＝"任务基址 + 轮次"，取值再落到组合空间取模。
     * 轮次每次加 1，组合下标也每次移一位——所以「重新生成」在同一个任务内<b>必然</b>换一版，
     * 不会出现"点了重新生成、三个方向跟上一轮一模一样"（纯哈希取模做不到这一点，会撞）。</p>
     *
     * @param taskId  项目ID（可为空）
     * @param batchNo 第几轮（同一任务内从 1 递增）
     * @return 种子
     */
    public static long variantSeed(Long taskId, int batchNo) {
        long base = Math.floorMod(mix64(taskId == null ? 0L : taskId), directionVariantSpace());
        return base + batchNo;
    }

    /**
     * 取某一条取舍这一次的拍法变体。
     *
     * <p>把种子当成一个"组合下标"，按位切给 A/B/C：A 取最低位、B 取次低位、C 取高位。
     * 这样相邻种子一定在 A 上先不同（人一眼就能看出"这次换了"），而 64 轮之后才会回到起点。</p>
     *
     * @param variants 该取舍的变体表
     * @param seed     差异种子
     * @param slot     第几条取舍（0/1/2）
     * @return 变体
     */
    private static DirectionVariant pick(List<DirectionVariant> variants, long seed, int slot) {
        int combo = (int) Math.floorMod(seed, directionVariantSpace());
        int slotBase = 1;
        for (int i = 0; i < slot; i++) {
            slotBase *= VARIANTS_PER_DIRECTION;
        }
        return variants.get((combo / slotBase) % VARIANTS_PER_DIRECTION);
    }

    /**
     * 追加在方向概念末尾的变体说明；第 0 号变体没有说明，输出因此与改造前逐字相同。
     *
     * @param variant 变体
     * @return 说明（可能为空串）
     */
    private static String note(DirectionVariant variant) {
        return variant.note() == null ? "" : variant.note();
    }

    /**
     * 方向 B 的场景写法：变体只提供"场景怎么处理"，参考图实测到的场景如实引用。
     *
     * <p>为什么不能直接把变体里的场景词当成结论：B 的概念里写着"参考图实测场景为「X」，本方向据此贴近"。
     * 如果变体把场景换成"窗边家居角落"，那句话就成了假话（参考图可能测出的是影棚底）。
     * 所以这里明确写成"对实测场景 X 的家居化处理：…"，测不出场景时照旧说"不猜"。</p>
     *
     * @param variant   变体
     * @param sceneType 参考图实测场景（可空）
     * @return 场景描述
     */
    private static String sceneB(DirectionVariant variant, String sceneType) {
        if (variant.scene() == null) {
            return "生活场景（暖白桌面/家居环境）";
        }
        if (StringUtils.isBlank(sceneType)) {
            return "生活场景（参考图未测出场景，不猜；本次按「" + variant.scene() + "」处理）";
        }
        return "生活场景（参考图实测场景「" + sceneType + "」的家居化处理：" + variant.scene() + "）";
    }

    /**
     * 64 位混洗（splitmix64 的收尾步）：把任务ID摊平到组合空间，避免"相邻项目ID只差一点、方向也几乎一样"。
     *
     * @param value 输入
     * @return 混洗结果（纯函数，与时间/机器/进程无关）
     */
    private static long mix64(long value) {
        long z = value + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
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
                    + "与材质都在画面上讲清，不靠一行文案解释；留白 " + levelCn(ctx.whitespace())
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
            // ------------------------------------------------------------------
            // 主图（MAIN_IMAGE，文档 §9.2）的五个屏（R29）
            //
            // 为什么需要专用策略：主图不是"短一点的详情页"——它的验收口径是**平台客观要求**
            // （1:1 方图、白底、不叠促销文字、单图不拼版、主体完整不裁），而此前这五种屏都落到
            // {@link #genericDraft} 的通用兜底文案上（"在骨架契约里没有专用文案策略"），
            // 出图提示词里因此完全没有主图口径。这里按屏类型给出各自的画面要求，
            // 事实字段照旧"有就用、没有就不写"，绝不编造。
            // ------------------------------------------------------------------
            case "MAIN_WHITE_BG" -> new ScreenDraft(screen.type(), screen.label(), screen.productLockLevel(),
                ctx.product() + " · 白底主图",
                firstNonBlank(ctx.spec(), "标准正视角"),
                joinNonBlank("；", ctx.productNameFact(), ctx.color(), ctx.mainVersion()),
                "这一屏要能直接当平台首图：纯白底、产品完整居中、不裁不遮，"
                    + (ctx.color() == null ? "已确认的配色" : "已确认的「" + ctx.color() + "」配色")
                    + "与材质如实呈现；画面上不出现边框、水印与促销文字（主图口径：单图不拼版、不叠字）。"
                    + "主体占比 " + ctx.ratio() + "，四周留白 " + levelCn(ctx.whitespace()) + "。");
            case "MAIN_SELLING_POINT" -> new ScreenDraft(screen.type(), screen.label(), screen.productLockLevel(),
                ctx.product() + " · " + sellingLabel(index == 1 ? ctx.color() : ctx.craft(),
                    index == 1 ? "配色卖点" : "工艺卖点", screen.label()),
                point == null ? null : blank(point.title()),
                point == null ? null : blank(point.content()),
                index == 1
                    ? (point != null && blank(point.content()) != null
                        ? "把卖点「" + blank(point.content()) + "」用画面讲清楚：让产品身上对应的那个特征当主角，"
                            + "文案只做一句话补充、不压住产品主体"
                        : "把第一个卖点用画面讲清楚：用「" + ctx.product() + "」身上最直观的那个特征当主角，"
                            + "文案只做一句话补充、不压住产品主体")
                    : ("这一屏的卖点与上一屏必须在画面上明显不同：换机位、换景别、换背景，"
                        + "别让两张主图看起来是同一张"
                        + (point == null || blank(point.content()) == null
                            ? "" : "；把「" + blank(point.content()) + "」讲成画面")));
            case "MAIN_SCENE" -> new ScreenDraft(screen.type(), screen.label(), screen.productLockLevel(),
                ctx.product() + " · " + sceneLabel(ctx.sceneType()) + "场景",
                null,
                null,
                "把产品放进真实使用环境里：1:1 画幅内交代清楚空间关系与使用状态，"
                    + (StringUtils.isBlank(ctx.sceneType())
                        ? "场景自定（参考图未测出场景，不猜）"
                        : "参考图实测场景为「" + ctx.sceneType() + "」，本屏贴近该场景")
                    + "；光线沿用基因的" + ctx.light() + "，背景以 " + ctx.background() + " 为基调，"
                    + "但产品仍必须是画面主体（占比 " + ctx.ratio() + "），不出现促销文字");
            case "MAIN_DETAIL" -> new ScreenDraft(screen.type(), screen.label(), screen.productLockLevel(),
                ctx.product() + " · 细节",
                firstNonBlank(ctx.craft(), "材质与工艺细节"),
                joinNonBlank("；", ctx.craft()),
                "主图里的细节屏要给出可信的工艺证据："
                    + (ctx.craft() == null
                        ? "把材质纹理、结构接缝、表面处理拍清楚（工艺字段尚未确认，先按画面可辨识为准）"
                        : "把「" + ctx.craft() + "」拍清楚：材质纹理、结构接缝、表面处理经得起看")
                    + "；一张图只讲一个细节，不与其它画面拼版（主图口径）");
            case "MAIN_SIZE" -> new ScreenDraft(screen.type(), screen.label(), screen.productLockLevel(),
                ctx.product() + " · 尺寸",
                firstNonBlank(ctx.spec(), ctx.quantity(), "尺寸与构成"),
                joinNonBlank("；", ctx.spec(), ctx.quantity()),
                "让人一眼读出大小与构成："
                    + (ctx.spec() == null
                        ? "放一个可对照的参照物，比例必须真实（尺寸字段尚未确认，请先确认规格再定这一屏）"
                        : "已确认规格「" + ctx.spec() + "」直接上图，并配可对照的参照物")
                    + "；画面上不叠加促销文字（主图口径）");
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
     * 档位的中文名（LOW/MEDIUM/HIGH → 低/中/高）。
     *
     * <p>v1 人工测试反馈：方向的策略明细与「基因依据」里原样出现 `MEDIUM` / `HIGH`——
     * 那是给代码看的枚举值。档位本身是给人读的，写「中」「高」即可。</p>
     *
     * <p>口径已抽到 {@link CreativeLevelText}：分镜屏规格里的「留白 HIGH」与
     * "没有方向时的光线兜底"也从那里取，避免同一件事三份实现（v1 就是这么漏翻的）。</p>
     *
     * @param level 档位码
     * @return 中文名
     */
    private static String levelCn(String level) {
        return CreativeLevelText.level(level);
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
