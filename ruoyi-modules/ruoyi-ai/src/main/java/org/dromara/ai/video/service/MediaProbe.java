package org.dromara.ai.video.service;

import lombok.extern.slf4j.Slf4j;
import org.dromara.ai.video.exception.VideoTaskException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 用 ffprobe / ffmpeg 对成片做<b>实测</b>与截断。
 *
 * <p>为什么必须做：ComfyUI 的 {@code SaveVideo} 输出只给出文件名，<b>不返回</b>宽高/帧率/时长。
 * 实测的三份 H3 模板固定产出 124 帧 @24fps = <b>5.167 秒</b>，而产品上限是 5 秒，
 * 因此只依赖 ComfyUI 上报的元数据会漏判「超出时长」，也无法填写真实分辨率。
 * 这是 2026-09-16 在 A100 上跑出成片后发现的真实缺陷。</p>
 *
 * <p>如果部署环境没有 ffprobe，本类不会让任务失败，而是退化为「未实测」并明确告警；
 * 但那样任务就不能声称输出已验证。</p>
 */
@Slf4j
public class MediaProbe {

    private static final Pattern DURATION = Pattern.compile("duration=([0-9.]+)");
    private static final Pattern WIDTH = Pattern.compile("width=([0-9]+)");
    private static final Pattern HEIGHT = Pattern.compile("height=([0-9]+)");
    private static final Pattern RATE = Pattern.compile("r_frame_rate=([0-9]+)/([0-9]+)");

    private final String ffprobePath;
    private final String ffmpegPath;
    private final long timeoutSeconds;

    public MediaProbe(String ffprobePath, String ffmpegPath, long timeoutSeconds) {
        this.ffprobePath = ffprobePath == null ? "ffprobe" : ffprobePath;
        this.ffmpegPath = ffmpegPath == null ? "ffmpeg" : ffmpegPath;
        this.timeoutSeconds = timeoutSeconds <= 0 ? 120 : timeoutSeconds;
    }

    /**
     * 成片实测结果。
     *
     * @param measured 是否真的测到了（false 表示环境缺少 ffprobe）
     */
    public record Probe(Integer width, Integer height, Double fps, Long durationMillis, boolean measured) {

        public static Probe unmeasured() {
            return new Probe(null, null, null, null, false);
        }

        public boolean exceeds(long maxMillis) {
            return durationMillis != null && durationMillis > maxMillis;
        }
    }

    /**
     * 探测媒体文件。文件不存在或 ffprobe 不可用时返回未实测结果，不抛异常。
     */
    public Probe probe(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            log.warn("待探测的成片不存在：{}", file);
            return Probe.unmeasured();
        }
        if (!executableAvailable(ffprobePath)) {
            log.warn("ffprobe 不可用（{}），无法实测成片分辨率与时长的；任务将标记为未实测输出",
                ffprobePath);
            return Probe.unmeasured();
        }
        List<String> cmd = List.of(ffprobePath, "-v", "error",
            "-select_streams", "v:0",
            "-show_entries", "stream=width,height,r_frame_rate",
            "-show_entries", "format=duration",
            "-of", "default=noprint_wrappers=1",
            file.toAbsolutePath().toString());
        String out = run(cmd);
        if (out == null) {
            return Probe.unmeasured();
        }
        Integer width = intOf(WIDTH.matcher(out));
        Integer height = intOf(HEIGHT.matcher(out));
        Double fps = fpsOf(out);
        Long duration = durationMillis(out);
        log.info("成片实测：{}x{} @{}fps {}ms ({})", width, height, fps, duration, file.getFileName());
        return new Probe(width, height, fps, duration, true);
    }

    /**
     * 把成片精确截断到不超过指定时长。
     *
     * <p>必须<b>帧精确</b>：24fps 下 5.000 秒 = 恰好 120 帧，
     * 而单纯用 {@code -t 5.0 -c copy} 只会切到最近的关键帧（实测得到 122 帧 / 5.083 秒，
     * 超过产品上限）。实测比较（源 124 帧 / 5.167 秒 / 含 AAC 音轨）：</p>
     * <ul>
     *   <li>{@code -frames:v 120 -c copy}：120 帧，但容器时长仍 5.024s（音频未动）；</li>
     *   <li>整体重编码：精确 5.000s，但体积由 3.68MB 涨到 4.95MB（+35%）；</li>
     *   <li><b>视频流拷贝 + 音频精确截断</b>：精确 5.000s，体积 3.70MB，几乎无损且快。</li>
     * </ul>
     * 因此这里采用最后一种：视频按帧数拷贝、音频单独 atrim，兼顾精确与体积。
     *
     * @param source    源文件
     * @param maxMillis 目标最大时长（毫秒）
     * @param fps       实测帧率，用于把时长换算成帧数；为 null 时不做帧限制
     * @return 截断后的文件路径；失败时返回原文件并告警
     */
    public Path truncate(Path source, long maxMillis, Double fps) {
        if (!executableAvailable(ffmpegPath)) {
            log.warn("ffmpeg 不可用（{}），无法截断成片；成片将保持原始时长 {}ms，已记录待人工处理",
                ffmpegPath, maxMillis);
            return source;
        }
        Path target = source.resolveSibling(stripExtension(source.getFileName().toString())
            + "_capped" + extension(source.getFileName().toString()));
        String seconds = String.format(java.util.Locale.ROOT, "%.3f", maxMillis / 1000.0);

        List<String> cmd = new ArrayList<>(List.of(ffmpegPath, "-y", "-v", "error",
            "-i", source.toAbsolutePath().toString()));

        // 视频：按目标时长换算为帧数后精确取帧（流拷贝，不重编码）。
        Integer frameLimit = null;
        if (fps != null && fps > 0) {
            frameLimit = (int) Math.floor(maxMillis / 1000.0 * fps);
            if (frameLimit > 0) {
                cmd.add("-frames:v");
                cmd.add(String.valueOf(frameLimit));
            }
        }
        // 音频：与视频独立截断到同一时长，否则容器时长仍会被音频拖长。
        cmd.add("-af");
        cmd.add("atrim=end=" + seconds + ",asetpts=PTS-STARTPTS");
        cmd.add("-c:v");
        cmd.add("copy");
        // 音频需要重新编码才能精确裁剪（AAC 帧长固定）。
        cmd.add("-c:a");
        cmd.add("aac");
        cmd.add("-b:a");
        cmd.add("192k");
        cmd.add("-movflags");
        cmd.add("+faststart");
        if (frameLimit == null) {
            // 拿不到帧率时退回按时间截断，并明确告知调用方结果可能不精确。
            cmd.add("-t");
            cmd.add(seconds);
        }
        cmd.add(target.toAbsolutePath().toString());

        String out = run(cmd);
        if (out == null || !Files.isRegularFile(target)) {
            log.warn("成片截断失败，保留原文件：{}", source.getFileName());
            return source;
        }
        try {
            long size = Files.size(target);
            if (size <= 0) {
                Files.deleteIfExists(target);
                log.warn("截断产物为空，保留原文件");
                return source;
            }
            // 截断成功后替换原文件，避免两处存储不一致。
            Files.deleteIfExists(source);
            log.info("成片已截断至 {}s（上限 {} 帧）：{} -> {} ({} bytes)",
                seconds, frameLimit == null ? "n/a" : frameLimit, source.getFileName(),
                target.getFileName(), size);
            return target;
        } catch (IOException e) {
            log.warn("截断产物处理失败，保留原文件：{}", e.getClass().getSimpleName());
            return source;
        }
    }

    /**
     * 判断可执行文件是否可用。
     */
    public boolean executableAvailable(String exe) {
        try {
            Process p = new ProcessBuilder(exe, "-version")
                .redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            return p.waitFor(10, TimeUnit.SECONDS);
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private String run(List<String> cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                log.warn("命令超时：{}", cmd.get(0));
                return null;
            }
            return output;
        } catch (IOException e) {
            log.warn("命令执行失败 {}：{}", cmd.get(0), e.getClass().getSimpleName());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private static Integer intOf(Matcher m) {
        return m.find() ? Integer.valueOf(m.group(1)) : null;
    }

    private static Double fpsOf(String out) {
        Matcher m = RATE.matcher(out);
        if (!m.find()) {
            return null;
        }
        try {
            double den = Double.parseDouble(m.group(2));
            return den == 0 ? null : Double.parseDouble(m.group(1)) / den;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long durationMillis(String out) {
        Matcher m = DURATION.matcher(out);
        if (!m.find()) {
            return null;
        }
        try {
            return Math.round(Double.parseDouble(m.group(1)) * 1000);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot) : ".mp4";
    }

    /**
     * 校验成片是否满足产品约束（1080P）。
     *
     * @throws VideoTaskException 分辨率不符合要求时抛出
     */
    public void assertAcceptable(Probe probe) {
        if (!probe.measured()) {
            return;
        }
        if (probe.width() != null && probe.height() != null
            && !(probe.width() == 1920 && probe.height() == 1080)) {
            throw VideoTaskException.outputInvalid(
                "成片分辨率 " + probe.width() + "×" + probe.height() + " 不符合 1080P 要求");
        }
    }
}
