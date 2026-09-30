package org.dromara.creative.helper;

import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「这次排版用哪个模板」的分支（V0.2 R23）。
 *
 * <p>为什么这些分支必须在单测里钉死：排版接口的第一道是**视觉门**，新项目根本走不到模板那步，
 * 所以"钉了未登记的模板会不会报错""钉冲突怎么办"在真机上验不到；不钉死就等于没验。</p>
 */
class CreativeTemplatePinTest {

    private static List<String> specs(String... json) {
        return new ArrayList<>(Arrays.asList(json));
    }

    @Test
    @DisplayName("正式路径：模板码来自**当前模块计划**（改模板不必重拆分镜）")
    void planCodesWin() {
        CreativeTemplatePin.Pinned pinned = CreativeTemplatePin.resolveCodes(
            Arrays.asList("longpage@1.0.2", "longpage@1.0.2"), "longpage", "1.0.1");

        assertEquals("longpage", pinned.code());
        assertEquals("1.0.2", pinned.version());
        assertTrue(pinned.fromPlan());
        // 计划里没钉 → 默认（不是"猜一个已发布版本"）
        assertFalse(CreativeTemplatePin.resolveCodes(List.of(), "longpage", "1.0.1").fromPlan());
        // 计划里钉冲突 → 报错（与屏上兜底路径同一套规则）
        ServiceException ex = assertThrows(ServiceException.class, () -> CreativeTemplatePin.resolveCodes(
            Arrays.asList("longpage@1.0.2", "longpage@1.0.1"), "longpage", "1.0.1"));
        assertTrue(ex.getMessage().contains("不同的排版模板"), ex.getMessage());
    }

    @Test
    @DisplayName("没有屏钉模板（含老分镜/脏 spec）→ 用默认模板")
    void defaultsWhenNothingPinned() {
        CreativeTemplatePin.Pinned pinned = CreativeTemplatePin.resolve(
            specs(null, "", "{\"shot\":\"正面\"}", "not-json"), "longpage", "1.0.2");

        assertEquals("longpage", pinned.code());
        assertEquals("1.0.2", pinned.version());
        assertFalse(pinned.fromPlan());
        assertEquals("1.0.2", CreativeTemplatePin.resolve(null, "longpage", "1.0.2").version(),
            "null 列表也不该抛，按没钉处理");
    }

    @Test
    @DisplayName("所有钉了的屏都指向同一个 code@version → 用它，并标出来自计划")
    void singlePinWins() {
        CreativeTemplatePin.Pinned pinned = CreativeTemplatePin.resolve(
            specs("{\"templateCodes\":[\"longpage@1.0.1\"]}",
                  "{\"templateCodes\":[\"longpage@1.0.1\"]}",
                  "{}"), "longpage", "1.0.2");

        assertEquals("longpage", pinned.code());
        assertEquals("1.0.1", pinned.version());
        assertTrue(pinned.fromPlan());
    }

    @Test
    @DisplayName("不同模块钉了不同模板 → 报冲突（一页只能一个模板）")
    void conflictingPinsAreRejected() {
        ServiceException ex = assertThrows(ServiceException.class, () -> CreativeTemplatePin.resolve(
            specs("{\"templateCodes\":[\"longpage@1.0.2\"]}",
                  "{\"templateCodes\":[\"longpage@1.0.1\"]}"), "longpage", "1.0.2"));
        assertTrue(ex.getMessage().contains("不同的排版模板"), ex.getMessage());
        assertTrue(ex.getMessage().contains("longpage@1.0.2") && ex.getMessage().contains("longpage@1.0.1"),
            "冲突要把它俩都列出来，用户才知道改哪个");
    }

    @Test
    @DisplayName("模板没写版本 → 报错要求写成 code@version（不猜版本）")
    void versionIsMandatory() {
        ServiceException ex = assertThrows(ServiceException.class, () -> CreativeTemplatePin.resolve(
            specs("{\"templateCodes\":[\"longpage\"]}"), "longpage", "1.0.2"));
        assertTrue(ex.getMessage().contains("模板码@版本"), ex.getMessage());

        ServiceException empty = assertThrows(ServiceException.class, () -> CreativeTemplatePin.resolve(
            specs("{\"templateCodes\":[\"@1.0.2\"]}"), "longpage", "1.0.2"));
        assertTrue(empty.getMessage().contains("模板码@版本"), empty.getMessage());
    }
}
