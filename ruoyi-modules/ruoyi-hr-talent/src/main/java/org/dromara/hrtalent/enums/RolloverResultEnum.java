package org.dromara.hrtalent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 月度结转执行结果枚举。
 * <p>对应数据字典 {@code recruit_rollover_result}（设计文档 §10 无此组，按 §8 月度结转
 * 执行结果的稳定编码补齐）。</p>
 *
 * <p><b>与既有常量的关系（重要）</b>：{@code PlanRolloverDomainService} 中已定义四个等值
 * String 常量 {@code RESULT_PROCESSING / RESULT_SUCCESS / RESULT_FAILED / RESULT_SKIPPED}
 * （{@code processing/success/failed/skipped}），本枚举的编码与其<b>一一对应且取值完全一致</b>。
 * 本枚举仅作为字典标签与前端下拉展示的权威来源，<b>不替换</b>领域服务中的常量；
 * 因此在代码中<b>存在两处定义</b>，建议后续由主控统一（例如领域服务改用本枚举的
 * {@link #getCode()}），在此之前请勿单独变更任一侧取值。</p>
 *
 * <p><b>说明</b>：入库一律保存 {@link #getCode()} 稳定编码，中文名称仅用于服务端提示与页面兜底展示；
 * 编码值一旦投入使用不得随意变更。</p>
 *
 * @author hr-talent
 */
@Getter
@AllArgsConstructor
public enum RolloverResultEnum {

    /**
     * 处理中：占位记录，用于幂等闸门
     */
    PROCESSING("processing", "处理中"),
    /**
     * 成功：结转任务已正常完成
     */
    SUCCESS("success", "成功"),
    /**
     * 失败：已记录原因，可安全重试
     */
    FAILED("failed", "失败"),
    /**
     * 跳过：不落库，仅出现在执行明细中
     */
    SKIPPED("skipped", "跳过");

    /**
     * 编码（入库值，投入使用后不得随意变更）
     */
    private final String code;

    /**
     * 描述（中文名称，页面展示由字典转换）
     */
    private final String desc;

    /**
     * 按编码查找。
     *
     * @param code 编码
     * @return 匹配的枚举，未命中返回 null
     */
    public static RolloverResultEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (RolloverResultEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
