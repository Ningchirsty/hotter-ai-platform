package org.dromara.aigov.task.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.task.domain.vo.AigTaskSweepVo;
import org.dromara.aigov.task.service.IAigTaskScheduler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 任务调度定时触发器（可选）。
 *
 * <p><b>两条触发路径，部署方按环境选一条</b>：</p>
 * <ol>
 *     <li><b>单实例 / 简单部署</b>：置 {@code aigov.task.scheduler.enabled=true}，
 *         由本类按 {@code aigov.task.scheduler.interval-ms} 周期调用。
 *         <b>前提是容器启用了 {@code @EnableScheduling}</b>——本模块自带的启用点是
 *         {@code AigSchedulingConfig}（{@code aigov.scheduling.enabled}）。
 *         <b>生产已于 2026-10-09 采用本路径</b>：两个开关都在 {@code application-prod.yml}
 *         里显式为 true（此前本类从未在生产跑过——不是坏了，而是没有任何触发器）。
 *         启用当天用生产夹具做过对照验证：PLATFORM 的陈旧 RETRY_WAIT 会被推进，
 *         EXTERNAL 的原样不动。</li>
 *     <li><b>集群 / 已有调度平台</b>：保持 {@code enabled=false}，改由 SnailJob
 *         （本仓既有的调度机制）或运维 cron 调用
 *         {@code POST /aigov/task/scheduler/sweep}。多实例同时触发也不会重复推进——
 *         推进走乐观锁，抢输的一方计入 skipped。</li>
 * </ol>
 *
 * @author ai-gov
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "aigov.task.scheduler", name = "enabled", havingValue = "true")
public class AigTaskSchedulerJob {

    private final IAigTaskScheduler scheduler;

    /**
     * 周期性扫描并推进任务。
     *
     * <p>用 {@code fixedDelay} 而不是 {@code fixedRate}：本轮扫完到下一轮开始之间留间隔，
     * 避免上一轮还没跑完就被叠加触发（任务量大的时候会滚成一堆并发扫描）。</p>
     */
    @Scheduled(fixedDelayString = "${aigov.task.scheduler.interval-ms:60000}")
    public void sweep() {
        try {
            AigTaskSweepVo result = scheduler.sweep();
            if (result.getRetried() > 0 || result.getTimedOut() > 0) {
                log.info("定时扫描推进任务：重试 {} 条，超时 {} 条，跳过 {} 条",
                    result.getRetried(), result.getTimedOut(), result.getSkipped());
            }
        } catch (Exception e) {
            // 定时任务抛异常会被容器吞掉、只留一行日志，且可能让下一次触发不再调度；
            // 这里显式接住，保证「一轮失败不影响下一轮」
            log.error("定时扫描任务时发生异常（本轮跳过，下一轮继续）", e);
        }
    }

}
