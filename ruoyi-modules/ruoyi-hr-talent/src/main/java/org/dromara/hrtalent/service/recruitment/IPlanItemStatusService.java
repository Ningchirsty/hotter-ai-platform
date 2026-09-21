package org.dromara.hrtalent.service.recruitment;

/**
 * 月度计划任务状态重算服务（SPEC-P2 §3.2 / §4.2）。
 *
 * <p>负责把 {@code PlanItemStatusDomainService} 的纯计算结果持久化，并维护计划表头的汇总人数。
 * 三个状态维度中<b>只有</b> {@code completion_status} 与人数类字段由本服务自动写入；
 * {@code control_status} / {@code control_reason} 只能由人工动作改写。</p>
 *
 * @author hr-talent
 */
public interface IPlanItemStatusService {

    /**
     * 重算单条计划任务：剩余人数、完成度、最后刷新时间。
     *
     * @param itemId 计划任务ID
     */
    void refreshItemStatus(Long itemId);

    /**
     * 重算某个计划表头下全部任务的派生字段。
     *
     * @param planId 计划表头ID
     */
    void refreshItemsOfPlan(Long planId);

    /**
     * 重算计划表头的汇总人数（计划总人数 / 累计到岗 / 累计剩余）。
     *
     * @param planId 计划表头ID
     */
    void refreshPlanTotals(Long planId);

}
