package org.dromara.creative.helper;

import org.dromara.common.core.utils.StringUtils;
import org.dromara.creative.domain.DpScenarioStep;

import java.util.List;

/**
 * 配置步骤的「种类」判据（V0.2 R52）。
 *
 * <p><b>为什么要有这个类</b>：R50 真机干跑撞出一个真实故障——海报的版式步叫
 * {@code POSTER_LAYOUT}，而长图排版当时是<b>按步骤编码精确等于 {@code LAYOUT}</b> 找排版环节的，
 * 于是海报点"渲染"得到的是「流程里没有排版环节」；同一个判据在多图渲染器那侧又反过来放行了海报。
 * R26 写那段时的注释说的是"判据取场景配置的步骤表……以后新增别的长图类交付类型时，
 * 只要配置里配了 LAYOUT 就能排版，不用改代码"——<b>意图是配置驱动，实现却挂在了一个具体编码上</b>。</p>
 *
 * <p>判据改成看 {@code dp_scenario_step.step_type}（配置里本来就有的"这一步是哪一类"），
 * 于是"换个交付类型、换个步骤编码"不再需要改代码：ECOM_DETAIL 的 {@code LAYOUT}、
 * 品牌海报的 {@code POSTER_LAYOUT} 都是 {@code step_type=LAYOUT}，两者按同一条规则判；
 * 商品主图（没有任何 LAYOUT 类步骤）依旧被判为"没有排版环节"。</p>
 *
 * <p>纯函数（只读入参、不碰容器），因此可以直接单测——这正是"判据写错会静默指错路"的地方。</p>
 *
 * @author creative
 */
public final class CreativeStepTypes {

    /** 排版类步骤（长图 / 海报版式这类"要排版"的步骤都是它） */
    public static final String LAYOUT = "LAYOUT";

    private CreativeStepTypes() {
    }

    /**
     * 步骤清单里有没有某一类步骤。
     *
     * <p>按 {@code step_type} 判，大小写不敏感；{@code step_type} 为空的步骤不算命中
     * （老配置漏写时按"没有这一类"处理，与 {@code CreativeStepProjection.skippable}
     * 对空值 fail-closed 的口径一致：字段没迁过来时不放行）。</p>
     *
     * @param steps    配置步骤（可空）
     * @param stepType 种类编码（如 LAYOUT）
     * @return 有则 true
     */
    public static boolean has(List<DpScenarioStep> steps, String stepType) {
        String want = StringUtils.trimToNull(stepType);
        if (steps == null || want == null) {
            return false;
        }
        for (DpScenarioStep step : steps) {
            if (step == null) {
                continue;
            }
            if (want.equalsIgnoreCase(StringUtils.trimToEmpty(step.getStepType()))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 该交付类型的流程里有没有排版环节（长图 / 海报版式都算）。
     *
     * @param steps 配置步骤（可空）
     * @return 有则 true
     */
    public static boolean hasLayout(List<DpScenarioStep> steps) {
        return has(steps, LAYOUT);
    }
}
