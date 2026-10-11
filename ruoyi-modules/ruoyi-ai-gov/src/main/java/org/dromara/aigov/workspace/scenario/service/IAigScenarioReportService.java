package org.dromara.aigov.workspace.scenario.service;

import org.dromara.aigov.workspace.scenario.domain.bo.AigScenarioReportBo;

/**
 * 场景任务回执服务（业务域 → 平台；增量 16）。
 *
 * <h3>它补的是哪条断链</h3>
 * <p>派发受理后平台任务**保持在 RUNNING**（交接完成 ≠ 域内作业完成）。域内的长跑作业
 * （内容侧：解析 → 闸门）结束后，由域调用本服务收尾平台任务。没有它，平台任务只会等超时
 * 被清扫成 TIMEOUT——那是"没收到回执"的错误说法。</p>
 *
 * <h3>为什么是进程内调用而不是 HTTP 回调</h3>
 * <p>各域与 aigov 在同一个应用里（同一次部署），直接注入服务即可，不必为此新开一个
 * 需要鉴权的公网/内网端点，也不依赖那个仍默认关闭的服务令牌开关。</p>
 *
 * <h3>幂等</h3>
 * <p>终态任务的重复回执是**无操作**（返回 false），不报错：回执可能重发、可能晚到。</p>
 *
 * @author ai-gov
 */
public interface IAigScenarioReportService {

    /**
     * 上报一次场景任务结论。
     *
     * @param bo 回执（平台任务ID、结论、可读说明）
     * @return 是否改变了平台任务状态（幂等无操作时 false）
     * @throws org.dromara.common.core.exception.ServiceException 入参非法或平台任务不存在
     */
    boolean report(AigScenarioReportBo bo);

}
