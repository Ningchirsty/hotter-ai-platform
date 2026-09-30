package org.dromara.creative.helper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 海报版面 payload 的组装（V0.2 R52）。
 *
 * <p>海报渲染最容易出错的不是"调渲染服务"，而是<b>下发给模板的东西对不对</b>：
 * 画布尺寸是哪一档、缺图时是不是留白、文案是不是编造出来的。这些是纯判定，必须能断言。</p>
 */
class CreativePosterLayoutTest {

    private static CreativePosterLayout.PosterModule module(String code, String image) {
        return new CreativePosterLayout.PosterModule(code, "S01", "HERO", "主张文案", image, 9L);
    }

    @Test
    @DisplayName("画布与模块如实下发：有图的带 image，没图的不带（模板画缺图提示）")
    void buildsCanvasAndModules() {
        Map<String, Object> payload = CreativePosterLayout.build(
            new CreativePosterLayout.Canvas("BRAND_POSTER_3_4", "3:4", 1080, 1440),
            List.of(module("MAIN_VISUAL", "data:image/png;base64,AAA"), module("BRAND_LOCKUP", null)),
            "主张", "副题", Map.of("background", "#f5f5f3"), "品牌", "产品", "页脚");

        @SuppressWarnings("unchecked")
        Map<String, Object> canvas = (Map<String, Object>) payload.get("canvas");
        assertEquals("BRAND_POSTER_3_4", canvas.get("specCode"));
        assertEquals(1080, canvas.get("width"));
        assertEquals(1440, canvas.get("height"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> modules = (List<Map<String, Object>>) payload.get("modules");
        assertEquals(2, modules.size());
        assertEquals("data:image/png;base64,AAA", modules.get(0).get("image"));
        assertFalse(modules.get(1).containsKey("image"), "缺图必须不带 image 字段，由模板画提示块");
        assertEquals(9L, modules.get(0).get("generationId"));
    }

    @Test
    @DisplayName("空文案/空品牌不编造：一律空串下发，模板据此整块不渲染")
    void neverInventsCopy() {
        Map<String, Object> payload = CreativePosterLayout.build(
            new CreativePosterLayout.Canvas(null, null, 0, 0), null, null, null, null, null, null, null);

        @SuppressWarnings("unchecked")
        Map<String, Object> copy = (Map<String, Object>) payload.get("copy");
        assertEquals("", copy.get("headline"));
        assertEquals("", copy.get("subline"));
        @SuppressWarnings("unchecked")
        Map<String, Object> brand = (Map<String, Object>) payload.get("brand");
        assertEquals("", brand.get("lockupText"));
        assertEquals("", payload.get("footerNote"));
        assertEquals(List.of(), payload.get("modules"));
        assertTrue(payload.containsKey("dna"));
    }

    @Test
    @DisplayName("标识文字兜底用品牌名；页脚品牌优先产品名")
    void brandFallbacks() {
        Map<String, Object> payload = CreativePosterLayout.build(
            new CreativePosterLayout.Canvas("X", "1:1", 100, 100), null, "", "", Map.of(),
            "品牌名", "产品名", "");
        @SuppressWarnings("unchecked")
        Map<String, Object> brand = (Map<String, Object>) payload.get("brand");
        assertEquals("品牌名", brand.get("lockupText"));
        assertEquals("产品名", brand.get("footerBrand"));
    }

    @Test
    @DisplayName("取第一个非空文案：全空返回空串（不编造）")
    void pickFirst() {
        assertEquals("b", CreativePosterLayout.pickFirst(java.util.Arrays.asList(null, "  ", "b", "c")));
        assertEquals("", CreativePosterLayout.pickFirst(java.util.Arrays.asList(null, "")));
        assertEquals("", CreativePosterLayout.pickFirst(null));
    }

    @Test
    @DisplayName("候选列表必须容得下 null——List.of 会 NPE，这是 R52 真机踩出来的坑")
    void candidatesTolerateNulls() {
        List<String> list = CreativePosterLayout.candidates(null, "有值", null);
        assertEquals(3, list.size(), "候选个数不能被悄悄压缩（否则优先级顺序就变了）");
        assertEquals("有值", CreativePosterLayout.pickFirst(list));
        // 反向钉一次：这正是当时写错的那句
        assertThrows(NullPointerException.class,
            () -> CreativePosterLayout.pickFirst(List.of("a", null)));
    }

    @Test
    @DisplayName("dna 为空时下发空对象（模板用自己的兜底色，不传 null 给模板）")
    void dnaNeverNull() {
        Map<String, Object> payload = CreativePosterLayout.build(
            new CreativePosterLayout.Canvas("X", "1:1", 100, 100), null, "a", "b",
            new LinkedHashMap<>(), "品牌", "产品", "页脚");
        assertTrue(payload.get("dna") instanceof Map);
    }
}
