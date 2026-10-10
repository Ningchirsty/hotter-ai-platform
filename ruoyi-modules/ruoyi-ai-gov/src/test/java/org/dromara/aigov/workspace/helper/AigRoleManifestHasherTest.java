package org.dromara.aigov.workspace.helper;

import org.dromara.aigov.studio.helper.AigStudioContentHasher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 岗位包清单哈希测试（增量 1b）。
 *
 * <p>它守的两件事：①清单哈希对**序列化细节**不敏感（否则界面会对没变的清单喊"有未发布改动"）；
 * ②岗位包与训练台用的是**同一份规范化实现**（同内容必须同哈希）——两份实现漂移是
 * "两边各自都能跑、合起来才出错"的典型。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigRoleManifestHasherTest {

    private static final String MANIFEST = "{\"identity\":{\"roleCode\":\"GRAPHIC_DESIGNER_AI\"},"
        + "\"categories\":[{\"code\":\"DETAIL_PAGE\",\"order\":10}]}";

    @Test
    @DisplayName("★ 键顺序/空白不同 → 同一哈希（清单没变，不能被判成改动）")
    void canonicalFormIsInsensitiveToFormatting() {
        String reordered = "{\n  \"categories\" : [ { \"order\" : 10 , \"code\" : \"DETAIL_PAGE\" } ],\n"
            + "  \"identity\" : { \"roleCode\" : \"GRAPHIC_DESIGNER_AI\" }\n}";
        assertEquals(AigRoleManifestHasher.hash(MANIFEST), AigRoleManifestHasher.hash(reordered));
    }

    @Test
    @DisplayName("真改动必须变哈希；数组顺序是内容的一部分")
    void realChangesChangeHash() {
        assertNotEquals(AigRoleManifestHasher.hash(MANIFEST),
            AigRoleManifestHasher.hash(MANIFEST.replace("GRAPHIC_DESIGNER_AI", "VIDEO_PRODUCER_AI")));
        assertNotEquals(AigRoleManifestHasher.hash("{\"l\":[1,2]}"),
            AigRoleManifestHasher.hash("{\"l\":[2,1]}"), "数组顺序改了就是内容变了");
    }

    @Test
    @DisplayName("★ 与训练台同一份规范化实现：同内容必须同哈希（两份实现漂移是合起来才出错的问题）")
    void sharesTheSameCanonicalization() {
        assertEquals(AigStudioContentHasher.hash(MANIFEST), AigRoleManifestHasher.hash(MANIFEST));
        assertEquals(AigStudioContentHasher.canonicalize(MANIFEST),
            AigRoleManifestHasher.canonicalize(MANIFEST));
    }

    @Test
    @DisplayName("规范化幂等；空白串与坏 JSON 报错（不能静默算出个哈希）")
    void invalidInputIsRejected() {
        String once = AigRoleManifestHasher.canonicalize(MANIFEST);
        assertEquals(once, AigRoleManifestHasher.canonicalize(once), "对规范化结果再算一次必须一致");
        assertThrows(IllegalArgumentException.class, () -> AigRoleManifestHasher.hash("   "));
        assertThrows(IllegalArgumentException.class, () -> AigRoleManifestHasher.hash("{not json"));
    }

    @Test
    @DisplayName("null 按 JSON null 处理（有确定哈希），不抛异常")
    void nullIsHandled() {
        assertEquals("null", AigRoleManifestHasher.canonicalize(null));
        assertEquals(AigRoleManifestHasher.hash("null"), AigRoleManifestHasher.hash(null));
    }

}
