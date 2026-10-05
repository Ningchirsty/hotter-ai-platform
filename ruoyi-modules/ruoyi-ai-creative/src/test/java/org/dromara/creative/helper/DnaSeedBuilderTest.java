package org.dromara.creative.helper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 种子基因「默认值」那条证据链的措辞测试（v1 人工测试反馈：证据链里印着 MEDIUM/HIGH/SOFT）。
 *
 * <p>这一行是"默认值一眼看全"的摘要，原先直接写死
 * {@code MEDIUM/MEDIUM/HIGH/纯色底/SOFT+FRONT/45~65%}——给代码看的枚举串。
 * 现在值从刚构造出来的 dna 上读回来、按 {@link CreativeLevelText} 写中文，
 * 所以这里同时钉住"中文"与"与 dna 一致"两件事。</p>
 *
 * @author creative
 */
class DnaSeedBuilderTest {

    private static final DnaSeedBuilder BUILDER = new DnaSeedBuilder();

    /** 只给最小输入：不填任何事实，走全默认值分支 */
    private static ObjectNode seed() {
        return BUILDER.build(new DnaSeedBuilder.SeedInput(
            1L, "鸢尾花", "SKU-1", List.of(), List.of("ref-A.png"))).dna();
    }

    private static String defaultEvidenceValue(ObjectNode dna) {
        for (JsonNode node : dna.path("evidence")) {
            if ("DEFAULT".equals(node.path("kind").asText())) {
                return node.path("value").asText();
            }
        }
        return null;
    }

    @Test
    @DisplayName("默认值摘要写中文档位，且不出现 MEDIUM/HIGH/SOFT/FRONT 这些枚举")
    void defaultEvidenceUsesChineseLevels() {
        String value = defaultEvidenceValue(seed());
        assertNotNull(value, "默认值那条证据必须存在");
        for (String code : List.of("MEDIUM", "HIGH", "LOW", "SOFT", "HARD", "FRONT", "SIDE")) {
            assertFalse(value.contains(code), "默认值摘要里不该出现枚举 " + code + "：" + value);
        }
        assertTrue(value.startsWith("中/中/高/"), "饱和度/对比度/留白 应为 中/中/高：" + value);
        assertTrue(value.contains("柔光+正面光"), "光线应为「柔光+正面光」：" + value);
        assertTrue(value.endsWith("45~65%"), "占比应如实带上区间：" + value);
    }

    @Test
    @DisplayName("摘要与 dna 本身一致：改默认值时不会出现「摘要说高、实际是低」")
    void defaultEvidenceTracksTheDna() {
        ObjectNode dna = seed();
        String value = defaultEvidenceValue(dna);
        assertTrue(value.contains(CreativeLevelText.level(dna.path("whitespaceLevel").asText(null))),
            "摘要里的留白档要与 dna 一致：" + value);
        assertTrue(value.contains(CreativeLevelText.lighting(dna.path("lighting").path("type").asText(null))),
            "摘要里的光型要与 dna 一致：" + value);
        assertTrue(value.contains(dna.path("sceneType").asText()), "摘要里的场景要与 dna 一致：" + value);
    }

    @Test
    @DisplayName("占比取不到时说「未设置」，不编一个区间")
    void ratioMissingIsNotInvented() {
        ObjectNode blank = VisualDnaSchema.empty();
        assertEquals("未设置", DnaSeedBuilder.ratioText(blank));
        blank.withObject("/productRatio").put("min", 30).put("max", 80);
        assertEquals("30~80%", DnaSeedBuilder.ratioText(blank));
    }
}
