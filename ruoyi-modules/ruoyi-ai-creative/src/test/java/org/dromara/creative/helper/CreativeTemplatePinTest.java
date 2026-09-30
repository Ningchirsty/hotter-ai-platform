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
