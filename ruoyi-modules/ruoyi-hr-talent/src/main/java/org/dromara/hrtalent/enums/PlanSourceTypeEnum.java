package org.dromara.hrtalent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 月度计划任务来源类型枚举。
 * <p>对应数据字典 {@code recruit_plan_source_type}（设计文档 §10）。
 * 两类来源分别统计，同公司同岗位的新增任务与结转任务<b>不合并</b>。</p>
 *
 * @author hr-talent
 */
@Getter
@AllArgsConstructor
public enum PlanSourceTypeEnum {

    /**
     * 当月新增
     */
    NEW("new", "新建"),
    /**
     * 上月自动结转
     */
    CARRYOVER("carryover", "上月结转");

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
    public static PlanSourceTypeEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (PlanSourceTypeEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
