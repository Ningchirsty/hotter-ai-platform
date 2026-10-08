package org.dromara.aigov.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 模型健康探测（M-003）配置。
 *
 * <p><b>背景</b>：2026-10-08 的只读巡检查出——生产 12 个治理行里，
 * 1 个从未测过（`qwen2.5:7b-instruct`）、1 个有状态无时间（`qwen2.5vl:3b`）、
 * `local-content` 已 14 天未复测；而**真正参与路由的 3 个模型里没有一个"近期测过且健康"**。
 * 根因不是探测能力缺失（`POST /aigov/model/{id}/test` 早就有），而是
 * <b>它只能靠人记得去点</b>。本配置把"记得点"变成"到点自动点"。</p>
 *
 * @author ai-gov
 */
@Data
@Component
@ConfigurationProperties(prefix = "aigov.model.health-probe")
public class AigModelHealthProbeProperties {

    /**
     * 是否启用周期性健康探测。
     *
     * <p><b>默认 false</b>，与 {@code aigov.task.scheduler} / {@code aigov.approval} 同一取舍：
     * 本类会**对外发起真实调用**（探测要连供应商），在没想清楚"探测哪些模型、多频繁、
     * 这笔探测成本谁认"之前，不该由一次上线替运维决定。</p>
     */
    private boolean enabled = false;

    /**
     * 探测周期（毫秒）。默认 30 分钟。
     *
     * <p>用 {@code fixedDelay} 语义（见 job 类），因此实际间隔 = 本轮耗时 + 该值。</p>
     */
    private long intervalMs = 1_800_000L;

    /**
     * 是否只探测"真正参与路由"的模型。
     *
     * <p><b>默认 true</b>：只为有启用绑定的模型花探测成本。没有绑定的模型即使不健康，
     * 也不会影响任何一次路由——为它们付费没有收益。</p>
     */
    private boolean onlyBound = true;

    /**
     * 单轮最多探测多少个模型（0 表示不限）。默认 20。
     *
     * <p>防"模型表变长后一轮探测把预算打光"：探测是**线性外呼成本**，
     * 必须有个上限，否则加模型就等于加钱。</p>
     */
    private int maxPerRound = 20;

    /**
     * 距上次探测超过该小时数的模型才重新探测（0 表示每轮都测）。
     *
     * <p>默认 6 小时：让"刚测过的不必再测"，把预算留给真正陈旧的模型。
     * 这也让"缩短周期"不会等比例放大成本。</p>
     */
    private int staleHours = 6;

}
