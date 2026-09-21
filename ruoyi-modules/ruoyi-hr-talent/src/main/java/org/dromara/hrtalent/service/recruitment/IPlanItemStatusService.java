package org.dromara.hrtalent.service.recruitment;

import org.dromara.hrtalent.domain.vo.recruitment.RecruitPlanItemStatusLogVo;

import java.util.List;

/**
 * 月度计划任务状态重算服务（SPEC-P2 §3.2 / §4.2 / SPEC-P3 §3.2）。
 *
 * <p>负责把 {@code PlanItemStatusDomainService} 的纯计算结果持久化，并维护计划表头的汇总人数。
 * 三个状态维度中，{@code completion_status}、人数类字段与自动推导的 {@code execution_status}
 * 由本服务自动写入；{@code control_status} / {@code control_reason} 只能由人工动作改写，
 * 自动刷新永不覆盖。</p>
 *
 * <p>按设计文档 §7.1.5 / §21.15，刷新时若状态实际发生变化，另追加一条状态变更日志
 * （{@code hr_recruit_plan_item_status_log}），记录触发事件、原状态、新状态与刷新时间。</p>
 *
 * @author hr-talent
 */
public interface IPlanItemStatusService {

    /**
     * 触发事件：应聘阶段变化（含候选人新增、流转、淘汰、放弃、转岗与移出计划任务）。
     */
    String TRIGGER_APPLICATION_STAGE_CHANGED = "application_stage_changed";

    /**
     * 触发事件：面试结果变化（面试安排、取消或结果提交）。
     */
    String TRIGGER_INTERVIEW_RESULT_CHANGED = "interview_result_changed";

    /**
     * 触发事件：候选人报到。
     */
    String TRIGGER_CANDIDATE_ARRIVED = "candidate_arrived";

    /**
     * 触发事件：背调结论变化。
     */
    String TRIGGER_BACKGROUND_RESULT_CHANGED = "background_result_changed";

    /**
     * 触发事件：人工/定时校准刷新（无法给出具体业务事件时的兜底编码）。
     */
    String TRIGGER_MANUAL_REFRESH = "manual_refresh";

    /**
     * 重算单条计划任务：剩余人数、完成度、最后刷新时间。
     *
     * @param itemId 计划任务ID
     */
    void refreshItemStatus(Long itemId);

    /**
     * 重算单条计划任务，并记录本次刷新的触发事件（设计文档 §7.1.5 / §21.15）。
     *
     * <p>当 {@code execution_status} 或 {@code completion_status} 实际发生变化时追加一条状态变更日志；
     * 状态未变化时不写日志。</p>
     *
     * @param itemId       计划任务ID
     * @param triggerEvent 触发事件稳定编码（如 {@code application_stage_changed} / {@code manual_refresh}）
     */
    void refreshItemStatus(Long itemId, String triggerEvent);

    /**
     * 查询单条计划任务的状态变更日志（按刷新时间升序，只读）。
     *
     * @param itemId 计划任务ID
     * @return 状态变更日志列表
     */
    List<RecruitPlanItemStatusLogVo> listStatusLogs(Long itemId);

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
