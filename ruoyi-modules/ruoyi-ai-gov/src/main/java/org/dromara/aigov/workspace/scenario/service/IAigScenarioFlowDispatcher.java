package org.dromara.aigov.workspace.scenario.service;

import org.dromara.scenario.api.domain.AigScenarioFlowRequest;
import org.dromara.scenario.api.domain.AigScenarioFlowResult;

/**
 * 场景任务跨模块派发器（增量 12）。
 *
 * <p>按适配器编码找到业务域的实现（{@code AigScenarioFlowPort}）并把任务递过去。
 * <b>找不到就拒绝</b>：一个需要真流程的场景任务，如果没有能处理它的实现，
 * 唯一诚实的做法是明确失败，而不是退化成一次普通模型调用——后者会让"跑过了"变成假事实。</p>
 *
 * @author ai-gov
 */
public interface IAigScenarioFlowDispatcher {

    /**
     * 该适配器此刻有没有实现。
     *
     * @param adapterCode 适配器编码
     * @return 有实现返回 true
     */
    boolean canDispatch(String adapterCode);

    /**
     * 派发一次（找不到实现/适配器不可识别 → 抛错，不静默）。
     *
     * @param request 派发请求
     * @return 受理结果（业务拒绝以 {@code accepted=false} 返回，不抛错）
     * @throws org.dromara.common.core.exception.ServiceException 入参非法、适配器不可识别、无实现、实现未返回结果
     */
    AigScenarioFlowResult dispatch(AigScenarioFlowRequest request);

}
