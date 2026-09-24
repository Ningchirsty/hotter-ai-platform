package org.dromara.creative.helper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 参考图实测分析器的单元测试（合成图，不依赖外部素材与模型）。
 *
 * <p>为什么用合成图：这些结论会直接进视觉基因，必须能回答「凭什么给出这个值」——
 * 用「白底 + 正中纯色方块」这种已知答案的图，才能验证量出来的背景色、产品占比、留白、
 * 主色确实是对的；用真实照片反而说不清对错。</p>
 */
class ReferenceImageAnalyzerTest {

    private final ReferenceImageAnalyzer analyzer = new ReferenceImageAnalyzer();

    @Test
    @DisplayName("白底 + 正中红块：背景/主色/留白/占比/场景都要量对")
    void analyzesSyntheticImage() {
        byte[] png = image(200, 200, g -> {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 200, 200);
            g.setColor(Color.RED);
            g.fillRect(60, 60, 80, 80);   // 16% 面积
        });

        ReferenceImageAnalyzer.Analysis result = analyzer.analyze(png);
        assertNotNull(result);
        Map<String, ReferenceImageAnalyzer.Recommendation> byField = index(result);

        String background = String.valueOf(byField.get("colors.background").value());
        assertTrue(background.startsWith("#F"), "背景应接近白色，实际 " + background);

        String primary = String.valueOf(byField.get("colors.primary").value());
        assertEquals("#FF0000", primary, "主色应为纯红，实际 " + primary);

        assertEquals("HIGH", byField.get("whitespaceLevel").value(), "留白应为高（84% 背景）");
        assertEquals("纯色底", byField.get("sceneType").value(), "背景一致度极高 → 纯色底");
        assertEquals("HIGH", byField.get("saturation").value(), "纯红是高饱和");

        int[] ratio = parseRatio(String.valueOf(byField.get("productRatio").value()));
        assertTrue(ratio[0] <= 16 && ratio[1] >= 16, "占比区间应覆盖实测 16%，实际 " + ratio[0] + "~" + ratio[1]);
        assertTrue(result.observedRatio() > 12 && result.observedRatio() < 20,
            "实测占比应接近 16%，实际 " + result.observedRatio());
    }

    @Test
    @DisplayName("灰阶图：饱和度应为低；且必须明确说「风格关键词/禁忌词/字体风格」测不出来")
    void grayImageIsLowSaturationAndSkipsSemanticFields() {
        byte[] png = image(160, 160, g -> {
            g.setColor(new Color(128, 128, 128));
            g.fillRect(0, 0, 160, 160);
            g.setColor(new Color(90, 90, 90));
            g.fillRect(40, 40, 80, 80);
        });

        ReferenceImageAnalyzer.Analysis result = analyzer.analyze(png);
        Map<String, ReferenceImageAnalyzer.Recommendation> byField = index(result);
        assertEquals("LOW", byField.get("saturation").value(), "灰阶图饱和度应为低");

        String skipped = String.join(" | ", result.skipped());
        assertTrue(skipped.contains("风格关键词"), skipped);
        assertTrue(skipped.contains("禁忌关键词"), skipped);
        assertTrue(skipped.contains("字体风格"), skipped);
    }

    @Test
    @DisplayName("纯色空图：识别不出主体时要如实说明，而不是硬编一个主色")
    void uniformImageHasNoSubject() {
        byte[] png = image(120, 120, g -> {
            g.setColor(new Color(240, 240, 240));
            g.fillRect(0, 0, 120, 120);
        });

        ReferenceImageAnalyzer.Analysis result = analyzer.analyze(png);
        assertTrue(result.notes().stream().anyMatch(n -> n.contains("没有识别出与背景明显不同的主体")),
            String.join(" | ", result.notes()));
        assertTrue(result.observedRatio() < 5, "纯色图主体占比应接近 0，实际 " + result.observedRatio());
    }

    @Test
    @DisplayName("解不开或空字节返回 null（调用方据此给出可读提示）")
    void unreadableReturnsNull() {
        assertNull(analyzer.analyze(null));
        assertNull(analyzer.analyze(new byte[0]));
        assertNull(analyzer.analyze("not an image".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    // ------------------------------------------------------------------

    private static byte[] image(int width, int height, java.util.function.Consumer<Graphics2D> painter) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        painter.accept(g);
        g.dispose();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, ReferenceImageAnalyzer.Recommendation> index(
        ReferenceImageAnalyzer.Analysis analysis) {
        return analysis.recommendations().stream()
            .collect(Collectors.toMap(ReferenceImageAnalyzer.Recommendation::field, Function.identity(),
                (a, b) -> a));
    }

    private static int[] parseRatio(String value) {
        String[] parts = value.split("~");
        return new int[] {Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
    }

}
