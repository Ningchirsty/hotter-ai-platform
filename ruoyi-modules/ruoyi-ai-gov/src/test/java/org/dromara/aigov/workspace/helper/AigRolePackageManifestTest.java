package org.dromara.aigov.workspace.helper;

import org.dromara.aigov.workspace.domain.AigRolePackageDraft;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 岗位包清单组装/还原测试（增量 1b）。
 *
 * <p>它守的是三件事：①清单能把"非卡片部分"完整地存下来并原样读回；
 * ②卡片<b>不</b>进清单（否则同一份配置有两个来源，迟早不一致）；
 * ③读回失败要花在明处——还原一旦静默缺字段，校验就会变松，
 * 而"配错了不报错"正是岗位包最危险的失效方式。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigRolePackageManifestTest {

    /**
     * 一份可用的草稿（两分类、一卡片）
     */
    private static AigRolePackageDraft draft() {
        return new AigRolePackageDraft("GRAPHIC_DESIGNER_AI", "平面设计 AI 工作台", "1.0.0",
            List.of(new AigRolePackageDraft.CategoryDraft("DETAIL_PAGE", "详情页设计"),
                new AigRolePackageDraft.CategoryDraft("BANNER", "横幅")),
            "DETAIL_PAGE",
            List.of(new AigRolePackageDraft.ActionDraft("DETAIL_PAGE_CREATE", "DETAIL_PAGE", "做详情页",
                "STUDIO", "QUICK_CAPABILITY", "detail_page_agent", "CREATIVE_PRODUCTION",
                "productId,brandId", true)),
            "ASSIGNED_ORG", "INTERNAL", "STRICT");
    }

    @Test
    @DisplayName("组装→还原：身份/分类/默认分类/策略都原样回来")
    void roundTripKeepsEverythingButActions() {
        String manifest = AigRolePackageManifest.toJson(draft());
        AigRolePackageDraft back = AigRolePackageManifest.fromJson(manifest,
            List.of(new AigRolePackageDraft.ActionDraft("DETAIL_PAGE_CREATE", "DETAIL_PAGE", "做详情页",
                "STUDIO", "QUICK_CAPABILITY", "detail_page_agent", "CREATIVE_PRODUCTION",
                "productId,brandId", true)));

        assertEquals("GRAPHIC_DESIGNER_AI", back.roleCode());
        assertEquals("1.0.0", back.version());
        assertEquals(2, back.categories().size());
        assertEquals("BANNER", back.categories().get(1).code());
        assertEquals("DETAIL_PAGE", back.defaultCategory());
        assertEquals("ASSIGNED_ORG", back.audienceScope());
        assertEquals("INTERNAL", back.defaultDataLevel());
        assertEquals("STRICT", back.maxDataLevel());
        // 卡片来自参数（表里那些行），不是从清单里读的
        assertEquals(1, back.actions().size());
        assertTrue(back.actions().get(0).enabled());
    }

    @Test
    @DisplayName("卡片不进清单：同一份配置不能有两个来源")
    void actionsAreNotStoredInManifest() {
        String manifest = AigRolePackageManifest.toJson(draft());
        assertFalse(manifest.contains("DETAIL_PAGE_CREATE"), "卡片编码不该出现在清单里：" + manifest);
        assertFalse(manifest.contains("detail_page_agent"), "启动目标不该出现在清单里：" + manifest);
    }

    @Test
    @DisplayName("入库的清单已经是规范化的：再规范化一次不再变化")
    void manifestIsAlreadyCanonical() {
        String manifest = AigRolePackageManifest.toJson(draft());
        assertEquals(manifest, AigRoleManifestHasher.canonicalize(manifest),
            "toJson 应直接给出规范化结果，否则 manifest_sha256 与库里的文本对不上");
        // 键排序后第一个键是 categories（而不是 roleCode）——这是规范化生效的可见证据
        assertTrue(manifest.startsWith("{\"categories\""), "键应按字符序排列：" + manifest);
    }

    @Test
    @DisplayName("缺字段还原成 null（而不是空串），让校验去报『缺什么』")
    void missingFieldsBecomeNullSoValidatorCanReport() {
        AigRolePackageDraft back = AigRolePackageManifest.fromJson("{}", List.of());
        assertNull(back.roleCode());
        assertNull(back.version());
        assertNull(back.audienceScope());
        assertNull(back.defaultDataLevel());
        assertTrue(back.categories().isEmpty());
        // 空清单同样按"字段全缺"处理，而不是抛异常
        assertNull(AigRolePackageManifest.fromJson("", List.of()).roleCode());
        assertNull(AigRolePackageManifest.fromJson(null, null).roleCode());
    }

    @Test
    @DisplayName("清单读不出来要抛，不能当成『没配置』悄悄放行")
    void brokenManifestFailsLoudly() {
        assertThrows(IllegalArgumentException.class,
            () -> AigRolePackageManifest.fromJson("{不是 JSON", List.of()));
        assertThrows(IllegalArgumentException.class,
            () -> AigRolePackageManifest.fromJson("[1,2,3]", List.of()));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> AigRolePackageManifest.toJson(null));
        assertInstanceOf(IllegalArgumentException.class, e);
    }

    @Test
    @DisplayName("分类与卡片漂移能被校验发现（还原必须把分类带回来）")
    void driftBetweenManifestAndActionsIsCaught() {
        // 库里的清单只有 DETAIL_PAGE 分类，而卡片行却挂在一个已不存在的分类上
        String manifest = AigRolePackageManifest.toJson(draft());
        AigRolePackageDraft drifted = AigRolePackageManifest.fromJson(manifest,
            List.of(new AigRolePackageDraft.ActionDraft("BANNER_CREATE", "BANNER_GONE", "做横幅",
                "QUICK", "QUICK_CAPABILITY", "banner_agent", null, null, true)));
        List<String> problems = AigRolePackageValidator.validate(drifted, (code, version) -> true);
        assertTrue(problems.stream().anyMatch(p -> p.contains("不在本包分类里")),
            "分类漂移必须被报出来，否则这类错误会以『点了没反应』的形式出现在员工侧：" + problems);
    }

}
