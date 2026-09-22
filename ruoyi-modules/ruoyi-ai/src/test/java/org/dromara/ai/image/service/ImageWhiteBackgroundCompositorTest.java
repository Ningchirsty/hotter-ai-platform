package org.dromara.ai.image.service;

import org.dromara.ai.image.exception.ImageTaskException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 白底图合成器测试。
 *
 * <p>守的是「白底图」这条链路的两个承诺：<b>背景恒为纯白</b>、<b>产品像素不变</b>，
 * 以及两条防「假白底图」的硬断言（没有 alpha 通道 / 抠图根本没抠掉）。</p>
 */
class ImageWhiteBackgroundCompositorTest {

    private final ImageWhiteBackgroundCompositor compositor = new ImageWhiteBackgroundCompositor();

    /**
     * 造一张「中心不透明方块 + 四周全透明」的蒙版图，模拟抠图输出。
     */
    private static byte[] matte(int size, int inset) throws Exception {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(200, 20, 20));           // 主体颜色
        g.fillRect(inset, inset, size - inset * 2, size - inset * 2);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static byte[] opaqueRgb(int size) throws Exception {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(10, 200, 10));
        g.fillRect(0, 0, size, size);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static byte[] fullyOpaqueAlpha(int size) throws Exception {
        // 有 alpha 通道但没有任何透明像素：等价于「抠图什么都没抠掉」
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(30, 60, 90, 255));
        g.fillRect(0, 0, size, size);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static BufferedImage decode(byte[] bytes) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    @Test
    @DisplayName("合成结果：背景是纯白、主体像素不变、且不再带 alpha")
    void compositeProducesPureWhiteAndKeepsSubject() throws Exception {
        byte[] source = matte(64, 16);
        BufferedImage before = decode(source);

        byte[] result = compositor.compositeOnWhite(source);
        BufferedImage after = decode(result);

        assertEquals(before.getWidth(), after.getWidth(), "宽度不得改变");
        assertEquals(before.getHeight(), after.getHeight(), "高度不得改变");
        assertFalse(after.getColorModel().hasAlpha(), "交付物必须是不透明 PNG（白底已铺满）");

        // 四角原来是全透明的，合成后必须是纯白 255,255,255
        for (int[] point : new int[][]{{0, 0}, {63, 0}, {0, 63}, {63, 63}, {2, 30}}) {
            int rgb = after.getRGB(point[0], point[1]) & 0xFFFFFF;
            assertEquals(0xFFFFFF, rgb,
                "透明区域必须变成纯白，实际 " + Integer.toHexString(rgb) + " @ " + point[0] + "," + point[1]);
        }

        // 主体区域必须与合成前完全一致（像素级不变）——这是白底图存在的意义
        int cx = 32;
        int cy = 32;
        assertEquals(before.getRGB(cx, cy) & 0xFFFFFF, after.getRGB(cx, cy) & 0xFFFFFF,
            "主体像素不得被改动");
        assertEquals(new Color(200, 20, 20).getRGB() & 0xFFFFFF, after.getRGB(cx, cy) & 0xFFFFFF);
    }

    @Test
    @DisplayName("半透明边缘按 alpha 混合到白底，而不是直接变白或变黑")
    void compositeBlendsSemiTransparentEdges() throws Exception {
        // 左半幅全透明（保证通过「抠图确实生效」的断言），右半幅半透明黑
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(0, 0, 0, 128));
        g.fillRect(4, 0, 4, 8);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        byte[] source = out.toByteArray();

        BufferedImage after = decode(compositor.compositeOnWhite(source));
        assertEquals(0xFFFFFF, after.getRGB(0, 0) & 0xFFFFFF, "全透明区域应为纯白");
        int rgb = after.getRGB(5, 5) & 0xFFFFFF;
        int red = (rgb >> 16) & 0xFF;
        // 128/255 的黑叠在白上约等于 127 灰；允许 ±3 的取整误差
        assertTrue(Math.abs(red - 127) <= 3, "半透明边缘应按 alpha 混合，实际灰度 " + red);
    }

    @Test
    @DisplayName("抠图输出没有 alpha 通道：拒绝，不合成假白底")
    void rejectsSourceWithoutAlpha() throws Exception {
        byte[] source = opaqueRgb(16);
        ImageTaskException e = assertThrows(ImageTaskException.class, () -> compositor.compositeOnWhite(source));
        assertEquals("OUTPUT_INVALID", e.getErrorCode());
        assertTrue(e.getMessage().contains("透明通道"), "报错要说清原因：" + e.getMessage());
    }

    @Test
    @DisplayName("抠图没生效（有 alpha 但零透明像素）：拒绝，避免把原图当白底图交付")
    void rejectsWhenNothingWasRemoved() throws Exception {
        byte[] source = fullyOpaqueAlpha(32);
        ImageTaskException e = assertThrows(ImageTaskException.class, () -> compositor.compositeOnWhite(source));
        assertEquals("OUTPUT_INVALID", e.getErrorCode());
        assertTrue(e.getMessage().contains("抠图未生效"), "报错要说清原因：" + e.getMessage());
    }

    @Test
    @DisplayName("空内容 / 非图片内容：拒绝并给出可读原因")
    void rejectsGarbage() {
        assertEquals("OUTPUT_INVALID",
            assertThrows(ImageTaskException.class, () -> compositor.compositeOnWhite(new byte[0])).getErrorCode());
        byte[] garbage = "not an image".getBytes(StandardCharsets.UTF_8);
        ImageTaskException e = assertThrows(ImageTaskException.class, () -> compositor.compositeOnWhite(garbage));
        assertEquals("OUTPUT_INVALID", e.getErrorCode());
    }

    @Test
    @DisplayName("合成结果可被 ImageIO 重新解析，尺寸与语义保持")
    void resultIsReReadable() throws Exception {
        byte[] result = compositor.compositeOnWhite(matte(48, 8));
        BufferedImage again = decode(result);
        assertEquals(48, again.getWidth());
        assertEquals(48, again.getHeight());
        assertFalse(again.getColorModel().hasAlpha());
    }
}
