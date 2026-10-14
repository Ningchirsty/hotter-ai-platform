package org.dromara.aigov.task.service;

import org.dromara.aigov.execution.AigExecutionPlan;
import org.dromara.aigov.task.domain.AigTaskStep;
import org.dromara.aigov.task.enums.AigTaskStepStatusEnum;

import java.util.List;

/**
 * 任务步骤账本服务（设计 §8.2、§6.5）。
 *
 * <p>它把"这次编排有哪些步骤"落成可追溯的记录：子 Agent 顺序调用与顺序流程节点各占一步。
 * 步骤<b>状态独立于任务状态</b>——任务 SUCCEEDED 而某一步 FAILED 是可能的（部分成功）。</p>
 *
 * @author ai-gov
 */
public interface IAigTaskStepService {

    /**
     * 按执行计划登记"已编排"的步骤（幂等：同一 task+sequence 已有记录则跳过）。
     *
     * @param taskId 任务ID
     * @param plan   执行计划（可空）
     * @return 本次新建的步骤数
     */
    int recordPlannedSteps(Long taskId, AigExecutionPlan plan);

    /**
     * 列出某任务的步骤（按序号升序）。
     *
     * @param taskId 任务ID
     * @return 步骤清单
     */
    List<AigTaskStep> listSteps(Long taskId);

    /**
     * 列出某任务的<b>流程节点</b>步骤（只取 {@code kind=FLOW_NODE}，按序号升序，只取首次尝试）。
     *
     * <p>它是"第 k 个流程节点 = 第 k 个流程节点步骤"这条对应关系的**唯一**出口：
     * 驱动按它对齐节点与状态，人工确认按它定位待批节点，界面按它展示"停在哪一步"。
     * 三处各写一遍过滤条件，迟早会有一处忘了排除重试 attempt 或子 Agent 步骤。</p>
     *
     * @param taskId 任务ID
     * @return 流程节点步骤（无则空列表）
     */
    List<AigTaskStep> listFlowNodes(Long taskId);

    /**
     * 按 (任务, 序号) 取步骤（首次尝试）。
     *
     * @param taskId   任务ID
     * @param sequence 序号
     * @return 步骤；不存在返回 null
     */
    AigTaskStep findBySequence(Long taskId, int sequence);

    /**
     * 把步骤置为执行中（{@code PLANNED → RUNNING}，CAS）。
     *
     * <p>已经是 {@code RUNNING} 时幂等返回（重试不报错）。</p>
     *
     * @param stepId 步骤ID
     * @return 迁移后的步骤
     */
    AigTaskStep startStep(Long stepId);

    /**
     * 把步骤置为执行中，并记录引用（如子任务 {@code task:{id}}）。
     *
     * @param stepId    步骤ID
     * @param outputRef 引用（可空）
     * @return 迁移后的步骤
     */
    AigTaskStep startStep(Long stepId, String outputRef);

    /**
     * 收尾一个步骤（{@code RUNNING → SUCCEEDED/FAILED/SKIPPED}，CAS）。
     *
     * <p>已是终态时幂等返回（不覆盖"它曾经失败过"的事实）。</p>
     *
     * @param stepId        步骤ID
     * @param status        目标状态
     * @param outputRef     结果引用（可空）
     * @param approvalRef   批准引用（可空）
     * @param errorCode     失败分类（可空）
     * @param errorMessage  失败原因（可空）
     * @return 迁移后的步骤
     */
    AigTaskStep finishStep(Long stepId, AigTaskStepStatusEnum status, String outputRef,
                           String approvalRef, String errorCode, String errorMessage);

}
