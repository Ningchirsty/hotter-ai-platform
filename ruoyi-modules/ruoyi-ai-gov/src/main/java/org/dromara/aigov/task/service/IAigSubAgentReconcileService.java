package org.dromara.aigov.task.service;

/**
 * 子 Agent 对账作业（设计 §6.5、§7.6）。
 *
 * <p>把"仍在执行中的子 Agent 步骤"按子任务终态收尾。做成独立服务而不是把逻辑写进定时器：
 * 触发方式（内置定时 / SnailJob / cron / 手工接口）是部署选项，判据与限流只应有一份实现。</p>
 *
 * @author ai-gov
 */
public interface IAigSubAgentReconcileService {

    /**
     * 扫一轮并收尾。
     *
     * @return 本轮收尾的父步骤数
     */
    int sweep();

}
