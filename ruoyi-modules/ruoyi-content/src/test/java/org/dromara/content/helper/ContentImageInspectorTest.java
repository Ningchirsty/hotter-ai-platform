package org.dromara.content.helper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 成品一致性检查「原图 vs 成品图」本地度量的行为锁定测试。
 *
 * <p><b>为什么必须单独测这一段</b>：这是验收环节唯一的确定性判据，
 * 判错了的后果是不合格成品被放行（假「一致」）或合格成品被拦下（假「不一致」）。
 * 它又是纯函数、不依赖 Spring 与模型，正好用单测把口径钉死。</p>
 *
 * <p><b>回归重点</b>：早先的实现只比对<b>亮度</b>网格，纯红与纯蓝只能算出 82/100（判「无法判定」），
 * 两张毫无关系的高频图能算出 97/100（直接判「一致」）——也就是说它几乎不可能判出「不一致」。
 * 下面用「结构相同、颜色完全不同」与「两张无关图」两个用例把这个口子堵住。</p>
 *
 * <p>比对的始终是两张<b>独立</b>的图：本类不做任何拼接或叠加，用例也据此断言——
 * 若将来有人引入「先拼成一张再比」，{@code 相同图得满分} 与 {@code 异色图得低分}
 * 这两条会同时失败。</p>
 *
 * @author content
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class ContentImageInspectorTest {

    private static final int W = 400;
    private static final int H = 400;

    // ------------------------------------------------------------------
    // 造图工具
    // ------------------------------------------------------------------

    private static BufferedImage solid(int w, int h, int rgb) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                img.setRGB(x, y, rgb);
            }
        }
        return img;
    }

    /** 平滑渐变：接近真实产品图，JPEG 再编码后仍应判「一致」。 */
    private static BufferedImage gradient(int w, int h, int baseR, int baseG, int baseB) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int r = Math.min(255, baseR + x * 60 / w);
                int g = Math.min(255, baseG + y * 60 / h);
                int b = Math.min(255, baseB + (x + y) * 40 / (w + h));
                img.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        return img;
    }

    private static BufferedImage noise(int w, int h, long seed) {
        Random r = new Random(seed);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                img.setRGB(x, y, r.nextInt(0x1000000));
            }
        }
        return img;
    }

    /** 在左上角涂一块异色，模拟「换掉了 Logo / 改了一个角落」。 */
    private static BufferedImage withPatch(BufferedImage src, int px, int py, int pw, int ph, int rgb) {
        BufferedImage copy = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                boolean inPatch = x >= px && x < px + pw && y >= py && y < py + ph;
                copy.setRGB(x, y, inPatch ? rgb : src.getRGB(x, y));
            }
        }
        return copy;
    }

    private static byte[] png(BufferedImage img) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private static byte[] jpeg(BufferedImage img, float quality) throws Exception {
        Iterator<ImageWriter> it = ImageIO.getImageWritersByFormatName("jpeg");
        assertTrue(it.hasNext(), "JDK 应自带 JPEG 编码器");
        ImageWriter writer = it.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(out)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(img, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    // ------------------------------------------------------------------
    // 用例
    // ------------------------------------------------------------------

    @Test
    @DisplayName("同一张图：相似度满分、结论一致、无差异方位")
    void identicalImages() throws Exception {
        byte[] a = png(gradient(W, H, 200, 40, 40));
        ContentImageInspector.Comparison c = ContentImageInspector.compare(a, a);

        assertTrue(c.comparable(), "同尺寸同长宽比应当可比");
        assertEquals("PIXEL", c.compareMode(), "同尺寸两图应走逐像素口径");
        assertNotNull(c.similarity());
        assertEquals(100d, c.similarity(), 0.01d, "字节完全相同的两图必须满分");
        assertEquals("CONSISTENT", ContentImageInspector.localVerdict(c));
        assertTrue(c.diffZones().isEmpty(), "完全相同时不应报出差异方位");
    }

    @Test
    @DisplayName("结构相同但颜色完全不同（纯红 vs 纯蓝）：必须判「不一致」")
    void sameLayoutDifferentColour() throws Exception {
        ContentImageInspector.Comparison c = ContentImageInspector.compare(
            png(solid(W, H, 0xFF0000)), png(solid(W, H, 0x0000FF)));

        assertTrue(c.comparable());
        // 这正是旧实现的漏洞：只比亮度时这里是 82/100「无法判定」。
        assertEquals("INCONSISTENT", ContentImageInspector.localVerdict(c),
            "颜色完全不同必须判不一致，实际相似度=" + c.similarity());
        assertTrue(c.similarity() < 75d,
            "纯红 vs 纯蓝的相似度应远低于 75，实际=" + c.similarity());
        assertEquals(170d, c.meanChannelDiff(), 1.0d, "平均通道差应接近 (255+0+255)/3");
    }

    @Test
    @DisplayName("两张互不相关的图：逐像素口径必须判「不一致」（网格口径会误判为 97）")
    void unrelatedImagesMustNotPassAsConsistent() throws Exception {
        ContentImageInspector.Comparison c = ContentImageInspector.compare(
            png(noise(W, H, 1L)), png(noise(W, H, 2L)));

        assertTrue(c.comparable());
        assertEquals("PIXEL", c.compareMode(), "同尺寸两图必须走逐像素口径");
        assertNotNull(c.similarity());
        assertTrue(c.similarity() < 90d,
            "互不相关的两张图不能被判为一致，实际相似度=" + c.similarity());
        assertEquals("INCONSISTENT", ContentImageInspector.localVerdict(c));
        // 网格均值会把单元内的高频差异抹平，对随机纹理不敏感：它只能作对照，不得用于出结论。
        // 这条断言把「结论取逐像素口径」这个设计决定钉死。
        assertTrue(c.gridSimilarity() > c.similarity(),
            "网格口径对随机纹理应明显更宽松（本例 " + c.gridSimilarity()
                + " vs 逐像素 " + c.similarity() + "），故不得用它出结论");
    }

    @Test
    @DisplayName("同一张图另存为 JPEG：仍应判「一致」（不能草木皆兵）")
    void sameImageReencodedAsJpeg() throws Exception {
        BufferedImage img = gradient(W, H, 190, 60, 60);
        ContentImageInspector.Comparison c = ContentImageInspector.compare(png(img), jpeg(img, 0.9f));

        assertTrue(c.comparable());
        assertEquals("PIXEL", c.compareMode());
        assertTrue(c.similarity() >= 90d,
            "同一张图换编码格式不应被判为不一致，实际相似度=" + c.similarity());
        assertEquals("CONSISTENT", ContentImageInspector.localVerdict(c));
    }

    @Test
    @DisplayName("同一张图整体位移 2 像素：仍应判「一致」（不因轻微错位误报）")
    void slightShiftIsTolerated() throws Exception {
        BufferedImage base = gradient(W, H, 160, 160, 160);
        BufferedImage shifted = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                shifted.setRGB(x, y, base.getRGB(Math.max(0, x - 2), y));
            }
        }
        ContentImageInspector.Comparison c = ContentImageInspector.compare(png(base), png(shifted));

        assertTrue(c.similarity() >= 90d,
            "整体轻微位移只影响边缘像素，不应判为不一致，实际相似度=" + c.similarity());
        assertEquals("CONSISTENT", ContentImageInspector.localVerdict(c));
    }

    @Test
    @DisplayName("仅左上角一处被改：整体仍算高分，但差异方位要指到「左上」")
    void localPatchIsLocalised() throws Exception {
        BufferedImage base = gradient(W, H, 180, 180, 180);
        BufferedImage patched = withPatch(base, 0, 0, 80, 80, 0x00FF00);

        ContentImageInspector.Comparison c = ContentImageInspector.compare(png(base), png(patched));

        assertTrue(c.comparable());
        assertTrue(c.meanChannelDiff() != null && c.meanChannelDiff() > 0d, "局部改动必须被测出来");
        assertFalse(c.diffZones().isEmpty(), "局部改动应当报出差异方位");
        assertTrue(c.diffZones().get(0).startsWith("左上"),
            "改动在左上角，首个差异方位应为「左上」，实际=" + c.diffZones());
    }

    @Test
    @DisplayName("长宽比差异过大：不做像素级比对，结论「无法判定」")
    void aspectMismatchIsNotComparable() throws Exception {
        ContentImageInspector.Comparison c = ContentImageInspector.compare(
            png(solid(400, 400, 0xFFFFFF)), png(solid(200, 800, 0xFFFFFF)));

        assertFalse(c.comparable(), "长宽比差一倍不应硬比");
        assertEquals("UNCERTAIN", ContentImageInspector.localVerdict(c));
    }

    @Test
    @DisplayName("不可解码的内容：不猜结论，判「无法判定」")
    void undecodableBytes() {
        ContentImageInspector.Comparison c = ContentImageInspector.compare(
            "not an image".getBytes(), "also not an image".getBytes());

        assertFalse(c.comparable());
        assertEquals("UNCERTAIN", ContentImageInspector.localVerdict(c));
        assertFalse(c.notes().isEmpty(), "必须给出不可用的可读原因");
    }
}
