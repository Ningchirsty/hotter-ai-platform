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
 *         <b>前提是容器启用了 {@code @EnableScheduling}</b>——本仓的启用点在
 *         {@code ruoyi-common-job} 的 {@code SnailJobConfig}，而 {@code ruoyi-ai-gov}
 *         并不依赖它。也就是说：若把本模块单独跑（不带 job 模块），本类<b>不会</b>被触发，
 *         而且不会有任何报错——只是「什么都没发生」。这是刻意写在这里的提示，
 *         因为「定时任务静默不执行」是很难从现象反推的。</li>
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
