package org.dromara.hrtalent.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 月度计划任务自动执行阶段枚举。
 * <p>对应数据字典 {@code recruit_plan_execution_status}（设计文档 §10）。
 * 由候选人流程驱动的自动状态，前端不得直接写入。</p>
 *
 * @author hr-talent
 */
@Getter
@AllArgsConstructor
public enum PlanExecutionStatusEnum {

    /**
     * 待启动
     */
    PENDING("pending", "待启动"),
    /**
     * 招聘中
     */
    RECRUITING("recruiting", "招聘中"),
    /**
     * 面试中
     */
    INTERVIEWING("interviewing", "面试中"),
    /**
     * 已发录用通知
     */
    OFFER("offer", "已发Offer"),
    /**
     * 待到岗
     */
    PENDING_ARRIVAL("pending_arrival", "待到岗");

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
    public static PlanExecutionStatusEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (PlanExecutionStatusEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

}
