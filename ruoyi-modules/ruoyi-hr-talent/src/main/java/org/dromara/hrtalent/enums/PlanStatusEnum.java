package org.dromara.hrtalent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 公司月度计划表头状态枚举。
 * <p>对应数据字典 {@code recruit_plan_status}（设计文档 §10）。</p>
 *
 * @author hr-talent
 */
@Getter
@AllArgsConstructor
public enum PlanStatusEnum {

    /**
     * 草稿
     */
    DRAFT("draft", "草稿"),
    /**
     * 执行中
     */
    EXECUTING("executing", "执行中"),
    /**
     * 已关闭
     */
    CLOSED("closed", "已关闭");

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
    public static PlanStatusEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (PlanStatusEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
