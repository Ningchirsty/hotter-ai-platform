package org.dromara.creative.helper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 参考图实测分析器：从上传的参考图里**量出**可用于视觉基因的取值。
 *
 * <p><b>为什么不用模型</b>：本部署治理台没有注册视觉模型，若让「推荐」依赖模型，用户只会得到一句
 * 「不可用」。而主色、背景色、饱和度、对比度、留白、产品占比这些东西本身就是**可测量的**——
 * 与其等模型，不如先量准，并且把每个结论的**依据与可信度**一并给出。</p>
 *
 * <p><b>哪些测不出来就不给</b>：风格关键词、禁忌词、字体风格属于审美与品牌语义，
 * 像素层面推不出来。本类对它们**一个字段都不猜**，而是列进 {@code skipped} 并说明原因——
 * 把「测不了」讲清楚，比给一个看起来很懂的假结论有用。</p>
 *
 * <p><b>确定性</b>：缩放采样 + 量化分桶，同一张图每次结果一致（便于单测与回归）。</p>
 *
 * @author creative
 */
@Slf4j
@Component
public class ReferenceImageAnalyzer {

    /**
     * 采样边长：缩放到该尺寸再统计。够稳且快（96×96 = 9216 个像素）。
     */
    private static final int SAMPLE_SIZE = 96;

    /**
     * 判定「与背景不同」的颜色距离阈值（RGB 欧氏距离，0~441）
     */
    private static final double SUBJECT_DISTANCE_THRESHOLD = 48;

    /**
     * 背景一致性容差（同上）
     */
    private static final double BACKGROUND_TOLERANCE = 26;

    /**
     * 主色分桶：每通道 4 位（16 级）→ 4096 个桶
     */
    private static final int BUCKET_BITS = 4;

    /**
     * 可信度
     */
    public static final String RELIABILITY_HIGH = "HIGH";
    public static final String RELIABILITY_MEDIUM = "MEDIUM";

    /**
     * 单字段推荐。
     *
     * @param field       字段名（与 DNA 结构一致，如 colors.primary）
     * @param value       推荐值
     * @param basis       依据（怎么量出来的，给用户看）
     * @param reliability HIGH 实测 / MEDIUM 启发式
     */
    public record Recommendation(String field, Object value, String basis, String reliability) {
    }

    /**
     * 分析结果。
     *
     * @param recommendations 逐字段推荐
     * @param skipped         测不出来的字段与原因
     * @param notes           整体说明
     * @param observedRatio   实测产品占画面比例（%）
     * @param width           原图宽度
     * @param height          原图高度
     */
    public record Analysis(List<Recommendation> recommendations, List<String> skipped,
                           List<String> notes, double observedRatio, int width, int height) {
    }

    /**
     * 分析参考图。
     *
     * @param bytes 图片字节（PNG/JPG/WEBP；解不开返回 null）
     * @return 分析结果；无法解码时返回 null（调用方据此说明「参考图不可用于分析」）
     */
    public Analysis analyze(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        BufferedImage source;
        try {
            source = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            log.warn("参考图解码失败：{}", e.getMessage());
            return null;
        }
        if (source == null) {
            return null;
        }

        BufferedImage sample = new BufferedImage(SAMPLE_SIZE, SAMPLE_SIZE, BufferedImage.TYPE_INT_RGB);
        sample.getGraphics().drawImage(source, 0, 0, SAMPLE_SIZE, SAMPLE_SIZE, null);

        int total = SAMPLE_SIZE * SAMPLE_SIZE;
        int[][] pixels = new int[SAMPLE_SIZE][SAMPLE_SIZE];
        for (int y = 0; y < SAMPLE_SIZE; y++) {
            for (int x = 0; x < SAMPLE_SIZE; x++) {
                pixels[y][x] = sample.getRGB(x, y) & 0xFFFFFF;
            }
        }

        // 1) 背景：取外围一圈的中位数，避免被主体边缘带偏
        int borderBand = Math.max(2, SAMPLE_SIZE / 12);
        List<Integer> border = new ArrayList<>();
        for (int y = 0; y < SAMPLE_SIZE; y++) {
            for (int x = 0; x < SAMPLE_SIZE; x++) {
                if (y < borderBand || y >= SAMPLE_SIZE - borderBand
                    || x < borderBand || x >= SAMPLE_SIZE - borderBand) {
                    border.add(pixels[y][x]);
                }
            }
        }
        int bg = medianColor(border);

        // 2) 主体掩码：与背景颜色距离超过阈值
        boolean[][] subject = new boolean[SAMPLE_SIZE][SAMPLE_SIZE];
        int subjectCount = 0;
        long uniformBorder = 0;
        for (int y = 0; y < SAMPLE_SIZE; y++) {
            for (int x = 0; x < SAMPLE_SIZE; x++) {
                if (distance(pixels[y][x], bg) > SUBJECT_DISTANCE_THRESHOLD) {
                    subject[y][x] = true;
                    subjectCount++;
                }
            }
        }
        for (int color : border) {
            if (distance(color, bg) <= BACKGROUND_TOLERANCE) {
                uniformBorder++;
            }
        }
        double backgroundUniformity = border.isEmpty() ? 1.0 : (double) uniformBorder / border.size();
        double subjectRatio = subjectCount * 100.0 / total;

        // 3) 主体主色：量化分桶后取最多的桶，再回算该桶平均色（避免只拿到桶心）
        Map<Integer, long[]> buckets = new LinkedHashMap<>();   // key → {count, sumR, sumG, sumB}
        // 饱和度只看**主体**：产品图常是大面积白底，把背景算进平均会把饱和度稀释成「低」，
        // 那个结论对出图没有指导意义（它描述的是背景，不是产品）。对比度则相反——它描述整幅画面，
        // 因此仍按整图统计。
        double subjectSatSum = 0;
        int subjectSatCount = 0;
        double allSatSum = 0;
        int allSatCount = 0;
        double lumSum = 0;
        double lumSqSum = 0;
        int lumCount = 0;
        int darkPixels = 0;
        int brightPixels = 0;
        double leftLum = 0;
        double rightLum = 0;
        int leftCount = 0;
        int rightCount = 0;
        double topLum = 0;
        double bottomLum = 0;
        int topCount = 0;
        int bottomCount = 0;

        for (int y = 0; y < SAMPLE_SIZE; y++) {
            for (int x = 0; x < SAMPLE_SIZE; x++) {
                int rgb = pixels[y][x];
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                float[] hsb = java.awt.Color.RGBtoHSB(r, g, b, null);
                double lum = 0.299 * r + 0.587 * g + 0.114 * b;
                allSatSum += hsb[1];
                allSatCount++;
                lumSum += lum;
                lumSqSum += lum * lum;
                lumCount++;
                if (lum < 60) {
                    darkPixels++;
                }
                if (lum > 200) {
                    brightPixels++;
                }
                if (subject[y][x]) {
                    subjectSatSum += hsb[1];
                    subjectSatCount++;
                    int key = bucketKey(r, g, b);
                    long[] cell = buckets.computeIfAbsent(key, k -> new long[4]);
                    cell[0]++;
                    cell[1] += r;
                    cell[2] += g;
                    cell[3] += b;
                    if (x < SAMPLE_SIZE / 2) {
                        leftLum += lum;
                        leftCount++;
                    } else {
                        rightLum += lum;
                        rightCount++;
                    }
                    if (y < SAMPLE_SIZE / 2) {
                        topLum += lum;
                        topCount++;
                    } else {
                        bottomLum += lum;
                        bottomCount++;
                    }
                }
            }
        }

        List<int[]> palette = new ArrayList<>();   // {r,g,b,count}
        buckets.values().stream()
            .sorted(Comparator.comparingLong((long[] c) -> -c[0]))
            .limit(4)
            .forEach(cell -> {
                if (cell[0] <= 0) {
                    return;
                }
                palette.add(new int[] {
                    (int) (cell[1] / cell[0]), (int) (cell[2] / cell[0]), (int) (cell[3] / cell[0]),
                    (int) cell[0]});
            });

        // 没有识别出主体时退回整图饱和度，避免给出一个没有意义的「低」
        double meanSat = subjectSatCount > 0
            ? subjectSatSum / subjectSatCount
            : (allSatCount == 0 ? 0 : allSatSum / allSatCount);
        boolean satFromSubject = subjectSatCount > 0;
        double meanLum = lumCount == 0 ? 0 : lumSum / lumCount;
        double variance = lumCount == 0 ? 0 : Math.max(0, lumSqSum / lumCount - meanLum * meanLum);
        double lumStd = Math.sqrt(variance);
        double darkShare = (double) darkPixels / total;
        double brightShare = (double) brightPixels / total;

        List<Recommendation> recs = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        // ---- 可实测：配色 ----
        if (!palette.isEmpty()) {
            int[] primary = palette.get(0);
            String primaryHex = hex(primary[0], primary[1], primary[2]);
            recs.add(new Recommendation("colors.primary", primaryHex,
                "参考图主体区域主色（占主体像素 " + percent(primary[3], subjectCount) + "）",
                RELIABILITY_HIGH));
            // 点缀色：候选里饱和度最高的那个（若明显更艳）
            int[] accent = palette.stream()
                .max(Comparator.comparingDouble(c -> saturation(c[0], c[1], c[2])))
                .orElse(primary);
            if (accent != primary && saturation(accent[0], accent[1], accent[2])
                > saturation(primary[0], primary[1], primary[2]) + 0.12) {
                recs.add(new Recommendation("colors.accent", hex(accent[0], accent[1], accent[2]),
                    "参考图中饱和度最高的次要色（占主体像素 " + percent(accent[3], subjectCount) + "）",
                    RELIABILITY_HIGH));
            }
            if (palette.size() > 1) {
                int[] secondary = palette.get(1);
                recs.add(new Recommendation("colors.secondary", hex(secondary[0], secondary[1], secondary[2]),
                    "参考图主体区域次要色（占主体像素 " + percent(secondary[3], subjectCount) + "）",
                    RELIABILITY_HIGH));
            }
        }
        recs.add(new Recommendation("colors.background", hex((bg >> 16) & 0xFF, (bg >> 8) & 0xFF, bg & 0xFF),
            "参考图外围一圈的中位色（背景一致度 "
                + Math.round(backgroundUniformity * 100) + "%）",
            RELIABILITY_HIGH));

        // ---- 可实测：饱和度 / 对比度 / 留白 / 产品占比 ----
        recs.add(new Recommendation("saturation", level(meanSat, 0.18, 0.45),
            (satFromSubject ? "主体平均饱和度 " : "整图平均饱和度（未识别出主体）")
                + String.format(Locale.ROOT, "%.2f", meanSat), RELIABILITY_HIGH));
        recs.add(new Recommendation("contrastLevel", level(lumStd, 30, 60),
            "亮度标准差 " + String.format(Locale.ROOT, "%.1f", lumStd), RELIABILITY_HIGH));
        double whitespace = 1 - subjectRatio / 100.0;
        recs.add(new Recommendation("whitespaceLevel", level(whitespace, 0.35, 0.6),
            "背景（留白）占画面 " + String.format(Locale.ROOT, "%.0f%%", whitespace * 100), RELIABILITY_HIGH));
        int ratioMin = (int) Math.max(5, Math.floor(subjectRatio - 8));
        int ratioMax = (int) Math.min(95, Math.ceil(subjectRatio + 8));
        recs.add(new Recommendation("productRatio", ratioMin + "~" + ratioMax,
            "参考图中主体实测占画面 " + String.format(Locale.ROOT, "%.0f%%", subjectRatio)
                + "，建议区间取实测值上下 8%", RELIABILITY_HIGH));

        // ---- 弱启发：光线与场景（如实标 MEDIUM） ----
        String lightingType = (lumStd >= 60 && (darkShare > 0.12 || brightShare > 0.12)) ? "HARD" : "SOFT";
        recs.add(new Recommendation("lighting.type", lightingType,
            "由亮度分布推测（暗部 " + String.format(Locale.ROOT, "%.0f%%", darkShare * 100)
                + "、亮部 " + String.format(Locale.ROOT, "%.0f%%", brightShare * 100)
                + "，标准差 " + String.format(Locale.ROOT, "%.0f", lumStd) + "）",
            RELIABILITY_MEDIUM));
        double dx = mean(leftLum, leftCount) - mean(rightLum, rightCount);
        double dy = mean(bottomLum, bottomCount) - mean(topLum, topCount);
        String direction;
        if (Math.abs(dx) >= Math.abs(dy) && Math.abs(dx) >= 8) {
            direction = "SIDE";
        } else if (Math.abs(dy) >= 8) {
            direction = dy > 0 ? "BACK" : "TOP";
        } else {
            direction = "FRONT";
        }
        recs.add(new Recommendation("lighting.direction", direction,
            "由主体明暗梯度推测（左右差 " + String.format(Locale.ROOT, "%.0f", dx)
                + "、上下差 " + String.format(Locale.ROOT, "%.0f", dy) + "）",
            RELIABILITY_MEDIUM));
        String scene = backgroundUniformity >= 0.85 ? "纯色底"
            : (backgroundUniformity >= 0.6 ? "生活场景" : "主题场景");
        recs.add(new Recommendation("sceneType", scene,
            "由背景一致度推测（" + String.format(Locale.ROOT, "%.0f%%", backgroundUniformity * 100) + "）",
            RELIABILITY_MEDIUM));

        // ---- 测不出来的一律不猜 ----
        skipped.add("风格关键词（styleKeywords）：属于审美与品牌语义，像素层面推不出来——请人工填写或由品牌调性事实带入");
        skipped.add("禁忌关键词（avoidKeywords）：同上，保持默认禁忌集即可");
        skipped.add("字体风格（typographyStyle）：参考图里未必有文字，字体授权与风格是业务决策");

        notes.add("以上配色/饱和度/对比度/留白/产品占比为**本地像素实测**，光线与场景为**弱启发**（标注为 MEDIUM，可改）");
        notes.add("实测产品占比 " + String.format(Locale.ROOT, "%.0f%%", subjectRatio)
            + "，背景一致度 " + String.format(Locale.ROOT, "%.0f%%", backgroundUniformity * 100));
        if (subjectCount == 0) {
            notes.add("没有识别出与背景明显不同的主体，配色建议可能只是背景色——建议换一张主体更清晰的参考图");
        }

        return new Analysis(recs, skipped, notes, subjectRatio, source.getWidth(), source.getHeight());
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    private static int bucketKey(int r, int g, int b) {
        int shift = 8 - BUCKET_BITS;
        return ((r >> shift) << (BUCKET_BITS * 2)) | ((g >> shift) << BUCKET_BITS) | (b >> shift);
    }

    private static int medianColor(List<Integer> colors) {
        if (colors.isEmpty()) {
            return 0xFFFFFF;
        }
        List<Integer> reds = new ArrayList<>();
        List<Integer> greens = new ArrayList<>();
        List<Integer> blues = new ArrayList<>();
        for (int rgb : colors) {
            reds.add((rgb >> 16) & 0xFF);
            greens.add((rgb >> 8) & 0xFF);
            blues.add(rgb & 0xFF);
        }
        reds.sort(Integer::compareTo);
        greens.sort(Integer::compareTo);
        blues.sort(Integer::compareTo);
        int mid = colors.size() / 2;
        return (reds.get(mid) << 16) | (greens.get(mid) << 8) | blues.get(mid);
    }

    private static double distance(int a, int b) {
        int dr = ((a >> 16) & 0xFF) - ((b >> 16) & 0xFF);
        int dg = ((a >> 8) & 0xFF) - ((b >> 8) & 0xFF);
        int db = (a & 0xFF) - (b & 0xFF);
        return Math.sqrt(dr * dr + dg * dg + db * db);
    }

    private static double saturation(int r, int g, int b) {
        return java.awt.Color.RGBtoHSB(r, g, b, null)[1];
    }

    private static double mean(double sum, int count) {
        return count == 0 ? 0 : sum / count;
    }

    private static String level(double value, double lowThreshold, double highThreshold) {
        if (value < lowThreshold) {
            return "LOW";
        }
        return value < highThreshold ? "MEDIUM" : "HIGH";
    }

    private static String hex(int r, int g, int b) {
        return String.format("#%02X%02X%02X", clamp(r), clamp(g), clamp(b));
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static String percent(int part, int whole) {
        if (whole <= 0) {
            return "0%";
        }
        return Math.round(part * 100.0 / whole) + "%";
    }

}
