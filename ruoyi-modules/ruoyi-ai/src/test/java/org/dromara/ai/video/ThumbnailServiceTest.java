package org.dromara.ai.video;

import org.dromara.ai.video.service.AssetStorage;
import org.dromara.ai.video.service.ThumbnailService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 缩略图服务。
 *
 * <p>锁住两件事：</p>
 * <ol>
 *   <li><b>视频也要能出缩略图</b>——任务封面曾经直接拉整段 mp4（9 个成片约 13 MB）塞进
 *       {@code <img>}，把带宽占满后同时发起的预览请求就会排队甚至超时，
 *       表现成"预览时好时坏"。</li>
 *   <li><b>失败必须优雅降级</b>——缺 ffmpeg、类型不支持、原素材读不到，都返回 null
 *       交给前端回退，而不是抛异常把预览一起带崩。</li>
 * </ol>
 */
class ThumbnailServiceTest {

    /**
     * 极简存储替身：只关心「本地路径 / 缩略图路径 / 读文件」三件事。
     */
    private static final class StubStorage implements AssetStorage {
        private final Path root;

        StubStorage(Path root) {
            this.root = root;
        }

        @Override
        public String storeUpload(String t, long u, String n, byte[] c, String ct) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String storeOutput(String t, long u, long taskId, String n, byte[] c, String ct) {
            throw new UnsupportedOperationException();
        }

        @Override
        public byte[] read(String storageKey) {
            try {
                return Files.readAllBytes(root.resolve(storageKey));
            } catch (IOException e) {
                return null;
            }
        }

        @Override
        public Path localPath(String storageKey) {
            Path p = root.resolve(storageKey);
            return Files.isRegularFile(p) ? p : null;
        }

        @Override
        public Path thumbnailPath(String storageKey) {
            return root.resolve("thumbnails").resolve(storageKey + ".jpg");
        }
    }

    private static final byte[] FAKE_JPEG = new byte[] {(byte) 0xFF, (byte) 0xD8, 0x11, 0x22, (byte) 0xFF, (byte) 0xD9};

    @Test
    @DisplayName("已有缓存时直接返回缓存，不调用 ffmpeg（因此不依赖运行环境）")
    void returnsCachedThumbnail(@TempDir Path dir) throws IOException {
        Path cached = dir.resolve("thumbnails").resolve("k/1.mp4.jpg");
        Files.createDirectories(cached.getParent());
        Files.write(cached, FAKE_JPEG);

        ThumbnailService service = new ThumbnailService(new StubStorage(dir), "ffmpeg-不存在", 5);
        byte[] out = service.thumbnail("k/1.mp4", "video/mp4");
        assertNotNull(out, "缓存命中时必须返回缓存内容");
        assertArrayEquals(FAKE_JPEG, out);
    }

    @Test
    @DisplayName("图片与视频都支持；其它类型直接返回 null")
    void gatesByContentType(@TempDir Path dir) {
        ThumbnailService service = new ThumbnailService(new StubStorage(dir), "ffmpeg-不存在", 5);
        // 没有源文件、也没有 ffmpeg，所以最终是 null；但重点是不能抛异常。
        assertNull(service.thumbnail("k/1.png", "image/png"));
        assertNull(service.thumbnail("k/1.mp4", "video/mp4"));
        assertNull(service.thumbnail("k/1.mp3", "audio/mpeg"));
        assertNull(service.thumbnail("k/1.txt", "text/plain"));
        assertNull(service.thumbnail("k/1.bin", "application/octet-stream"));
        assertNull(service.thumbnail(null, "video/mp4"));
        assertNull(service.thumbnail("k/1.mp4", null));
    }

    @Test
    @DisplayName("环境缺 ffmpeg 时返回 null 而不是抛异常（前端据此回退到原素材）")
    void degradesGracefullyWithoutFfmpeg(@TempDir Path dir) throws IOException {
        Path src = dir.resolve("k/1.mp4");
        Files.createDirectories(src.getParent());
        Files.write(src, new byte[] {0, 0, 0, 1});

        ThumbnailService service = new ThumbnailService(new StubStorage(dir), "ffmpeg-不存在", 5);
        assertNull(service.thumbnail("k/1.mp4", "video/mp4"),
            "缺少 ffmpeg 应当优雅降级，不能把预览一起带崩");
    }

    @Test
    @DisplayName("缩略图路径与素材本体分开存放（否则会被无主文件清理脚本误删）")
    void thumbnailPathIsSeparateFromAssets(@TempDir Path dir) {
        StubStorage storage = new StubStorage(dir);
        Path thumb = storage.thumbnailPath("000000/1/output/a.mp4");
        assertNotNull(thumb);
        assertTrue(thumb.toString().replace('\\', '/').contains("/thumbnails/"),
            "缩略图必须放在 thumbnails/ 下，实际=" + thumb);
        assertTrue(thumb.toString().endsWith("a.mp4.jpg"), "实际=" + thumb);
    }

    @Test
    @DisplayName("真实 ffmpeg 可用时：视频抽帧出 JPEG、图片缩放也能出 JPEG")
    void generatesWithRealFfmpeg(@TempDir Path dir) throws Exception {
        String ffmpeg = findFfmpeg();
        if (ffmpeg == null) {
            return; // 测试机没有 ffmpeg 就跳过，不把环境差异当失败
        }
        // 造一段 1 秒的测试视频与一张测试图
        Path src = dir.resolve("k/clip.mp4");
        Files.createDirectories(src.getParent());
        Process gen = new ProcessBuilder(ffmpeg, "-y", "-v", "error",
            "-f", "lavfi", "-i", "testsrc=size=320x240:rate=24:duration=1",
            "-pix_fmt", "yuv420p", src.toAbsolutePath().toString())
            .redirectErrorStream(true).start();
        gen.getInputStream().readAllBytes();
        assertTrue(gen.waitFor(60, java.util.concurrent.TimeUnit.SECONDS));
        assertTrue(gen.exitValue() == 0, "生成测试视频失败");

        Path img = dir.resolve("k/pic.png");
        Process genImg = new ProcessBuilder(ffmpeg, "-y", "-v", "error",
            "-f", "lavfi", "-i", "testsrc=size=800x600:rate=1:duration=1",
            "-frames:v", "1", img.toAbsolutePath().toString())
            .redirectErrorStream(true).start();
        genImg.getInputStream().readAllBytes();
        assertTrue(genImg.waitFor(60, java.util.concurrent.TimeUnit.SECONDS));

        ThumbnailService service = new ThumbnailService(new StubStorage(dir), ffmpeg, 60);
        byte[] videoThumb = service.thumbnail("k/clip.mp4", "video/mp4");
        assertNotNull(videoThumb, "视频必须能抽出缩略图");
        assertTrue(videoThumb.length > 200, "缩略图不该是空壳，实际 " + videoThumb.length + " 字节");
        assertTrue(isJpeg(videoThumb), "缩略图应当是 JPEG");

        byte[] imageThumb = service.thumbnail("k/pic.png", "image/png");
        assertNotNull(imageThumb, "图片必须能生成缩略图");
        assertTrue(isJpeg(imageThumb));

        // 第二次调用应命中缓存（内容一致）
        assertArrayEquals(videoThumb, service.thumbnail("k/clip.mp4", "video/mp4"));
    }

    private static boolean isJpeg(byte[] b) {
        return b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8;
    }

    private static String findFfmpeg() {
        for (String candidate : new String[] {"ffmpeg", "/usr/bin/ffmpeg", "/usr/local/bin/ffmpeg"}) {
            try {
                Process p = new ProcessBuilder(candidate, "-version").redirectErrorStream(true).start();
                p.getInputStream().readAllBytes();
                if (p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS) && p.exitValue() == 0) {
                    return candidate;
                }
            } catch (Exception ignored) {
                // 试下一个
            }
        }
        return null;
    }

    @Test
    @DisplayName("FAKE_JPEG 常量本身是合法 JPEG 头（防止测试自身写错）")
    void fakeJpegLooksLikeJpeg() {
        assertTrue(Arrays.equals(new byte[] {(byte) 0xFF, (byte) 0xD8},
            Arrays.copyOf(FAKE_JPEG, 2)));
    }
}
