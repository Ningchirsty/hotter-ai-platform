package org.dromara.aigov.workspace.scenario.service.impl;

import org.dromara.aigov.workspace.scenario.service.IAigScenarioFlowDispatcher;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.scenario.api.AigScenarioFlowPort;
import org.dromara.scenario.api.domain.AigScenarioFlowRequest;
import org.dromara.scenario.api.domain.AigScenarioFlowResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 场景任务跨模块派发测试（增量 12）。
 *
 * <p>钉住的是"找不到实现时不许静默"这条：一个需要真流程的场景任务，如果没有域实现，
 * 必须**明确失败**，而不是退化成一次普通模型调用——后者会让"跑过了"变成假事实。
 * 另钉适配器归一化、同编码多实现只取第一个、实现返回 null 要报错。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigScenarioFlowDispatcherImplTest {

    @Test
    @DisplayName("没有任何实现：canDispatch=false；派发明确失败（不退化成模型调用）")
    void noImplementationFailsClosed() {
        IAigScenarioFlowDispatcher dispatcher = new AigScenarioFlowDispatcherImpl(List.of());

        assertFalse(dispatcher.canDispatch("CREATIVE_EXISTING_FLOW"));
        ServiceException e = assertThrows(ServiceException.class,
            () -> dispatcher.dispatch(request("CREATIVE_EXISTING_FLOW")));
        assertTrue(e.getMessage().contains("没有能处理该适配器"), e.getMessage());
    }

    @Test
    @DisplayName("有实现：canDispatch=true，且结果原样透传（业务拒绝也是一种结论）")
    void dispatchPassesThroughResult() {
        IAigScenarioFlowDispatcher dispatcher = new AigScenarioFlowDispatcherImpl(
            List.of(port("CREATIVE_EXISTING_FLOW", req -> AigScenarioFlowResult.accepted("creative-task-1"))));

        assertTrue(dispatcher.canDispatch("creative_existing_flow"), "适配器应大小写不敏感");
        AigScenarioFlowResult result = dispatcher.dispatch(request("CREATIVE_EXISTING_FLOW"));

        assertTrue(result.isAccepted());
        assertEquals("creative-task-1", result.getExternalRef());
    }

    @Test
    @DisplayName("业务拒绝（accepted=false）不抛错，如实返回")
    void rejectedResultIsNotAnError() {
        IAigScenarioFlowDispatcher dispatcher = new AigScenarioFlowDispatcherImpl(
            List.of(port("CONTENT_EXISTING_FLOW", req -> AigScenarioFlowResult.rejected("快照缺少 productId"))));

        AigScenarioFlowResult result = dispatcher.dispatch(request("CONTENT_EXISTING_FLOW"));

        assertFalse(result.isAccepted());
        assertEquals("快照缺少 productId", result.getMessage());
    }

    @Test
    @DisplayName("适配器不可识别 / 请求为空：抛错，不猜")
    void invalidInputsAreRejected() {
        IAigScenarioFlowDispatcher dispatcher = new AigScenarioFlowDispatcherImpl(
            List.of(port("VIDEO_EXISTING_FLOW", req -> AigScenarioFlowResult.accepted("v1"))));

        assertThrows(ServiceException.class, () -> dispatcher.dispatch(null));
        assertThrows(ServiceException.class, () -> dispatcher.dispatch(request(null)));
        assertThrows(ServiceException.class, () -> dispatcher.dispatch(request("NOT_AN_ADAPTER")));
        assertFalse(dispatcher.canDispatch("NOT_AN_ADAPTER"));
        assertFalse(dispatcher.canDispatch(null));
    }

    @Test
    @DisplayName("实现没有返回结果：报错（否则任务层会把 null 当成受理）")
    void nullResultIsRejected() {
        IAigScenarioFlowDispatcher dispatcher = new AigScenarioFlowDispatcherImpl(
            List.of(port("VIDEO_EXISTING_FLOW", req -> null)));

        ServiceException e = assertThrows(ServiceException.class,
            () -> dispatcher.dispatch(request("VIDEO_EXISTING_FLOW")));
        assertTrue(e.getMessage().contains("没有返回结果"), e.getMessage());
    }

    @Test
    @DisplayName("同一适配器多个实现：只取第一个（结果由代码决定，不由 Bean 顺序决定）")
    void duplicateAdapterKeepsFirst() {
        List<String> called = new ArrayList<>();
        AigScenarioFlowPort first = port("VIDEO_EXISTING_FLOW", req -> {
            called.add("first");
            return AigScenarioFlowResult.accepted("first");
        });
        AigScenarioFlowPort second = port("video_existing_flow", req -> {
            called.add("second");
            return AigScenarioFlowResult.accepted("second");
        });

        IAigScenarioFlowDispatcher dispatcher = new AigScenarioFlowDispatcherImpl(List.of(first, second));
        AigScenarioFlowResult result = dispatcher.dispatch(request("VIDEO_EXISTING_FLOW"));

        assertEquals(List.of("first"), called);
        assertEquals("first", result.getExternalRef());
    }

    @Test
    @DisplayName("声明了未知适配器的实现被跳过（不因此让整个派发器失效）")
    void unknownAdapterImplementationIsSkipped() {
        IAigScenarioFlowDispatcher dispatcher = new AigScenarioFlowDispatcherImpl(List.of(
            port("NOT_AN_ADAPTER", req -> AigScenarioFlowResult.accepted("x")),
            port("CONTENT_EXISTING_FLOW", req -> AigScenarioFlowResult.accepted("c1"))));

        assertFalse(dispatcher.canDispatch("NOT_AN_ADAPTER"));
        assertTrue(dispatcher.canDispatch("CONTENT_EXISTING_FLOW"));
    }

    private static AigScenarioFlowRequest request(String adapter) {
        AigScenarioFlowRequest request = new AigScenarioFlowRequest();
        request.setAdapter(adapter);
        request.setTaskId(88L);
        request.setScenarioCode("COMMERCE");
        request.setScenarioVersion("1.0.0");
        return request;
    }

    private static AigScenarioFlowPort port(String adapter,
                                            java.util.function.Function<AigScenarioFlowRequest,
                                                AigScenarioFlowResult> behavior) {
        return new AigScenarioFlowPort() {
            @Override
            public String adapter() {
                return adapter;
            }

            @Override
            public AigScenarioFlowResult dispatch(AigScenarioFlowRequest request) {
                return behavior.apply(request);
            }
        };
    }

}
