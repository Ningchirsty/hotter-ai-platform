package org.dromara.aigov.task.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 子 Agent 对账配置（{@code aigov.subagent.reconcile.*}）。
 *
 * <p><b>默认关闭是刻意的</b>：与任务调度器同一理由——对账会周期性扫 {@code aig_task_step}
 * 并按子任务终态推进父步骤；在一个还没跑过建表脚本的库里打开它只会持续刷"表不存在"。
 * 部署方显式打开，或改用 SnailJob/cron 调用同一入口。</p>
 *
 * @author ai-gov
 */
@Data
@Component
@ConfigurationProperties(prefix = "aigov.subagent.reconcile")
public class AigSubAgentReconcileProperties {

    /**
     * 是否启用内置定时对账（需要容器启用了 {@code @EnableScheduling}）
     */
    private boolean enabled = false;

    /**
     * 对账间隔（毫秒）
     */
    private long intervalMs = 30000L;

    /**
     * 单轮最多取多少条"执行中的子 Agent 步骤"（避免一次扫太多把库压住）
     */
    private int batchSize = 200;

}
