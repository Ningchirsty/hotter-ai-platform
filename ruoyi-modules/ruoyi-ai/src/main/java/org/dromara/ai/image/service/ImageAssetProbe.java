package org.dromara.ai.image.service;

import lombok.extern.slf4j.Slf4j;
import org.dromara.ai.image.exception.ImageTaskException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 图像输出实测：用 JDK 自带的 ImageIO 读取真实宽高、格式与 alpha 通道。
 *
 * <p>为什么不用 ffprobe：视频模块需要 ffprobe 是因为要量帧率/时长并做帧精确截断；
 * 图像只需要宽高、格式和 alpha，ImageIO 足够，且不依赖外部二进制（镜像里也不必再装东西）。</p>
 *
 * <p>与视频模块同一条原则：<b>数据库里记录的尺寸必须来自实测</b>，不能用 ComfyUI 返回值，
 * 更不能凭空假设。</p>
 */
@Slf4j
public class ImageAssetProbe {

    /**
     * 实测结果。
     *
     * @param width     宽（未实测时为 null）
     * @param height    高
     * @param format    格式名（如 png）
     * @param hasAlpha  是否带 alpha 通道
     * @param sizeBytes 文件字节数
     * @param measured  是否真的读到了像素数据
     */
    public record Probe(Integer width, Integer height, String format, boolean hasAlpha,
                        long sizeBytes, boolean measured) {

        public static Probe unmeasured(long sizeBytes) {
            return new Probe(null, null, null, false, sizeBytes, false);
        }

        public boolean exceedsPixels(int maxPixels) {
            return measured && width != null && height != null && (long) width * height > maxPixels;
        }

        public boolean isExactly(int expectedWidth, int expectedHeight) {
            return measured && width != null && height != null
                && width == expectedWidth && height == expectedHeight;
        }
    }

    /**
     * 读取一个图像文件。读不到时返回未实测结果（调用方据此跳过断言并如实标注）。
     */
    public Probe probe(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return Probe.unmeasured(0);
        }
        long size;
        try {
            size = Files.size(path);
        } catch (IOException e) {
            size = 0;
        }
        try {
            BufferedImage image = ImageIO.read(path.toFile());
            if (image == null) {
                log.warn("ImageIO 无法识别该图像文件：{}", path.getFileName());
                return Probe.unmeasured(size);
            }
            String format = null;
            try (var stream = ImageIO.createImageInputStream(path.toFile())) {
                if (stream != null) {
                    var readers = ImageIO.getImageReaders(stream);
                    if (readers.hasNext()) {
                        format = readers.next().getFormatName();
                    }
                }
            } catch (IOException ignored) {
                // 格式名不是关键信息，读不到就留空
            }
            return new Probe(image.getWidth(), image.getHeight(), format,
                image.getColorModel().hasAlpha(), size, true);
        } catch (IOException e) {
            log.warn("读取图像文件失败：{}（{}）", path.getFileName(), e.getMessage());
            return Probe.unmeasured(size);
        }
    }

    /**
     * 输出断言：像素上限 + 可选的 alpha 要求（抠图能力必须带透明通道）。
     */
    public void assertAcceptable(Probe probe, int maxPixels, boolean requireAlpha) {
        if (!probe.measured()) {
            log.warn("输出未能实测，跳过图像断言（不得声称输出已验证）");
            return;
        }
        if (probe.exceedsPixels(maxPixels)) {
            throw ImageTaskException.outputInvalid(
                "输出图片像素过大：" + probe.width() + "×" + probe.height()
                    + "，上限 " + maxPixels + " 像素");
        }
        if (requireAlpha && !probe.hasAlpha()) {
            throw ImageTaskException.outputInvalid("抠图结果缺少透明通道（未输出 RGBA PNG）");
        }
    }
}
