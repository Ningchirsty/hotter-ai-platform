package org.dromara.aigov.task.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.task.service.IAigSubAgentReconcileService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 子 Agent 对账定时触发器（可选，设计 §6.5）。
 *
 * <p><b>两条触发路径，部署方按环境选一条</b>：</p>
 * <ol>
 *     <li><b>单实例 / 简单部署</b>：置 {@code aigov.subagent.reconcile.enabled=true}，
 *         按 {@code interval-ms} 周期对账（前提是容器启用了 {@code @EnableScheduling}，
 *         本模块的启用点是 {@code AigSchedulingConfig}）；</li>
 *     <li><b>集群 / 已有调度平台</b>：保持 {@code enabled=false}，改由 SnailJob 或运维 cron
 *         调用同一服务入口。多实例同时触发也不会重复收尾——步骤迁移走 CAS，抢输的一方报冲突并被接住。</li>
 * </ol>
 *
 * <p>用 {@code fixedDelay} 而不是 {@code fixedRate}：本轮跑完到下一轮开始之间留间隔，
 * 避免上一轮没跑完就被叠加触发。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "aigov.subagent.reconcile", name = "enabled", havingValue = "true")
public class AigSubAgentReconcileJob {

    private final IAigSubAgentReconcileService reconcileService;

    /**
     * 周期性对账并收尾子 Agent 父步骤。
     */
    @Scheduled(fixedDelayString = "${aigov.subagent.reconcile.interval-ms:30000}")
    public void sweep() {
        try {
            reconcileService.sweep();
        } catch (Exception e) {
            // 定时任务抛异常会被容器吞掉且可能让下一次触发不再调度；显式接住，保证「一轮失败不影响下一轮」
            log.error("子 Agent 对账发生异常（本轮跳过，下一轮继续）", e);
        }
    }

}
