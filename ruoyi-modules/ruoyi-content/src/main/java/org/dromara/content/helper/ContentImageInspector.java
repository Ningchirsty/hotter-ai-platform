package org.dromara.content.helper;

import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.StringUtils;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 成品图的本地确定性分析器（纯 JDK，不出网、不依赖任何模型）。
 *
 * <p><b>它在一致性检查里扮演什么角色</b>：语义级判断（主体形态、颜色、数量、Logo、
 * 包装文字是否与参考图一致）必须靠视觉模型或人工；但有一类问题<b>不需要模型</b>也能
 * 确凿地测出来，而且模型未必算得准——画布尺寸、长宽比、以及两图在归一化网格上的
 * 整体明暗结构差异。本类负责后者，产出两个用途：</p>
 * <ol>
 *     <li>作为<b>提示词证据</b>喂给视觉模型，让模型的判断有可核对的量化依据；</li>
 *     <li>作为<b>无模型时的兜底结论</b>（本地调用器直接据此出 verdict），
 *     使能力在没有配置任何视觉模型的环境里依然可用、且结论可解释。</li>
 * </ol>
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
     * 归一化网格边长（16×16 = 256 个采样点）。
     * <p>取 16 是因为它既能反映版面结构，又对「同一版面的轻微缩放/裁边」不过度敏感。</p>
     */
    private static final int GRID = 16;

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
     * @param gridSimilarity  归一化网格相似度 0–100；不可比时为 null
     * @param meanLumaDiff    平均亮度差 0–255；不可比时为 null
     * @param notes           度量说明（给用户看的，不是给程序看的）
     */
    public record Comparison(ImageInfo reference, ImageInfo result, boolean comparable,
                             Double gridSimilarity, Double meanLumaDiff, List<String> notes) {

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
            map.put("gridSimilarity", gridSimilarity == null ? null : round(gridSimilarity));
            map.put("meanLumaDiff", meanLumaDiff == null ? null : round(meanLumaDiff));
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
            return new Comparison(refInfo, resInfo, false, null, null, notes);
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
            return new Comparison(refInfo, resInfo, false, null, null, notes);
        }

        double[] refGrid = gridSignature(referenceBytes, refInfo);
        double[] resGrid = gridSignature(resultBytes, resInfo);
        if (refGrid == null || resGrid == null || refGrid.length != resGrid.length) {
            notes.add("图片像素读取失败，无法生成结构签名");
            return new Comparison(refInfo, resInfo, false, null, null, notes);
        }

        double sumAbs = 0d;
        for (int i = 0; i < refGrid.length; i++) {
            sumAbs += Math.abs(refGrid[i] - resGrid[i]);
        }
        double meanLumaDiff = sumAbs / refGrid.length;
        // 亮度差 0–255 线性映射到 0–100 相似度。这是粗粒度结构相似，不是像素级判等；
        // 阈值取得保守（见各调用点），避免把「轻微色差」报成「不一致」。
        double similarity = Math.max(0d, 100d - (meanLumaDiff / 255d * 100d));
        notes.add("本地结构相似度 " + Math.round(similarity) + "/100（16×16 归一化网格亮度比对，"
            + "只反映版面与明暗结构，不判断颜色准确性与文字内容）");
        return new Comparison(refInfo, resInfo, true, similarity, meanLumaDiff, notes);
    }

    /**
     * 生成归一化灰度网格签名。
     *
     * @param bytes 图片字节
     * @param info  图片信息（已解码成功）
     * @return 长度 {@value #GRID}×{@value #GRID} 的亮度数组；失败返回 null
     */
    private static double[] gridSignature(byte[] bytes, ImageInfo info) {
        BufferedImage image = decode(bytes);
        if (image == null) {
            return null;
        }
        double[] grid = new double[GRID * GRID];
        int w = image.getWidth();
        int h = image.getHeight();
        // 区域均值采样：每个网格单元覆盖源图的对应矩形，取该矩形的平均亮度。
        // 不用最近邻是为了抑制摩尔纹——商品图上细密纹理很多，点采样会放大噪声。
        for (int gy = 0; gy < GRID; gy++) {
            int y0 = (int) ((long) gy * h / GRID);
            int y1 = Math.max(y0 + 1, (int) ((long) (gy + 1) * h / GRID));
            for (int gx = 0; gx < GRID; gx++) {
                int x0 = (int) ((long) gx * w / GRID);
                int x1 = Math.max(x0 + 1, (int) ((long) (gx + 1) * w / GRID));
                double sum = 0d;
                long count = 0;
                // 单元内再按步长抽样，避免大图上逐像素遍历（最坏情况是整幅图全扫一遍）
                int stepX = Math.max(1, (x1 - x0) / 8);
                int stepY = Math.max(1, (y1 - y0) / 8);
                for (int y = y0; y < y1 && y < h; y += stepY) {
                    for (int x = x0; x < x1 && x < w; x += stepX) {
                        int rgb = image.getRGB(x, y);
                        int r = (rgb >> 16) & 0xFF;
                        int g = (rgb >> 8) & 0xFF;
                        int b = rgb & 0xFF;
                        // 标准 Rec.601 亮度权重
                        sum += 0.299d * r + 0.587d * g + 0.114d * b;
                        count++;
                    }
                }
                grid[gy * GRID + gx] = count == 0 ? 0d : sum / count;
            }
        }
        return grid;
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
     * 按本地度量给出保守结论。
     *
     * <p>阈值刻意保守：本地度量只看得到版面与明暗结构，看不到颜色准确性与文字内容，
     * 因此只有在「结构差异非常大」时才敢判不一致，中间地带一律交人工/视觉模型。</p>
     *
     * @param comparison 比对结果
     * @return 结论编码（CONSISTENT / INCONSISTENT / UNCERTAIN）
     */
    public static String localVerdict(Comparison comparison) {
        if (comparison == null || !comparison.comparable() || comparison.gridSimilarity() == null) {
            return "UNCERTAIN";
        }
        double similarity = comparison.gridSimilarity();
        if (similarity >= 92d) {
            return "CONSISTENT";
        }
        return similarity < 60d ? "INCONSISTENT" : "UNCERTAIN";
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
        String base = "本地结构化比对（16×16 网格亮度）相似度 "
            + Math.round(comparison.gridSimilarity()) + "/100。";
        return switch (verdict) {
            case "CONSISTENT" -> base + "版面与明暗结构高度接近；本结论不覆盖颜色准确性与文字内容，请人工确认关键信息。";
            case "INCONSISTENT" -> base + "版面结构与参考图差异明显，请核对是否为同一版面。";
            default -> base + "差异处于中间地带，本地度量不足以判定，请人工复核或改用视觉模型。";
        };
    }

}
