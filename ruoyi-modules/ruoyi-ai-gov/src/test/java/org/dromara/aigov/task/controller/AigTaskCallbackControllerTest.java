package org.dromara.aigov.task.controller;

import org.dromara.aigov.task.domain.bo.AigTaskCallbackBo;
import org.dromara.aigov.task.domain.vo.AigCallbackVo;
import org.dromara.aigov.task.service.IAigTaskService;
import org.dromara.common.core.domain.R;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 回调入口（HTTP 层）的行为测试。
 *
 * <p><b>三条不变式</b>：</p>
 * <ol>
 *     <li><b>原始字节必须原样交给验签</b>：多一个空格的差别就是「签名对不上」，
 *         而这类故障看起来像密钥配错，极难排查。因此断言的是"完全相等"，不是"包含"；</li>
 *     <li><b>裸请求（没有 Provider 头）与坏 JSON 不进服务层、不写账本</b>：
 *         账本行是"一次回调尝试"的证据，而这个端点无需登录态；
 *         若什么请求都记一行，任何人裸 POST 就能把表刷大（配合限流是第二道防线）；</li>
 *     <li><b>处理结论要映射成真实 HTTP 状态码</b>：调用方是机器，它按状态码决定要不要重推。</li>
 * </ol>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigTaskCallbackControllerTest {

    private IAigTaskService taskService;
    private AigTaskCallbackController controller;

    @BeforeEach
    void setUp() {
        taskService = mock(IAigTaskService.class);
        controller = new AigTaskCallbackController(taskService, JsonMapper.builder().build());
    }

    private static String body() {
        // 刻意带换行与缩进：签名是对这些字节算的，控制器不得"顺手规整"它
        return """
            {
              "eventId": "evt-1",
              "providerJobId": "job-9",
              "toStatus": "SUCCEEDED",
              "detail": "渲染完成",
              "progress": 100
            }""";
    }

    private static AigCallbackVo vo(String processResult) {
        AigCallbackVo vo = new AigCallbackVo();
        vo.setProcessResult(processResult);
        return vo;
    }

    @Test
    @DisplayName("★ 原始请求体必须原样传给验签（不得 trim/重排/再序列化）")
    void rawBodyIsPassedThroughByteExact() {
        when(taskService.handleCallback(any())).thenReturn(vo("ACCEPTED"));
        String raw = body();

        controller.receive(raw, "bluocto", "sig-abc", "HMAC-SHA256");

        ArgumentCaptor<AigTaskCallbackBo> captor = ArgumentCaptor.forClass(AigTaskCallbackBo.class);
        verify(taskService).handleCallback(captor.capture());
        AigTaskCallbackBo sent = captor.getValue();
        assertEquals(raw, sent.getRawPayload(),
            "请求体被改动过 → 所有签名都会对不上，而现场看起来像密钥配错");
        assertEquals("bluocto", sent.getProviderCode());
        assertEquals("sig-abc", sent.getSignature());
        assertEquals("HMAC-SHA256", sent.getSignAlgorithm());
    }

    @Test
    @DisplayName("业务字段从请求体解析出来（解析的是同一串字节的副本，不影响验签）")
    void businessFieldsAreExtracted() {
        when(taskService.handleCallback(any())).thenReturn(vo("ACCEPTED"));

        controller.receive(body(), "bluocto", "sig", null);

        ArgumentCaptor<AigTaskCallbackBo> captor = ArgumentCaptor.forClass(AigTaskCallbackBo.class);
        verify(taskService).handleCallback(captor.capture());
        AigTaskCallbackBo sent = captor.getValue();
        assertEquals("evt-1", sent.getEventId());
        assertEquals("job-9", sent.getProviderJobId());
        assertEquals("SUCCEEDED", sent.getToStatus());
        assertEquals("渲染完成", sent.getDetail());
        assertEquals(100, sent.getProgress());
    }

    @Test
    @DisplayName("★ 缺少 Provider 头 → 400 且不进服务层（不写账本，避免裸 POST 刷表）")
    void missingProviderHeaderIsRejected() {
        ResponseEntity<R<AigCallbackVo>> response = controller.receive(body(), null, "sig", null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().getMsg().contains(AigTaskCallbackController.HEADER_PROVIDER),
            response.getBody().getMsg());
        verify(taskService, never()).handleCallback(any());
    }

    @Test
    @DisplayName("★ 载荷不是合法 JSON → 400 且不进服务层（日志留痕以便区分对接方发错与有人乱打）")
    void malformedBodyIsRejected() {
        ResponseEntity<R<AigCallbackVo>> response = controller.receive("not-json", "bluocto", "sig", null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().getMsg().contains("不是合法 JSON"), response.getBody().getMsg());
        verify(taskService, never()).handleCallback(any());
    }

    @Test
    @DisplayName("progress 不是 0-100 的整数 → 400（契约违反要说清楚，不猜）")
    void nonIntegerProgressIsRejected() {
        ResponseEntity<R<AigCallbackVo>> response = controller.receive(
            "{\"eventId\":\"e\",\"providerJobId\":\"j\",\"toStatus\":\"RUNNING\",\"progress\":\"half\"}",
            "bluocto", "sig", null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().getMsg().contains("progress"), response.getBody().getMsg());
        verify(taskService, never()).handleCallback(any());
    }

    @Test
    @DisplayName("字段缺失不在这里报错：交给服务层按「未知目标状态/定位不到」处理并记账")
    void missingFieldsReachTheService() {
        when(taskService.handleCallback(any())).thenReturn(vo("ORDER_STALE"));

        controller.receive("{}", "bluocto", "sig", null);

        ArgumentCaptor<AigTaskCallbackBo> captor = ArgumentCaptor.forClass(AigTaskCallbackBo.class);
        verify(taskService).handleCallback(captor.capture());
        assertNull(captor.getValue().getToStatus(), "缺字段由服务层判定并留痕，控制器不代它下结论");
        assertNull(captor.getValue().getProgress());
    }

    @Test
    @DisplayName("★ 处理结论映射成真实 HTTP 状态码（机器按它决定要不要重推）")
    void processResultMapsToHttpStatus() {
        when(taskService.handleCallback(any())).thenReturn(vo("ACCEPTED"));
        assertEquals(HttpStatus.OK, controller.receive(body(), "p", "s", null).getStatusCode(),
            "已推进 → 200");

        when(taskService.handleCallback(any())).thenReturn(vo("DUPLICATE"));
        assertEquals(HttpStatus.OK, controller.receive(body(), "p", "s", null).getStatusCode(),
            "幂等忽略也算收到了，不该让对方重推");

        when(taskService.handleCallback(any())).thenReturn(vo("REJECTED_UNSIGNED"));
        assertEquals(HttpStatus.UNAUTHORIZED, controller.receive(body(), "p", "s", null).getStatusCode(),
            "验签失败要 401：对方要改的是密钥/算法，重推无用");

        when(taskService.handleCallback(any())).thenReturn(vo("TASK_NOT_FOUND"));
        assertEquals(HttpStatus.NOT_FOUND, controller.receive(body(), "p", "s", null).getStatusCode(),
            "定位不到任务 → 404（可能是回调先于登记到达）");

        when(taskService.handleCallback(any())).thenReturn(vo("ORDER_STALE"));
        assertEquals(HttpStatus.CONFLICT, controller.receive(body(), "p", "s", null).getStatusCode(),
            "顺序过期 → 409：迟到的完成回调重推永远无用");
    }

}
