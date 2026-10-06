package org.dromara.creative.helper;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 提示词「措辞种子」的测试（v1 人工测试反馈裁定 ⑤）。
 *
 * <p><b>裁定原文</b>：「可以复现，但是每次生成都要有差异化」。对到基因页那一条原文是
 * 「不满足可『重新生成』，点了要出现新提示词」——改造前提示词是基因的纯函数，
 * 『重新生成』若补出来的字段一样，提示词就逐字一样，于是"点了没变化"。</p>
 *
 * <p>本测试钉住三件事，缺一条这个机制就会变成"看着变了、其实变了别的"：</p>
 * <ol>
 *   <li><b>可复现</b>：同一颗种子两次派生逐字相同；不带种子的老调用等于种子 0；</li>
 *   <li><b>有差异</b>：相邻版本（种子必然不同）派生出的提示词必然不同；
 *       但差异只能出现在<b>措辞</b>上——色号、光线、留白、占比、场景、各档位的值必须逐字保留，
 *       而且"先说什么"的顺序之外，负向提示词一个字都不许变（它是禁忌词表，不是文案）；</li>
 *   <li><b>不猜</b>：基因里没有的项在任何变体下都不出现（变体没有"顺手补一句"的权限）。</li>
 * </ol>
 *
 * <p>纯函数断言：不连库、不调模型。</p>
 *
 * @author creative
 */
class DnaPromptVariantTest {

    private final DnaPromptBuilder builder = new DnaPromptBuilder();

    /**
     * 一份"每块都有值"的基因：任何变体都不能弄丢其中任何一项。
     */
    private static ObjectNode fullDna() {
        ObjectNode node = VisualDnaSchema.empty();
        ObjectNode colors = node.withObject("/colors");
        colors.put("primary", "#C8443C");
        colors.put("secondary", "#F2E8E6");
        colors.put("accent", "#8C6239");
        colors.put("background", "#F5F5F3");
        node.withObject("/lighting").put("type", "SOFT");
        node.withObject("/lighting").put("direction", "FRONT");
        node.put("saturation", "LOW");
        node.put("contrastLevel", "MEDIUM");
        node.put("whitespaceLevel", "HIGH");
        node.put("sceneType", "纯色底");
        node.withArray("/styleKeywords").add("现代简约").add("清爽留白");
        node.withArray("/avoidKeywords").add("杂乱背景").add("文字水印");
        node.withObject("/productRatio").put("min", 45);
        node.withObject("/productRatio").put("max", 65);
        return node;
    }

    private String promptOf(long seed) {
        return builder.build(fullDna(), "鸢尾花", "HERO 主图", null, null, null, seed).prompt();
    }

    @Test
    @DisplayName("⑤ 同一颗种子逐字可复现；不带种子的重载等于改造前的原文案")
    void sameSeedIsReproducible() {
        for (long seed : new long[] {0L, 1L, 3L, 5L, 6L, 99L}) {
            assertEquals(promptOf(seed), promptOf(seed), "种子 " + seed + " 两次派生必须逐字相同");
        }
        // 老调用点（不带种子）必须与"种子 0"一致：历史版本、老评测记录因此逐字不变
        String legacy = builder.build(fullDna(), "鸢尾花", "HERO 主图", null, null, null).prompt();
        assertEquals(legacy, promptOf(0L), "不带种子的重载必须等于种子 0");
        // 种子 0 就是改造前的写法（原顺序 + 原引导语），这一条防的是"顺手改了默认文案"
        assertTrue(legacy.startsWith("电商详情页HERO 主图：鸢尾花。整体风格：现代简约、清爽留白；配色："), legacy);
        assertTrue(legacy.contains("光线：柔和散射光、正面光；留白高；"), legacy);
        assertTrue(legacy.contains("产品占画面 45%~65%；场景：纯色底；饱和度低、对比度中；"), legacy);
        assertTrue(legacy.endsWith("产品结构、配色与细节保持与参考图一致，画面干净、主体清晰。"), legacy);
    }

    @Test
    @DisplayName("⑤ 相邻版本（种子 +1）派生出的提示词必然不同，6 套之内两两不同")
    void adjacentVersionsAlwaysDiffer() {
        Set<String> seen = new LinkedHashSet<>();
        for (long seed = 0; seed < DnaPromptBuilder.PROMPT_VARIANT_SPACE; seed++) {
            String text = promptOf(seed);
            assertTrue(seen.add(text),
                "种子 " + seed + " 与前面某套措辞逐字相同——『重新生成』等于没生效：" + text);
        }
        assertEquals(DnaPromptBuilder.PROMPT_VARIANT_SPACE, seen.size(), "6 套措辞必须两两不同");

        // 任务 + 版本 → 种子：同一版本必然同一套，相邻版本必然不同套（这就是"可复现 + 每次不同"）
        Long taskId = 2104582766641799169L;
        Set<String> byVersion = new LinkedHashSet<>();
        for (int version = 1; version <= 6; version++) {
            byVersion.add(promptOf(DnaPromptBuilder.variantSeed(taskId, version)));
        }
        assertEquals(6, byVersion.size(), "同一任务的 6 个相邻版本必须给出 6 套不同的措辞");
        assertEquals(promptOf(DnaPromptBuilder.variantSeed(taskId, 3)),
            promptOf(DnaPromptBuilder.variantSeed(taskId, 3)),
            "同一版本再派生一次必须逐字相同");
        // 空 taskId 不许抛异常（没有项目上下文时的兜底）
        assertEquals(DnaPromptBuilder.variantSeed(null, 1), DnaPromptBuilder.variantSeed(0L, 1));
    }

    @Test
    @DisplayName("⑤ 变体只换措辞：色号、光线、留白、占比、场景、档位在任何种子下都逐字保留")
    void variantKeepsEveryValue() {
        String[] mustKeep = {
            "主色 #C8443C", "辅色 #F2E8E6", "点缀色 #8C6239", "背景 #F5F5F3",
            "柔和散射光", "正面光", "45%~65%", "现代简约", "清爽留白"
        };
        for (long seed = 0; seed < DnaPromptBuilder.PROMPT_VARIANT_SPACE; seed++) {
            String text = promptOf(seed);
            for (String token : mustKeep) {
                assertTrue(text.contains(token),
                    "种子 " + seed + " 的提示词丢了「" + token + "」：" + text);
            }
            // 场景：两种引导语各认一种，但场景值必须出现（值不许丢）
            assertTrue(text.contains("场景：纯色底") || text.contains("使用场景：纯色底"),
                "场景丢了吗（种子 " + seed + "）：" + text);
            // 留白与档位：两种写法各认一种，但必须出现其一（值不许丢）
            assertTrue(text.contains("留白高") || text.contains("画面留白：高"), "留白丢了吗（种子 " + seed + "）：" + text);
            assertTrue(text.contains("饱和度低") || text.contains("低饱和"),
                "饱和度丢了吗（种子 " + seed + "）：" + text);
            assertTrue(text.contains("对比度中") || text.contains("中对比"),
                "对比度丢了吗（种子 " + seed + "）：" + text);
            // 结尾那句"与参考图一致"的要求也必须一直都在（它是最容易在重排时被漏掉的）
            assertTrue(text.contains("与参考图一致"), "结尾要求丢了（种子 " + seed + "）：" + text);
            // 每套措辞都要以"这一张图是什么"开头：变体不许把主体句挤走
            assertTrue(text.startsWith("电商详情页HERO 主图：鸢尾花。"),
                "首句必须是主体句（种子 " + seed + "）：" + text);
        }
    }

    @Test
    @DisplayName("⑤ 负向提示词不受措辞种子影响（它是禁忌词表，不是文案）")
    void negativePromptIsNotRephrased() {
        String baseline = builder.build(fullDna(), "鸢尾花", "HERO 主图", null, null, null, 0L)
            .negativePrompt();
        for (long seed = 0; seed < DnaPromptBuilder.PROMPT_VARIANT_SPACE; seed++) {
            assertEquals(baseline, builder.build(fullDna(), "鸢尾花", "HERO 主图", null, null, null, seed)
                .negativePrompt(), "负向提示词不该随措辞变（种子 " + seed + "）");
        }
    }

    @Test
    @DisplayName("⑤ 不猜：基因里没有的项，在任何变体下都不出现")
    void neverFabricatesInAnyVariant() {
        ObjectNode empty = VisualDnaSchema.empty();
        for (long seed = 0; seed < DnaPromptBuilder.PROMPT_VARIANT_SPACE; seed++) {
            DnaPromptBuilder.Prompt prompt =
                builder.build(empty, "鸢尾花", null, null, null, null, seed);
            String text = prompt.prompt();
            for (String absent : new String[] {"场景：", "使用场景：", "产品占画面", "主体占比", "null"}) {
                assertFalse(text.contains(absent),
                    "基因里没有这一项就不该出现「" + absent + "」（种子 " + seed + "）：" + text);
            }
            // 留白这一项要单独判：风格兜底文案里本来就有「清爽留白」这个词，
            // 所以只能判"留白档位块"有没有出现（留白高/中/低，或第二套写法的「画面留白：」）
            for (String level : new String[] {"留白高", "留白中", "留白低", "画面留白："}) {
                assertFalse(text.contains(level),
                    "基因里没有留白档位就不该出现「" + level + "」（种子 " + seed + "）：" + text);
            }
            // 风格有内置兜底（否则提示词会变成半句话），这一条是"有兜底但不编造"的边界
            assertTrue(text.contains("现代简约、清爽留白、商业摄影"), text);
        }
    }

    @Test
    @DisplayName("⑤ 人工改写的提示词优先于措辞变体（③ 与 ⑤ 不互相打架）")
    void promptOverrideWins() {
        ObjectNode node = fullDna();
        ObjectNode override = node.withObject("/promptOverride");
        override.put("positive", "人工写的正向提示词");
        override.put("negative", "人工写的负向提示词");
        for (long seed = 0; seed < DnaPromptBuilder.PROMPT_VARIANT_SPACE; seed++) {
            DnaPromptBuilder.Prompt prompt =
                builder.build(node, "鸢尾花", "HERO 主图", null, null, null, seed);
            assertEquals("人工写的正向提示词", prompt.prompt(), "人工改写必须是唯一权威（种子 " + seed + "）");
            assertEquals("人工写的负向提示词", prompt.negativePrompt());
            assertTrue(prompt.applied().contains("promptOverride(人工改写)"));
        }
        assertNotEquals(promptOf(0L), promptOf(1L));
    }
}
