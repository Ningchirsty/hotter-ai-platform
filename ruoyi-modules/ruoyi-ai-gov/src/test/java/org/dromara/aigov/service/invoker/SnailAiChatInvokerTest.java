package org.dromara.aigov.service.invoker;

import com.aizuda.snail.ai.common.model.Result;
import com.aizuda.snail.ai.common.openapi.dto.OpenApiChatRequest;
import com.aizuda.snail.ai.common.openapi.dto.OpenApiChatSyncResponse;
import com.aizuda.snail.ai.openapi.client.core.api.OpenApiChatClient;
import org.dromara.aigov.config.AigGovProperties;
import org.dromara.aigov.domain.vo.AigSnailAgentVo;
import org.dromara.aigov.mapper.AigSnailAgentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 「模型 ↔ Agent」精确映射的守门测试（阶段2）。
 *
 * <p>钉住的是本模块最不能出的一类错：<b>路由选中模型 A、实际却跑了模型 B，而且不报错</b>。
 * 因此关键断言不只是「选了正确的 Agent」，还有
 * <b>映射不存在时绝不发起调用</b>——只要还是发了一次请求，就有可能是拿别的模型跑的。</p>
 *
 * <p>纯 Mockito：不加载 Spring、不连 snail-ai。真链路（snail-ai server 与 Agent 数据）
 * 由目标环境的连通性探测负责。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class SnailAiChatInvokerTest {

    private static final Long MODEL_ID = 77L;

    private ObjectProvider<OpenApiChatClient> chatClientProvider;
    private OpenApiChatClient client;
    private AigSnailAgentMapper agentMapper;
    private AigGovProperties properties;
    private SnailAiChatInvoker invoker;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        chatClientProvider = mock(ObjectProvider.class);
        client = mock(OpenApiChatClient.class);
        agentMapper = mock(AigSnailAgentMapper.class);
        properties = new AigGovProperties();
        properties.setEnabled(true);
        when(chatClientProvider.getIfAvailable()).thenReturn(client);
        invoker = new SnailAiChatInvoker(chatClientProvider, properties, agentMapper);
    }

    /**
     * 造一个 Agent。
     *
     * @param id      Agent ID
     * @param status  状态（1-活跃）
     * @param appId   关联应用（null=本地执行）
     * @param modelId 关联模型
     * @return Agent
     */
    private static AigSnailAgentVo agent(long id, int status, String appId, Long modelId) {
        AigSnailAgentVo vo = new AigSnailAgentVo();
        vo.setId(id);
        vo.setName("agent-" + id);
        vo.setStatus(status);
        vo.setAppId(appId);
        vo.setChatModelId(modelId);
        return vo;
    }

    /**
     * 造一次调用请求。
     *
     * @param modelId 模型ID
     * @return 请求
     */
    private static ModelInvokeRequest request(Long modelId) {
        ModelInvokeRequest request = new ModelInvokeRequest();
        request.setCapabilityCode("talent_match");
        request.setModelId(modelId);
        request.setModelKey("glm-5.1");
        request.setPrompt("hello");
        return request;
    }

    @Test
    @DisplayName("★ 该模型在 snail-ai 里没有 Agent：明确失败，且绝不发起调用（不会拿别的 Agent 顶替）")
    void failsAndNeverCallsWhenNoAgentMappedToModel() {
        when(agentMapper.selectByChatModelId(MODEL_ID)).thenReturn(List.of());

        ModelInvokeResult result = invoker.invoke(request(MODEL_ID));

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorSummary().contains("没有关联的 Agent"), result.getErrorSummary());
        assertTrue(result.getErrorSummary().contains(String.valueOf(MODEL_ID)),
            "报错必须点名是哪个模型：" + result.getErrorSummary());
        // 关键：一次请求都没发出去
        verify(client, never()).chatSync(any());
    }

    @Test
    @DisplayName("映射命中：用 chat_model_id 对应的 Agent 发起调用（实际跑的模型＝治理层选的模型）")
    void usesAgentMappedToModel() {
        when(agentMapper.selectByChatModelId(MODEL_ID))
            .thenReturn(List.of(agent(42L, 1, null, MODEL_ID)));
        when(client.chatSync(any())).thenReturn(ok("done"));

        ModelInvokeResult result = invoker.invoke(request(MODEL_ID));

        assertTrue(result.isSuccess(), result.getErrorSummary());
        ArgumentCaptor<OpenApiChatRequest> captor = ArgumentCaptor.forClass(OpenApiChatRequest.class);
        verify(client).chatSync(captor.capture());
        assertEquals(42L, captor.getValue().getAgentId(), "必须用映射到的那个 Agent");
    }

    @Test
    @DisplayName("一个模型对应多个启用 Agent：取 id 最小者并留 WARN（不静默）")
    void picksLowestIdWhenAmbiguous() {
        when(agentMapper.selectByChatModelId(MODEL_ID)).thenReturn(List.of(
            agent(15L, 1, null, MODEL_ID), agent(12L, 1, null, MODEL_ID)));
        when(client.chatSync(any())).thenReturn(ok("done"));

        ModelInvokeResult result = invoker.invoke(request(MODEL_ID));

        assertTrue(result.isSuccess(), result.getErrorSummary());
        ArgumentCaptor<OpenApiChatRequest> captor = ArgumentCaptor.forClass(OpenApiChatRequest.class);
        verify(client).chatSync(captor.capture());
        assertEquals(12L, captor.getValue().getAgentId());
    }

    @Test
    @DisplayName("Agent 全部处于非活跃/已废弃/已禁用：报出「有几个、为什么不能用」，不发请求")
    void failsWhenAllAgentsInactive() {
        when(agentMapper.selectByChatModelId(MODEL_ID)).thenReturn(List.of(
            agent(3L, 2, null, MODEL_ID), agent(4L, 4, null, MODEL_ID)));

        ModelInvokeResult result = invoker.invoke(request(MODEL_ID));

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorSummary().contains("启用中"), result.getErrorSummary());
        assertTrue(result.getErrorSummary().contains("2 个"), "要报出匹配到几个、其中几个不可用："
            + result.getErrorSummary());
        verify(client, never()).chatSync(any());
    }

    @Test
    @DisplayName("配置了应用作用域：属于别的应用的 Agent 不被选中；本地执行(app_id 为空)仍可用")
    void skipsAgentsScopedToAnotherApp() {
        properties.setAppId("1");
        when(agentMapper.selectByChatModelId(MODEL_ID)).thenReturn(List.of(
            agent(8L, 1, "2", MODEL_ID),      // 别的应用：跳过
            agent(9L, 1, null, MODEL_ID)));   // 本地执行：可用
        when(client.chatSync(any())).thenReturn(ok("done"));

        ModelInvokeResult result = invoker.invoke(request(MODEL_ID));

        assertTrue(result.isSuccess(), result.getErrorSummary());
        ArgumentCaptor<OpenApiChatRequest> captor = ArgumentCaptor.forClass(OpenApiChatRequest.class);
        verify(client).chatSync(captor.capture());
        assertEquals(9L, captor.getValue().getAgentId(),
            "不能把请求派给别的应用（表现是发出去了但没人应答）");
    }

    @Test
    @DisplayName("配置了应用作用域且只剩别的应用：明确失败，不发请求")
    void failsWhenOnlyForeignAppAgents() {
        properties.setAppId("1");
        when(agentMapper.selectByChatModelId(MODEL_ID))
            .thenReturn(List.of(agent(8L, 1, "2", MODEL_ID)));

        ModelInvokeResult result = invoker.invoke(request(MODEL_ID));

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorSummary().contains("其它应用"), result.getErrorSummary());
        verify(client, never()).chatSync(any());
    }

    @Test
    @DisplayName("缺 modelId：明确失败（映射以 chat_model_id 为准，没有模型ID就无法确定跑哪个模型）")
    void failsWithoutModelId() {
        ModelInvokeResult result = invoker.invoke(request(null));

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorSummary().contains("缺少模型ID"), result.getErrorSummary());
        verify(client, never()).chatSync(any());
        verify(agentMapper, never()).selectByChatModelId(any());
    }

    @Test
    @DisplayName("available() 只管通道本身（客户端+开关）；模型有没有对应 Agent 是逐次调用的判定")
    void availableOnlyReflectsChannel() {
        assertTrue(invoker.available());
        properties.setEnabled(false);
        assertFalse(invoker.available(), "开关关掉时通道不可用");
        properties.setEnabled(true);
        when(chatClientProvider.getIfAvailable()).thenReturn(null);
        assertFalse(invoker.available(), "客户端不在时通道不可用");
    }

    @Test
    @DisplayName("图片载荷：snail-ai 只收文本，必须显式拒绝（不能忽略图片照常发文本）")
    void rejectsImagePayload() {
        when(agentMapper.selectByChatModelId(MODEL_ID))
            .thenReturn(List.of(agent(42L, 1, null, MODEL_ID)));
        ModelInvokeRequest request = request(MODEL_ID);
        // 键名必须是 ModelImagePayload.KEY（"images"）：用别的键不算图片载荷，
        // 会一路走到拼 content（那里用到 Spring 感知的 JsonUtils）
        request.setPayload(java.util.Map.of(
            ModelImagePayload.KEY,
            java.util.List.of(java.util.Map.of("mimeType", "image/png", "base64", "AAAA"))));

        ModelInvokeResult result = invoker.invoke(request);

        assertFalse(result.isSuccess());
        assertTrue(result.getErrorSummary().contains("图片"), result.getErrorSummary());
        verify(client, never()).chatSync(any());
    }

    /**
     * 造一个成功的 snail-ai 返回。
     *
     * @param content 内容
     * @return Result
     */
    private static Result<OpenApiChatSyncResponse> ok(String content) {
        OpenApiChatSyncResponse data = new OpenApiChatSyncResponse();
        data.setContent(content);
        data.setDurationMs(12L);
        Result<OpenApiChatSyncResponse> result = new Result<>();
        result.setStatus(1);
        result.setData(data);
        return result;
    }

}
