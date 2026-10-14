package org.dromara.aigov.task.service;

import org.dromara.aigov.execution.AigExecutionPlan;
import org.dromara.aigov.task.domain.AigTaskStep;

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

}
