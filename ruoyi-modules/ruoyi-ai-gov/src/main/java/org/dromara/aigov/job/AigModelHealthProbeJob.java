package org.dromara.aigov.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.domain.vo.AigModelHealthProbeVo;
import org.dromara.aigov.service.IAigModelHealthProbeService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 模型健康探测定时触发器（M-003，可选）。
 *
 * <p><b>两条触发路径，部署方按环境选一条</b>（与 {@code AigTaskSchedulerJob} 同一约定）：</p>
 * <ol>
 *     <li><b>单实例 / 简单部署</b>：置 {@code aigov.model.health-probe.enabled=true}，
 *         由本类按 {@code interval-ms} 周期探测。
 *         <b>前提是容器启用了 {@code @EnableScheduling}</b>——本仓的启用点在
 *         {@code ruoyi-common-job} 的 {@code SnailJobConfig}，而 {@code ruoyi-ai-gov}
 *         并不依赖它。也就是说：若本模块单独跑（不带 job 模块），本类<b>不会</b>被触发，
 *         <b>而且不会有任何报错——只是「什么都没发生」</b>。
 *         这条提示是刻意抄在这里的：既有的两个定时任务都栽在同一个坑上，
 *         而"定时任务静默不执行"极难从现象反推。</li>
 *     <li><b>集群 / 已有调度平台</b>：保持 {@code enabled=false}，由运维 cron 调
 *         {@code probeOnce()}（或经 SnailJob 调同义入口）。多实例同时跑也不会互相破坏——
 *         探测是幂等的读+写健康字段，最坏情况是同一模型被多测一次（多花一次外呼）。</li>
 * </ol>
 *
 * @author ai-gov
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "aigov.model.health-probe", name = "enabled", havingValue = "true")
public class AigModelHealthProbeJob {

    private final IAigModelHealthProbeService healthProbeService;

    /**
     * 周期性探测模型健康并落回治理表。
     *
     * <p>用 {@code fixedDelay} 而不是 {@code fixedRate}：探测是**对外调用**，
     * 耗时受供应商影响；fixedRate 会在上一轮没跑完时叠加触发，把外呼量放大。</p>
     */
    @Scheduled(fixedDelayString = "${aigov.model.health-probe.interval-ms:1800000}")
    public void probe() {
        try {
            AigModelHealthProbeVo result = healthProbeService.probeOnce();
            if (!result.isExecuted()) {
                return;
            }
            if (result.getUnhealthy() > 0 || result.getSkipped() > 0) {
                // 不健康要显式点名：否则运维只知道"有几个坏了"，还得自己去翻是哪几个
                log.warn("模型健康探测：测 {} 个，健康 {}，不健康 {} {}，跳过 {}，本轮未处理 {}",
                    result.getProbed(), result.getHealthy(), result.getUnhealthy(),
                    result.getUnhealthyModels(), result.getSkipped(), result.getDeferred());
            } else {
                log.info("模型健康探测：测 {} 个，全部健康，本轮未处理 {}",
                    result.getProbed(), result.getDeferred());
            }
        } catch (Exception e) {
            // 定时任务抛异常会被容器吞掉、只留一行日志，且可能让下一次触发不再调度；
            // 显式接住，保证「一轮失败不影响下一轮」
            log.error("模型健康探测定时任务异常（本轮跳过，下一轮继续）", e);
        }
    }

}
