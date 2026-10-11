package org.dromara.creative.service;

import org.dromara.creative.domain.vo.CreativeLedgerReconciliationVo;

/**
 * 创作域候选侧的「父子对账」服务（增量 19）。
 *
 * <p>把一条创意项目下的<b>父任务</b>（场景派发出来的平台任务）与<b>子任务</b>
 * （每个候选登记的治理任务）并排读出来，并逐候选判断两套状态是否对得上。
 * 纯只读：不刷新内核状态、不回写任务。</p>
 *
 * @author creative
 */
public interface ICreativeLedgerReconciliationService {

    /**
     * 对账一个项目的父任务与候选子任务。
     *
     * @param projectId 项目ID（{@code cp_task.task_id}）
     * @return 对账视图
     * @throws org.dromara.common.core.exception.ServiceException 项目不存在
     */
    CreativeLedgerReconciliationVo reconcile(Long projectId);
}
