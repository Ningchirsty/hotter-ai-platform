package org.dromara.aigov.workspace.scenario.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.workspace.helper.AigScenarioDispatch;
import org.dromara.aigov.workspace.scenario.service.IAigScenarioFlowDispatcher;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.scenario.api.AigScenarioFlowPort;
import org.dromara.scenario.api.domain.AigScenarioFlowRequest;
import org.dromara.scenario.api.domain.AigScenarioFlowResult;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 场景任务跨模块派发实现（增量 12）。
 *
 * <h3>三条取舍</h3>
 * <ol>
 *     <li><b>实现是"可选的 Bean 集合"</b>：没有任何业务域实现端口时，本 Bean 仍然装配
 *         （只注入空集合），于是"没有实现"是一句可读的拒绝，而不是启动失败。</li>
 *     <li><b>适配器编码归一化后再索引</b>：实现方写 {@code creative_existing_flow} 或带空白
 *         也能命中；认不出的适配器被跳过并 WARN——那说明实现与枚举脱节了。</li>
 *     <li><b>同编码多个实现只取第一个</b>（构造期固定），并在日志告警：结果由代码决定，
 *         不由 Bean 扫描顺序决定。</li>
 * </ol>
 *
 * <p><b>不负责"该不该派发"</b>：{@code NONE}（纯登记）不需要派发，判断在调用方
 * （{@code AigScenarioDispatch#requiresWorkflow}）；本类只回答"交给谁、结果如何"。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Service
public class AigScenarioFlowDispatcherImpl implements IAigScenarioFlowDispatcher {

    private final Map<String, AigScenarioFlowPort> byAdapter;

    public AigScenarioFlowDispatcherImpl(List<AigScenarioFlowPort> flowPorts) {
        Map<String, AigScenarioFlowPort> index = new LinkedHashMap<>();
        if (flowPorts != null) {
            for (AigScenarioFlowPort port : flowPorts) {
                if (port == null) {
                    continue;
                }
                String adapter = AigScenarioDispatch.normalize(port.adapter());
                if (adapter == null) {
                    log.warn("跳过声明了未知适配器的场景流程实现：adapter={}, 实现={}",
                        port.adapter(), port.getClass().getName());
                    continue;
                }
                if (index.putIfAbsent(adapter, port) != null) {
                    log.warn("同一适配器出现了多个场景流程实现，只取第一个：adapter={}, 被跳过={}",
                        adapter, port.getClass().getName());
                }
            }
        }
        this.byAdapter = Map.copyOf(index);
    }

    @Override
    public boolean canDispatch(String adapterCode) {
        String adapter = AigScenarioDispatch.normalize(adapterCode);
        return adapter != null && byAdapter.containsKey(adapter);
    }

    @Override
    public AigScenarioFlowResult dispatch(AigScenarioFlowRequest request) {
        if (request == null) {
            throw new ServiceException("场景派发请求不能为空");
        }
        String adapter = AigScenarioDispatch.normalize(request.getAdapter());
        if (adapter == null) {
            throw new ServiceException("场景适配器不可识别，拒绝派发：adapter=" + request.getAdapter()
                + "，taskId=" + request.getTaskId());
        }
        AigScenarioFlowPort port = byAdapter.get(adapter);
        if (port == null) {
            throw new ServiceException("没有能处理该适配器的场景流程实现（跨模块执行派发尚未接入该域）：adapter="
                + adapter + "，taskId=" + request.getTaskId()
                + "。宁可明确失败，也不退化成一次普通模型调用");
        }
        AigScenarioFlowResult result = port.dispatch(request);
        if (result == null) {
            throw new ServiceException("场景流程实现没有返回结果：adapter=" + adapter
                + "，实现=" + port.getClass().getName() + "，taskId=" + request.getTaskId());
        }
        return result;
    }

}
