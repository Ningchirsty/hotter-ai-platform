package org.dromara.aigov.task.service;

import org.dromara.aigov.task.domain.bo.AigTaskExecuteBo;
import org.dromara.aigov.task.domain.vo.AigTaskExecuteVo;

/**
 * 任务执行器：把「任务」与「统一调用入口」串成一条链。
 *
 * <p><b>它补的是哪条断链</b>：在它之前，{@code aig_task} 与 {@code IAigInvokeService} 是两套
 * 各自独立的东西——任务层能建任务、能收回调、能推进状态，但<b>没有任何东西用它去真正调用模型</b>；
 * 调用层能路由、能调用、能写审计，但不知道「这是一次任务的第几次尝试」。
 * 两者不串起来，任务的 {@code route_snapshot} 永远是空的，
 * 而审计里的每次调用也归不到哪个任务上。</p>
 *
 * <p><b>分层不变</b>：本执行器只做「编排」——它<b>不</b>自己路由、<b>不</b>自己发 HTTP。
 * 路由与调用一律交给 {@code IAigInvokeService}（它内部完成 7 步路由、有序 fallback、
 * 退避重试、逐次审计）。这样「谁能外发」「谁能用哪个模型」这些判定只有一个地方做，
 * 不会出现「任务层的判断与调用层的判断不一致」。</p>
 *
 * <p><b>执行前的快照校验</b>：重算 {@code snapshot_hash} 与冻结值比对，不一致直接拒绝执行。
 * 快照是「结果可复现」的唯一依据，被改写过的快照执行出来的结果无法归因——
 * 宁可停下来说清楚，也不要产出一个事后解释不了的产物。</p>
 *
 * @author ai-gov
 */
public interface IAigTaskExecutor {

    /**
     * 执行一次任务：校验快照 → QUEUED→RUNNING → 走统一调用入口 → 记录执行事实 → 落成功/失败。
     *
     * @param bo 执行入参（提示词与载荷由业务域给出）
     * @return 执行结果（含任务结局与调用细节）
     */
    AigTaskExecuteVo execute(AigTaskExecuteBo bo);

}
