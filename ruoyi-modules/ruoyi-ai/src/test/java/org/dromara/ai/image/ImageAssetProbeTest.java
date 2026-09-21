package org.dromara.ai.image;

import org.dromara.ai.image.exception.ImageTaskException;
import org.dromara.ai.image.service.ImageAssetProbe;
import org.dromara.ai.image.service.ImageAssetProbe.Probe;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 输出实测：尺寸/格式/alpha 必须来自真实文件，而不是 ComfyUI 的返回值。
 */
class ImageAssetProbeTest {

    private final ImageAssetProbe probe = new ImageAssetProbe();

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("读取真实 PNG：宽高、格式与 alpha 都被量出来")
    void probesRealPng() throws Exception {
        Path alpha = writePng("a.png", 48, 32, true);
        Probe probed = probe.probe(alpha);
        assertTrue(probed.measured());
        assertEquals(48, probed.width());
        assertEquals(32, probed.height());
        assertTrue(probed.hasAlpha());
        assertEquals("png", probed.format().toLowerCase());
        assertEquals(Files.size(alpha), probed.sizeBytes());
    }

    @Test
    @DisplayName("不带 alpha 的 JPEG 不会被误判为透明")
    void jpegHasNoAlpha() throws Exception {
        Path path = tempDir.resolve("b.jpg");
        ImageIO.write(new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB), "jpg", path.toFile());
        Probe probed = probe.probe(path);
        assertTrue(probed.measured());
        assertFalse(probed.hasAlpha());
    }

    @Test
    @DisplayName("像素上限：超限判失败，未超限通过")
    void pixelLimit() throws Exception {
        Probe big = probe.probe(writePng("big.png", 64, 64, true));
        assertTrue(big.exceedsPixels(1000));
        assertFalse(big.exceedsPixels(64 * 64));
        ImageTaskException e = assertThrows(ImageTaskException.class,
            () -> probe.assertAcceptable(big, 1000, false));
        assertEquals("OUTPUT_INVALID", e.getErrorCode());
        probe.assertAcceptable(big, 64 * 64, false);
    }

    @Test
    @DisplayName("抠图要求 alpha：缺失时判失败")
    void alphaRequired() throws Exception {
        Probe opaque = probe.probe(writePng("opaque.png", 16, 16, false));
        assertFalse(opaque.hasAlpha());
        assertThrows(ImageTaskException.class, () -> probe.assertAcceptable(opaque, 1_000_000, true));
    }

    @Test
    @DisplayName("文件不存在或不可识别时返回未实测，并且不做断言（不得假装已验证）")
    void unmeasuredIsHonest() {
        Probe missing = probe.probe(tempDir.resolve("nope.png"));
        assertFalse(missing.measured());
        probe.assertAcceptable(missing, 1, true);
    }

    private Path writePng(String name, int width, int height, boolean alpha) throws Exception {
        Path path = tempDir.resolve(name);
        int type = alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        ImageIO.write(new BufferedImage(width, height, type), "png", path.toFile());
        return path;
    }
}
