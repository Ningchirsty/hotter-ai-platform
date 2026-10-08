package org.dromara.aigov.service;

import org.dromara.aigov.domain.vo.AigModelHealthProbeVo;

/**
 * 模型健康探测（M-003）。
 *
 * <p>把"人记得点 {@code POST /aigov/model/{id}/test}"变成"到点自动探测一轮"。</p>
 *
 * @author ai-gov
 */
public interface IAigModelHealthProbeService {

    /**
     * 探测一轮：挑出需要复测的模型，逐个调连通性测试并把结果落回治理表。
     *
     * <p><b>不抛异常</b>：单个模型探测失败只计入 skipped 并继续后面的模型——
     * 一轮里某个供应商超时不代表其余模型不该测。</p>
     *
     * <p><b>⚠️ 这是同步阻塞调用：实测 8 个模型约 133 秒</b>（每个都要真的外呼）。
     * HTTP 入口<b>不要</b>直接调它——会被 Cloudflare（约 100 秒即 524）与 nginx 掐断，
     * 而后端仍会继续跑，调用方却以为失败。HTTP 入口与定时任务应走 {@link #triggerAsync()}。</p>
     *
     * @return 本轮结果汇总
     */
    AigModelHealthProbeVo probeOnce();

    /**
     * 提交一轮后台探测并<b>立即返回</b>，不等待探测完成。
     *
     * <p>为什么要"提交即返回"：一次完整探测实测 133 秒，而网关约 100 秒即超时；
     * 更糟的是<b>网关超时不会让后端停下来</b>——探测仍在跑，只是调用方拿不到结果，
     * 于是重试，把同一批模型反复外呼。所以入口只提交，不等待。</p>
     *
     * <p><b>为什么返回枚举而不是 boolean</b>："没提交"有四种彼此不同的原因：
     * 开关关闭、已有一轮在跑、执行器不可用，以及（将来可能增加的）其它前置条件。
     * 用 boolean 会把它们压成同一个"false"，让人只能回一句含糊的
     * "可能已在跑，也可能出错了"——而运维需要知道的是<b>到底是哪一种</b>。</p>
     *
     * <p><b>去重是必须的，且必须在这里做</b>：单线程执行器会把第二次提交<b>排队</b>
     * 而不是拒绝，若只依赖执行器，连续两次提交就会真的外呼两轮（等于双倍账单）。</p>
     *
     * @return 提交结果，见 {@link SubmitOutcome}
     */
    SubmitOutcome triggerAsync();

    /**
     * 一次"提交探测"的结局。
     *
     * @author ai-gov
     */
    enum SubmitOutcome {

        /**
         * 本次已提交，后台正在（或即将）探测
         */
        ACCEPTED,

        /**
         * 已有一轮在跑，本次<b>未</b>提交（去重，避免同一批模型被重复外呼）
         */
        ALREADY_RUNNING,

        /**
         * 开关关闭（{@code aigov.model.health-probe.enabled=false}），本次未提交
         */
        DISABLED,

        /**
         * 后台执行器不可用，本次未提交（属于异常情况，需要看日志）
         */
        REJECTED
    }

}
