package org.dromara.creative.helper;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;
import java.util.Locale;

/**
 * 参考图适配器：把要送进模型的参考图缩到工作流能接受的像素预算内。
 *
 * <p><b>为什么必须有它（生产实测的真实故障）</b>：{@code wf-i2i-qwen21} 的契约写明
 * {@code sizePolicy = 跟随输入图尺寸，服务端不改写宽高}，并且 {@code outputRule.maxPixels = 4194304}。
 * 也就是说该工作流**只支持「跟随输入图」尺寸**（没有可传的尺寸档位），于是：
 * 参考图 2496×2992（7.47MP）→ 产出同样大小 → 超过上限 → 被判 {@code OUTPUT_INVALID}，
 * 而且是在 GPU 出完图之后才判，白烧一次卡。</p>
 *
 * <p><b>纪律</b>：</p>
 * <ul>
 *   <li><b>只缩要送模型的那一份</b>：项目里的参考图/产品图附件本身不动，产品图基准质检仍用原图；</li>
 *   <li><b>只超限才缩</b>（用户确认的口径 B）：未超限的图原样返回，不做任何重编码，
 *       避免「本来没问题却被降质」；</li>
 *   <li><b>先读尺寸再决定是否解码</b>：用 {@link ImageReader} 只读宽高；确实要缩时按子采样解码，
 *       避免一张 100MP 的图直接把内存打满；</li>
 *   <li><b>缩放结果如实记录</b>：调用方把 {@link Fitted#note()} 写进生成记录与阶段事件，
 *       页面上能回答「这张图是按多大的参考图出的」。</li>
 * </ul>
 *
 * @author creative
 */
@Slf4j
public final class ReferenceImageFitter {

    /**
     * 缩放结果。
     *
     * @param bytes       实际要送模型的字节（未缩放时就是原字节）
     * @param fileName    文件名（格式变了会改扩展名，避免扩展名撒谎）
     * @param contentType MIME
     * @param scaled      是否发生了缩放
     * @param fromWidth   原宽（未读出为 null）
     * @param fromHeight  原高
     * @param width       送模型的宽
     * @param height      送模型的高
     * @param maxPixels   该工作流的像素上限
     * @param note        可读说明（未缩放时为 null；页面上如实展示）
     */
    public record Fitted(byte[] bytes, String fileName, String contentType, boolean scaled,
                         Integer fromWidth, Integer fromHeight, Integer width, Integer height,
                         int maxPixels, String note) {
    }

    private ReferenceImageFitter() {
    }

    /**
     * 按工作流的像素上限适配参考图。
     *
     * @param original  参考图原始字节
     * @param fileName  原始文件名
     * @param maxPixels 工作流输出像素上限（来自契约，不是拍脑袋的常数）
     * @return 适配结果
     */
    public static Fitted fit(byte[] original, String fileName, int maxPixels) {
        String name = blankToDefault(fileName, "reference.png");
        String contentType = contentTypeOf(name);
        if (original == null || original.length == 0 || maxPixels <= 0) {
            return new Fitted(original, name, contentType, false, null, null, null, null, maxPixels, null);
        }

        int[] size = readSize(original);
        if (size == null) {
            // 读不出尺寸就不猜：交给内核侧的实测断言去判，也如实说明「本次未做适配」
            log.warn("参考图尺寸读取失败，未做缩放：{}", name);
            return new Fitted(original, name, contentType, false, null, null, null, null, maxPixels,
                "参考图尺寸读取失败，未做缩放（由内核侧实测断言判定）");
        }
        int fromW = size[0];
        int fromH = size[1];
        long pixels = (long) fromW * fromH;
        if (pixels <= maxPixels) {
            return new Fitted(original, name, contentType, false, fromW, fromH, fromW, fromH, maxPixels, null);
        }

        int[] target = fitWithin(fromW, fromH, maxPixels);
        try {
            BufferedImage scaled = scale(original, fromW, fromH, target[0], target[1], maxPixels);
            if (scaled == null) {
                return new Fitted(original, name, contentType, false, fromW, fromH, fromW, fromH, maxPixels,
                    "参考图 " + fromW + "×" + fromH + " 超过上限 " + maxPixels + " 像素，但解码失败未能缩放");
            }
            boolean keepAlpha = scaled.getColorModel().hasAlpha();
            boolean jpeg = isJpeg(name) && !keepAlpha;
            byte[] encoded = jpeg ? writeJpeg(scaled) : writePng(scaled);
            if (encoded == null || encoded.length == 0) {
                return new Fitted(original, name, contentType, false, fromW, fromH, fromW, fromH, maxPixels,
                    "参考图 " + fromW + "×" + fromH + " 超过上限 " + maxPixels + " 像素，但重编码失败未能缩放");
            }
            String outName = jpeg ? replaceExt(name, "jpg") : replaceExt(name, "png");
            String outType = jpeg ? "image/jpeg" : "image/png";
            String note = "参考图 " + fromW + "×" + fromH + "（" + pixels + " 像素）超过该工作流上限 "
                + maxPixels + " 像素：已缩放到 " + target[0] + "×" + target[1] + "（"
                + (long) target[0] * target[1] + " 像素）后送模型；项目里的参考图附件本身未改动，"
                + "产品图基准质检仍用原图";
            log.info("参考图已适配工作流像素上限：{} {}×{} → {}×{}（上限 {}）",
                name, fromW, fromH, target[0], target[1], maxPixels);
            return new Fitted(encoded, outName, outType, true, fromW, fromH, target[0], target[1], maxPixels, note);
        } catch (Exception e) {
            log.warn("参考图缩放失败，按原图提交（内核侧会如实判定）：{} {}", name, e.toString());
            return new Fitted(original, name, contentType, false, fromW, fromH, fromW, fromH, maxPixels,
                "参考图 " + fromW + "×" + fromH + " 超过上限 " + maxPixels + " 像素，缩放异常：" + e.getClass().getSimpleName());
        }
    }

    /**
     * 在不超过 maxPixels 的前提下，按原比例取最大整数尺寸。
     *
     * @param w         原宽
     * @param h         原高
     * @param maxPixels 上限
     * @return {宽, 高}
     */
    static int[] fitWithin(int w, int h, int maxPixels) {
        double scale = Math.sqrt((double) maxPixels / ((double) w * h));
        int tw = Math.max(1, (int) Math.floor(w * scale));
        int th = Math.max(1, (int) Math.floor(h * scale));
        // 浮点误差可能让 w*h 刚好越界，逐步收敛保证「一定不超上限」
        while ((long) tw * th > maxPixels && tw > 1) {
            tw--;
            th = Math.max(1, (int) Math.floor((double) h * tw / w));
        }
        return new int[] {tw, th};
    }

    /**
     * 只读图片宽高（不解码像素，避免大图打满内存）。
     *
     * @param content 图片字节
     * @return {宽, 高}；读不出返回 null
     */
    static int[] readSize(byte[] content) {
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            if (stream == null) {
                return null;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                int w = reader.getWidth(0);
                int h = reader.getHeight(0);
                return w > 0 && h > 0 ? new int[] {w, h} : null;
            } finally {
                reader.dispose();
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 缩放解码：按子采样先降采样再精确缩到目标尺寸。
     *
     * @param content   原字节
     * @param fromW     原宽
     * @param fromH     原高
     * @param toW       目标宽
     * @param toH       目标高
     * @param maxPixels 上限（用于算子采样步长）
     * @return 缩放后的图像；失败返回 null
     */
    private static BufferedImage scale(byte[] content, int fromW, int fromH, int toW, int toH, int maxPixels) {
        BufferedImage source = decodeSubsampled(content, fromW, fromH, maxPixels);
        if (source == null) {
            return null;
        }
        boolean alpha = source.getColorModel().hasAlpha();
        BufferedImage target = new BufferedImage(toW, toH,
            alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(source, 0, 0, toW, toH, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    private static BufferedImage decodeSubsampled(byte[] content, int fromW, int fromH, int maxPixels) {
        int step = 1;
        long pixels = (long) fromW * fromH;
        if (pixels > maxPixels) {
            step = Math.max(1, (int) Math.floor(Math.sqrt((double) pixels / maxPixels)));
        }
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            if (stream != null) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
                if (readers.hasNext()) {
                    ImageReader reader = readers.next();
                    try {
                        reader.setInput(stream, true, true);
                        ImageReadParam param = reader.getDefaultReadParam();
                        if (step > 1) {
                            param.setSourceSubsampling(step, step, 0, 0);
                        }
                        return reader.read(0, param);
                    } finally {
                        reader.dispose();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("子采样解码失败，回退整图解码：{}", e.toString());
        }
        try {
            return ImageIO.read(new ByteArrayInputStream(content));
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] writePng(BufferedImage image) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            return ImageIO.write(image, "png", out) ? out.toByteArray() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] writeJpeg(BufferedImage image) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            return writePng(image);
        }
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(0.92f);
            }
            writer.write(null, new IIOImage(image, null, null), param);
            ios.flush();
            return out.toByteArray();
        } catch (Exception e) {
            return writePng(image);
        } finally {
            writer.dispose();
        }
    }

    private static boolean isJpeg(String fileName) {
        String ext = extOf(fileName);
        return "jpg".equals(ext) || "jpeg".equals(ext);
    }

    private static String contentTypeOf(String fileName) {
        return switch (extOf(fileName)) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "webp" -> "image/webp";
            case "bmp" -> "image/bmp";
            default -> "image/png";
        };
    }

    private static String extOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int idx = fileName.lastIndexOf('.');
        return idx < 0 ? "" : fileName.substring(idx + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 换扩展名（格式变了必须换，否则扩展名在撒谎）。
     *
     * @param fileName 原文件名
     * @param ext      新扩展名
     * @return 新文件名
     */
    static String replaceExt(String fileName, String ext) {
        int idx = fileName.lastIndexOf('.');
        String base = idx < 0 ? fileName : fileName.substring(0, idx);
        return base + "." + ext;
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
