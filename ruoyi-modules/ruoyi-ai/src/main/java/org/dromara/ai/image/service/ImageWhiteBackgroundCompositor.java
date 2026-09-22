package org.dromara.ai.image.service;

import lombok.extern.slf4j.Slf4j;
import org.dromara.ai.image.exception.ImageTaskException;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * 白底图合成：把抠图输出的透明 PNG 合成到纯白底，输出<b>不透明</b> PNG。
 *
 * <p><b>为什么这一步放在后端、而不是让模型直接画白底</b>：模型路线（图生图/指令改图）
 * 无法可靠地「只改背景、不动产品」——实测会把产品贴花、文字一起重绘。而白底图是电商交付物，
 * 要求产品像素级不变、背景恒为 {@code 255,255,255}。所以由程序做最后一步：
 * 抠图给蒙版（模型擅长），合成给白底（程序确定性完成，永远不会画坏产品）。</p>
 *
 * <p><b>两条硬断言</b>（宁可失败，也不要交付一张「看起来是白底、其实是原图」的假白底图）：</p>
 * <ol>
 *   <li>抠图输出必须带 alpha 通道 —— 拿不到 RGBA 说明这一步没产生蒙版，直接判 {@code OUTPUT_INVALID}；</li>
 *   <li>透明像素占比必须达到下限 —— 抠图模型偶尔会「什么都没抠掉」（背景原样保留），
 *       此时若照常合成，用户拿到的就是原图铺在白底上，问题会被悄悄掩盖。下限取 0.5%，
 *       对正常产品图（主体远未铺满整幅）非常宽松，只有真正没抠动才会触发。</li>
 * </ol>
 */
@Slf4j
public class ImageWhiteBackgroundCompositor {

    /**
     * 视为「抠图未生效」的透明像素占比下限（0.5%）。
     */
    static final double MIN_TRANSPARENT_RATIO = 0.005;

    /**
     * 合成白底。入参是 ComfyUI 产出的 RGBA PNG 字节，返回不透明 PNG 字节。
     *
     * @throws ImageTaskException {@code OUTPUT_INVALID}：输出无法解析、没有 alpha、或抠图未生效
     */
    public byte[] compositeOnWhite(byte[] source) {
        if (source == null || source.length == 0) {
            throw ImageTaskException.outputInvalid("白底图合成失败：抠图输出为空");
        }
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(source));
        } catch (IOException e) {
            throw ImageTaskException.outputInvalid("白底图合成失败：无法解析抠图输出（" + e.getMessage() + "）");
        }
        if (image == null) {
            throw ImageTaskException.outputInvalid("白底图合成失败：抠图输出不是可识别的图片");
        }
        if (!image.getColorModel().hasAlpha()) {
            throw ImageTaskException.outputInvalid("白底图合成失败：抠图输出没有透明通道（不是 RGBA PNG），背景无法被替换");
        }
        double clearRatio = transparentRatio(image);
        if (clearRatio < MIN_TRANSPARENT_RATIO) {
            throw ImageTaskException.outputInvalid(String.format(
                "抠图未生效：结果几乎不含透明区域（全透明像素仅 %.2f%%），无法生成白底图；请换一张主体与背景区分更明显的图重试",
                clearRatio * 100));
        }

        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage flattened = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = flattened.createGraphics();
        try {
            // 先铺纯白，再把带 alpha 的原图叠上去；SrcOver 对直通 alpha（PNG 的存法）是正确的
            graphics.setComposite(AlphaComposite.Src);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setComposite(AlphaComposite.SrcOver);
            graphics.drawImage(image, 0, 0, null);
        } finally {
            graphics.dispose();
        }

        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            if (!ImageIO.write(flattened, "png", buffer)) {
                throw ImageTaskException.outputInvalid("白底图合成失败：当前 JVM 没有可用的 PNG 编码器");
            }
            byte[] result = buffer.toByteArray();
            log.info("白底图合成完成：{}×{}，全透明像素占比 {}%，输出不透明 PNG {} 字节",
                width, height, Math.round(clearRatio * 10000) / 100.0, result.length);
            return result;
        } catch (IOException e) {
            throw ImageTaskException.outputInvalid("白底图合成失败：写出 PNG 出错（" + e.getMessage() + "）");
        }
    }

    /**
     * 全透明像素（alpha=0）占比。用栅格逐行取，避免 {@code getRGB} 的逐像素装箱开销。
     */
    private static double transparentRatio(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        long total = (long) width * height;
        if (total <= 0) {
            return 0d;
        }
        long clear = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if ((image.getRGB(x, y) >>> 24) == 0) {
                    clear++;
                }
            }
        }
        return (double) clear / total;
    }
}
