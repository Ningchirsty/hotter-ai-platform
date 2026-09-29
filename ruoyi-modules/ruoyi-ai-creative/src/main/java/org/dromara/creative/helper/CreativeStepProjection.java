package org.dromara.creative.helper;

import org.dromara.common.core.utils.StringUtils;
import org.dromara.creative.enums.DpVisualStageEnum;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 「项目阶段 → 步骤状态」投影（V0.2 D2）。
 *
 * <p><b>为什么必须是纯函数</b>：这一步决定界面上"我做到第几步了"，投影错了会把人指到错的环节，
 * 而且看起来还挺像对的。纯函数可以直接断言，不需要起容器、不需要数据库。</p>
 *
 * <p><b>权威顺序</b>：阶段的顺序来自代码里的 {@link DpVisualStageEnum}（19 个阶段的声明顺序），
 * 配置（{@code dp_scenario_step.stage_codes}）只说明"这一步骤覆盖哪几个阶段"。
 * 也就是说：**配置能改步骤的粒度与命名，改不了阶段的先后与合法性**（阶段合法性在 canMoveTo）。</p>
 *
 * <p>判定规则（对每个配置步骤）：</p>
 * <ol>
 *   <li>当前阶段就在该步骤的 stage_codes 里 → {@link #ACTIVE}；</li>
 *   <li>该步骤覆盖的所有阶段都排在当前阶段之前 → {@link #DONE}；</li>
 *   <li>该步骤覆盖的所有阶段都排在当前阶段之后 → {@link #PENDING}；</li>
 *   <li>当前阶段落在该步骤的覆盖区间之内（前有、后有）→ {@link #ACTIVE}（正在这一步里）；</li>
 *   <li>stage_codes 为空或一个都认不出 → {@code PENDING}（判不了就不编造）。</li>
 * </ol>
 *
 * <p><b>一个例外</b>：当前阶段是 {@link DpVisualStageEnum#COMPLETED}（V1.0 已交付）时，
 * 所有步骤一律 {@link #DONE}。不这么做的话，覆盖 {@code COMPLETED} 的「终审交付」那一步会因为
 * "当前阶段在覆盖范围内"被判成进行中，已交付的项目看起来还差最后一步没做完——
 * 交付完成是整条链路的收束，这一点不该交给配置去表达。</p>
 */
public final class CreativeStepProjection {

    /** 已完成 */
    public static final String DONE = "DONE";

    /** 进行中 */
    public static final String ACTIVE = "ACTIVE";

    /** 待办 */
    public static final String PENDING = "PENDING";

    private CreativeStepProjection() {
    }

    /**
     * 配置侧的步骤定义。
     *
     * @param stepCode   步骤编码
     * @param stepName   步骤名称
     * @param stageCodes 覆盖的阶段（逗号分隔）
     * @param sortNo     顺序
     */
    public record ConfiguredStep(String stepCode, String stepName, String stageCodes, Integer sortNo) {
    }

    /**
     * 投影结果。
     *
     * @param stepCode 步骤编码
     * @param stepName 步骤名称
     * @param sortNo   顺序
     * @param status   状态（DONE/ACTIVE/PENDING）
     */
    public record StepState(String stepCode, String stepName, Integer sortNo, String status) {
    }

    /**
     * 按当前阶段推导每一步的状态。
     *
     * @param steps        配置步骤（应为已按 sortNo 排好的顺序）
     * @param currentStage 当前阶段编码（可空；空视为资料就绪）
     * @return 与入参同序的状态列表；steps 为空返回空列表
     */
    public static List<StepState> project(List<ConfiguredStep> steps, String currentStage) {
        List<StepState> out = new ArrayList<>();
        if (steps == null || steps.isEmpty()) {
            return out;
        }
        DpVisualStageEnum current = DpVisualStageEnum.find(
            StringUtils.blankToDefault(currentStage, DpVisualStageEnum.MATERIAL_READY.getCode()));
        if (current == null) {
            current = DpVisualStageEnum.MATERIAL_READY;
        }
        int currentOrd = current.ordinal();
        for (ConfiguredStep step : steps) {
            // 已交付：整条流程收束，没有"还差最后一步"的悬念
            String status = current == DpVisualStageEnum.COMPLETED
                ? DONE : statusOf(step.stageCodes(), currentOrd, current);
            out.add(new StepState(step.stepCode(), step.stepName(), step.sortNo(), status));
        }
        return out;
    }

    /**
     * 单步状态判定。
     *
     * @param stageCodes 该步骤覆盖的阶段
     * @param currentOrd 当前阶段的声明序号
     * @param current    当前阶段
     * @return 状态
     */
    private static String statusOf(String stageCodes, int currentOrd, DpVisualStageEnum current) {
        if (StringUtils.isBlank(stageCodes)) {
            return PENDING;
        }
        boolean coversCurrent = false;
        Integer min = null;
        Integer max = null;
        for (String raw : Arrays.asList(stageCodes.split(","))) {
            DpVisualStageEnum stage = DpVisualStageEnum.find(StringUtils.trim(raw));
            if (stage == null) {
                continue;
            }
            if (stage == current) {
                coversCurrent = true;
            }
            int ord = stage.ordinal();
            min = (min == null || ord < min) ? ord : min;
            max = (max == null || ord > max) ? ord : max;
        }
        if (min == null) {
            return PENDING;
        }
        if (coversCurrent) {
            return ACTIVE;
        }
        if (max < currentOrd) {
            return DONE;
        }
        if (min > currentOrd) {
            return PENDING;
        }
        // 当前阶段落在这一步的覆盖区间内部（例如步骤覆盖 DNA_*→DIRECTION_*，而当前是中间某个阶段）
        return ACTIVE;
    }
}
