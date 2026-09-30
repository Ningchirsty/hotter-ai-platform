package org.dromara.creative.helper;

import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 渲染器中枢（V0.2 R30，文档 §26 Renderer Hub）。
 *
 * <p>这个类的价值全在"解析得对、拒绝得清"：渲染器由 {@code dp_delivery_type.render_mode}
 * 解析（不写死交付类型），而文档 §26 里规划中但**还没实现**的 Poster/Article/Print/Video
 * 必须被明确拒绝——一个跑空壳还报"成功"的渲染器，比没有渲染器更糟。</p>
 */
class CreativeRendererHubTest {

    private static CreativeRenderer fake(String code, boolean implemented) {
        return new CreativeRenderer() {
            @Override
            public String code() {
                return code;
            }

            @Override
            public String displayName() {
                return code + " 渲染器";
            }

            @Override
            public String targetStep() {
                return "STEP_" + code;
            }

            @Override
            public boolean implemented() {
                return implemented;
            }

            @Override
            public String note() {
                return "测试用";
            }

            @Override
            public Outcome render(Context context) {
                return new Outcome(List.of(), "fake");
            }
        };
    }

    @Test
    @DisplayName("能力清单：已实现的与规划中的都在，且规划中的 implemented=false")
    void listsImplementedAndPlanned() {
        CreativeRendererHub hub = new CreativeRendererHub(List.of(fake("LONG_PAGE", true)));

        List<String> codes = hub.all().stream().map(CreativeRenderer::code).toList();
        assertTrue(codes.contains("LONG_PAGE"), codes.toString());
        // 文档 §26 列的四类渲染器必须登记（否则页面上会"查不到"而不是"还没实现"）
        for (String planned : List.of("POSTER", "ARTICLE", "PRINT", "VIDEO")) {
            assertTrue(codes.contains(planned), "缺少规划中的渲染器 " + planned + "：" + codes);
        }
        assertEquals("LONG_PAGE", hub.implementedCodes());
    }

    @Test
    @DisplayName("按渲染模式解析：LONGPAGE→LONG_PAGE、MULTI_IMAGE→MULTI_IMAGE（大小写不敏感）")
    void resolvesByRenderMode() {
        CreativeRendererHub hub = new CreativeRendererHub(List.of(
            fake("LONG_PAGE", true), fake("MULTI_IMAGE", true)));

        assertEquals("LONG_PAGE", hub.resolveFor("LONGPAGE").code());
        assertEquals("MULTI_IMAGE", hub.resolveFor("multi_image").code());
        assertEquals("MULTI_IMAGE", hub.resolveFor(" MULTI_IMAGE ").code());
    }

    @Test
    @DisplayName("渲染模式没配/不认识 → 拒绝，并把已登记的映射说出来")
    void unknownRenderModeIsRejected() {
        CreativeRendererHub hub = new CreativeRendererHub(List.of(fake("LONG_PAGE", true)));

        ServiceException blank = assertThrows(ServiceException.class, () -> hub.resolveFor(null));
        assertTrue(blank.getMessage().contains("没有配置渲染模式"), blank.getMessage());
        ServiceException unknown = assertThrows(ServiceException.class, () -> hub.resolveFor("POSTER"));
        assertTrue(unknown.getMessage().contains("还没有对应的渲染器"), unknown.getMessage());
    }

    @Test
    @DisplayName("没实现的渲染器不能执行：报错要说清「还没实现」以及现在能跑什么")
    void plannedRendererIsRefused() {
        CreativeRendererHub hub = new CreativeRendererHub(List.of(fake("LONG_PAGE", true)));

        ServiceException e = assertThrows(ServiceException.class, () -> hub.require("POSTER"));
        assertTrue(e.getMessage().contains("还没有实现"), e.getMessage());
        assertTrue(e.getMessage().contains("LONG_PAGE"), "报错必须给出可用清单：" + e.getMessage());
        // 未知编码同样是拒绝，且给出已登记清单
        ServiceException unknown = assertThrows(ServiceException.class, () -> hub.require("NOPE"));
        assertTrue(unknown.getMessage().contains("没有这个渲染器"), unknown.getMessage());
        assertTrue(unknown.getMessage().contains("POSTER"), unknown.getMessage());
        assertTrue(hub.require("long_page").implemented(), "已实现的渲染器应能取到（大小写不敏感）");
    }

    @Test
    @DisplayName("产物文件名：ASCII 安全、带序号与屏号；中文不进文件名")
    void fileNameIsAsciiSafe() {
        assertEquals("01-S01-MAIN_WHITE_BG.png",
            CreativeRenderer.fileNameOf(1, "S01", "MAIN_WHITE_BG", "png"));
        assertEquals("02-S02-SCREEN.png", CreativeRenderer.fileNameOf(2, "S02", null, null));
        // 中文/空格/斜杠一律换成下划线，连续下划线收敛成一个（ZIP 条目名跨工具兼容）
        String name = CreativeRenderer.fileNameOf(3, "屏 01", "主图/白底", "png");
        assertEquals("03-_01-_.png", name);
        assertTrue(name.matches("[A-Za-z0-9_.\\-]+"), name);
    }

    @Test
    @DisplayName("产物清单转行：字段齐全（页面表格直接用）")
    void productRowsCarryAllFields() {
        CreativeRenderer.Product product = new CreativeRenderer.Product(
            "01-S01-HERO.png", CreativeDeliveryManifest.ROLE_SCREEN, "S01", "HERO", "HERO",
            123L, 456L, 800, 800, 1024L, "abc");
        List<Map<String, Object>> rows = CreativeRenderer.toRows(List.of(product));

        assertEquals(1, rows.size());
        assertEquals(800, rows.get(0).get("width"));
        assertEquals(123L, rows.get(0).get("fileId"));
        assertEquals("HERO", rows.get(0).get("moduleCode"));
        assertEquals("abc", rows.get(0).get("sha256"));
    }
}
