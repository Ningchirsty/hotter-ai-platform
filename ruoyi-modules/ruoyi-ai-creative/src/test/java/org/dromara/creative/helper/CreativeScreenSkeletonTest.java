package org.dromara.creative.helper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 屏骨架契约测试（V0.2 C′：去固定 7 屏）。
 *
 * <p>要钉住三件事：</p>
 * <ol>
 *   <li>内置契约能加载、内容与改造前一致（默认 7 屏、顺序与展示名不变）——否则这次重构就是行为变更；</li>
 *   <li>坏契约必须<b>当场拒绝</b>（空骨架/缺字段/非法保真等级/展示名重复），不能带病生成分镜；</li>
 *   <li>展示短名的兜底：历史分镜里可能有契约已下线的屏类型（如 PACKAGE），不能显示成空白。</li>
 * </ol>
 */
class CreativeScreenSkeletonTest {

    private static CreativeScreenSkeleton.ScreenSpec spec(String type, String label, String level) {
        return new CreativeScreenSkeleton.ScreenSpec(type, label, level, "中景");
    }

    @Test
    @DisplayName("内置契约可加载：默认 7 屏，顺序与改造前逐项一致")
    void defaultSkeletonMatchesLegacySevenScreens() {
        CreativeScreenSkeleton skeleton = CreativeScreenSkeleton.load(null);
        assertEquals(7, skeleton.size(), "默认契约必须还是 7 屏（改造不改变生产行为）");
        assertEquals(List.of("HERO", "SELLING_POINT", "SELLING_POINT", "SCENE", "DETAIL", "SIZE", "BRAND"),
            skeleton.screens().stream().map(CreativeScreenSkeleton.ScreenSpec::type).toList());
        assertEquals(List.of("主图", "卖点一", "卖点二", "使用场景", "细节工艺", "尺寸参数", "品牌收尾"),
            skeleton.screens().stream().map(CreativeScreenSkeleton.ScreenSpec::label).toList());
        assertEquals(List.of("STRICT", "LOOSE", "LOOSE", "LOOSE", "STRICT", "STRICT", "LOOSE"),
            skeleton.screens().stream().map(CreativeScreenSkeleton.ScreenSpec::productLockLevel).toList());
        assertEquals(2, skeleton.countOf("SELLING_POINT"), "卖点屏数量仍为 2（决定取几条卖点块）");
        assertEquals(0, skeleton.countOf("NOT_EXIST"));
        assertEquals(6, skeleton.types().size(), "去重后的类型数");
        assertTrue(skeleton.brief().contains("SELLING_POINT×2"), skeleton.brief());
    }

    @Test
    @DisplayName("展示短名来自契约；契约里没有的类型回落为展示名/原样，不返回空")
    void descOfFallsBackReadably() {
        CreativeScreenSkeleton skeleton = CreativeScreenSkeleton.load(null);
        assertEquals("主图", skeleton.descOf("HERO"));
        assertEquals("卖点", skeleton.descOf("SELLING_POINT"));
        // PACKAGE 不在默认骨架的 screens 里，但 typeDesc 里保留了它（历史分镜可能有）
        assertEquals("包装", skeleton.descOf("PACKAGE"));
        assertEquals("某个未来类型", skeleton.descOf("某个未来类型"), "取不到就原样返回，不能变成空");
        assertEquals("主图", CreativeScreenSkeletonRegistry.skeleton().descOf("HERO"),
            "工具类拿到的骨架应与契约一致（Spring 没起时也要能懒加载）");
    }

    @Test
    @DisplayName("取景描述支持 {ratio} 占位；SCENE 与改造前的字符串逐字一致")
    void shotSubstitutesRatio() {
        CreativeScreenSkeleton skeleton = CreativeScreenSkeleton.load(null);
        CreativeScreenSkeleton.ScreenSpec scene = skeleton.screens().get(3);
        assertEquals("SCENE", scene.type());
        assertEquals("环境全景，产品占画面 45%~65%", skeleton.shotOf(scene, "45%~65%"));
        CreativeScreenSkeleton.ScreenSpec hero = skeleton.screens().get(0);
        assertEquals("产品全貌，正视角（或 15° 微侧）", skeleton.shotOf(hero, "45%~65%"),
            "没有占位符的取景描述应原样返回");
    }

    @Test
    @DisplayName("坏契约必须当场拒绝（空/缺字段/非法等级/展示名重复）")
    void invalidContractsRejected() {
        assertThrows(IllegalArgumentException.class, () -> CreativeScreenSkeleton.parse(""),
            "空内容要拒");
        assertThrows(IllegalArgumentException.class, () -> CreativeScreenSkeleton.parse("{不是 JSON"),
            "非法 JSON 要拒");
        assertThrows(IllegalArgumentException.class, () -> CreativeScreenSkeleton.parse("{\"screens\":[]}"),
            "空骨架要拒");
        assertThrows(IllegalArgumentException.class,
            () -> CreativeScreenSkeleton.of(List.of(spec("HERO", null, "STRICT")), Map.of()),
            "缺展示名要拒");
        assertThrows(IllegalArgumentException.class,
            () -> CreativeScreenSkeleton.of(List.of(spec("HERO", "主图", "MAYBE")), Map.of()),
            "非法保真等级要拒");
        assertThrows(IllegalArgumentException.class,
            () -> CreativeScreenSkeleton.of(List.of(spec("HERO", "主图", "STRICT"),
                spec("SELLING_POINT", "主图", "LOOSE")), Map.of()),
            "展示名重复要拒（同类型多屏必须能区分）");
        assertThrows(IllegalArgumentException.class,
            () -> CreativeScreenSkeleton.of(List.of(
                new CreativeScreenSkeleton.ScreenSpec("HERO", "主图", "STRICT", null)), Map.of()),
            "缺取景描述要拒");
    }

    @Test
    @DisplayName("可配置：外部契约文件能覆盖内置契约（改屏数不用改代码/重新打包）")
    void externalContractOverridesBuiltIn(@TempDir java.nio.file.Path dir) throws Exception {
        java.nio.file.Path file = dir.resolve("my-skeleton.json");
        java.nio.file.Files.writeString(file, """
            {
              "schema": "creative-screen-skeleton/1",
              "typeDesc": {"HERO": "封面", "BRAND": "落版"},
              "screens": [
                {"type": "HERO", "label": "封面图", "productLockLevel": "STRICT", "shot": "产品全貌"},
                {"type": "BRAND", "label": "品牌落版", "productLockLevel": "LOOSE", "shot": "产品与品牌合影"}
              ]
            }
            """, java.nio.charset.StandardCharsets.UTF_8);

        CreativeScreenSkeleton custom = CreativeScreenSkeleton.load(file.toString());
        assertEquals(2, custom.size(), "外部契约有几屏就是几屏");
        assertEquals("封面图", custom.screens().get(0).label());
        assertEquals("封面", custom.descOf("HERO"), "typeDesc 也来自外部契约");

        // 外部契约坏掉时不能把服务带下去：解析失败必须抛（启动即失败），但"文件不存在"只回落内置契约
        assertEquals(7, CreativeScreenSkeleton.load(dir.resolve("不存在.json").toString()).size(),
            "路径写错/文件没挂上时回落内置契约（只告警，不让服务起不来）");
        java.nio.file.Files.writeString(file, "{坏的", java.nio.charset.StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> CreativeScreenSkeleton.load(file.toString()),
            "外部契约内容坏掉必须当场失败——坏骨架会让分镜错位，比不启动更糟");
    }

    @Test
    @DisplayName("内部契约自带校验：展示名唯一、保真等级合法、条数与 screens 一致")
    void builtInContractIsSelfConsistent() {
        CreativeScreenSkeleton skeleton = CreativeScreenSkeleton.load(null);
        assertEquals(skeleton.size(), skeleton.screens().size());
        assertEquals(skeleton.size(),
            skeleton.screens().stream().map(CreativeScreenSkeleton.ScreenSpec::label).distinct().count(),
            "展示名必须两两不同（否则 UI 上分不出是哪一屏）");
        Map<String, Integer> byLevel = new LinkedHashMap<>();
        for (CreativeScreenSkeleton.ScreenSpec s : skeleton.screens()) {
            byLevel.merge(s.productLockLevel(), 1, Integer::sum);
            assertFalse(s.type().isBlank() || s.label().isBlank() || s.shot().isBlank());
        }
        assertTrue(byLevel.containsKey(CreativeScreenSkeleton.LEVEL_STRICT));
        assertTrue(byLevel.containsKey(CreativeScreenSkeleton.LEVEL_LOOSE));
    }
}
