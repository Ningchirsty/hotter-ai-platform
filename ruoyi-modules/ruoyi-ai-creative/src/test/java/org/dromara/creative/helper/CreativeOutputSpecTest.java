package org.dromara.creative.helper;

import org.dromara.creative.domain.DpOutputSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 主图 1:1 规格接进出图（V0.2 R27）。
 *
 * <p>两件事必须钉住：</p>
 * <ol>
 *   <li><b>什么规格算"固定尺寸"</b>：只有 {@code height_mode=FIXED} 且宽高有效才算——
 *       长图那种"高度自动"的规格不能当出图尺寸用（否则会把长图当方图出）；</li>
 *   <li><b>怎么适配</b>：输出跟随输入图的工作流，要靠把参考图适配成目标尺寸来定产出大小；
 *       适配必须是「等比缩放到盖住 → 中心裁切」，**不能拉伸**（拉伸会把产品拍扁，比不缩放更糟）。</li>
 * </ol>
 */
class CreativeOutputSpecTest {

    private static DpOutputSpec spec(String code, Integer w, Integer h, String heightMode) {
        DpOutputSpec spec = new DpOutputSpec();
        spec.setSpecCode(code);
        spec.setWidth(w);
        spec.setHeight(h);
        spec.setHeightMode(heightMode);
        spec.setRatio("1:1");
        return spec;
    }

    private static byte[] png(int w, int h) throws Exception {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    @Test
    @DisplayName("固定尺寸规格（主图 800×800）→ 给出目标尺寸；AUTO 高度的长图规格 → 不适用")
    void onlyFixedSpecsCount() {
        CreativeOutputSpecResolver.TargetSize main = CreativeOutputSpecResolver.fixedSizeOf(
            spec("MAIN_IMAGE_800", 800, 800, "FIXED"));
        assertNotNull(main);
        assertEquals(800, main.width());
        assertEquals(800, main.height());
        assertEquals("MAIN_IMAGE_800", main.code());
        assertTrue(main.reason().contains("800×800"), main.reason());

        assertNull(CreativeOutputSpecResolver.fixedSizeOf(spec("TAOBAO_DETAIL", 750, null, "AUTO")),
            "长图那种高度自动的规格不能当出图尺寸");
        assertNull(CreativeOutputSpecResolver.fixedSizeOf(spec("X", 0, 800, "FIXED")));
        assertNull(CreativeOutputSpecResolver.fixedSizeOf(null));
    }

    @Test
    @DisplayName("参考图适配到 800×800：等比缩放到盖住再中心裁切（不是拉伸）")
    void fitToCoversThenCrops() throws Exception {
        byte[] source = png(1000, 400);   // 宽扁图：要盖住 800×800 必须先放大到 2000×800 再裁中间
        ReferenceImageFitter.Fitted fitted = ReferenceImageFitter.fitTo(source, "wide.png", 800, 800);

        assertTrue(fitted.scaled(), "尺寸不一致时必须适配");
        assertEquals(800, fitted.width());
        assertEquals(800, fitted.height());
        int[] out = ReferenceImageFitter.readSize(fitted.bytes());
        assertNotNull(out);
        assertEquals(800, out[0]);
        assertEquals(800, out[1]);
        assertTrue(fitted.note().contains("裁切"), fitted.note());
        assertTrue(fitted.note().contains("附件未改动"), "要说清项目里的附件没被改");
    }

    @Test
    @DisplayName("已经是目标尺寸 → 不缩放、不动字节")
    void alreadyTargetSizeIsUntouched() throws Exception {
        byte[] source = png(800, 800);
        ReferenceImageFitter.Fitted fitted = ReferenceImageFitter.fitTo(source, "exact.png", 800, 800);

        assertEquals(false, fitted.scaled());
        assertEquals(800, fitted.width());
        assertEquals(800, fitted.height());
        assertNull(fitted.note());
    }

    @Test
    @DisplayName("读不出尺寸/坏字节 → 如实回落原图并说明（不猜、不假装成功）")
    void unreadableFallsBackHonestly() {
        byte[] broken = "not-an-image".getBytes();
        ReferenceImageFitter.Fitted fitted = ReferenceImageFitter.fitTo(broken, "broken.png", 800, 800);

        assertEquals(false, fitted.scaled());
        assertNotNull(fitted.note());
        assertTrue(fitted.note().contains("800×800"), fitted.note());
    }
}
