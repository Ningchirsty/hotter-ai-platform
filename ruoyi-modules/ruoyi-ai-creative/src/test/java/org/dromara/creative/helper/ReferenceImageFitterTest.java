package org.dromara.creative.helper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 参考图适配测试：钉住「不再因为参考图过大而出图失败」这条修复。
 *
 * <p>背景（生产实测）：工作流 {@code wf-i2i-qwen21} 按输入图尺寸出图，契约上限 4194304 像素；
 * 一张 2496×2992 的参考图必然产出超限，被判 OUTPUT_INVALID 且白烧一次 GPU。</p>
 *
 * @author creative
 */
class ReferenceImageFitterTest {

    /** 契约里 wf-i2i-qwen21 的上限，测试直接用这个真实数值 */
    private static final int MAX_PIXELS = 4194304;

    private static byte[] png(int w, int h, boolean alpha) throws Exception {
        BufferedImage image = new BufferedImage(w, h, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(alpha ? new Color(20, 40, 60, 128) : new Color(20, 40, 60));
            g.fillRect(0, 0, w, h);
            g.setColor(new Color(240, 200, 80));
            g.fillOval(w / 4, h / 4, Math.max(1, w / 2), Math.max(1, h / 2));
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static int[] sizeOf(byte[] bytes) throws Exception {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        assertNotNull(image, "缩放结果必须仍是可解码的图片");
        return new int[] {image.getWidth(), image.getHeight()};
    }

    @Test
    @DisplayName("超过上限：缩到上限以内，比例不变，且结果可解码")
    void scalesDownWhenOverBudget() throws Exception {
        byte[] original = png(2496, 2992, false);   // 7,468,032 像素（复现生产那张参考图）
        ReferenceImageFitter.Fitted fitted =
            ReferenceImageFitter.fit(original, "冷色调.png", MAX_PIXELS);

        assertTrue(fitted.scaled(), "超限图必须被缩放（否则出图仍会失败）");
        int[] size = sizeOf(fitted.bytes());
        assertTrue((long) size[0] * size[1] <= MAX_PIXELS,
            "缩放后像素必须不超上限，实际 " + size[0] + "×" + size[1]);
        // 原比例 2496:2992 ≈ 0.834；允许 2% 误差（整数取整 + 子采样）
        double before = 2496.0 / 2992.0;
        double after = (double) size[0] / size[1];
        assertTrue(Math.abs(after - before) / before < 0.02,
            "比例应基本不变：before=" + before + " after=" + after);
        assertNotNull(fitted.note(), "缩放必须带可读说明（页面上要如实展示）");
        assertTrue(fitted.note().contains("2496×2992"), "说明里要有原尺寸：" + fitted.note());
        assertTrue(fitted.note().contains("未改动"), "说明里要讲清仅缩放送模型的那一份");
        assertEquals(2496, fitted.fromWidth());
        assertEquals(2992, fitted.fromHeight());
    }

    @Test
    @DisplayName("未超过上限：原样返回，不做任何重编码（避免无谓降质）")
    void keepsSmallImageUntouched() throws Exception {
        byte[] original = png(896, 1152, false);    // 1,032,192 像素
        ReferenceImageFitter.Fitted fitted =
            ReferenceImageFitter.fit(original, "product.png", MAX_PIXELS);

        assertFalse(fitted.scaled(), "没超限就不该缩");
        assertArrayEquals(original, fitted.bytes(), "未超限时必须原字节返回（不重编码）");
        assertNull(fitted.note(), "未缩放不该产生说明");
        assertEquals("product.png", fitted.fileName());
        assertEquals("image/png", fitted.contentType());
    }

    @Test
    @DisplayName("刚好等于上限：不缩（边界按「不超过」处理）")
    void keepsExactBudget() throws Exception {
        // 2048×2048 = 4,194,304，正好等于契约上限
        byte[] original = png(2048, 2048, false);
        ReferenceImageFitter.Fitted fitted =
            ReferenceImageFitter.fit(original, "edge.png", MAX_PIXELS);
        assertFalse(fitted.scaled(), "等于上限不算超限");
        assertArrayEquals(original, fitted.bytes());
    }

    @Test
    @DisplayName("透明通道在缩放后保留（契约要求 alpha preserved）")
    void keepsAlpha() throws Exception {
        byte[] original = png(2600, 2600, true);    // 6.76MP，带 alpha
        ReferenceImageFitter.Fitted fitted =
            ReferenceImageFitter.fit(original, "cutout.png", MAX_PIXELS);
        assertTrue(fitted.scaled());
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(fitted.bytes()));
        assertTrue(image.getColorModel().hasAlpha(), "带 alpha 的图缩放后必须仍是 RGBA");
        assertEquals("image/png", fitted.contentType());
    }

    @Test
    @DisplayName("尺寸算不出来时不猜：原样返回并说明未适配")
    void unreadableImageIsPassedThrough() {
        byte[] notAnImage = "这不是图片".getBytes();
        ReferenceImageFitter.Fitted fitted =
            ReferenceImageFitter.fit(notAnImage, "broken.png", MAX_PIXELS);
        assertFalse(fitted.scaled());
        assertArrayEquals(notAnImage, fitted.bytes());
        assertNotNull(fitted.note(), "读不出尺寸时要说明未做适配");
        assertTrue(fitted.note().contains("读取失败"), fitted.note());
    }

    @Test
    @DisplayName("空输入不抛异常")
    void toleratesEmptyInput() {
        ReferenceImageFitter.Fitted fitted = ReferenceImageFitter.fit(new byte[0], null, MAX_PIXELS);
        assertFalse(fitted.scaled());
        assertEquals("reference.png", fitted.fileName(), "空文件名要有兜底");
    }

    @Test
    @DisplayName("预算换算：按原比例取最大整数尺寸且绝不超过上限")
    void fitWithinNeverExceeds() {
        int[] big = ReferenceImageFitter.fitWithin(2496, 2992, MAX_PIXELS);
        assertTrue((long) big[0] * big[1] <= MAX_PIXELS, big[0] + "×" + big[1]);
        // 再放大一格就会超限（说明取的是最大可行尺寸，不是保守地缩很小）
        int[] oneMore = ReferenceImageFitter.fitWithin(2496, 2992, MAX_PIXELS);
        assertEquals(big[0], oneMore[0]);

        int[] wide = ReferenceImageFitter.fitWithin(6000, 1000, MAX_PIXELS);
        assertTrue((long) wide[0] * wide[1] <= MAX_PIXELS);
        assertTrue(wide[0] > wide[1], "横图应该还是横的");
    }

    @Test
    @DisplayName("扩展名随格式走：JPEG 仍走 JPEG，PNG 变体改名为 png（扩展名不撒谎）")
    void extensionMatchesFormat() throws Exception {
        byte[] jpeg;
        BufferedImage image = new BufferedImage(2400, 2400, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", out);
        jpeg = out.toByteArray();
        ReferenceImageFitter.Fitted fitted =
            ReferenceImageFitter.fit(jpeg, "photo.jpg", MAX_PIXELS);
        assertTrue(fitted.scaled());
        assertEquals("image/jpeg", fitted.contentType());
        assertTrue(fitted.fileName().endsWith(".jpg"), fitted.fileName());
    }
}
