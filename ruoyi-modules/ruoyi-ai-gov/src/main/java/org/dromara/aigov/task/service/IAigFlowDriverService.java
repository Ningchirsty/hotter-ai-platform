package org.dromara.aigov.task.service;

import org.dromara.aigov.execution.AigExecutionPlan;
import org.dromara.aigov.task.domain.AigTaskStep;

import java.util.Map;

/**
 * 流程节点运行时驱动（设计 §6.5「条件判断、人工确认」）。
 *
 * <p>它把 {@link AigFlowRuntime} 的判定落到任务步骤账本上：启动节点、把人工确认节点
 * 挂起、条件不满足时跳过其后节点、收尾输出节点。<b>不执行业务动作</b>——
 * {@code STEP} 节点的工作由调用方（执行器 / 业务域）跑完后收尾，本服务只负责
 * "该谁上、能不能上"。</p>
 *
 * <p>人工确认的"恢复"入口是 {@link #confirm}：批准被记在步骤的 {@code approval_ref} 上，
 * 下一次 {@link #advance} 就会越过它继续。因此确认与推进之间没有额外的内存状态——
 * 进程重启也不丢"已经批过"这件事。</p>
 *
 * @author ai-gov
 */
public interface IAigFlowDriverService {

    /**
     * 推进一个任务的流程节点。
     *
     * <p>每次调用<b>最多</b>处理一个节点（可在同一节点上重复调用，判据幂等）。调用方
     * 按返回值决定：继续调用（控制类节点已就地收尾）、去执行（{@code STEP} 节点）、
     * 等待（人工确认/条件结论）或停止。</p>
     *
     * @param taskId           任务ID
     * @param plan             执行计划（含流程节点；无节点时直接返回已完成）
     * @param conditionResults 条件结论（键为节点 ref；可空=还没算出来）
     * @return 推进结论
     */
    AigFlowDriveResult advance(Long taskId, AigExecutionPlan plan, Map<String, Boolean> conditionResults);

    /**
     * 记录一次人工确认（挂起后的恢复入口）。
     *
     * <p>先置执行中再收尾成功并写入 {@code approval_ref}：步骤状态机里
     * {@code PLANNED → SUCCEEDED} 不是合法边，且"这一步确实被挂起过、然后被批准了"
     * 比"从来没挂起"更接近事实。</p>
     *
     * <p><b>为什么不需要执行计划</b>：节点身份（类型 + 引用）已随步骤落库
     * （{@code aig_task_step.node_type/node_ref}）。确认接口只要能读到账本，
     * 就能回答"你批的是不是人工确认节点"——不需要重新解析版本配置（那还可能
     * 解出一份与当时不同的计划，把批准落到另一个节点上）。
     * 存量步骤没有登记身份时<b>拒绝盲批</b>并说明原因。</p>
     *
     * @param taskId      任务ID
     * @param nodeIndex   待确认的节点下标（0 基，与流程节点顺序一致）
     * @param approvalRef 批准引用（必填：没有凭据的"确认"不可追溯）
     * @return 收尾后的步骤
     */
    AigTaskStep confirm(Long taskId, int nodeIndex, String approvalRef);

    /**
     * 收尾一个被驱动启动的步骤节点（成功/失败）。
     *
     * @param stepId       步骤ID
     * @param succeeded    是否成功
     * @param outputRef    结果引用（可空）
     * @param errorCode    失败分类（可空）
     * @param errorMessage 失败原因（可空）
     * @return 收尾后的步骤
     */
    AigTaskStep settle(Long stepId, boolean succeeded, String outputRef, String errorCode,
                       String errorMessage);

}
