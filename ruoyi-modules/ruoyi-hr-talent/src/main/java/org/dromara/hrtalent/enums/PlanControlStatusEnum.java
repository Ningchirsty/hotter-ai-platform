package org.dromara.hrtalent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 月度计划任务人工控制状态枚举。
 * <p>对应数据字典 {@code recruit_plan_control_status}（设计文档 §10）。
 * 该维度只由人工操作驱动，自动刷新不得写入。</p>
 *
 * @author hr-talent
 */
@Getter
@AllArgsConstructor
public enum PlanControlStatusEnum {

    /**
     * 正常
     */
    NORMAL("normal", "正常"),
    /**
     * 已暂停
     */
    PAUSED("paused", "已暂停"),
    /**
     * 已取消
     */
    CANCELLED("cancelled", "已取消");

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
    public static PlanControlStatusEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (PlanControlStatusEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
