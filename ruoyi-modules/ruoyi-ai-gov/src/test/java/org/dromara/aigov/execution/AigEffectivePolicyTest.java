package org.dromara.aigov.execution;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 有效权限/有效等级判据测试（设计 §7.2）。
 *
 * <p>三条都是"算错了不会报错"的判据：交集算成并集会让越权资源进入运行；等级取最低会让
 * 受限资料被当成公开；外发的"与"算成"或"会让任意一方允许就外发。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigEffectivePolicyTest {

    @Test
    @DisplayName("★ 范围相交：收窄为空=不额外收窄（空值语义是「不限」，不是「都不匹配」）")
    void emptyNarrowingMeansNoNarrowing() {
        assertEquals(List.of("a", "b"), AigEffectivePolicy.intersect(List.of("a", "b"), List.of()));
        assertEquals(List.of("a", "b"), AigEffectivePolicy.intersect(List.of("a", "b"), null));
    }

    @Test
    @DisplayName("★ 范围相交：有收窄只保留交集（绑定只做收窄）")
    void narrowingKeepsIntersection() {
        assertEquals(List.of("b"), AigEffectivePolicy.intersect(List.of("a", "b"), List.of("b", "c")));
        assertTrue(AigEffectivePolicy.intersect(List.of("a"), List.of("x")).isEmpty(),
            "完全不相交即空 = 拒绝，而不是退回声明范围");
    }

    @Test
    @DisplayName("范围相交：去重与空白清理")
    void intersectionCleansInputs() {
        assertEquals(List.of("a"), AigEffectivePolicy.intersect(List.of("a", "a", " "), List.of()));
    }

    @Test
    @DisplayName("★ 有效数据等级取最高值；不可识别项忽略；全不可识别返回 null")
    void strictestDataLevel() {
        assertEquals("STRICT", AigEffectivePolicy.strictestDataLevel("INTERNAL", "STRICT", "PUBLIC"));
        assertEquals("RESTRICTED", AigEffectivePolicy.strictestDataLevel("INTERNAL", "RESTRICTED"));
        assertEquals("INTERNAL", AigEffectivePolicy.strictestDataLevel("INTERNAL", null, "NOT_A_LEVEL"));
        assertNull(AigEffectivePolicy.strictestDataLevel("nope", null));
        assertNull(AigEffectivePolicy.strictestDataLevel());
    }

    @Test
    @DisplayName("★ 有效外发许可：任意一方禁止即为否（null 表示该方不额外限制）")
    void externalAllowedIsAndOfAllParties() {
        assertTrue(AigEffectivePolicy.externalAllowed(Boolean.TRUE, null, Boolean.TRUE));
        assertFalse(AigEffectivePolicy.externalAllowed(Boolean.TRUE, Boolean.FALSE, null));
        assertFalse(AigEffectivePolicy.externalAllowed(Boolean.FALSE));
        assertFalse(AigEffectivePolicy.externalAllowed((Boolean[]) null), "整组为空安全失败（不放宽）");
    }

    @Test
    @DisplayName("有效外发许可：全为 null（各方都不额外限制）视为允许")
    void allNullMeansAllowed() {
        assertTrue(AigEffectivePolicy.externalAllowed((Boolean) null, (Boolean) null));
        assertTrue(AigEffectivePolicy.externalAllowed());
    }

    @Test
    @DisplayName("交集结果不可被外部修改（避免调用方顺手改掉授权范围）")
    void intersectionResultIsImmutable() {
        assertThrowsForImmutable();
    }

    private static void assertThrowsForImmutable() {
        List<String> result = AigEffectivePolicy.intersect(Set.of("a"), Set.of());
        try {
            result.add("b");
            throw new AssertionError("交集结果应不可变");
        } catch (UnsupportedOperationException expected) {
            // 预期
        }
    }

}
