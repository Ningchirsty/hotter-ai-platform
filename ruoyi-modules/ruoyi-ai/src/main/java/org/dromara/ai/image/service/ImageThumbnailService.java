package org.dromara.ai.image.service;

import lombok.extern.slf4j.Slf4j;
import org.dromara.ai.video.service.AssetStorage;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 图像缩略图服务（JDK ImageIO 实现，无需 ffmpeg）。
 *
 * <p>缩略图写在 {@link AssetStorage#thumbnailPath(String)} 指定的独立目录下：
 * 素材本体目录会被运维按「数据库无引用」判定为无主文件清理，缩略图不入库，
 * 混在本体目录里会被误删（这是视频模块踩过的坑，这里沿用同样的隔离方式）。</p>
 */
@Slf4j
public class ImageThumbnailService {

    /**
     * 缩略图长边上限。
     */
    public static final int MAX_EDGE = 480;

    private final AssetStorage storage;

    public ImageThumbnailService(AssetStorage storage) {
        this.storage = storage;
    }

    /**
     * 生成或读取缓存缩略图。
     *
     * @param storageKey  素材存储键
     * @param contentType 素材 MIME（非图片直接返回 null，让前端退回原图）
     * @return JPEG 字节；无法生成时返回 null（调用方退回使用原图，不要报错）
     */
    public byte[] thumbnail(String storageKey, String contentType) {
        if (storageKey == null || contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            return null;
        }
        Path target = storage.thumbnailPath(storageKey);
        if (target == null) {
            return null;
        }
        try {
            if (Files.isRegularFile(target)) {
                return Files.readAllBytes(target);
            }
        } catch (IOException e) {
            log.debug("读取已缓存缩略图失败：{}", e.getMessage());
        }
        Path source = storage.localPath(storageKey);
        if (source == null || !Files.isRegularFile(source)) {
            return null;
        }
        try {
            BufferedImage original = ImageIO.read(source.toFile());
            if (original == null) {
                return null;
            }
            BufferedImage scaled = scale(original);
            Files.createDirectories(target.getParent());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(scaled, "jpg", out);
            byte[] bytes = out.toByteArray();
            Files.write(target, bytes);
            return bytes;
        } catch (IOException e) {
            log.warn("生成缩略图失败：{}", e.getMessage());
            return null;
        }
    }

    private BufferedImage scale(BufferedImage original) {
        int width = original.getWidth();
        int height = original.getHeight();
        double ratio = Math.min(1.0, (double) MAX_EDGE / Math.max(width, height));
        int targetWidth = Math.max(1, (int) Math.round(width * ratio));
        int targetHeight = Math.max(1, (int) Math.round(height * ratio));
        BufferedImage canvas = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = canvas.createGraphics();
        try {
            // 透明 PNG 转 JPEG 必须垫白底，否则透明区域会变成黑块
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, targetWidth, targetHeight);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(original, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return canvas;
    }
}
