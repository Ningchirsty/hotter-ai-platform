package org.dromara.aigov.task.service;

import org.dromara.aigov.execution.AigExecutionPlan;

import java.math.BigDecimal;
import java.util.List;

/**
 * 子 Agent 运行时驱动（设计 §6.5）。
 *
 * <p>两步：{@link #dispatchChildren} 按派发规格创建<b>子任务</b>并把对应步骤置为执行中
 * （记录子任务引用）；{@link #reconcile} 依据<b>子任务的终态</b>回写父步骤的状态。</p>
 *
 * <p><b>为什么是"创建 + 对账"而不是同步等待子任务跑完</b>：父任务与子任务都是平台任务，
 * 同步等待会把父任务挂在一次长网络调用上（不可恢复、无法重试）。创建子任务后由平台调度执行，
 * 父步骤通过"对账"收尾——这与设计 §6.5「由父任务记录子任务、输入摘要与结果引用」一致，
 * 也与 §7.6「结果未知时先查凭证再决定」同一纪律。</p>
 *
 * @author ai-gov
 */
public interface IAigSubAgentRunner {

    /**
     * 按执行计划创建子任务，并把对应父步骤置为执行中（记录引用 {@code task:{childId}}）。
     *
     * @param parentTaskId 父任务ID
     * @param taskType     任务类型（继承父任务）
     * @param projectType  业务域（继承父任务）
     * @param dataLevel    数据等级（继承父任务，不降低）
     * @param snapshotJson 输入快照（继承父任务）
     * @param budget       预算（可空）
     * @param plan         执行计划（可空=无子 Agent）
     * @return 创建出的子任务ID（按计划顺序）
     */
    List<Long> dispatchChildren(Long parentTaskId, String taskType, String projectType, String dataLevel,
                                String snapshotJson, BigDecimal budget, AigExecutionPlan plan);

    /**
     * 对账：把"仍在执行中且引用了子任务"的父步骤，按子任务终态收尾。
     *
     * @param parentTaskId 父任务ID
     * @return 本次收尾的步骤数
     */
    int reconcile(Long parentTaskId);

}
