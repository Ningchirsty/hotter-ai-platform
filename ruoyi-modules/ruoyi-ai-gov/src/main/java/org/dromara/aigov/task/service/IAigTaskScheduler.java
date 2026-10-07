package org.dromara.aigov.task.service;

import org.dromara.aigov.task.domain.vo.AigTaskSweepVo;

/**
 * 任务调度：把「卡在中间态」的任务推进下去。
 *
 * <p><b>为什么需要它</b>：状态机定义了 {@code RETRY_WAIT → QUEUED} 这条边，但边本身不会走路。
 * 没有调度器时，一次可重试的失败会让任务永远停在 {@code RETRY_WAIT}——
 * 界面上就是「一直等重试」，而没有任何东西会去重试它。同理，
 * 一个已经派发出去但对方再也没回调的任务会永远停在 {@code RUNNING}，
 * 既不失败也不超时，占用预算却无人知晓。</p>
 *
 * <p><b>多实例安全</b>：扫描与推进之间天然存在竞态（两台机器同时扫到同一个任务）。
 * 本服务不靠加锁，而是让每次推进都走带乐观锁的
 * {@code IAigTaskService#transition}：抢输的那一台拿到 0 行更新，
 * 计入 {@code skipped} 而不是覆盖对方的结论。因此「重复扫描」是安全的，
 * 部署方不需要为它做单实例保证。</p>
 *
 * @author ai-gov
 */
public interface IAigTaskScheduler {

    /**
     * 执行一次扫描：驱动待重试任务重新入队 + 把超时的在途任务记为失败。
     *
     * <p>两件事都走状态机，因此不会绕过任何校验（例如已终态的任务不会被它碰到）。</p>
     *
     * @return 扫描结果（含各类计数与逐条失败原因）
     */
    AigTaskSweepVo sweep();

}
