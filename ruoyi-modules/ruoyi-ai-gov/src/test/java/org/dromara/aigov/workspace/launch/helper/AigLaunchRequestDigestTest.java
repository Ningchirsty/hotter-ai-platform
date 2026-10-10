package org.dromara.aigov.workspace.launch.helper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * 启动请求摘要测试（增量 3）。
 *
 * <p>守的是两个方向：**格式不同、内容相同**不能算冲突（否则重试永远失败）；
 * **内容不同**必须算冲突（否则用户改了输入却拿到上次的结果，改动被静默丢弃）。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigLaunchRequestDigestTest {

    @Test
    @DisplayName("键顺序/空白/数值形态不同但内容相同 → 同一摘要（重试必须能命中幂等）")
    void formattingDoesNotChangeDigest() {
        String a = AigLaunchRequestDigest.of("R1", 7L, "A1", "cap/x", "TEXT_GENERATION", "creative", 1L,
            "INTERNAL", "{\"b\":1,\"a\":2}", Map.of("productId", "9", "brandId", "3"));
        String b = AigLaunchRequestDigest.of("R1", 7L, "A1", "cap/x", "TEXT_GENERATION", "creative", 1L,
            "INTERNAL", "{ \"a\" : 2.0 , \"b\" : 1 }", Map.of("brandId", "3", "productId", "9"));
        assertEquals(a, b, "同样的输入不该因为 JSON 排版被判成冲突");
    }

    @Test
    @DisplayName("内容变了必须换摘要（否则用户改的输入会被静默丢弃）")
    void contentChangesDigest() {
        String base = AigLaunchRequestDigest.of("R1", 7L, "A1", "cap/x", "TEXT_GENERATION", "creative", 1L,
            "INTERNAL", "{\"a\":1}", Map.of("productId", "9"));
        assertNotEquals(base, AigLaunchRequestDigest.of("R1", 7L, "A1", "cap/x", "TEXT_GENERATION",
            "creative", 2L, "INTERNAL", "{\"a\":1}", Map.of("productId", "9")), "换了项目");
        assertNotEquals(base, AigLaunchRequestDigest.of("R1", 7L, "A1", "cap/x", "TEXT_GENERATION",
            "creative", 1L, "STRICT", "{\"a\":1}", Map.of("productId", "9")), "换了数据等级");
        assertNotEquals(base, AigLaunchRequestDigest.of("R1", 7L, "A1", "cap/x", "TEXT_GENERATION",
            "creative", 1L, "INTERNAL", "{\"a\":2}", Map.of("productId", "9")), "换了输入");
        assertNotEquals(base, AigLaunchRequestDigest.of("R1", 7L, "A1", "cap/x", "TEXT_GENERATION",
            "creative", 1L, "INTERNAL", "{\"a\":1}", Map.of()), "少了上下文");
        assertNotEquals(base, AigLaunchRequestDigest.of("R1", 8L, "A1", "cap/x", "TEXT_GENERATION",
            "creative", 1L, "INTERNAL", "{\"a\":1}", Map.of("productId", "9")), "换了岗位版本");
    }

    @Test
    @DisplayName("非 JSON 的输入（纯文本）也能算摘要，不抛异常")
    void nonJsonSnapshotIsAccepted() {
        String digest = AigLaunchRequestDigest.of("R1", 7L, "A1", "cap/x", "TEXT_GENERATION", "creative",
            null, "INTERNAL", "一段纯文本输入", null);
        assertEquals(64, digest.length(), "是 sha256 十六进制");
        assertNotEquals(digest, AigLaunchRequestDigest.of("R1", 7L, "A1", "cap/x", "TEXT_GENERATION",
            "creative", null, "INTERNAL", "另一段纯文本输入", null));
    }

}
