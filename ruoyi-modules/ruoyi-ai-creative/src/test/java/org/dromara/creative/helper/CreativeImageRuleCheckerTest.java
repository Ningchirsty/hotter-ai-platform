package org.dromara.creative.helper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 交付图确定性规则体检（V0.2 R29）。
 *
 * <p>全部用**合成的图**做自变量，因为要钉的是度量算法本身：白底 + 居中色块应该判过、
 * 长方形应该判 CANVAS_SQUARE 不过、带 alpha 的应该判 NO_ALPHA 不过、
 * 色块铺到边上的应该判 EDGE_BLEED 不过。真机那边验的是"真实交付图上的数值"，
 * 这里验的是"阈值到了就翻脸"。</p>
 *
 * <p>还有一条最重要的口径：<b>没配规则 → NOT_CONFIGURED，不是 PASS</b>。
 * "没检查"和"检查通过"必须能分开，否则页面会出现假绿。</p>
 */
class CreativeImageRuleCheckerTest {

    private static final String MAIN_RULES = """
        {
          "schema": "screen-qa/1",
          "square": true,
          "minSide": 800,
          "alphaForbidden": true,
          "whiteBackground": {"enabled": true, "minEdgeWhiteness": 0.90},
          "subjectRatio": {"enabled": true, "min": 0.10},
          "edgeBleed": {"enabled": true, "maxRatio": 0.01}
        }
        """;

    /**
     * 画一张白底图，中间放一个居中的色块。
     *
     * @param width   画布宽
     * @param height  画布高
     * @param block   色块边长（0 表示不放）
     * @param blockColor 色块颜色
     * @param fullBleed 色块是否横向铺满并贴住上下边（用来造"被裁切"的图）
     * @return PNG 字节
     */
    private static byte[] image(int width, int height, int block, Color blockColor, boolean fullBleed)
        throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        if (block > 0) {
            g.setColor(blockColor);
            if (fullBleed) {
                g.fillRect(0, 0, width, block);
            } else {
                g.fillRect((width - block) / 2, (height - block) / 2, block, block);
            }
        }
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static CreativeImageRuleChecker.Finding finding(CreativeImageRuleChecker.Report report, String code) {
        return report.findings().stream().filter(f -> f.code().equals(code)).findFirst().orElse(null);
    }

    @Test
    @DisplayName("没配规则 → NOT_CONFIGURED，不是 PASS（「没检查」与「检查通过」必须分开）")
    void unconfiguredIsNotPass() {
        CreativeImageRuleChecker.Report report =
            CreativeImageRuleChecker.inspect(new byte[]{1, 2, 3}, CreativeQaRules.NONE);

        assertFalse(report.configured());
        assertEquals(CreativeImageRuleChecker.VERDICT_NOT_CONFIGURED, report.verdict());
        assertTrue(report.findings().isEmpty(), "没配规则就不该产出任何检查项");
        assertFalse(report.passed(), "没配规则绝不能算通过");
    }

    @Test
    @DisplayName("800×800 白底 + 居中主体 → 全部通过，且度量值可核对")
    void cleanMainImagePasses() throws Exception {
        CreativeQaRules rules = CreativeQaRules.parse(MAIN_RULES);
        // 主体 400×400 = 160000 像素 / 640000 ≈ 25% > 10% 下限
        CreativeImageRuleChecker.Report report =
            CreativeImageRuleChecker.inspect(image(800, 800, 400, new Color(40, 90, 60), false), rules);

        assertEquals(CreativeImageRuleChecker.VERDICT_PASS, report.verdict(), report.findings().toString());
        assertTrue(report.passed());
        assertEquals(800, report.metrics().get("width"));
        assertEquals(800, report.metrics().get("height"));
        assertEquals(1.0d, (double) report.metrics().get("edgeWhiteness"), 0.001d,
            "边缘全是白 → 边缘白度应为 1");
        assertTrue((double) report.metrics().get("subjectRatio") > 0.2d,
            "主体占比应接近 25%：" + report.metrics().get("subjectRatio"));
        assertEquals(0.0d, (double) report.metrics().get("edgeBleedRatio"), 0.001d, "居中主体不该贴边");
        assertTrue(report.findings().stream().allMatch(CreativeImageRuleChecker.Finding::ok));
    }

    @Test
    @DisplayName("1024×768 → CANVAS_SQUARE 与 MIN_SIDE 都不过（HARD），且逐项如实说明实测值")
    void nonSquareFailsHard() throws Exception {
        CreativeImageRuleChecker.Report report = CreativeImageRuleChecker.inspect(
            image(1024, 768, 300, new Color(40, 90, 60), false), CreativeQaRules.parse(MAIN_RULES));

        assertEquals(CreativeImageRuleChecker.VERDICT_HARD_FAILED, report.verdict());
        assertEquals(2, report.hardFailed(), report.findings().toString());
        assertFalse(finding(report, "CANVAS_SQUARE").ok());
        assertEquals(CreativeQaRules.LEVEL_HARD, finding(report, "CANVAS_SQUARE").level());
        assertTrue(finding(report, "CANVAS_SQUARE").detail().contains("1024×768"),
            finding(report, "CANVAS_SQUARE").detail());
        assertFalse(finding(report, "MIN_SIDE").ok(), "最短边 768 < 800，应不过");
        assertTrue(finding(report, "MIN_SIDE").detail().contains("768"), finding(report, "MIN_SIDE").detail());
    }

    @Test
    @DisplayName("最短边不足 800 → MIN_SIDE 不过（HARD）")
    void tooSmallFailsMinSide() throws Exception {
        CreativeImageRuleChecker.Report report = CreativeImageRuleChecker.inspect(
            image(600, 600, 200, new Color(40, 90, 60), false), CreativeQaRules.parse(MAIN_RULES));

        assertEquals(CreativeImageRuleChecker.VERDICT_HARD_FAILED, report.verdict());
        assertFalse(finding(report, "MIN_SIDE").ok());
        assertTrue(finding(report, "MIN_SIDE").detail().contains("600"), finding(report, "MIN_SIDE").detail());
        assertTrue(finding(report, "CANVAS_SQUARE").ok(), "600×600 仍是正方形");
    }

    @Test
    @DisplayName("主体铺到画布边 → EDGE_BLEED 不过（SOFT），HARD 项仍过")
    void bleedingSubjectFailsSoft() throws Exception {
        // 400px 高的色块横向铺满并贴住上边：贴边主体占比很高
        CreativeImageRuleChecker.Report report = CreativeImageRuleChecker.inspect(
            image(800, 800, 400, new Color(40, 90, 60), true), CreativeQaRules.parse(MAIN_RULES));

        assertEquals(CreativeImageRuleChecker.VERDICT_SOFT_ONLY, report.verdict(), report.findings().toString());
        assertEquals(0, report.hardFailed());
        assertTrue(report.softFailed() >= 1);
        assertFalse(finding(report, "EDGE_BLEED").ok());
        assertTrue((double) report.metrics().get("edgeBleedRatio") > 0.01d);
    }

    @Test
    @DisplayName("白底要求开着但边缘是深色 → WHITE_BACKGROUND 不过（SOFT）")
    void nonWhiteBackgroundFailsSoft() throws Exception {
        BufferedImage image = new BufferedImage(800, 800, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(30, 30, 30));
        g.fillRect(0, 0, 800, 800);
        g.setColor(Color.WHITE);
        g.fillRect(300, 300, 200, 200);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);

        CreativeImageRuleChecker.Report report =
            CreativeImageRuleChecker.inspect(out.toByteArray(), CreativeQaRules.parse(MAIN_RULES));

        assertFalse(finding(report, "WHITE_BACKGROUND").ok());
        assertEquals(CreativeQaRules.LEVEL_SOFT, finding(report, "WHITE_BACKGROUND").level());
        assertEquals(CreativeImageRuleChecker.VERDICT_SOFT_ONLY, report.verdict());
    }

    @Test
    @DisplayName("带透明通道 → NO_ALPHA 不过（HARD）")
    void alphaFailsHard() throws Exception {
        BufferedImage image = new BufferedImage(800, 800, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        // 必须用 AlphaComposite.Src：默认的 SrcOver 会把半透明色**合成到**不透明白底上，
        // 结果 alpha 又变回 255——第一版就是这么写的，于是这张"带透明"的图其实没有透明像素。
        g.setComposite(java.awt.AlphaComposite.Src);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 800, 800);
        g.setColor(new Color(40, 90, 60, 120));
        g.fillRect(200, 200, 400, 400);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);

        CreativeImageRuleChecker.Report report =
            CreativeImageRuleChecker.inspect(out.toByteArray(), CreativeQaRules.parse(MAIN_RULES));

        assertEquals(Boolean.TRUE, report.metrics().get("hasAlpha"), "合成口径不对会让这条断言失去意义");
        assertFalse(finding(report, "NO_ALPHA").ok());
        assertEquals(CreativeQaRules.LEVEL_HARD, finding(report, "NO_ALPHA").level());
        assertEquals(CreativeImageRuleChecker.VERDICT_HARD_FAILED, report.verdict());
    }

    @Test
    @DisplayName("不是图片的字节 → UNREADABLE（如实说没体检，不假装通过）")
    void unreadableBytesAreReportedHonestly() {
        CreativeImageRuleChecker.Report report =
            CreativeImageRuleChecker.inspect("这不是图片".getBytes(), CreativeQaRules.parse(MAIN_RULES));

        assertEquals(CreativeImageRuleChecker.VERDICT_UNREADABLE, report.verdict());
        assertEquals(1, report.hardFailed());
        assertFalse(report.passed());
        assertNotNull(finding(report, "UNREADABLE"));
    }

    @Test
    @DisplayName("落库 JSON 能被读回（verdictOf 取总结论；明细里带实测值）")
    void reportJsonIsReadable() throws Exception {
        CreativeImageRuleChecker.Report report = CreativeImageRuleChecker.inspect(
            image(800, 800, 400, new Color(40, 90, 60), false), CreativeQaRules.parse(MAIN_RULES));
        String json = report.toJson();

        assertEquals(CreativeImageRuleChecker.VERDICT_PASS, CreativeImageRuleChecker.verdictOf(json));
        assertTrue(json.contains("\"edgeWhiteness\""), json);
        assertTrue(json.contains("\"CANVAS_SQUARE\""), json);
        assertEquals(null, CreativeImageRuleChecker.verdictOf(null));
        assertEquals(null, CreativeImageRuleChecker.verdictOf("{}"));
    }
}
