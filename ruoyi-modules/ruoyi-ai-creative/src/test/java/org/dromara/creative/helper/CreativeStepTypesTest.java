package org.dromara.creative.helper;

import org.dromara.creative.domain.DpScenarioStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 步骤"种类"判据（V0.2 R52）。
 *
 * <p>为什么值得单独测：R50 真机干跑撞出的故障就是<b>这个判据写错了</b>——
 * 海报的版式步叫 {@code POSTER_LAYOUT}，按 {@code step_code == 'LAYOUT'} 判，长图排版认为
 * "海报没有排版环节"，而多图渲染器那侧又反过来放行了它。判据错了不会抛异常，
 * 只会让两个渲染器对同一份配置得出相反结论，所以必须钉死在单测里。</p>
 */
class CreativeStepTypesTest {

    private static DpScenarioStep step(String code, String type) {
        DpScenarioStep row = new DpScenarioStep();
        row.setStepCode(code);
        row.setStepType(type);
        return row;
    }

    @Test
    @DisplayName("按 step_type 判：ECOM 的 LAYOUT 与海报的 POSTER_LAYOUT 都算排版环节")
    void matchesByStepTypeNotCode() {
        List<DpScenarioStep> ecom = List.of(step("INPUT", "INPUT"), step("LAYOUT", "LAYOUT"));
        List<DpScenarioStep> poster = List.of(step("INPUT", "INPUT"), step("POSTER_LAYOUT", "LAYOUT"));
        assertTrue(CreativeStepTypes.hasLayout(ecom));
        assertTrue(CreativeStepTypes.hasLayout(poster), "海报那一步 step_type=LAYOUT，必须算排版环节");
    }

    @Test
    @DisplayName("商品主图（没有任何排版类步骤）不算排版环节")
    void noLayoutForMainImage() {
        List<DpScenarioStep> main = List.of(step("INPUT", "INPUT"), step("GENERATION", "GENERATION"),
            step("FINAL", "FINAL_REVIEW"));
        assertFalse(CreativeStepTypes.hasLayout(main));
    }

    @Test
    @DisplayName("空清单 / 空 step_type / 空入参一律不算命中（fail-closed）")
    void failClosedOnMissingData() {
        assertFalse(CreativeStepTypes.hasLayout(null));
        assertFalse(CreativeStepTypes.hasLayout(List.of()));
        assertFalse(CreativeStepTypes.hasLayout(List.of(step("LAYOUT", null))),
            "老配置漏写 step_type 时按「没有这一类」处理，不能放行");
        assertFalse(CreativeStepTypes.has(null, "LAYOUT"));
        assertFalse(CreativeStepTypes.has(null, null));
    }

    @Test
    @DisplayName("大小写与空格不影响判定（配置是人填的）")
    void caseInsensitive() {
        assertTrue(CreativeStepTypes.has(List.of(step("POSTER_LAYOUT", " layout ")), "LAYOUT"));
    }
}
