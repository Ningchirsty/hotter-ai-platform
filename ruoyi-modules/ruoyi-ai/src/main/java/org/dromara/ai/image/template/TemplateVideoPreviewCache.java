package org.dromara.ai.image.template;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.InputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Authenticated template previews use bounded, browser-compatible cached media. */
public final class TemplateVideoPreviewCache {
    private static final long MAX_SOURCE = 64L * 1024 * 1024;
    private static final long MAX_PREVIEW = 16L * 1024 * 1024;
    private final Path root;
    private final Builder builder;
    private final Semaphore workers = new Semaphore(2);
    private final ConcurrentHashMap<String,Object> locks = new ConcurrentHashMap<>();
    @FunctionalInterface interface Builder { void build(URI source, Path target) throws Exception; }

    public TemplateVideoPreviewCache(Path feedRoot) { this(feedRoot, TemplateVideoPreviewCache::build); }
    TemplateVideoPreviewCache(Path feedRoot, Builder builder) {
        this.root = feedRoot.toAbsolutePath().normalize().resolve("video-previews-v1");
        this.builder = builder;
    }
    static URI source(JsonNode template) {
        if (!"video".equals(template.path("type").asText())) throw new TemplateFeedException(404,"视频模板不存在");
        try {
            URI uri = URI.create(template.path("video_meta").path("reference_video").asText());
            String path = uri.getRawPath();
            if (!"https".equals(uri.getScheme()) || !"media.goodcase.ai".equals(uri.getHost())
                || (uri.getPort() != -1 && uri.getPort() != 443) || uri.getUserInfo() != null
                || uri.getRawQuery() != null || uri.getRawFragment() != null || path == null
                || !path.matches("/(?:media/video|cases|supabase-legacy/case-media)/[A-Za-z0-9_./-]+\\.mp4")
                || path.contains("..") || !uri.normalize().equals(uri)) throw new IllegalArgumentException();
            return uri;
        } catch (Exception exception) { throw new TemplateFeedException(404,"此模板暂无可用示例视频"); }
    }
    static boolean available(JsonNode template) {
        try { source(template); return true; } catch (TemplateFeedException exception) { return false; }
    }
    public Path get(JsonNode template) {
        URI source = source(template);
        String key = TemplateFeedCache.sha(("v1:" + template.path("id").asText() + ":"
            + template.path("revision").asInt() + ":" + source).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Path target = root.resolve(key + ".mp4");
        synchronized (locks.computeIfAbsent(key, ignored -> new Object())) {
            try {
                if (Files.isRegularFile(target) && Files.size(target) > 0 && Files.size(target) <= MAX_PREVIEW) return target;
                Files.createDirectories(root);
                if (Files.getFileStore(root).getUsableSpace() < 256L * 1024 * 1024) throw new TemplateFeedException(503,"示例视频缓存空间不足");
                try (var files = Files.list(root)) {
                    if (files.filter(p -> p.getFileName().toString().matches("[a-f0-9]{64}\\.mp4")).mapToLong(p -> {
                        try { return Files.size(p); } catch (Exception exception) { return MAX_PREVIEW; }
                    }).sum() > 256L * 1024 * 1024) throw new TemplateFeedException(503,"示例视频缓存空间不足");
                }
                if (!workers.tryAcquire(60, TimeUnit.SECONDS)) throw new TemplateFeedException(503,"示例视频正在准备，请稍后重试");
                Path temporary = null;
                try {
                    temporary = Files.createTempFile(root, "preview-", ".mp4");
                    builder.build(source, temporary);
                    if (Files.size(temporary) < 1 || Files.size(temporary) > MAX_PREVIEW) throw new TemplateFeedException(503,"示例视频暂不可用");
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                    return target;
                } finally { try { if (temporary != null) Files.deleteIfExists(temporary); } finally { workers.release(); } }
            } catch (TemplateFeedException exception) { throw exception; }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new TemplateFeedException(503,"示例视频读取已中断"); }
            catch (Exception exception) { throw new TemplateFeedException(503,"示例视频暂不可用，请稍后重试"); }
        }
    }
    private static void build(URI source, Path target) throws Exception {
        Path original = Files.createTempFile(target.getParent(), "source-", ".mp4");
        try {
            download(source, original);
            transcode(original,target);
        } finally { Files.deleteIfExists(original); }
    }
    static void transcode(Path original, Path target) throws Exception {
            probe(original);
            var command = List.of("ffmpeg","-nostdin","-y","-v","error","-threads","2",
                "-protocol_whitelist","file,pipe","-f","mov","-i",original.toString(),
                "-map","0:v:0","-map","0:a:0?","-vf","scale=640:640:force_original_aspect_ratio=decrease:force_divisible_by=2,fps=24",
                "-c:v","libx264","-threads","2","-preset","veryfast","-crf","28","-maxrate","1000k","-bufsize","2000k",
                "-profile:v","baseline","-pix_fmt","yuv420p","-c:a","aac","-b:a","64k",
                "-movflags","+faststart","-f","mp4",target.toString());
            Process process = new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            try {
                if (!process.waitFor(90,TimeUnit.SECONDS) || process.exitValue() != 0) throw new TemplateFeedException(503,"示例视频准备失败");
            } finally { if (process.isAlive()) process.destroyForcibly(); }
            probe(target);
    }
    private static void download(URI source, Path target) throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
        var request = HttpRequest.newBuilder(source).timeout(Duration.ofSeconds(30)).header("User-Agent","hotter-template-preview/1").GET().build();
        var response = client.send(request,HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream input = response.body()) {
            if (response.statusCode() != 200 || response.headers().firstValueAsLong("Content-Length").orElse(0) > MAX_SOURCE
                || !response.headers().firstValue("Content-Type").orElse("").split(";")[0].strip().equals("video/mp4"))
                throw new TemplateFeedException(503,"示例视频源暂不可用");
            var transfer = new FutureTask<Void>(() -> {
                try (var output = Files.newOutputStream(target)) {
                    byte[] buffer = new byte[65536]; long count = 0; int n;
                    while ((n = input.read(buffer)) != -1) {
                        count += n;
                        if (count > MAX_SOURCE) throw new TemplateFeedException(503,"示例视频源过大");
                        output.write(buffer,0,n);
                    }
                    if (count == 0) throw new TemplateFeedException(503,"示例视频源为空");
                }
                return null;
            });
            Thread.startVirtualThread(transfer);
            try { transfer.get(90,TimeUnit.SECONDS); }
            finally { if (!transfer.isDone()) transfer.cancel(true); }
        }
    }
    private static void probe(Path path) throws Exception {
        Path output = Files.createTempFile(path.getParent(),"probe-",".json");
        try {
            Process process = new ProcessBuilder("ffprobe","-v","error","-protocol_whitelist","file,pipe","-f","mov",
                "-select_streams","v:0","-show_entries","stream=width,height:format=duration","-of","json",path.toString())
                .redirectOutput(output.toFile()).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            try {
                if (!process.waitFor(15,TimeUnit.SECONDS) || process.exitValue() != 0 || Files.size(output) > 32768) throw new TemplateFeedException(503,"示例视频内容无效");
            } finally { if (process.isAlive()) process.destroyForcibly(); }
            var media = TemplateFeedCache.JSON.readTree(Files.readAllBytes(output));
            var stream = media.path("streams").path(0); double seconds = media.path("format").path("duration").asDouble();
            if (stream.path("width").asInt() < 1 || stream.path("height").asInt() < 1
                || stream.path("width").asInt() > 4096 || stream.path("height").asInt() > 4096
                || !Double.isFinite(seconds) || seconds <= 0 || seconds > 120) throw new TemplateFeedException(503,"示例视频内容无效");
        } finally { Files.deleteIfExists(output); }
    }
}
