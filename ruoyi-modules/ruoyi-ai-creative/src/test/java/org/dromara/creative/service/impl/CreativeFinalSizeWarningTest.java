package org.dromara.creative.service.impl;

import org.dromara.creative.domain.DpOutputSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 终版尺寸与输出规格比对的规则表（内测 S22 / C10 的回归钉）。
 *
 * <p><b>钉的是哪一类错</b>：这是"警告"而不是"拦截"，所以判错的方向有两个，
 * 而且都很隐蔽：</p>
 * <ol>
 *   <li><b>漏报</b>：真不合规却说没问题——512×512 当成 750 宽的 V1.0 交出去（S22 原样）。</li>
 *   <li><b>误报</b>：长图的高由内容决定（实测 750×4551），拿规格里的高度去比长图，
 *       每一版都会被判"不合规"。<b>警告一旦常态化就没人看了，等于没有</b>——
 *       所以 {@code heightMode=AUTO} 时高度必须完全不参与判定。</li>
 * </ol>
 *
 * <p>规则是纯函数（{@link CreativeLayoutServiceImpl#sizeWarning}），这里逐条钉住；
 * 真实链路的"上传即警告 + 刷新后仍在"由本地 R53 实例验证。</p>
 *
 * @author creative
 */
class CreativeFinalSizeWarningTest {

    /**
     * 造一条输出规格。默认按详情页的实际情况：750 宽、高度 AUTO（长图高由内容决定）。
     */
    private static DpOutputSpec spec(String code, Integer width, Integer height, String heightMode,
                                     Integer sourceScale) {
        DpOutputSpec spec = new DpOutputSpec();
        spec.setSpecCode(code);
        spec.setDeliveryType("ECOM_DETAIL");
        spec.setWidth(width);
        spec.setHeight(height);
        spec.setHeightMode(heightMode);
        spec.setSourceScale(sourceScale);
        return spec;
    }

    @Test
    @DisplayName("长图：宽一致 + 高度 AUTO（不论多高）都不报警")
    void autoHeightIsNeverCompared() {
        DpOutputSpec taobao = spec("TAOBAO_DETAIL", 750, null, "AUTO", 1);
        assertNull(CreativeLayoutServiceImpl.sizeWarning(taobao, new int[] {750, 4551}),
            "750×4551 是实测的正常长图，不该被判不合规");
        assertNull(CreativeLayoutServiceImpl.sizeWarning(taobao, new int[] {750, 30000}),
            "高度 AUTO 时高度完全不参与判定");
        // 即使规格里碰巧也填了高度，AUTO 模式下依然不比
        assertNull(CreativeLayoutServiceImpl.sizeWarning(spec("X", 750, 999, "AUTO", 1),
            new int[] {750, 4551}));
    }

    @Test
    @DisplayName("漏报防线：宽度不一致必须报警，且两个数字都要可读")
    void widthMismatchIsReported() {
        String warning = CreativeLayoutServiceImpl.sizeWarning(
            spec("TAOBAO_DETAIL", 750, null, "AUTO", 1), new int[] {512, 512});
        assertNotNull(warning, "512 宽的终版交到要求 750 宽的项目上，必须报警");
        assertTrue(warning.contains("512"), "要写清实际是多少：" + warning);
        assertTrue(warning.contains("750"), "要写清规格要求多少：" + warning);
        assertTrue(warning.contains("TAOBAO_DETAIL"), "要说清是拿哪条规格比的：" + warning);
        assertTrue(warning.contains("不拦交付"), "只警告不拦这件事要让用户知道：" + warning);
    }

    @Test
    @DisplayName("高度 FIXED 的规格（如主图 800×800）高度也要比")
    void fixedHeightIsCompared() {
        DpOutputSpec square = spec("MAIN_IMAGE_800", 800, 800, "FIXED", 1);
        assertNull(CreativeLayoutServiceImpl.sizeWarning(square, new int[] {800, 800}));
        String warning = CreativeLayoutServiceImpl.sizeWarning(square, new int[] {800, 600});
        assertNotNull(warning, "FIXED 规格下高度不符要报警");
        assertTrue(warning.contains("600") && warning.contains("800"), warning);
    }

    @Test
    @DisplayName("sourceScale 参与判定：倍率 2 的规格期望两倍像素")
    void sourceScaleMultipliesExpectedSize() {
        DpOutputSpec scaled = spec("X2", 750, null, "AUTO", 2);
        assertNull(CreativeLayoutServiceImpl.sizeWarning(scaled, new int[] {1500, 100}),
            "倍率 2 时 1500 宽才是对的");
        String warning = CreativeLayoutServiceImpl.sizeWarning(scaled, new int[] {750, 100});
        assertNotNull(warning, "倍率 2 时交 750 宽应报警");
        assertTrue(warning.contains("1500"), warning);
    }

    @Test
    @DisplayName("无法判定 ≠ 不合规：读不出尺寸、没有规格、规格没宽，都不报警")
    void unknownCasesAreNotWarnings() {
        DpOutputSpec taobao = spec("TAOBAO_DETAIL", 750, null, "AUTO", 1);
        assertNull(CreativeLayoutServiceImpl.sizeWarning(taobao, new int[] {0, 0}),
            "读不出尺寸时不能把不确定说成有罪");
        assertNull(CreativeLayoutServiceImpl.sizeWarning(taobao, null));
        assertNull(CreativeLayoutServiceImpl.sizeWarning(null, new int[] {512, 512}),
            "没有配置输出规格就没有判据，不猜");
        assertNull(CreativeLayoutServiceImpl.sizeWarning(spec("X", null, null, "AUTO", 1),
            new int[] {512, 512}));
        assertNull(CreativeLayoutServiceImpl.sizeWarning(spec("X", 0, null, "AUTO", 1),
            new int[] {512, 512}));
    }
}
