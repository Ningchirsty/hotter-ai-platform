package org.dromara.aigov.task.helper;

import org.dromara.aigov.task.config.AigArtifactProperties;
import org.dromara.aigov.task.domain.bo.AigTaskArtifactBo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 制品登记校验的边界测试。
 *
 * <p><b>为什么这段要逐条钉住</b>：这些规则的失效方式都是<b>安静地放行</b>——
 * 清单比对写成「包含」、大小写成 {@code >}、路径判断只认 Unix 前缀，
 * 都会让某类不该收的制品收进来，而系统不会报任何错。
 * 因此这里既有正例（合法的要过），也有反例（每条规则都要能拦住）。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigArtifactValidatorTest {

    private static final String SHA = "9f2b1c4d5e6f708192a3b4c5d6e7f8091a2b3c4d5e6f708192a3b4c5d6e7f809";

    private AigArtifactProperties properties() {
        return new AigArtifactProperties();
    }

    private AigTaskArtifactBo valid() {
        AigTaskArtifactBo bo = new AigTaskArtifactBo();
        bo.setTaskId(1L);
        bo.setArtifactType("IMAGE");
        bo.setMimeType("image/png");
        bo.setSizeBytes(1024L);
        bo.setSha256(SHA);
        bo.setStorageRef("aig-private/artifact/1/out-9f2b1c4d.png");
        return bo;
    }

    @Test
    @DisplayName("合法制品：通过，且 MIME 与 sha256 被归一化为小写")
    void validArtifactPasses() {
        AigTaskArtifactBo bo = valid();
        bo.setMimeType("  IMAGE/PNG  ");
        bo.setSha256(SHA.toUpperCase());

        AigArtifactValidator.AigArtifactCheck check = AigArtifactValidator.check(bo, properties());

        assertTrue(check.passed(), "合法制品不该被拦：" + check.detail());
        assertNull(check.detail(), "通过时不该有失败明细");
        assertEquals("image/png", check.mimeType(), "MIME 按 RFC 大小写不敏感，统一小写存储");
        assertEquals(SHA, check.sha256(), "sha256 归一化为小写（契约的 pattern 是小写）");
        assertEquals("IMAGE", check.artifactType());
        assertEquals("aig-private/artifact/1/out-9f2b1c4d.png", check.storageRef());
    }

    @Test
    @DisplayName("★ MIME 不在允许清单内 → 拦住，且明细里给出清单（让人一次改对）")
    void mimeNotAllowedIsRejected() {
        AigTaskArtifactBo bo = valid();
        bo.setMimeType("text/html");

        AigArtifactValidator.AigArtifactCheck check = AigArtifactValidator.check(bo, properties());

        assertFalse(check.passed());
        assertTrue(check.detail().contains("text/html"), check.detail());
        assertTrue(check.detail().contains("不在允许清单内"), check.detail());
        assertTrue(check.detail().contains("image/png"), "要把允许的类型列出来：" + check.detail());
    }

    @Test
    @DisplayName("★ 允许清单支持 image/* 族通配（新图片格式不必改代码）")
    void mimeFamilyWildcardIsSupported() {
        AigArtifactProperties props = properties();
        props.setAllowedMimeTypes(new ArrayList<>(List.of("image/*")));
        AigTaskArtifactBo bo = valid();
        bo.setMimeType("image/heic");

        assertTrue(AigArtifactValidator.check(bo, props).passed(), "image/* 应命中 image/heic");

        // 但只按 "类型/" 前缀匹配：不相关的族仍要被拦
        bo.setMimeType("video/mp4");
        assertFalse(AigArtifactValidator.check(bo, props).passed(), "image/* 不该放过 video/mp4");
    }

    @Test
    @DisplayName("★ 允许清单为空 → 全部拒绝（配置写错的默认边必须选失败的那边）")
    void emptyAllowListRejectsEverything() {
        AigArtifactProperties props = properties();
        props.setAllowedMimeTypes(new ArrayList<>());

        AigArtifactValidator.AigArtifactCheck check = AigArtifactValidator.check(valid(), props);

        assertFalse(check.passed(), "空清单最可能是配置写错，绝不能解释成「不限制」");
        assertTrue(check.detail().contains("允许清单为空"), check.detail());
        assertTrue(check.detail().contains("aigov.artifact.allowed-mime-types"),
            "要说清去哪改：" + check.detail());
    }

    @Test
    @DisplayName("MIME 形态不对（缺 类型/子类型）→ 拦住")
    void malformedMimeIsRejected() {
        AigTaskArtifactBo bo = valid();
        bo.setMimeType("png");

        AigArtifactValidator.AigArtifactCheck check = AigArtifactValidator.check(bo, properties());

        assertFalse(check.passed());
        assertTrue(check.detail().contains("形态不对"), check.detail());
    }

    @Test
    @DisplayName("大小：超过上限 / 负数 / 缺失 都要拦住，边界值等于上限算过")
    void sizeBoundaries() {
        AigArtifactProperties props = properties();
        props.setMaxSizeBytes(2048L);

        AigTaskArtifactBo tooBig = valid();
        tooBig.setSizeBytes(2049L);
        AigArtifactValidator.AigArtifactCheck big = AigArtifactValidator.check(tooBig, props);
        assertFalse(big.passed(), "超过上限要拦住");
        assertTrue(big.detail().contains("2049"), big.detail());
        assertTrue(big.detail().contains("2048"), "明细要有上限数字，否则不知道超了多少：" + big.detail());

        AigTaskArtifactBo atLimit = valid();
        atLimit.setSizeBytes(2048L);
        assertTrue(AigArtifactValidator.check(atLimit, props).passed(), "恰好等于上限应当放过（判据是 ≤）");

        AigTaskArtifactBo negative = valid();
        negative.setSizeBytes(-1L);
        assertFalse(AigArtifactValidator.check(negative, props).passed());

        AigTaskArtifactBo missing = valid();
        missing.setSizeBytes(null);
        AigArtifactValidator.AigArtifactCheck none = AigArtifactValidator.check(missing, props);
        assertFalse(none.passed());
        assertTrue(none.detail().contains("大小不能为空"), none.detail());
    }

    @Test
    @DisplayName("sha256：长度不对、含非十六进制字符都要拦住")
    void sha256Shape() {
        AigTaskArtifactBo shortHash = valid();
        shortHash.setSha256(SHA.substring(0, 63));
        AigArtifactValidator.AigArtifactCheck a = AigArtifactValidator.check(shortHash, properties());
        assertFalse(a.passed(), "63 位不是 64 位");
        assertTrue(a.detail().contains("64 位"), a.detail());

        AigTaskArtifactBo badChars = valid();
        badChars.setSha256("z".repeat(64));
        AigArtifactValidator.AigArtifactCheck b = AigArtifactValidator.check(badChars, properties());
        assertFalse(b.passed(), "非十六进制要拦住");
        assertTrue(b.detail().contains("非十六进制"), "长度对但字符不对时要说清是字符问题：" + b.detail());

        AigTaskArtifactBo empty = valid();
        empty.setSha256("   ");
        AigArtifactValidator.AigArtifactCheck c = AigArtifactValidator.check(empty, properties());
        assertFalse(c.passed());
        assertTrue(c.detail().contains("sha256 不能为空"), c.detail());
    }

    @Test
    @DisplayName("★ 对象引用不得是服务器本地路径（四种形态都要拦住）")
    void storageRefMustNotBeLocalPath() {
        String[] bad = {
            "C:\\out\\image.png",
            "D:/data/out.png",
            "/var/lib/hotter/out.png",
            "file:///tmp/out.png",
            "aig-private\\artifact\\out.png",
        };
        for (String ref : bad) {
            AigTaskArtifactBo bo = valid();
            bo.setStorageRef(ref);
            AigArtifactValidator.AigArtifactCheck check = AigArtifactValidator.check(bo, properties());
            assertFalse(check.passed(), "本地路径必须拦住：" + ref);
        }
    }

    @Test
    @DisplayName("对象引用含上跳段（..）→ 拦住；但名字里恰好含两个点的不算")
    void storageRefTraversal() {
        AigTaskArtifactBo traversal = valid();
        traversal.setStorageRef("aig-private/artifact/../../etc/passwd");
        AigArtifactValidator.AigArtifactCheck a = AigArtifactValidator.check(traversal, properties());
        assertFalse(a.passed(), "上跳路径必须拦住");
        assertTrue(a.detail().contains("上跳"), a.detail());

        // 尺寸：按「段」判断，段名里含两个点不该被误伤（误伤 = 合法产出被拒）
        AigTaskArtifactBo lookalike = valid();
        lookalike.setStorageRef("aig-private/artifact/v1..2/out.png");
        assertTrue(AigArtifactValidator.check(lookalike, properties()).passed(),
            "v1..2 不是上跳段，不该被拦住");
    }

    @Test
    @DisplayName("★ 多处同时不合格 → 一次把原因列全（不是只报第一条）")
    void allProblemsReportedTogether() {
        AigTaskArtifactBo bo = new AigTaskArtifactBo();
        bo.setArtifactType("X".repeat(40));
        bo.setMimeType("text/html");
        bo.setSizeBytes(-5L);
        bo.setSha256("not-a-hash");
        bo.setStorageRef("/tmp/out.png");

        AigArtifactValidator.AigArtifactCheck check = AigArtifactValidator.check(bo, properties());

        assertFalse(check.passed());
        String detail = check.detail();
        assertTrue(detail.contains("制品类型长度"), detail);
        assertTrue(detail.contains("不在允许清单内"), detail);
        assertTrue(detail.contains("不能为负数"), detail);
        assertTrue(detail.contains("64 位"), detail);
        assertTrue(detail.contains("绝对路径"), detail);
    }

    @Test
    @DisplayName("入参为 null → 拒绝而不是抛 NPE")
    void nullBoIsRejected() {
        AigArtifactValidator.AigArtifactCheck check = AigArtifactValidator.check(null, properties());
        assertFalse(check.passed());
        assertTrue(check.detail().contains("入参不能为空"), check.detail());
    }

}
