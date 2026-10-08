package org.dromara.aigov.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 调度子系统的启用点（执行顺序 1→2→3 的<b>第 3 步</b>）。
 *
 * <p><b>为什么需要单独一个类</b>：{@code @Scheduled} 只是"声明"，真正让它生效的是
 * {@code ScheduledAnnotationBeanPostProcessor}，而它只由 {@code @EnableScheduling} 注册。
 * 本仓此前该注解<b>只</b>出现在 {@code ruoyi-common-job} 的 {@code SnailJobConfig} 上，
 * 且被 {@code snail-job.enabled} 门控（生产为 false，且没有部署 SnailJob server）。
 * 结果是三个 {@code @Scheduled} 任务在生产<b>一个都不会跑，且不会有任何报错</b>——
 * 这正是 R65 记下的坑，也是本次要关掉的那个"静默失效"。</p>
 *
 * <p><b>为什么不放在启动类上</b>：放在 {@code DromaraApplication} 上会变成"永远开启、
 * 无法关闭"，而本仓已有若干从未在生产跑过的写操作定时任务（任务扫描、审批超时扫描）。
 * 用 {@code aigov.scheduling.enabled} 门控，至少让"是否启用调度"是一个显式的、
 * 可以 grep 到的配置决定，而不是一个隐式事实。</p>
 *
 * <p><b>⚠️ 生效范围是全局的，不是"只对 ai-gov 生效"</b>：{@code @EnableScheduling} 注册的
 * 后处理器作用于整个容器。本仓当前只有三个 {@code @Scheduled}（全在 {@code ruoyi-ai-gov}），
 * 其中两个各自被 {@code aigov.task.scheduler.enabled} / {@code aigov.approval.expire-scan-enabled}
 * 门控且默认关闭，所以打开本开关<b>实际只会激活「模型健康探测」一个任务</b>。
 * 但将来任何人新增 {@code @Scheduled}，都会随本开关一起上线——这是本类必须写清的前提。</p>
 *
 * <p><b>关于线程池（实测修正）</b>：{@code @Scheduled} 在本仓跑在
 * {@code ruoyi-common-core} 的 {@code ThreadPoolConfig} 所定义的那个全局
 * {@code ScheduledExecutorService} 上（核心线程数 = {@code availableProcessors() + 1}，
 * 线程名 {@code schedule-pool-%d}）——Spring 在没有 {@code TaskScheduler} bean 时会采用
 * 该执行器，**不是** Spring Boot 默认的单线程调度器。生产实测第一轮探测的日志线程名即
 * {@code schedule-pool-1}，主机 8 核 ⇒ 该池 9 个线程。
 * 因此单轮约 133 秒的探测只占用其中 1 个线程，另两个任务不会因此排不上队；
 * 也**不要**去调 {@code spring.task.scheduling.pool.size}（它配的是 Boot 自动配置的
 * {@code ThreadPoolTaskScheduler}，在本仓不参与调度）。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "aigov.scheduling", name = "enabled", havingValue = "true")
public class AigSchedulingConfig {

    /**
     * 构造时留一行日志：调度是否真的启用，必须能从日志里一眼确认，
     * 而不是靠"任务没报错所以大概在跑"去猜。
     */
    public AigSchedulingConfig() {
        log.info("已启用调度子系统（aigov.scheduling.enabled=true）：@Scheduled 任务开始生效");
    }

}
