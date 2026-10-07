package org.dromara.aigov.task.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 任务调度器配置。
 *
 * <p><b>默认关闭（{@code enabled=false}）</b>，这是刻意的：调度器会周期性扫库并推进任务状态、
 * 对超时任务记失败。在一个还没跑过 A2 建表脚本的库里打开它，只会持续刷「表不存在」的报错；
 * 在多实例部署里打开它，若没有乐观锁就会重复推进。因此让部署方显式打开，
 * 并同时给出「怎么触发」的两条路（见 {@code AigTaskSchedulerJob}）。</p>
 *
 * @author ai-gov
 */
@Data
@Component
@ConfigurationProperties(prefix = "aigov.task.scheduler")
public class AigTaskSchedulerProperties {

    /**
     * 是否启用内置定时扫描（需要容器启用了 {@code @EnableScheduling}）
     */
    private boolean enabled = false;

    /**
     * 扫描间隔（毫秒）
     */
    private long intervalMs = 60000L;

    /**
     * 单次扫描每类动作最多处理多少条（避免一次扫太多把库压住）
     */
    private int batchSize = 50;

    /**
     * {@code RETRY_WAIT} 停留多久后才重新入队（秒）。
     * <p>给退避留出时间：刚失败就立刻重排会把同一次故障连续重试完，
     * 而多数限流/不可用是分钟级的。</p>
     */
    private long retryDelaySeconds = 60L;

    /**
     * 在途任务（已派发/执行中）多久没动静就判超时（秒）
     */
    private long timeoutSeconds = 1800L;

}
