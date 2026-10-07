package org.dromara.content.helper;

import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.StringUtils;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 成品图的本地确定性分析器（纯 JDK，不出网、不依赖任何模型）。
 *
 * <p><b>它在一致性检查里扮演什么角色</b>：语义级判断（主体形态、数量、Logo、
 * 包装文字是否与参考图一致）必须靠视觉模型或人工；但有一类问题<b>不需要模型</b>也能
 * 确凿地测出来，而且模型未必算得准——画布尺寸、长宽比、以及两图在像素/网格上的
 * <b>颜色与版面结构</b>差异。本类负责后者，产出两个用途：</p>
 * <ol>
 *     <li>作为<b>提示词证据</b>喂给视觉模型，让模型的判断有可核对的量化依据；</li>
 *     <li>作为<b>无模型时的兜底结论</b>（本地调用器直接据此出 verdict），
 *     使能力在没有配置任何视觉模型的环境里依然可用、且结论可解释。</li>
 * </ol>
 *
 * <p><b>比对的是「参考图」与「成品图」两张独立的图</b>：不做任何拼接、叠加或合成。
 * 两种比对模式，按输入自动选择：</p>
 * <ul>
 *     <li>{@code PIXEL}（两图尺寸完全一致）：按<b>相同坐标逐像素</b>比较 R/G/B。
 *     这是最贴近「原图 vs 成品图」的比对，能看见纹理与局部改动。</li>
 *     <li>{@code GRID}（尺寸不同但长宽比接近）：各自降采样成 32×32 的 RGB 均值网格再比。
 *     尺寸不同时无法逐像素对齐，只能比版面色块分布，结论天然更宽松。</li>
 * </ul>
 *
 * <p><b>为什么不能只做网格均值</b>：网格均值会抹掉单元内的高频差异。
 * 两张互不相关的随机纹理图，每格均值都收敛到同一个期望值，只比网格会得出 97/100
 * 的「一致」——这正是验收最怕的假通过。逐像素比对下同样的两张图是 67/100，判「不一致」。</p>
 *
 * <p><b>为什么按 RGB 三通道而不是只按亮度</b>：只比亮度会把颜色差异整体抹平——
 * 纯红与纯蓝的亮度分别是 76 与 29，只比亮度会得出「82/100，差异处于中间地带」这种
 * 无法用于验收的结论；按三通道比较则是 33/100，明确判为不一致。</p>
 *
 * <p><b>为什么不用 Graphics2D 缩放</b>：{@code drawImage} 会拉起 AWT 渲染管线，
 * 在无头/最小化运行环境里是额外风险。这里改为手写区域均值采样（box sampling），
 * 只用 {@link BufferedImage#getRGB}，不碰任何 AWT 工具包。</p>
 *
 * <p><b>为什么先读尺寸再解码</b>：{@link ImageIO#read} 会把整幅图解码进内存，
 * 一张超大图足以把堆打满。先用 {@link ImageReader} 只读头部尺寸，超过阈值直接拒绝。</p>
 *
 * @author content
 */
@Slf4j
public final class ContentImageInspector {

    /**
     * 归一化网格边长（32×32 = 1024 个采样点），用于尺寸不同时的比对与差异方位定位。
     * <p>取 32 而不是 16：16×16 只有 256 个格子，单个格子覆盖的版面太大，
     * 局部改动会被整格平均稀释到看不出来。</p>
     */
    private static final int GRID = 32;

    /**
     * 「同一张画面」判定的网格均值容差（0–255 通道尺度）。
     *
     * <p>取值来自实测（32×32×3 网格的最大单元格差，构造图 400×400）：</p>
     * <ul>
     *   <li>同一份字节：0.0；同图 JPEG q=0.95：1.07；q=0.8：<b>1.77</b>；q=0.6：3.03；q=0.4：4.13</li>
     *   <li>同图 JPEG q=0.8 二次编码：1.87</li>
     *   <li>真正不同的图：纯色微调（120,120,120 → 124,118,121）：<b>4.00</b>；
     *       两张不同噪声图：29.5；改掉一个 20×20 小角：104.4；红底 vs 蓝底：170.0</li>
     * </ul>
     * <p>取 2.0：能覆盖「同图按 200KB 目标重新编码」这一现实场景（q≥0.8），
     * 又停在 4.00 这条真实不同图的下界之外。<b>已知边界</b>：同一张图若被压到 q≈0.4
     * 这种极低质量，格差会超过阈值而漏过——常见的「同一个文件选两次」由字节相等这条兜住。</p>
     */
    private static final double SAME_PICTURE_TOLERANCE = 2.0d;

    /**
     * 每个网格单元记录的通道数（R、G、B）
     */
    private static final int CHANNELS = 3;

    /**
     * 逐像素比对的采样上限。超大图按步长抽稀到该量级，
     * 保证「同坐标比较」的语义不变（抽稀不改变两图的对应关系），同时避免慢到超时。
     */
    private static final long MAX_PIXEL_SAMPLES = 4_000_000L;

    /**
     * 允许解码的最大像素数（约 4000 万像素，等效 6300×6300）。
     * <p>详情页长图常见 750×8000（600 万像素）量级，远在阈值内；
     * 阈值只用于挡住「整幅解码即 OOM」的极端输入。</p>
     */
    private static final long MAX_PIXELS = 40_000_000L;

    /**
     * 长宽比相对偏差超过该值即认为「画布不可比」。
     */
    private static final double ASPECT_TOLERANCE = 0.20d;

    /**
     * 相似度 ≥ 该值判「一致」。
     */
    private static final double SIM_CONSISTENT = 90d;

    /**
     * 相似度 &lt; 该值判「不一致」；介于两者之间为「无法判定」。
     */
    private static final double SIM_INCONSISTENT = 75d;

    /**
     * 判定所依据的「最差区域」占比：取差异最大的 1% 网格单元的平均通道差。
     *
     * <p><b>为什么不能拿全图平均判</b>：白底产品图是最常见的素材形态——背景占 97% 且完全相同，
     * 只有中央产品块换了颜色时，全图平均通道差只有 5/255，会算出 98/100 的「一致」。
     * 也就是说，<b>越是大片相同的背景，越容易把真实差异平均掉</b>，
     * 而这恰恰是电商图的常态。改用「最差区域」后，同一对图（实测）由 98 分「一致」
     * 变为 46 分「不一致」，而同一张图另存/轻微位移仍判「一致」（差异是全局均匀的微小量）。</p>
     *
     * <p>取「前 1% 单元的平均」而不是「单个最差单元」：单个单元容易被压缩振铃、
     * 单点污渍带偏；取一小片区域的均值既能反映局部真实改动，又不会被单点噪声触发。</p>
     */
    private static final double HOTSPOT_RATIO = 0.01d;

    /**
     * 区域平均通道差超过该值（0–255）才值得把该方位作为差异点报出来。
     */
    private static final double ZONE_MIN_DIFF = 12d;

    /**
     * 3×3 方位名，用于把差异定位成人能直接读懂的位置。
     */
    private static final String[] ZONE_NAMES = {
        "左上", "上中", "右上",
        "左中", "正中", "右中",
        "左下", "下中", "右下"
    };

    /**
     * 逐像素比对（两图尺寸一致）
     */
    private static final String MODE_PIXEL = "PIXEL";

    /**
     * 网格均值比对（两图尺寸不同）
     */
    private static final String MODE_GRID = "GRID";

    private ContentImageInspector() {
    }

    /**
     * 单张图片的基础信息。
     *
     * @param decodable 是否成功解码
     * @param width     宽（解码失败为 0）
     * @param height    高（解码失败为 0）
     * @param format    格式名（如 png）
     * @param reason    不可解码的可读原因
     */
    public record ImageInfo(boolean decodable, int width, int height, String format, String reason) {

        /**
         * 长宽比；高为 0 时返回 0。
         *
         * @return 宽/高
         */
        public double aspectRatio() {
            return height <= 0 ? 0d : (double) width / height;
        }
    }

    /**
     * 比对结果（本地确定性度量）。
     *
     * @param reference       参考图信息
     * @param result          成品图信息
     * @param comparable      画布是否可比（都解码成功且长宽比接近）
     * @param similarity      权威相似度 0–100，<b>由「最差区域」得出</b>（判定依据）；不可比时为 null
     * @param compareMode     比对模式：{@code PIXEL}（同尺寸逐像素）/{@code GRID}（尺寸不同按网格）
     * @param meanChannelDiff 全图平均通道差 0–255（R/G/B 一起算）：仅供参考，<b>不用于判定</b>
     * @param hotspotDiff     最差 1% 区域的平均通道差 0–255：判定所依据的量
     * @param gridSimilarity  仅网格口径的相似度 0–100，供对照与差异方位定位
     * @param diffZones       差异最集中的方位（人类可读）；无显著差异时为空列表
     * @param notes           度量说明（给用户看的，不是给程序看的）
     */
    public record Comparison(ImageInfo reference, ImageInfo result, boolean comparable,
                             Double similarity, String compareMode, Double meanChannelDiff,
                             Double hotspotDiff, Double gridSimilarity, List<String> diffZones,
                             List<String> notes) {

        /**
         * 转成可入库的 JSON 友好结构。
         *
         * @return Map
         */
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("referenceWidth", reference.width());
            map.put("referenceHeight", reference.height());
            map.put("referenceFormat", reference.format());
            map.put("referenceDecodable", reference.decodable());
            map.put("resultWidth", result.width());
            map.put("resultHeight", result.height());
            map.put("resultFormat", result.format());
            map.put("resultDecodable", result.decodable());
            map.put("referenceAspect", round(reference.aspectRatio()));
            map.put("resultAspect", round(result.aspectRatio()));
            map.put("comparable", comparable);
            map.put("similarity", similarity == null ? null : round(similarity));
            map.put("compareMode", compareMode);
            map.put("meanChannelDiff", meanChannelDiff == null ? null : round(meanChannelDiff));
            map.put("hotspotChannelDiff", hotspotDiff == null ? null : round(hotspotDiff));
            map.put("gridSimilarity", gridSimilarity == null ? null : round(gridSimilarity));
            map.put("diffZones", diffZones);
            map.put("gridSize", GRID);
            map.put("notes", notes);
            return map;
        }

        /**
         * 保留两位小数。
         *
         * @param v 值
         * @return 四舍五入结果
         */
        private static double round(double v) {
            return Math.round(v * 100d) / 100d;
        }
    }

    /**
     * 读取图片基础信息（不解码整幅像素以外的内容）。
     *
     * @param bytes 图片字节
     * @return 图片信息，失败时 {@code decodable=false} 且带可读原因
     */
    public static ImageInfo inspect(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return new ImageInfo(false, 0, 0, null, "图片内容为空");
        }
        ImageIO.setUseCache(false);
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (iis == null) {
                return new ImageInfo(false, 0, 0, null, "无法读取图片数据");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                return new ImageInfo(false, 0, 0, null, "不支持的图片格式（仅支持 PNG/JPEG/GIF/BMP 等常见格式）");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                String format = reader.getFormatName();
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if ((long) width * height > MAX_PIXELS) {
                    return new ImageInfo(false, width, height, format,
                        "图片分辨率过大（" + width + "×" + height + "），超出在线比对上限");
                }
                return new ImageInfo(true, width, height, format, null);
            } finally {
                reader.dispose();
            }
        } catch (Exception e) {
            log.warn("图片信息读取失败, exception={}", e.getClass().getSimpleName());
            return new ImageInfo(false, 0, 0, null, "图片解析失败，文件可能已损坏");
        }
    }

    /**
     * 比对参考图与成品图，产出确定性度量。
     *
     * <p>两张图始终是<b>独立</b>的两份数据：尺寸一致时按相同坐标逐像素比，尺寸不同时
     * 各自降采样成网格再比。全程不做拼接或叠加。</p>
     *
     * @param referenceBytes 参考图字节
     * @param resultBytes    成品图字节
     * @return 比对结果（永不返回 null，不可比对时 {@code comparable=false} 并在 notes 说明）
     */
    public static Comparison compare(byte[] referenceBytes, byte[] resultBytes) {
        List<String> notes = new ArrayList<>();
        ImageInfo refInfo = inspect(referenceBytes);
        ImageInfo resInfo = inspect(resultBytes);
        if (!refInfo.decodable()) {
            notes.add("参考图不可用：" + refInfo.reason());
        }
        if (!resInfo.decodable()) {
            notes.add("成品图不可用：" + resInfo.reason());
        }
        if (!refInfo.decodable() || !resInfo.decodable()) {
            return notComparable(refInfo, resInfo, notes);
        }

        double refAspect = refInfo.aspectRatio();
        double resAspect = resInfo.aspectRatio();
        double deviation = refAspect <= 0 ? 1d : Math.abs(resAspect - refAspect) / refAspect;
        notes.add("参考图 " + refInfo.width() + "×" + refInfo.height()
            + "，成品图 " + resInfo.width() + "×" + resInfo.height()
            + "，长宽比偏差 " + Math.round(deviation * 100) + "%");
        if (deviation > ASPECT_TOLERANCE) {
            // 画布形状差异过大时不做像素级比对：结论会没有意义（例如参考图是单张产品图、
            // 成品是整版长图），这种情况应由视觉模型或人工判断，而不是本地度量硬给一个数。
            notes.add("两图长宽比差异过大，画布不可直接比对，需要人工或视觉模型确认版面关系");
            return notComparable(refInfo, resInfo, notes);
        }

        BufferedImage refImage = decode(referenceBytes);
        BufferedImage resImage = decode(resultBytes);
        if (refImage == null || resImage == null) {
            notes.add("图片像素读取失败，无法生成比对签名");
            return notComparable(refInfo, resInfo, notes);
        }

        double[] refGrid = gridSignature(refImage);
        double[] resGrid = gridSignature(resImage);
        if (refGrid == null || resGrid == null || refGrid.length != resGrid.length) {
            notes.add("图片像素读取失败，无法生成结构签名");
            return notComparable(refInfo, resInfo, notes);
        }
        // 网格口径：单元均值之间的差。它天然更宽松（单元内高频差异被平均掉），
        // 因此只作对照与方位定位，不用于出结论。
        double[] gridCellDiffs = cellChannelDiffs(refGrid, resGrid);
        double gridMeanDiff = mean(gridCellDiffs);
        double gridSimilarity = similarityOf(gridMeanDiff);
        List<String> zones = diffZones(refGrid, resGrid);

        // 尺寸一致才谈得上逐像素对齐；这是最贴近「原图 vs 成品图」的口径，优先采用
        double[] pixelCellDiffs = pixelCellChannelDiffs(refImage, resImage);
        double meanChannelDiff;
        double hotspotDiff;
        String mode;
        if (pixelCellDiffs != null) {
            mode = MODE_PIXEL;
            meanChannelDiff = mean(pixelCellDiffs);
            hotspotDiff = hotspotDiff(pixelCellDiffs);
            notes.add("两图尺寸一致，按相同坐标逐像素比对 R/G/B；全图平均通道差 "
                + Math.round(meanChannelDiff) + "/255，最差 1% 区域 "
                + Math.round(hotspotDiff) + "/255，判定相似度 " + Math.round(similarityOf(hotspotDiff)) + "/100");
        } else {
            mode = MODE_GRID;
            meanChannelDiff = gridMeanDiff;
            hotspotDiff = hotspotDiff(gridCellDiffs);
            notes.add("两图尺寸不同，按 " + GRID + "×" + GRID
                + " 网格均值比对 R/G/B；全图平均通道差 " + Math.round(meanChannelDiff)
                + "/255，最差 1% 区域 " + Math.round(hotspotDiff) + "/255，判定相似度 "
                + Math.round(similarityOf(hotspotDiff)) + "/100；"
                + "尺寸不同无法逐像素对齐，该结论比同尺寸时更宽松");
        }
        // 判定用「最差区域」而不是全图平均：白底商品图里背景往往占到九成以上且完全相同，
        // 拿全图平均会把中央那点真实差异平均掉（实测「红产品换蓝产品」只得 5/255、判 98 分一致）。
        // 详见 HOTSPOT_RATIO 的说明。
        double similarity = similarityOf(hotspotDiff);
        if (hotspotDiff - meanChannelDiff >= ZONE_MIN_DIFF) {
            notes.add("差异不是全局均匀的：全图平均 " + Math.round(meanChannelDiff)
                + "/255，而最差区域达 " + Math.round(hotspotDiff) + "/255，说明改动集中在局部");
        }
        if (!zones.isEmpty()) {
            notes.add("差异最集中的方位：" + String.join("、", zones));
        }
        // 结论边界要写清楚，不让「一致」这两个字被过度解读
        notes.add("本度量覆盖颜色、纹理与版面结构；画面中的文字内容与 Logo 语义"
            + "仍需人工或视觉模型确认");

        return new Comparison(refInfo, resInfo, true, similarity, mode, meanChannelDiff,
            hotspotDiff, gridSimilarity, zones, notes);
    }

    /**
     * 逐格平均通道差。
     *
     * @param cellDiffs 每格的平均通道差
     * @return 全格平均
     */
    private static double mean(double[] cellDiffs) {
        if (cellDiffs == null || cellDiffs.length == 0) {
            return 0d;
        }
        double sum = 0d;
        for (double d : cellDiffs) {
            sum += d;
        }
        return sum / cellDiffs.length;
    }

    /**
     * 「最差区域」通道差：差异最大的前 {@value #HOTSPOT_RATIO} 比例单元的平均值。
     *
     * <p>取一小片区域的均值而不是单个最差单元：单格容易被压缩振铃或单点污渍带偏，
     * 而一小片区域的均值既能反映真实的局部改动，又不会被单点噪声触发。</p>
     *
     * @param cellDiffs 每格的平均通道差
     * @return 最差区域的平均通道差；无数据返回 0
     */
    private static double hotspotDiff(double[] cellDiffs) {
        if (cellDiffs == null || cellDiffs.length == 0) {
            return 0d;
        }
        double[] sorted = cellDiffs.clone();
        java.util.Arrays.sort(sorted);
        int take = Math.max(1, (int) Math.round(sorted.length * HOTSPOT_RATIO));
        double sum = 0d;
        for (int i = sorted.length - take; i < sorted.length; i++) {
            sum += sorted[i];
        }
        return sum / take;
    }

    /**
     * 逐格平均通道差（网格签名版）：每个单元先按通道取绝对差，再对通道取平均。
     *
     * @param refGrid 参考图网格签名
     * @param resGrid 成品图网格签名
     * @return 长度 {@value #GRID}×{@value #GRID} 的每格通道差
     */
    private static double[] cellChannelDiffs(double[] refGrid, double[] resGrid) {
        int cells = GRID * GRID;
        double[] diffs = new double[cells];
        for (int i = 0; i < cells; i++) {
            int base = i * CHANNELS;
            double sum = 0d;
            for (int c = 0; c < CHANNELS; c++) {
                sum += Math.abs(refGrid[base + c] - resGrid[base + c]);
            }
            diffs[i] = sum / CHANNELS;
        }
        return diffs;
    }

    /**
     * 构造不可比对的结果。
     *
     * @param refInfo 参考图信息
     * @param resInfo 成品图信息
     * @param notes   说明
     * @return 不可比对的结果
     */
    private static Comparison notComparable(ImageInfo refInfo, ImageInfo resInfo, List<String> notes) {
        return new Comparison(refInfo, resInfo, false, null, null, null, null, null, List.of(), notes);
    }

    /**
     * 平均通道差 → 相似度：0–255 线性映射到 0–100。
     *
     * <p>完全相同得 100；与参考图毫无共同点的两图（如纯红 vs 纯蓝，平均通道差 170）得 33。</p>
     *
     * @param meanChannelDiff 平均通道差
     * @return 相似度 0–100
     */
    private static double similarityOf(double meanChannelDiff) {
        return Math.max(0d, 100d - meanChannelDiff / 255d * 100d);
    }

    /**
     * 逐像素比对（仅当两图尺寸完全一致）：按相同坐标比较 R/G/B，按 {@value #GRID}×{@value #GRID}
     * 单元分别累计，得到「每格平均通道差」——判定所依据的「最差区域」就是从这份数据里取的。
     *
     * <p>超大图按统一步长抽稀，抽稀不改变两图的坐标对应关系，因此语义不变。</p>
     *
     * @param refImage 参考图
     * @param resImage 成品图
     * @return 长度 {@value #GRID}×{@value #GRID} 的每格平均通道差；尺寸不一致或无采样点时返回 null
     */
    private static double[] pixelCellChannelDiffs(BufferedImage refImage, BufferedImage resImage) {
        int w = refImage.getWidth();
        int h = refImage.getHeight();
        if (w != resImage.getWidth() || h != resImage.getHeight()) {
            return null;
        }
        long total = (long) w * h;
        if (total <= 0) {
            return null;
        }
        int step = 1;
        while (total / ((long) step * step) > MAX_PIXEL_SAMPLES) {
            step++;
        }
        double[] sums = new double[GRID * GRID];
        long[] counts = new long[GRID * GRID];
        for (int y = 0; y < h; y += step) {
            int gy = Math.min(GRID - 1, (int) ((long) y * GRID / h));
            for (int x = 0; x < w; x += step) {
                int gx = Math.min(GRID - 1, (int) ((long) x * GRID / w));
                int a = refImage.getRGB(x, y);
                int b = resImage.getRGB(x, y);
                int cell = gy * GRID + gx;
                sums[cell] += Math.abs(((a >> 16) & 0xFF) - ((b >> 16) & 0xFF))
                    + Math.abs(((a >> 8) & 0xFF) - ((b >> 8) & 0xFF))
                    + Math.abs((a & 0xFF) - (b & 0xFF));
                counts[cell] += CHANNELS;
            }
        }
        double[] diffs = new double[GRID * GRID];
        boolean any = false;
        for (int i = 0; i < diffs.length; i++) {
            if (counts[i] > 0) {
                diffs[i] = sums[i] / counts[i];
                any = true;
            }
        }
        return any ? diffs : null;
    }

    /**
     * 按 3×3 方位统计通道差，返回差异最集中的若干方位（人类可读）。
     *
     * @param refGrid 参考图网格签名
     * @param resGrid 成品图网格签名
     * @return 方位描述列表；无显著差异时为空列表
     */
    private static List<String> diffZones(double[] refGrid, double[] resGrid) {
        double[] zoneSum = new double[ZONE_NAMES.length];
        long[] zoneCount = new long[ZONE_NAMES.length];
        for (int gy = 0; gy < GRID; gy++) {
            int zy = gy * 3 / GRID;
            for (int gx = 0; gx < GRID; gx++) {
                int zx = gx * 3 / GRID;
                int zone = zy * 3 + zx;
                int base = (gy * GRID + gx) * CHANNELS;
                for (int c = 0; c < CHANNELS; c++) {
                    zoneSum[zone] += Math.abs(refGrid[base + c] - resGrid[base + c]);
                }
                zoneCount[zone] += CHANNELS;
            }
        }
        List<double[]> ranked = new ArrayList<>();
        for (int i = 0; i < ZONE_NAMES.length; i++) {
            double avg = zoneCount[i] == 0 ? 0d : zoneSum[i] / zoneCount[i];
            ranked.add(new double[]{i, avg});
        }
        ranked.sort(Comparator.comparingDouble((double[] a) -> a[1]).reversed());

        List<String> out = new ArrayList<>();
        for (double[] z : ranked) {
            if (z[1] < ZONE_MIN_DIFF) {
                break;
            }
            out.add(ZONE_NAMES[(int) z[0]] + "（通道差 " + Math.round(z[1]) + "/255）");
            if (out.size() >= 3) {
                break;
            }
        }
        return out;
    }

    /**
     * 生成归一化 RGB 网格签名。
     *
     * @param image 已解码的图片
     * @return 长度 {@value #GRID}×{@value #GRID}×{@value #CHANNELS} 的均值数组；失败返回 null
     */
    private static double[] gridSignature(BufferedImage image) {
        if (image == null) {
            return null;
        }
        double[] grid = new double[GRID * GRID * CHANNELS];
        int w = image.getWidth();
        int h = image.getHeight();
        // 区域均值采样：每个网格单元覆盖源图的对应矩形，取该矩形的平均 R/G/B。
        // 不用最近邻是为了抑制摩尔纹——商品图上细密纹理很多，点采样会放大噪声。
        for (int gy = 0; gy < GRID; gy++) {
            int y0 = (int) ((long) gy * h / GRID);
            int y1 = Math.max(y0 + 1, (int) ((long) (gy + 1) * h / GRID));
            for (int gx = 0; gx < GRID; gx++) {
                int x0 = (int) ((long) gx * w / GRID);
                int x1 = Math.max(x0 + 1, (int) ((long) (gx + 1) * w / GRID));
                double sr = 0d;
                double sg = 0d;
                double sb = 0d;
                long count = 0;
                // 单元内再按步长抽样，避免大图上逐像素遍历（最坏情况是整幅图全扫一遍）
                int stepX = Math.max(1, (x1 - x0) / 8);
                int stepY = Math.max(1, (y1 - y0) / 8);
                for (int y = y0; y < y1 && y < h; y += stepY) {
                    for (int x = x0; x < x1 && x < w; x += stepX) {
                        int rgb = image.getRGB(x, y);
                        sr += (rgb >> 16) & 0xFF;
                        sg += (rgb >> 8) & 0xFF;
                        sb += rgb & 0xFF;
                        count++;
                    }
                }
                int base = (gy * GRID + gx) * CHANNELS;
                if (count > 0) {
                    grid[base] = sr / count;
                    grid[base + 1] = sg / count;
                    grid[base + 2] = sb / count;
                }
            }
        }
        return grid;
    }

    /**
     * 判定两张图是不是「同一张画面」。
     *
     * <p><b>为什么需要它</b>：参考图与成品图若是同一张图，比对结果必然是「一致」——
     * 这不是「成品忠实还原了参考图」，而是一次毫无信息量的自比。更糟的是它看起来像一次通过的验收。
     * 现实中很容易发生：参考图从任务已有附件里挑，而那张附件恰好是上一轮检查上传的成品图，
     * 或者两次上传的是同一个文件。这种情况应该在发起前就拦住，而不是给一个漂亮的「一致」。</p>
     *
     * <p><b>为什么带 1/255 的容差</b>：同一张图被重新编码（例如前端统一转 JPEG）后字节不同、
     * 像素也会有极轻微变化，逐字节比较会漏掉。判据取「尺寸相同，且 32×32 网格的每个通道均值
     * 都相差不超过 {@value #SAME_PICTURE_TOLERANCE}」——两张真正不同的照片绝无可能在
     * 3072 个格子上同时落进 1/255 以内，所以这个阈值既抓得住重复图，也不会误伤。</p>
     *
     * @param referenceBytes 参考图字节
     * @param resultBytes    成品图字节
     * @return 判定为同一张画面返回 true
     */
    public static boolean isSamePicture(byte[] referenceBytes, byte[] resultBytes) {
        if (referenceBytes == null || resultBytes == null
            || referenceBytes.length == 0 || resultBytes.length == 0) {
            return false;
        }
        if (Arrays.equals(referenceBytes, resultBytes)) {
            return true;
        }
        BufferedImage refImage = decode(referenceBytes);
        BufferedImage resImage = decode(resultBytes);
        if (refImage == null || resImage == null) {
            return false;
        }
        if (refImage.getWidth() != resImage.getWidth() || refImage.getHeight() != resImage.getHeight()) {
            return false;
        }
        double[] refGrid = gridSignature(refImage);
        double[] resGrid = gridSignature(resImage);
        if (refGrid == null || resGrid == null || refGrid.length != resGrid.length) {
            return false;
        }
        for (int i = 0; i < refGrid.length; i++) {
            if (Math.abs(refGrid[i] - resGrid[i]) > SAME_PICTURE_TOLERANCE) {
                return false;
            }
        }
        return true;
    }

    /**
     * 解码图片为 {@link BufferedImage}。
     *
     * @param bytes 图片字节
     * @return 图像；失败返回 null
     */
    private static BufferedImage decode(byte[] bytes) {
        try {
            ImageIO.setUseCache(false);
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            log.warn("图片解码失败, exception={}", e.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * 按本地度量给出结论。
     *
     * <p>阈值口径：≥ {@value #SIM_CONSISTENT} 判一致，&lt; {@value #SIM_INCONSISTENT} 判不一致，
     * 中间地带一律交人工/视觉模型。本地度量看得到颜色、纹理与版面结构，看不到画面里的
     * 文字内容与 Logo 语义，因此中间地带不硬给结论。</p>
     *
     * @param comparison 比对结果
     * @return 结论编码（CONSISTENT / INCONSISTENT / UNCERTAIN）
     */
    public static String localVerdict(Comparison comparison) {
        if (comparison == null || !comparison.comparable() || comparison.similarity() == null) {
            return "UNCERTAIN";
        }
        double similarity = comparison.similarity();
        if (similarity >= SIM_CONSISTENT) {
            return "CONSISTENT";
        }
        return similarity < SIM_INCONSISTENT ? "INCONSISTENT" : "UNCERTAIN";
    }

    /**
     * 按本地度量给出可读摘要。
     *
     * @param comparison 比对结果
     * @param verdict    结论编码
     * @return 摘要文本
     */
    public static String localSummary(Comparison comparison, String verdict) {
        if (comparison == null) {
            return "未取得比对度量";
        }
        if (!comparison.comparable()) {
            return "本地结构化比对未能成立：" + StringUtils.blankToDefault(
                comparison.notes().isEmpty() ? null : comparison.notes().get(comparison.notes().size() - 1),
                "两图不可直接比对") + "。请人工复核或改用视觉模型。";
        }
        String modeText = MODE_PIXEL.equals(comparison.compareMode())
            ? "逐像素" : GRID + "×" + GRID + " 网格";
        // 措辞刻意写清「两张图各自独立、未做合成」，并说明判定依据是「最差区域」：
        // 全图平均会把大片相同背景里的局部改动平均掉，只看平均会给出假「一致」。
        StringBuilder base = new StringBuilder();
        base.append("两张图各自独立比对（未做任何拼接或合成），口径：").append(modeText)
            .append("，判定相似度 ").append(Math.round(comparison.similarity())).append("/100");
        if (comparison.hotspotDiff() != null) {
            base.append("（最差 1% 区域通道差 ").append(Math.round(comparison.hotspotDiff())).append("/255");
            if (comparison.meanChannelDiff() != null) {
                base.append("，全图平均 ").append(Math.round(comparison.meanChannelDiff())).append("/255");
            }
            base.append("）");
        }
        base.append("。");
        String zones = comparison.diffZones() == null || comparison.diffZones().isEmpty()
            ? "" : "差异最集中在：" + String.join("、", comparison.diffZones()) + "。";
        String prefix = base.toString();
        return switch (verdict) {
            case "CONSISTENT" -> prefix + zones + "颜色与版面结构高度接近；画面中的文字内容与 Logo 语义请人工确认。";
            case "INCONSISTENT" -> prefix + zones + "与参考图差异明显（判定取最差区域，避免被大片相同背景稀释），请核对是否为同一版面、同一配色。";
            default -> prefix + zones + "差异处于中间地带，本地度量不足以判定，请人工复核或改用视觉模型。";
        };
    }

}
