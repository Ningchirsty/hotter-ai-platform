package org.dromara.ai.video.service;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 素材缩略图（图片缩放 + 视频抽首帧）。
 *
 * <p><b>为什么需要。</b>素材库格子和任务卡片只有一两百像素宽，此前直接拿素材本体当缩略图：
 * 3.2 MB 的原图整张拉下来渲染成小图；视频更夸张——任务封面曾经把<b>整段 mp4</b> 拉下来塞进
 * {@code <img>}（9 个成片合计约 13 MB）。经 Cloudflare 实测吞吐只有 258 KB/s ~ 790 KB/s，
 * 这会把带宽占满，让同一时刻发起的预览请求排队、乃至超时——表现就是"预览时好时坏"。</p>
 *
 * <p><b>为什么要落盘缓存。</b>缩略图只依赖原素材，生成一次就不会变。缓存在
 * {@code thumbnails/} 下（与素材本体分开存放，避免被「无主文件」清理脚本误删），
 * 之后直接读文件。生成失败就返回 null，前端回退到原素材、再回退到图标，卡片不会空白。</p>
 */
@Slf4j
public class ThumbnailService {

    /**
     * 缩略图最长边。格子和卡片显示尺寸远小于此，再大只是浪费带宽。
     */
    private static final int MAX_EDGE = 480;

    /**
     * 视频抽帧的时间点（秒）。
     *
     * <p>不取第 0 帧：成片开头常是淡入或黑场，抽出来一片黑，看起来像"封面坏了"。</p>
     */
    private static final String VIDEO_SEEK_SECONDS = "0.5";

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
     * @param storageKey  素材存储键
     * @param contentType 素材 MIME；只支持 {@code image/*} 与 {@code video/*}
     * @return JPEG 字节；不支持或生成失败时返回 null
     */
    public byte[] thumbnail(String storageKey, String contentType) {
        if (storageKey == null || contentType == null) {
            return null;
        }
        String type = contentType.toLowerCase();
        boolean image = type.startsWith("image/");
        boolean video = type.startsWith("video/");
        if (!image && !video) {
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
            // 纯对象存储实现没有本地路径，无法调 ffmpeg；由调用方回退到原素材。
            return null;
        }
        if (!generate(source, target, video)) {
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
     * 用 ffmpeg 生成缩略图：图片直接缩放，视频先定位到 {@value #VIDEO_SEEK_SECONDS} 秒再取一帧。
     *
     * <p>{@code min(480,iw)} 保证小图不会被放大；{@code -2} 让高度按比例取偶数，
     * 避免部分编码器对奇数尺寸报错。</p>
     */
    private boolean generate(Path source, Path target, boolean video) {
        try {
            Files.createDirectories(target.getParent());
        } catch (IOException e) {
            log.warn("创建缩略图目录失败：{}", e.getClass().getSimpleName());
            return false;
        }
        List<String> cmd = new java.util.ArrayList<>();
        cmd.add(ffmpegPath);
        cmd.add("-y");
        cmd.add("-v");
        cmd.add("error");
        if (video) {
            // 放在 -i 之前是快速定位，不必解码前面的帧。
            cmd.add("-ss");
            cmd.add(VIDEO_SEEK_SECONDS);
        }
        cmd.add("-i");
        cmd.add(source.toAbsolutePath().toString());
        cmd.add("-vf");
        cmd.add("scale=min(" + MAX_EDGE + "\\,iw):-2");
        cmd.add("-frames:v");
        cmd.add("1");
        cmd.add("-q:v");
        cmd.add("5");
        cmd.add(target.toAbsolutePath().toString());
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
            // 环境里没有 ffmpeg 属于可预期情况：回退到原素材即可，不该影响预览本身。
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
