package org.dromara.aigov.agent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 评测运行状态（{@code aig_evaluation_run.result_status}）。
 *
 * <p>刻意把 {@link #ERROR} 与 {@link #FAIL} 分开：</p>
 * <ul>
 *     <li>{@link #FAIL} = 用例跑完了，但结果不满足判据。这是<b>被测对象</b>的问题；</li>
 *     <li>{@link #ERROR} = 用例没能跑完（执行器缺失、输入快照取不到、执行抛异常）。
 *         这是<b>评测环境或用例本身</b>的问题。</li>
 * </ul>
 * <p>两者合并成一个「不通过」，会让人拿着一条环境故障去改 Prompt。而门槛判定对两者
 * <b>一视同仁地不放行</b>——分开是为了排查方向，不是为了放行。</p>
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigEvaluationStatusEnum {

    /**
     * 运行中（已插入账本，尚未得出结论）
     */
    RUNNING("RUNNING", "运行中"),

    /**
     * 通过
     */
    PASS("PASS", "通过"),

    /**
     * 不通过（跑完了，但不满足判据）
     */
    FAIL("FAIL", "不通过"),

    /**
     * 出错（没跑完：执行器缺失/输入取不到/执行异常）
     */
    ERROR("ERROR", "出错");

    /**
     * 编码（入库值）
     */
    private final String code;

    /**
     * 描述
     */
    private final String desc;

    /**
     * 是否已得出结论（非 RUNNING）。
     *
     * @return 已结束返回 true
     */
    public boolean finished() {
        return this != RUNNING;
    }

    /**
     * 按 code 查找，找不到返回 null。
     *
     * @param code 编码
     * @return 枚举；未命中返回 null
     */
    public static AigEvaluationStatusEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AigEvaluationStatusEnum item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return null;
    }

}
