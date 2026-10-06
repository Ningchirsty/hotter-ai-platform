package org.dromara.creative.helper;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 视觉方向的「差异种子」测试（v1 人工测试反馈裁定 ⑤）。
 *
 * <p><b>裁定原文</b>：「可以复现，但是每次生成都要有差异化」——这两件事必须同时成立，
 * 而且都必须能被验证，否则只是一句口号：</p>
 * <ul>
 *   <li><b>可复现</b>：同一颗种子两次生成逐字相同（{@link #sameSeedIsReproducible()}）；</li>
 *   <li><b>有差异</b>：相邻轮次（种子必然不同）出来的三份方向<b>一定</b>不一样
 *       （{@link #everyRoundLooksDifferent()}）——注意这里断言的是"每次都不同"，
 *       而不是"大多数时候不同"：纯随机或哈希取模都会撞，撞了人就以为"重新生成没生效"；</li>
 *   <li><b>变体不越界</b>：种子只换"怎么拍"，不换"拍什么"——背景色/占比/档位/已确认事实
 *       仍然只来自基因与事实（{@link #variantNeverTouchesGeneFacts()}），
 *       参考图实测到的场景也绝不被变体顶替（{@link #measuredSceneSurvivesEveryVariant()}）。</li>
 * </ul>
 *
 * <p>全是纯函数断言：不需要容器、不需要模型、不需要数据库。</p>
 *
 * @author creative
 */
class CreativeDraftFactoryVariantTest {

    /** 差异种子的组合空间（三条取舍各有 4 种拍法） */
    private static final int SPACE = CreativeDraftFactory.directionVariantSpace();

    private static ObjectNode dna(String background, String sceneType) {
        ObjectNode node = VisualDnaSchema.empty();
        node.withObject("/colors").put("primary", "#2E6B4F");
        node.withObject("/colors").put("background", background);
        node.withObject("/lighting").put("type", "SOFT");
        node.withObject("/lighting").put("direction", "FRONT");
        node.put("saturation", "LOW");
        node.put("contrastLevel", "MEDIUM");
        node.put("whitespaceLevel", "HIGH");
        node.put("sceneType", sceneType);
        node.withObject("/productRatio").put("min", 45);
        node.withObject("/productRatio").put("max", 65);
        return node;
    }

    private static Map<String, String> facts(String... kv) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            map.put(kv[i], kv[i + 1]);
        }
        return map;
    }

    /** 把一次生成的全部文案压成一个字符串：只要有一个字不同，比较就会失败 */
    private static String flatten(List<CreativeDraftFactory.DirectionDraft> drafts) {
        StringBuilder sb = new StringBuilder();
        for (CreativeDraftFactory.DirectionDraft draft : drafts) {
            sb.append(draft.code()).append('|').append(draft.name()).append('|')
                .append(draft.concept()).append('|').append(draft.strategy()).append('\n');
        }
        return sb.toString();
    }

    private static List<CreativeDraftFactory.DirectionDraft> drafts(long seed) {
        return CreativeDraftFactory.directions(dna("#F5F5F3", "纯色底"), "鸢尾花",
            facts("product_name", "鸢尾花", "color", "蓝紫渐变", "craft", "UV+喷漆"), seed);
    }

    /** 取某个方向的一个策略维度（如 composition） */
    private static String strategy(List<CreativeDraftFactory.DirectionDraft> list, String code, String key) {
        for (CreativeDraftFactory.DirectionDraft draft : list) {
            if (draft.code().equals(code)) {
                return String.valueOf(draft.strategy().get(key));
            }
        }
        throw new IllegalStateException("没有方向 " + code);
    }

    @Test
    @DisplayName("⑤ 同一颗种子逐字可复现（可回归、可回到当时那一版）")
    void sameSeedIsReproducible() {
        for (long seed : new long[] {0L, 1L, 7L, 23L, 64L, 999L}) {
            assertEquals(flatten(drafts(seed)), flatten(drafts(seed)),
                "种子 " + seed + " 的两次生成必须逐字相同");
        }
        // 不带种子的重载 == 种子 0：所以改造之前的行为可以被逐字回归
        Map<String, String> facts = facts("product_name", "鸢尾花", "color", "蓝紫渐变", "craft", "UV+喷漆");
        ObjectNode dna = dna("#F5F5F3", "纯色底");
        assertEquals(flatten(CreativeDraftFactory.directions(dna, "鸢尾花", facts)),
            flatten(CreativeDraftFactory.directions(dna, "鸢尾花", facts, 0L)),
            "不带种子的重载必须等价于种子 0");
        assertEquals(flatten(drafts(0L)), flatten(CreativeDraftFactory.directions(dna, "鸢尾花", facts)),
            "种子 0 必须与不带种子的重载给出同一份文案");
    }

    @Test
    @DisplayName("⑤ 同一任务相邻轮次一定不同：连着 12 轮，十二份文案两两不同")
    void everyRoundLooksDifferent() {
        Long taskId = 2104582766641799169L;
        Set<String> seen = new LinkedHashSet<>();
        List<Long> seeds = new ArrayList<>();
        for (int batchNo = 1; batchNo <= 12; batchNo++) {
            long seed = CreativeDraftFactory.variantSeed(taskId, batchNo);
            seeds.add(seed);
            String text = flatten(drafts(seed));
            assertTrue(seen.add(text), "第 " + batchNo + " 轮（种子 " + seed + "）与前面某一轮逐字相同："
                + "「重新生成」等于没生效");
        }
        assertEquals(12, seen.size(), "12 轮必须有 12 份不同的文案");
        assertEquals(12, seeds.stream().distinct().count(), "12 轮的种子必须两两不同");
    }

    @Test
    @DisplayName("⑤ 换一个任务就是另一套起点（不同项目的方向不会长得一模一样）")
    void differentProjectsStartFromDifferentVariants() {
        // 取多个相邻的任务ID：如果种子只是"任务ID 取模"，相邻任务会落在相邻变体上，
        // 于是两个项目的方向卡看起来几乎一样。这里要求相邻任务ID至少有一半不是同一个组合。
        int different = 0;
        for (long taskId = 2104582766641799000L; taskId < 2104582766641799010L; taskId++) {
            if (CreativeDraftFactory.variantSeed(taskId, 1) != CreativeDraftFactory.variantSeed(taskId + 1, 1)) {
                different++;
            }
        }
        assertTrue(different >= 5, "相邻任务ID的起点应该有明显区别，实际只有 " + different + "/10");
        assertEquals(CreativeDraftFactory.variantSeed(null, 1),
            CreativeDraftFactory.variantSeed(0L, 1),
            "空的 taskId 按 0 处理，不能抛异常");
    }

    @Test
    @DisplayName("⑤ 种子只换「怎么拍」：基因背景色、产品占比、档位在任何种子下都不变")
    void variantNeverTouchesGeneFacts() {
        for (long seed = 0; seed < SPACE; seed++) {
            List<CreativeDraftFactory.DirectionDraft> list = drafts(seed);
            assertEquals("#F5F5F3", strategy(list, "A", "background"),
                "种子里不该出现「背景色变了」这种事（种子 " + seed + "）");
            assertEquals("45%~65%", strategy(list, "A", "productRatio"), "产品占比来自基因（种子 " + seed + "）");
            // B 的底色是一句固定的暖白（不是基因背景色），但同样不该被种子改动
            assertEquals(strategy(drafts(0L), "B", "background"), strategy(list, "B", "background"),
                "B 的底色不该随着变体改（种子 " + seed + "）");
            // 基因依据必须逐字一致：它是"这条方向是按哪份基因定的"的凭据
            assertEquals(strategy(drafts(0L), "A", "dnaBasis"), strategy(list, "A", "dnaBasis"),
                "基因依据不该随着变体改（种子 " + seed + "）");
            // 三条取舍的骨架不许被种子换掉
            assertEquals("克制影棚", list.get(0).name().split(" · ")[0], "A 永远是克制影棚（种子 " + seed + "）");
            assertEquals("生活代入", list.get(1).name().split(" · ")[0], "B 永远是生活代入（种子 " + seed + "）");
            assertEquals("质感特写", list.get(2).name().split(" · ")[0], "C 永远是质感特写（种子 " + seed + "）");
        }
    }

    @Test
    @DisplayName("⑤ 参考图实测的场景在任何种子下都不会丢；测不出来时照旧说「不猜」")
    void measuredSceneSurvivesEveryVariant() {
        for (long seed = 0; seed < SPACE; seed++) {
            List<CreativeDraftFactory.DirectionDraft> withScene = CreativeDraftFactory.directions(
                dna("#F5F5F3", "影棚纯色底"), "鸢尾花", Map.of(), seed);
            String scene = strategy(withScene, "B", "scene");
            // 第 0 号变体是改造前的原文案（场景写的是固定的"暖白桌面/家居环境"），
            // 其余变体会把"怎么处理场景"写出来——两种写法都必须带上实测场景，
            // 或者至少让概念那一句如实交代实测场景（第 0 号变体就是靠那句）。
            assertTrue(scene.contains("影棚纯色底") || scene.equals("生活场景（暖白桌面/家居环境）"),
                "变体不许把实测场景顶掉（种子 " + seed + "）：" + scene);
            assertTrue(withScene.get(1).concept().contains("影棚纯色底"),
                "B 的概念必须始终写明参考图实测场景（种子 " + seed + "）");
            if (scene.contains("家居化处理")) {
                assertTrue(scene.contains("影棚纯色底"),
                    "既然写成「对实测场景的处理」，就必须把实测场景写出来（种子 " + seed + "）：" + scene);
            }

            List<CreativeDraftFactory.DirectionDraft> noScene = CreativeDraftFactory.directions(
                dna("#F5F5F3", null), "鸢尾花", Map.of(), seed);
            String sceneB = strategy(noScene, "B", "scene");
            assertTrue(sceneB.contains("不猜") || sceneB.equals("生活场景（暖白桌面/家居环境）"),
                "测不出场景时必须说「不猜」（种子 " + seed + "）：" + sceneB);
            assertTrue(noScene.get(1).concept().contains("未测"),
                "测不出场景时概念里要如实写「未测」（种子 " + seed + "）");
        }
    }

    @Test
    @DisplayName("⑤ 变体表本身：三个方向各有 4 种拍法，64 个种子能把 4 种都用到（没有死变体）")
    void everyVariantIsReachable() {
        assertEquals(64, SPACE, "4×4×4＝64");
        Set<String> aCompositions = new LinkedHashSet<>();
        Set<String> bCompositions = new LinkedHashSet<>();
        Set<String> cCompositions = new LinkedHashSet<>();
        for (long seed = 0; seed < SPACE; seed++) {
            List<CreativeDraftFactory.DirectionDraft> list = drafts(seed);
            aCompositions.add(strategy(list, "A", "composition"));
            bCompositions.add(strategy(list, "B", "composition"));
            cCompositions.add(strategy(list, "C", "composition"));
        }
        // 写死的变体表如果有重复项，或者某个变体永远取不到，这里就会少于 4
        assertEquals(CreativeDraftFactory.VARIANTS_PER_DIRECTION, aCompositions.size(), "A 的 4 种拍法都要用得到");
        assertEquals(CreativeDraftFactory.VARIANTS_PER_DIRECTION, bCompositions.size(), "B 的 4 种拍法都要用得到");
        assertEquals(CreativeDraftFactory.VARIANTS_PER_DIRECTION, cCompositions.size(), "C 的 4 种拍法都要用得到");
    }

    @Test
    @DisplayName("⑤ 第 0 号变体就是改造前的原文案（默认输出没有被动过）")
    void firstVariantIsTheLegacyText() {
        List<CreativeDraftFactory.DirectionDraft> list = drafts(0L);
        assertEquals("产品居中、正投影，四周留白均等（留白 高）", strategy(list, "A", "composition"),
            "A 的默认构图应与改造前逐字一致");
        assertEquals("柔光（正面光），无环境光干扰", strategy(list, "A", "lighting"));
        assertEquals("生活场景（暖白桌面/家居环境）", strategy(list, "B", "scene"));
        assertEquals("主题暗场（深色渐变）", strategy(list, "C", "scene"));
        // 第 0 号变体不加"变体说明"，所以概念文案与改造前一致（不含任何多余句子）
        assertFalse(list.get(0).concept().contains("机位改为"), list.get(0).concept());
        // 换个种子就会多出这句说明——"重新生成"因此在页面上是看得见的
        assertNotEquals(list.get(0).concept(), drafts(1L).get(0).concept());
    }
}
