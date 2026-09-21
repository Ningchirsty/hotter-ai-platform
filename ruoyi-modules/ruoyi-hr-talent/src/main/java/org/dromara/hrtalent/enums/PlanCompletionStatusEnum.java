package org.dromara.hrtalent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 月度计划任务完成与结转状态枚举。
 * <p>对应数据字典 {@code recruit_plan_completion_status}（设计文档 §10）。
 * 结转只新增目标任务并保留链路，禁止通过修改原任务月份实现。</p>
 *
 * @author hr-talent
 */
@Getter
@AllArgsConstructor
public enum PlanCompletionStatusEnum {

    /**
     * 未完成
     */
    UNFINISHED("unfinished", "未完成"),
    /**
     * 部分完成
     */
    PARTIAL_COMPLETED("partial_completed", "部分完成"),
    /**
     * 已完成
     */
    COMPLETED("completed", "已完成"),
    /**
     * 已结转
     */
    ROLLED_OVER("rolled_over", "已结转");

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
    public static PlanCompletionStatusEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (PlanCompletionStatusEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
