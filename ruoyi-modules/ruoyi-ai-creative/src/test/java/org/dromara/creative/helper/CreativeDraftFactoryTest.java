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

        // 画面独白必须逐屏不同：7 屏 7 句（R3 之前是同一句反复出现）
        List<CreativeDraftFactory.ScreenDraft> drafts = CreativeDraftFactory.screens(dna, "鸢尾花", Map.of());
        List<String> solos = new ArrayList<>();
        for (CreativeDraftFactory.ScreenDraft draft : drafts) {
            solos.add(draft.soloStatement());
        }
        assertEquals(7, solos.size(), "分镜固定 7 屏（业务骨架不变）");
        assertEquals(7, solos.stream().distinct().count(),
            "7 屏的画面独白必须两两不同，这正是「70 行只有 7 句」问题的修复点");
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

}
