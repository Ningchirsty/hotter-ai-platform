package org.dromara.ai.image.cloud;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 守的是 ADR-008 的迁移：验收档位必须来自<b>数据文件</b>，而不是写死在 Java 源码里。
 *
 * <p><b>为什么需要这条测试</b>：迁移前 {@code CloudImageValidation} 里有一份
 * {@code Map.of(...)} 硬编码常量，改一次验收结论就要改代码、走一次发版，
 * 而且那份快照会过期却没有任何提醒。迁移后数据在
 * {@code /cloud-image-validation.json}，风险换成了另一种：
 * <b>文件与代码脱节</b>（改名、写错字段、少一个型号都不会编译报错）。</p>
 *
 * <p>所以这里断言两件事：
 * ① classpath 上确实有该文件，且能被解析成完整的档位；
 * ② 解析结果的<b>口径</b>与迁移前的硬编码常量一致（型号集合、状态、参考图上下限）。
 * 另外覆盖"外部文件覆盖"与"文件缺失回落内置默认"两条分支——
 * 后者是刻意保留的兜底：本类被大量纯单测静态调用，若文件缺失就报错，
 * 等于把"配置缺失"放大成"所有云端图像能力不可提交"。</p>
 */
class CloudImageValidationSourceTest {

    /** 迁移前硬编码常量的口径，逐项固化下来。 */
    private static final Map<String, Map<String, String>> EXPECTED = Map.of(
        "flux-2-pro", Map.of("T2I", "HTTP_503"),
        "gpt-image-2.5-flare", Map.of("T2I", "PASSED", "EDIT", "PASSED", "MULTI", "PASSED",
            "MASK", "PASSED", "OUTPAINT", "PASSED", "TRANSPARENT", "PASSED"),
        "gpt-image-2.5-sunburst", Map.of("T2I", "PASSED", "EDIT", "PASSED", "MULTI", "PASSED",
            "MASK", "PASSED", "OUTPAINT", "PASSED", "TRANSPARENT", "PASSED"),
        "qwen-image-3.0", Map.of("T2I", "PASSED", "EDIT", "PASSED", "MULTI", "PASSED"),
        "qwen-image-3.0-pro", Map.of("T2I", "PASSED", "EDIT", "PASSED", "MULTI", "PASSED"),
        "wan2.7-image", Map.of("T2I", "PASSED", "EDIT", "PASSED", "MULTI", "HTTP_400"),
        "wan2.7-image-pro", Map.of("T2I", "PASSED", "EDIT", "PASSED", "MULTI", "PASSED"));

    @Test
    @DisplayName("★ classpath 上必须有 cloud-image-validation.json，且型号/状态口径与迁移前一致")
    void classpathFileIsPresentAndMatchesThePreviousHardcodedContract() {
        Map<String, CloudImageValidation.Entry> snap = CloudImageValidation.load("");
        assertEquals(EXPECTED.keySet(), snap.keySet(),
            "档位型号集合与迁移前不一致：多了或少了型号（文件与代码脱节）");
        for (Map.Entry<String, Map<String, String>> model : EXPECTED.entrySet()) {
            CloudImageValidation.Entry e = snap.get(model.getKey());
            assertNotNull(e, "缺少型号 " + model.getKey());
            assertEquals(model.getValue(), e.capabilities(),
                model.getKey() + " 的能力状态与迁移前不一致");
        }
    }

    @Test
    @DisplayName("参考图上下限沿用原按前缀推导的结果（qwen=3 / wan=9 / 其它=16）")
    void referenceLimitsStillFollowTheOriginalPerFamilyValues() {
        Map<String, CloudImageValidation.Entry> snap = CloudImageValidation.load("");
        assertEquals(3, snap.get("qwen-image-3.0").maxReferenceImages());
        assertEquals(3, snap.get("qwen-image-3.0-pro").maxReferenceImages());
        assertEquals(9, snap.get("wan2.7-image").maxReferenceImages());
        assertEquals(9, snap.get("wan2.7-image-pro").maxReferenceImages());
        assertEquals(16, snap.get("gpt-image-2.5-flare").maxReferenceImages());
        assertEquals(16, snap.get("gpt-image-2.5-sunburst").maxReferenceImages());
        assertEquals(16, snap.get("flux-2-pro").maxReferenceImages());

        assertEquals(20 * 1024 * 1024, snap.get("gpt-image-2.5-flare").maxReferenceBytes());
        assertEquals(20 * 1024 * 1024, snap.get("gpt-image-2.5-sunburst").maxReferenceBytes());
        assertEquals(10 * 1024 * 1024, snap.get("flux-2-pro").maxReferenceBytes());
        assertEquals(10 * 1024 * 1024, snap.get("wan2.7-image").maxReferenceBytes());
    }

    @Test
    @DisplayName("外部文件可覆盖档位（运维不发版改验收结论的入口）")
    void externalFileOverridesTheClasspathSnapshot(@TempDir Path dir) throws Exception {
        Path override = dir.resolve("override.json");
        Files.writeString(override, """
            [ { "model": "qwen-image-3.0", "testedAt": "2099-01-01",
                "maxReferenceImages": 7, "maxReferenceBytes": 123,
                "capabilities": { "T2I": "PASSED", "EDIT": "HTTP_500" } } ]
            """, StandardCharsets.UTF_8);

        Map<String, CloudImageValidation.Entry> snap = CloudImageValidation.load(override.toString());
        assertEquals(java.util.Set.of("qwen-image-3.0"), snap.keySet(),
            "外部文件应当整体取代内置档位，而不是合并");
        assertEquals("HTTP_500", snap.get("qwen-image-3.0").capabilities().get("EDIT"));
        assertEquals(7, snap.get("qwen-image-3.0").maxReferenceImages());
        assertEquals("2099-01-01", snap.get("qwen-image-3.0").testedAt());
    }

    @Test
    @DisplayName("外部文件不可读时回落内置默认，不抛异常、不返回空档位")
    void unreadableExternalFileFallsBackWithoutBreakingAnything(@TempDir Path dir) {
        Map<String, CloudImageValidation.Entry> snap =
            CloudImageValidation.load(dir.resolve("does-not-exist.json").toString());
        assertFalse(snap.isEmpty(), "回落必须是内置默认，不能是空档位（那会让所有能力不可提交）");
        assertEquals(EXPECTED.keySet(), snap.keySet());
    }

    @Test
    @DisplayName("内置默认值本身必须完整（文件缺失时的最后一道兜底）")
    void builtInDefaultsAreComplete() {
        List<String> models = List.of("flux-2-pro", "gpt-image-2.5-flare", "gpt-image-2.5-sunburst",
            "qwen-image-3.0", "qwen-image-3.0-pro", "wan2.7-image", "wan2.7-image-pro");
        try (InputStream in = CloudImageValidation.class
            .getResourceAsStream("/cloud-image-validation.json")) {
            // 文件必须存在；若被误删，这里直接失败而不是静默走兜底
            assertNotNull(in, "classpath 缺少 /cloud-image-validation.json（ADR-008 要求它在数据文件里）");
        } catch (Exception e) {
            fail("读取 classpath 档位失败：" + e);
        }
        assertEquals(7, models.size());
    }
}
