package org.dromara.creative.helper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 「枚举写成人话」唯一口径的单元测试（v1 人工测试反馈：卡片上印着 MEDIUM/HIGH/SOFT）。
 *
 * <p>这三条口径原先在三处各写各的（方向内容、分镜屏规格的留白、没有方向时的光线兜底），
 * 结果分镜卡片上漏出「留白 HIGH」、兜底句里漏出「光线：SOFT」。抽到一处之后在这里钉住。</p>
 *
 * @author creative
 */
class CreativeLevelTextTest {

    @Test
    @DisplayName("档位：低/中/高；空值说「未设置」，不默认成「中」")
    void levelWords() {
        assertEquals("低", CreativeLevelText.level("LOW"));
        assertEquals("中", CreativeLevelText.level("MEDIUM"));
        assertEquals("高", CreativeLevelText.level("HIGH"));
        assertEquals("高", CreativeLevelText.level(" high "), "大小写与空白都要认");
        assertEquals("未设置", CreativeLevelText.level(null));
        assertEquals("未设置", CreativeLevelText.level("  "));
    }

    @Test
    @DisplayName("认不出的档位原样返回——难看但可发现，不抹成「中」也不留空")
    void unknownLevelKept() {
        assertEquals("VERY_HIGH", CreativeLevelText.level("VERY_HIGH"));
    }

    @Test
    @DisplayName("光型与光位：SOFT→柔光、FRONT→正面光；认不出原样返回")
    void lightingWords() {
        assertEquals("柔光", CreativeLevelText.lighting("SOFT"));
        assertEquals("硬光", CreativeLevelText.lighting("HARD"));
        assertEquals("影棚均匀光", CreativeLevelText.lighting("STUDIO"));
        assertEquals("自然光", CreativeLevelText.lighting("NATURAL"));
        assertEquals("NEON", CreativeLevelText.lighting("NEON"));
        assertEquals("未设置", CreativeLevelText.lighting(null));

        assertEquals("正面光", CreativeLevelText.lightingDirection("FRONT"));
        assertEquals("侧光", CreativeLevelText.lightingDirection("SIDE"));
        assertEquals("顶光", CreativeLevelText.lightingDirection("TOP"));
        assertEquals("逆光轮廓", CreativeLevelText.lightingDirection("BACK"));
        assertEquals("", CreativeLevelText.lightingDirection(null), "光位缺省给空串（调用方据此省略这一段）");
    }
}
