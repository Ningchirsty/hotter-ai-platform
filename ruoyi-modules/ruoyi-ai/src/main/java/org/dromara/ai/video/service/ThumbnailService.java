package org.dromara.ai.video.service;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 素材缩略图。
 *
 * <p><b>为什么需要。</b>素材库的格子只有一两百像素宽，此前直接用素材本体当缩略图，
 * 于是一张 3.2 MB 的原图会被整张拉下来渲染成小图；经 Cloudflare 的链路实测吞吐
 * 只有 258 KB/s ~ 790 KB/s，一张图就要好几秒，而页面上一屏可能有好几张。</p>
 *
 * <p><b>为什么要落盘缓存。</b>缩略图只依赖原图，生成一次就不会变。缓存在
 * {@code thumbnails/} 下（与素材本体分开存放，避免被「无主文件」清理脚本误删），
 * 之后直接读文件。生成失败就返回 null，前端回退到直接用原图，不会让卡片空白。</p>
 *
 * <p>只处理图片：视频成片仍用图标。刻意不去抽视频帧——那要解码视频，
 * 代价和风险都远大于一张缩略图带来的收益。</p>
 */
@Slf4j
public class ThumbnailService {

    /**
     * 缩略图最长边。素材库格子显示尺寸远小于此，再大只是浪费带宽。
     */
    private static final int MAX_EDGE = 480;

    private final AssetStorage assetStorage;

    private final String ffmpegPath;

    private final long timeoutSeconds;

    public ThumbnailService(AssetStorage assetStorage, String ffmpegPath, long timeoutSeconds) {
        this.assetStorage = assetStorage;
        this.ffmpegPath = ffmpegPath == null || ffmpegPath.isBlank() ? "ffmpeg" : ffmpegPath;
        this.timeoutSeconds = timeoutSeconds <= 0 ? 30 : timeoutSeconds;
    }

    /**
     * 取缩略图字节（有缓存则直接读缓存）。
     *
     * @param storageKey 素材存储键
     * @param contentType 素材 MIME；非图片直接返回 null
     * @return JPEG 字节；不支持或生成失败时返回 null
     */
    public byte[] thumbnail(String storageKey, String contentType) {
        if (storageKey == null || contentType == null
            || !contentType.toLowerCase().startsWith("image/")) {
            return null;
        }
        Path target = assetStorage.thumbnailPath(storageKey);
        if (target == null) {
            return null;
        }
        byte[] cached = readIfPresent(target);
        if (cached != null) {
            return cached;
        }
        Path source = assetStorage.localPath(storageKey);
        if (source == null) {
            // 纯对象存储实现没有本地路径，无法调 ffmpeg；由调用方回退到原图。
            return null;
        }
        if (!generate(source, target)) {
            return null;
        }
        return readIfPresent(target);
    }

    private byte[] readIfPresent(Path target) {
        try {
            if (Files.isRegularFile(target) && Files.size(target) > 0) {
                return Files.readAllBytes(target);
            }
        } catch (IOException e) {
            log.warn("读取缩略图缓存失败：{}", e.getClass().getSimpleName());
        }
        return null;
    }

    /**
     * 用 ffmpeg 生成缩略图。
     *
     * <p>{@code min(480,iw)} 保证小图不会被放大；{@code -2} 让高度按比例取偶数，
     * 避免部分编码器对奇数尺寸报错。</p>
     */
    private boolean generate(Path source, Path target) {
        try {
            Files.createDirectories(target.getParent());
        } catch (IOException e) {
            log.warn("创建缩略图目录失败：{}", e.getClass().getSimpleName());
            return false;
        }
        List<String> cmd = List.of(ffmpegPath, "-y", "-v", "error", "-i",
            source.toAbsolutePath().toString(),
            "-vf", "scale=min(" + MAX_EDGE + "\\,iw):-2",
            "-frames:v", "1", "-q:v", "5",
            target.toAbsolutePath().toString());
        try {
            Process process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            process.getInputStream().readAllBytes();
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                log.warn("缩略图生成超时（{}s）", timeoutSeconds);
                return false;
            }
            if (process.exitValue() != 0) {
                log.warn("缩略图生成失败，ffmpeg 退出码 {}", process.exitValue());
                return false;
            }
        } catch (IOException e) {
            // 环境里没有 ffmpeg 属于可预期情况：回退到原图即可，不该影响预览本身。
            log.warn("无法执行缩略图生成（{}）", e.getClass().getSimpleName());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        try {
            return Files.isRegularFile(target) && Files.size(target) > 0;
        } catch (IOException e) {
            return false;
        }
    }
}
