package org.dromara.creative.helper;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 参数化草稿工厂测试：钉住「去固化」的两条性质。
 *
 * <p>背景（问题 3）：生产库 {@code dp_visual_direction} 27 行只有 3 个不同名称、
 * {@code dp_storyboard_screen} 70 行只有 7 句不同画面独白——刷新永远一样。
 * 修复后必须同时满足：</p>
 * <ul>
 *   <li><b>不同输入必然不同</b>（换基因/换事实/换产品 → 文案变化）；</li>
 *   <li><b>同输入完全可复现</b>（同一份输入两次调用结果逐字相同，便于对比「改了什么」）。</li>
 * </ul>
 *
 * <p>这些断言全部是纯函数级的，不需要容器、不需要模型、不需要数据库——
 * 它们能失败的空间只有「输入没被真正用起来」这一种。</p>
 *
 * @author creative
 */
class CreativeDraftFactoryTest {

    /**
     * 构造一份 DNA：参数即断言用的自变量。
     */
    private static ObjectNode dna(String background, String primary, String lightingType,
                                  String lightingDir, String saturation, String contrast,
                                  String whitespace, String sceneType, int ratioMin, int ratioMax) {
        ObjectNode node = VisualDnaSchema.empty();
        ObjectNode colors = node.withObject("/colors");
        colors.put("primary", primary);
        colors.put("background", background);
        ObjectNode lighting = node.withObject("/lighting");
        lighting.put("type", lightingType);
        lighting.put("direction", lightingDir);
        node.put("saturation", saturation);
        node.put("contrastLevel", contrast);
        node.put("whitespaceLevel", whitespace);
        node.put("sceneType", sceneType);
        node.withObject("/productRatio").put("min", ratioMin);
        node.withObject("/productRatio").put("max", ratioMax);
        return node;
    }

    private static Map<String, String> facts(String... kv) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            map.put(kv[i], kv[i + 1]);
        }
        return map;
    }

    private static String flatten(List<CreativeDraftFactory.DirectionDraft> drafts) {
        StringBuilder sb = new StringBuilder();
        for (CreativeDraftFactory.DirectionDraft draft : drafts) {
            sb.append(draft.code()).append('|').append(draft.name()).append('|')
                .append(draft.concept()).append('|').append(draft.strategy()).append('\n');
        }
        return sb.toString();
    }

    private static String flattenScreens(List<CreativeDraftFactory.ScreenDraft> drafts) {
        StringBuilder sb = new StringBuilder();
        for (CreativeDraftFactory.ScreenDraft draft : drafts) {
            sb.append(draft.type()).append('|').append(draft.title()).append('|')
                .append(nullSafe(draft.subtitle())).append('|').append(nullSafe(draft.bodyText()))
                .append('|').append(draft.soloStatement()).append('\n');
        }
        return sb.toString();
    }

    /**
     * 空字段在拼串时写成明确占位符。
     *
     * <p>副标题/正文为空是<b>合法</b>的业务状态（不是所有屏都需要正文），
     * 但如果直接 append(null) 会在文本里出现字面 "null"，让「不能出现 null」这条断言失真。
     * 因此这里显式渲染占位符，断言才指向真正的编造问题。</p>
     *
     * @param value 文本
     * @return 占位后的文本
     */
    private static String nullSafe(String value) {
        return value == null ? "∅" : value;
    }

    @Test
    @DisplayName("同输入完全可复现：两次调用逐字相同（不引入随机、不看时间）")
    void sameInputIsReproducible() {
        ObjectNode dna = dna("#F5F5F3", "#2E6B4F", "SOFT", "FRONT", "LOW", "MEDIUM", "HIGH",
            "纯色底", 45, 65);
        Map<String, String> facts = facts("product_name", "鸢尾花", "color", "蓝紫渐变",
            "craft", "UV+喷漆", "spec_params", "257.60*149.30");

        assertEquals(flatten(CreativeDraftFactory.directions(dna, "鸢尾花", facts)),
            flatten(CreativeDraftFactory.directions(dna, "鸢尾花", facts)),
            "同一份输入两次生成的方向必须逐字相同");
        assertEquals(flattenScreens(CreativeDraftFactory.screens(dna, "鸢尾花", facts)),
            flattenScreens(CreativeDraftFactory.screens(dna, "鸢尾花", facts)),
            "同一份输入两次生成的分镜必须逐字相同");
    }

    @Test
    @DisplayName("档位写中文：方向文案里不出现 MEDIUM / HIGH 这种给代码看的枚举（v1 反馈）")
    void levelsAreWrittenInChinese() {
        // 三个档位故意取三个不同的值，任何一个漏翻都会在下面的断言里露出来
        ObjectNode dna = dna("#F5F5F3", "#2E6B4F", "SOFT", "FRONT", "MEDIUM", "HIGH", "LOW",
            "纯色底", 45, 65);
        String text = flatten(CreativeDraftFactory.directions(dna, "鸢尾花", facts("product_name", "鸢尾花")));

        assertFalse(text.contains("MEDIUM"), "方向文案里不该出现枚举 MEDIUM：\n" + text);
        assertFalse(text.contains("HIGH"), "方向文案里不该出现枚举 HIGH：\n" + text);
        assertFalse(text.contains("LOW"), "方向文案里不该出现枚举 LOW：\n" + text);
        // 换成中文之后信息不能丢：三个档位都要读得出来
        assertTrue(text.contains("饱和度 中"), "饱和度档应写成中文「中」：\n" + text);
        assertTrue(text.contains("对比度 高"), "对比度档应写成中文「高」：\n" + text);
        assertTrue(text.contains("留白 低"), "留白档应写成中文「低」：\n" + text);
        // 基因里档位缺省时也不能写成"中"（那是编造），要明确说未设置
        ObjectNode blank = dna("#F5F5F3", "#2E6B4F", "SOFT", "FRONT", null, null, null,
            "纯色底", 45, 65);
        String blankText = flatten(CreativeDraftFactory.directions(blank, "鸢尾花", Map.of()));
        assertTrue(blankText.contains("饱和度 未设置"), "缺档位要说「未设置」，不能默认成「中」：\n" + blankText);
    }

    @Test
    @DisplayName("换基因必然换文案：背景/光型/场景/密度不同 → 方向名与分镜独白都不同")
    void differentDnaYieldsDifferentText() {
        Map<String, String> facts = facts("product_name", "鸢尾花", "color", "蓝紫渐变");
        ObjectNode a = dna("#F5F5F3", "#2E6B4F", "SOFT", "FRONT", "LOW", "MEDIUM", "HIGH",
            "纯色底", 45, 65);
        ObjectNode b = dna("#1C1C1C", "#C0392B", "HARD", "BACK", "HIGH", "HIGH", "LOW",
            "主题场景", 70, 88);

        String directionsA = flatten(CreativeDraftFactory.directions(a, "鸢尾花", facts));
        String directionsB = flatten(CreativeDraftFactory.directions(b, "鸢尾花", facts));
        assertNotEquals(directionsA, directionsB, "换了基因，方向文案必须变（否则仍是固化模板）");

        String screensA = flattenScreens(CreativeDraftFactory.screens(a, "鸢尾花", facts));
        String screensB = flattenScreens(CreativeDraftFactory.screens(b, "鸢尾花", facts));
        assertNotEquals(screensA, screensB, "换了基因，分镜文案必须变");
    }

    @Test
    @DisplayName("换事实必然换文案：画面独白不再是固定 7 句")
    void differentFactsYieldDifferentText() {
        ObjectNode dna = dna("#F5F5F3", "#2E6B4F", "SOFT", "FRONT", "LOW", "MEDIUM", "HIGH",
            "纯色底", 45, 65);
        String withFacts = flattenScreens(CreativeDraftFactory.screens(dna, "鸢尾花",
            facts("product_name", "鸢尾花", "color", "蓝紫渐变", "craft", "UV+喷漆",
                "spec_params", "257.60*149.30", "quantity", "1 支")));
        String withoutFacts = flattenScreens(CreativeDraftFactory.screens(dna, "鸢尾花", Map.of()));
        assertNotEquals(withFacts, withoutFacts, "有没有已确认事实，分镜文案必须不同");

        // 画面独白必须逐屏不同（R3 之前是同一句反复出现）
        // 断言的是「结构不变量」而不是「7 屏」这个具体数字：屏数属于可配置项（V0.2 多场景），
        // 写死 7 会在去掉 7 屏硬编码后变成假失败，而真正要守住的是下面这几条。
        List<CreativeDraftFactory.ScreenDraft> drafts = CreativeDraftFactory.screens(dna, "鸢尾花", Map.of());
        List<String> solos = new ArrayList<>();
        for (CreativeDraftFactory.ScreenDraft draft : drafts) {
            solos.add(draft.soloStatement());
        }
        assertTrue(solos.size() >= 3, "业务骨架至少要有 开篇/内容/收尾 三类屏，实际 " + solos.size());
        assertEquals(solos.size(), solos.stream().distinct().count(),
            "每一屏的画面独白必须两两不同，这正是「70 行只有 7 句」问题的修复点");
        assertEquals("HERO", drafts.get(0).type(), "第一屏必须是主图屏");
        assertEquals("BRAND", drafts.get(drafts.size() - 1).type(), "最后一屏必须是品牌收尾屏");
        for (CreativeDraftFactory.ScreenDraft draft : drafts) {
            assertTrue(draft.type() != null && !draft.type().isBlank(), "屏类型不能为空");
            assertTrue(draft.label() != null && !draft.label().isBlank(), "屏名称不能为空");
        }
    }

    @Test
    @DisplayName("不猜：没有事实时不编造，也不把 null 写进文案")
    void neverFabricates() {
        ObjectNode dna = dna("#F5F5F3", "#2E6B4F", "SOFT", "FRONT", "LOW", "MEDIUM", "HIGH",
            null, 45, 65);
        List<CreativeDraftFactory.DirectionDraft> directions =
            CreativeDraftFactory.directions(dna, null, Map.of());
        List<CreativeDraftFactory.ScreenDraft> screens =
            CreativeDraftFactory.screens(dna, null, Map.of());

        // 只有出现在人眼前的文案字段才做「不含 null」断言；空副标题/空正文是合法业务状态
        for (CreativeDraftFactory.DirectionDraft draft : directions) {
            assertFalse(draft.name().contains("null"), "方向名不能出现 null");
            assertFalse(draft.concept().contains("null"), "方向说明不能出现 null");
        }
        for (CreativeDraftFactory.ScreenDraft draft : screens) {
            assertFalse(draft.title().contains("null"), "分镜标题不能出现 null");
            assertFalse(draft.soloStatement().contains("null"), "画面独白不能出现 null");
        }
        assertTrue(flatten(directions).contains("未测"),
            "测不出来的场景要如实写「未测」而不是编一个");
    }

    @Test
    @DisplayName("每个字都由输入决定：只改一个参数，文案就会变")
    void singleParameterChangeIsVisible() {
        Map<String, String> facts = facts("product_name", "鸢尾花", "color", "蓝紫渐变");
        ObjectNode base = dna("#F5F5F3", "#2E6B4F", "SOFT", "FRONT", "LOW", "MEDIUM", "HIGH",
            "纯色底", 45, 65);
        // 只把留白从 HIGH 改成 LOW
        ObjectNode changed = dna("#F5F5F3", "#2E6B4F", "SOFT", "FRONT", "LOW", "MEDIUM", "LOW",
            "纯色底", 45, 65);
        assertNotEquals(flatten(CreativeDraftFactory.directions(base, "鸢尾花", facts)),
            flatten(CreativeDraftFactory.directions(changed, "鸢尾花", facts)),
            "留白档位变了，方向文案应随之变化");
    }

    // ------------------------------------------------------------------
    // C′：屏骨架可配置（去固定 7 屏）
    // ------------------------------------------------------------------

    private static CreativeScreenSkeleton.ScreenSpec spec(String type, String label, String level) {
        return new CreativeScreenSkeleton.ScreenSpec(type, label, level, "中景");
    }

    @Test
    @DisplayName("C′：骨架有几屏就出几屏——3 屏骨架（改造前只会出 7 屏）")
    void threeScreenSkeletonYieldsThreeDrafts() {
        CreativeScreenSkeleton skeleton = CreativeScreenSkeleton.of(
            List.of(spec("HERO", "主图", "STRICT"),
                spec("DETAIL", "细节工艺", "STRICT"),
                spec("BRAND", "品牌收尾", "LOOSE")), Map.of());
        ObjectNode dna = dna("#F5F5F3", "#2E6B4F", "SOFT", "FRONT", "LOW", "MEDIUM", "HIGH",
            "纯色底", 45, 65);
        List<CreativeDraftFactory.ScreenDraft> drafts =
            CreativeDraftFactory.screens(skeleton, dna, "鸢尾花", facts("product_name", "鸢尾花"), null, List.of());

        assertEquals(3, drafts.size(), "3 屏骨架必须只出 3 条草稿");
        assertEquals(List.of("HERO", "DETAIL", "BRAND"),
            drafts.stream().map(CreativeDraftFactory.ScreenDraft::type).toList());
        assertEquals(List.of("STRICT", "STRICT", "LOOSE"),
            drafts.stream().map(CreativeDraftFactory.ScreenDraft::productLockLevel).toList(),
            "保真等级要跟着契约走（同一类型在不同契约里可以不同）");
        for (CreativeDraftFactory.ScreenDraft draft : drafts) {
            assertTrue(draft.title() != null && !draft.title().isBlank(), "每屏都要有标题");
            assertTrue(draft.soloStatement() != null && !draft.soloStatement().isBlank(), "每屏都要有画面独白");
        }
    }

    @Test
    @DisplayName("C′：9 屏骨架（含 4 个卖点屏 + 一个未知类型）也能出满 9 屏且独白两两不同")
    void nineScreenSkeletonWithUnknownTypeStillWorks() {
        CreativeScreenSkeleton skeleton = CreativeScreenSkeleton.of(
            List.of(spec("HERO", "主图", "STRICT"),
                spec("SELLING_POINT", "卖点一", "LOOSE"),
                spec("SELLING_POINT", "卖点二", "LOOSE"),
                spec("SELLING_POINT", "卖点三", "LOOSE"),
                spec("SELLING_POINT", "卖点四", "LOOSE"),
                spec("SCENE", "使用场景", "LOOSE"),
                spec("DETAIL", "细节工艺", "STRICT"),
                spec("SIZE", "尺寸参数", "STRICT"),
                spec("CERT", "资质认证", "STRICT")), Map.of());
        ObjectNode dna = dna("#F5F5F3", "#2E6B4F", "SOFT", "FRONT", "LOW", "MEDIUM", "HIGH",
            "纯色底", 45, 65);
        List<CreativeDraftFactory.CopyHint> points = List.of(
            new CreativeDraftFactory.CopyHint("卖点A", "A 的说明"),
            new CreativeDraftFactory.CopyHint("卖点B", "B 的说明"),
            new CreativeDraftFactory.CopyHint("卖点C", "C 的说明"),
            new CreativeDraftFactory.CopyHint("卖点D", "D 的说明"));
        List<CreativeDraftFactory.ScreenDraft> drafts = CreativeDraftFactory.screens(
            skeleton, dna, "鸢尾花", facts("product_name", "鸢尾花", "color", "蓝紫渐变"), null, points);

        assertEquals(9, drafts.size(), "契约有 9 屏就出 9 条草稿");
        // 第 1~4 个卖点屏分别拿到第 1~4 条卖点块（这是"同类型多屏按出现顺序取"的语义）
        assertEquals(List.of("卖点A", "卖点B", "卖点C", "卖点D"),
            drafts.stream().filter(d -> d.type().equals("SELLING_POINT"))
                .map(CreativeDraftFactory.ScreenDraft::subtitle).toList());
        // 未知类型不能抛异常、不能少屏：走兜底草稿，标题/独白非空且写明它没有专用策略
        CreativeDraftFactory.ScreenDraft cert = drafts.get(8);
        assertEquals("CERT", cert.type());
        assertEquals("资质认证", cert.label(), "展示名来自契约");
        assertFalse(cert.soloStatement().isBlank());
        assertTrue(cert.soloStatement().contains("没有专用文案策略"), cert.soloStatement());
        // 逐屏独白两两不同
        assertEquals(drafts.size(),
            drafts.stream().map(CreativeDraftFactory.ScreenDraft::soloStatement).distinct().count(),
            "9 屏的画面独白必须两两不同");
    }

    @Test
    @DisplayName("C′：展示名与保真等级都取自契约（改契约就改文案骨架，不用改 Java）")
    void labelsAndLockLevelsComeFromContract() {
        CreativeScreenSkeleton skeleton = CreativeScreenSkeleton.of(
            List.of(spec("HERO", "封面主图", "LOOSE"),
                spec("BRAND", "品牌落版", "STRICT")), Map.of());
        ObjectNode dna = dna("#F5F5F3", "#2E6B4F", "SOFT", "FRONT", "LOW", "MEDIUM", "HIGH",
            "纯色底", 45, 65);
        List<CreativeDraftFactory.ScreenDraft> drafts =
            CreativeDraftFactory.screens(skeleton, dna, "鸢尾花", Map.of(), null, List.of());
        assertEquals(List.of("封面主图", "品牌落版"),
            drafts.stream().map(CreativeDraftFactory.ScreenDraft::label).toList());
        assertEquals(List.of("LOOSE", "STRICT"),
            drafts.stream().map(CreativeDraftFactory.ScreenDraft::productLockLevel).toList());
    }

    @Test
    @DisplayName("C′：默认骨架（未指定）仍出 7 屏，且与改造前的屏类型序列一致")
    void defaultSkeletonStillSevenScreens() {
        ObjectNode dna = dna("#F5F5F3", "#2E6B4F", "SOFT", "FRONT", "LOW", "MEDIUM", "HIGH",
            "纯色底", 45, 65);
        List<CreativeDraftFactory.ScreenDraft> drafts =
            CreativeDraftFactory.screens(dna, "鸢尾花", facts("product_name", "鸢尾花"));
        assertEquals(7, drafts.size(), "默认契约仍为 7 屏（生产行为不变）");
        assertEquals(List.of("HERO", "SELLING_POINT", "SELLING_POINT", "SCENE", "DETAIL", "SIZE", "BRAND"),
            drafts.stream().map(CreativeDraftFactory.ScreenDraft::type).toList());
        assertEquals(List.of("主图", "卖点一", "卖点二", "使用场景", "细节工艺", "尺寸参数", "品牌收尾"),
            drafts.stream().map(CreativeDraftFactory.ScreenDraft::label).toList());
    }

    // ------------------------------------------------------------------
    // R29：主图（MAIN_IMAGE，文档 §9.2）五个屏的专用文案
    //
    // 改造前这五种屏类型都落到通用兜底（独白里写着"没有专用文案策略"），
    // 于是喂给出图的画面描述里完全没有主图口径（1:1、白底、不叠促销文字、不拼版）。
    // 下面的断言只钉两件事：① 走的是专用策略（不再是兜底）；② 主图口径确实写进了独白。
    // ------------------------------------------------------------------

    private static CreativeScreenSkeleton mainImageSkeleton() {
        return CreativeScreenSkeleton.of(
            List.of(spec("MAIN_WHITE_BG", "白底主图", "STRICT"),
                spec("MAIN_SELLING_POINT", "卖点图", "LOOSE"),
                spec("MAIN_SCENE", "场景图", "LOOSE"),
                spec("MAIN_DETAIL", "细节图", "STRICT"),
                spec("MAIN_SIZE", "尺寸图", "STRICT")), Map.of());
    }

    @Test
    @DisplayName("R29：主图五屏各有专用文案策略（不再落通用兜底），且写明主图口径")
    void mainImageScreensHaveDedicatedCopy() {
        ObjectNode dna = dna("#FFFFFF", "#2E6B4F", "SOFT", "FRONT", "LOW", "MEDIUM", "HIGH",
            "纯色底", 60, 80);
        List<CreativeDraftFactory.CopyHint> points =
            List.of(new CreativeDraftFactory.CopyHint("卖点A", "手工吹制、每只纹理不同"));
        List<CreativeDraftFactory.ScreenDraft> drafts = CreativeDraftFactory.screens(
            mainImageSkeleton(), dna, "鸢尾花",
            facts("product_name", "鸢尾花", "color", "蓝紫渐变", "craft", "手工吹制", "spec_params", "高 28cm"),
            null, points);

        assertEquals(5, drafts.size());
        for (CreativeDraftFactory.ScreenDraft draft : drafts) {
            assertFalse(draft.soloStatement().contains("没有专用文案策略"),
                draft.type() + " 应该走主图专用策略：" + draft.soloStatement());
            assertTrue(draft.title().contains("鸢尾花"), "标题要带产品名：" + draft.title());
        }

        // 白底主图：平台首图口径（白底、不裁不遮、不叠促销文字、不拼版）
        String white = drafts.get(0).soloStatement();
        assertTrue(white.contains("白底") && white.contains("不裁不遮"), white);
        assertTrue(white.contains("边框") && white.contains("水印") && white.contains("促销文字"), white);
        // 卖点图：拿到模块配的卖点块，并要求"文案不压主体"
        assertEquals("卖点A", drafts.get(1).subtitle());
        assertEquals("手工吹制、每只纹理不同", drafts.get(1).bodyText());
        assertTrue(drafts.get(1).soloStatement().contains("不压住产品主体"), drafts.get(1).soloStatement());
        // 场景图 / 细节图 / 尺寸图：各自讲各自的事，且都提"主图口径"
        assertTrue(drafts.get(2).soloStatement().contains("纯色底"), drafts.get(2).soloStatement());
        assertTrue(drafts.get(3).soloStatement().contains("手工吹制"), drafts.get(3).soloStatement());
        assertTrue(drafts.get(3).soloStatement().contains("不与其它画面拼版"), drafts.get(3).soloStatement());
        assertTrue(drafts.get(4).soloStatement().contains("高 28cm"), drafts.get(4).soloStatement());
        assertTrue(drafts.get(4).soloStatement().contains("不叠加促销文字"), drafts.get(4).soloStatement());
    }

    @Test
    @DisplayName("R29：主图五屏的独白是平台口径载体（模型润色不许改写它）")
    void mainImageScreensCarryPlatformRules() {
        for (String type : List.of("MAIN_WHITE_BG", "MAIN_SELLING_POINT", "MAIN_SCENE",
            "MAIN_DETAIL", "MAIN_SIZE")) {
            assertTrue(CreativeDraftFactory.hasPlatformRules(type), type + " 的独白应由代码保证");
            assertTrue(CreativeDraftFactory.hasPlatformRules(type.toLowerCase()),
                "大小写不该影响判断：" + type);
        }
        // 其它屏不是口径载体：它们的独白可以接受模型改写（创意文案）
        for (String type : List.of("HERO", "SELLING_POINT", "SCENE", "DETAIL", "SIZE", "BRAND", "CERT")) {
            assertFalse(CreativeDraftFactory.hasPlatformRules(type), type + " 不该被当成口径屏");
        }
        assertFalse(CreativeDraftFactory.hasPlatformRules(null));
        assertFalse(CreativeDraftFactory.hasPlatformRules("  "));
    }

    @Test
    @DisplayName("R29：主图事实缺失时不编造——只说「先确认」，并且仍不落兜底")
    void mainImageCopyWithoutFactsDoesNotInvent() {
        ObjectNode dna = dna("#FFFFFF", null, "SOFT", "FRONT", "LOW", "MEDIUM", "HIGH",
            null, 60, 80);
        List<CreativeDraftFactory.ScreenDraft> drafts = CreativeDraftFactory.screens(
            mainImageSkeleton(), dna, "鸢尾花", Map.of(), null, List.of());

        String scene = drafts.get(2).soloStatement();
        assertTrue(scene.contains("不猜"), "参考图没测出场景时不许编一个场景：" + scene);
        String detail = drafts.get(3).soloStatement();
        assertTrue(detail.contains("尚未确认"), detail);
        String size = drafts.get(4).soloStatement();
        assertTrue(size.contains("尚未确认"), size);
        for (CreativeDraftFactory.ScreenDraft draft : drafts) {
            assertFalse(draft.soloStatement().contains("没有专用文案策略"), draft.type());
        }
    }

}
