package org.dromara.ai.video.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dromara.ai.video.exception.VideoTaskException;
import org.dromara.ai.video.service.VideoTaskRepository;
import org.dromara.ai.video.service.VideoTaskSubmissionService;
import org.dromara.scenario.api.domain.AigScenarioFlowRequest;
import org.dromara.scenario.api.domain.AigScenarioFlowResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 视频域场景端口测试（增量 17）。
 *
 * <p>钉住"场景 → 视频"这段契约的边界：①快照读不懂 / 缺归属 → <b>业务拒绝</b>（绝不建空视频任务）；
 * ②合法快照走<b>同一条</b>建任务编排并把平台任务ID带上（追溯 + 幂等）；③<b>幂等</b>——
 * 已派发过的平台任务复用同一条视频任务；④视频域自己的业务拒绝原样回，不当系统故障。</p>
 *
 * @author ai-gov
 */
class VideoScenarioFlowPortTest {

    private VideoTaskSubmissionService submissionService;
    private VideoTaskRepository repository;
    private JdbcTemplate jdbc;
    private VideoScenarioFlowPort port;

    @BeforeEach
    void setUp() {
        submissionService = mock(VideoTaskSubmissionService.class);
        repository = mock(VideoTaskRepository.class);
        jdbc = mock(JdbcTemplate.class);
        port = new VideoScenarioFlowPort(submissionService, repository, jdbc, new ObjectMapper());
    }

    @Test
    @DisplayName("适配器编码固定为 VIDEO_EXISTING_FLOW")
    void adapterCodeIsStable() {
        assertEquals("VIDEO_EXISTING_FLOW", port.adapter());
    }

    @Test
    @DisplayName("★合法快照：走同一条建任务编排、带平台任务ID与派生幂等键")
    void validSnapshotSubmitsWithPlatformTaskId() {
        when(repository.findByPlatformTaskId(88L)).thenReturn(null);
        when(jdbc.queryForList(anyString(), eq(String.class), eq(9L))).thenReturn(List.of("000000"));
        when(submissionService.submit(any(), eq("000000"), eq(9L), eq(88L)))
            .thenReturn(new VideoTaskSubmissionService.SubmissionResult(501L, "VIDEO-20260101-000501", false));

        AigScenarioFlowResult result = port.dispatch(request(
            "{\"capabilityCode\":\"T2V\",\"workflowCode\":\"wf-t2v-h3\","
                + "\"fields\":{\"desc\":\"一只猫\",\"tier\":\"流畅 · 720P\",\"dur\":\"5 秒\"},"
                + "\"taskName\":\"场景短视频\"}"));

        assertTrue(result.isAccepted());
        assertEquals("501", result.getExternalRef());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(submissionService).submit(captor.capture(), eq("000000"), eq(9L), eq(88L));
        Map<String, Object> payload = captor.getValue();
        assertEquals("T2V", payload.get("capabilityCode"));
        assertEquals("wf-t2v-h3", payload.get("workflowCode"));
        assertEquals("场景短视频", payload.get("taskName"));
        assertEquals("PLATFORM-88", payload.get("idempotencyKey"),
            "平台任务ID派生幂等键：同一平台任务重复派发只应有同一条视频任务");
        assertNotNull(payload.get("fields"));
    }

    @Test
    @DisplayName("快照读不懂 / 非对象：业务拒绝，绝不提交建任务")
    void unreadableSnapshotIsRejected() {
        when(repository.findByPlatformTaskId(88L)).thenReturn(null);

        AigScenarioFlowResult notJson = port.dispatch(request("这不是 JSON"));
        assertFalse(notJson.isAccepted());

        AigScenarioFlowResult notObject = port.dispatch(request("[1,2,3]"));
        assertFalse(notObject.isAccepted());

        AigScenarioFlowResult missing = port.dispatch(request(null));
        assertFalse(missing.isAccepted());

        verify(submissionService, never()).submit(any(), anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("缺提交人：业务拒绝（无法确定归属，不退化成默认租户）")
    void missingRequesterIsRejected() {
        when(repository.findByPlatformTaskId(88L)).thenReturn(null);
        AigScenarioFlowRequest req = request("{\"capabilityCode\":\"T2V\",\"workflowCode\":\"wf-t2v-h3\"}");
        req.setRequesterId(null);

        AigScenarioFlowResult result = port.dispatch(req);

        assertFalse(result.isAccepted());
        assertTrue(result.getMessage().contains("提交人"), result.getMessage());
        verify(submissionService, never()).submit(any(), anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("★幂等：同一平台任务已派发过 → 复用同一条视频任务，不再建")
    void dispatchIsIdempotentByPlatformTaskId() {
        when(repository.findByPlatformTaskId(88L)).thenReturn(501L);

        AigScenarioFlowResult result = port.dispatch(request(
            "{\"capabilityCode\":\"T2V\",\"workflowCode\":\"wf-t2v-h3\"}"));

        assertTrue(result.isAccepted());
        assertEquals("501", result.getExternalRef());
        verify(submissionService, never()).submit(any(), anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("视频域自己的业务拒绝（契约不符/素材越权…）原样回，不当系统故障")
    void domainRejectionIsPassedThrough() {
        when(repository.findByPlatformTaskId(88L)).thenReturn(null);
        when(jdbc.queryForList(anyString(), eq(String.class), eq(9L))).thenReturn(List.of("000000"));
        when(submissionService.submit(any(), eq("000000"), eq(9L), eq(88L)))
            .thenThrow(VideoTaskException.invalidContract("不支持的能力编码"));

        AigScenarioFlowResult result = port.dispatch(request(
            "{\"capabilityCode\":\"NOT_A_CAP\",\"workflowCode\":\"wf-t2v-h3\"}"));

        assertFalse(result.isAccepted());
        assertEquals("不支持的能力编码", result.getMessage());
    }

    @Test
    @DisplayName("缺平台任务ID：业务拒绝")
    void missingPlatformTaskIdIsRejected() {
        AigScenarioFlowRequest req = request("{\"capabilityCode\":\"T2V\"}");
        req.setTaskId(null);

        assertFalse(port.dispatch(req).isAccepted());
        verify(submissionService, never()).submit(any(), anyString(), anyLong(), any());
    }

    private static AigScenarioFlowRequest request(String snapshotJson) {
        AigScenarioFlowRequest request = new AigScenarioFlowRequest();
        request.setTaskId(88L);
        request.setTaskNo("AIG-88");
        request.setScenarioCode("CONTENT_SHORT_VIDEO");
        request.setScenarioVersion("1.0.0");
        request.setAdapter("VIDEO_EXISTING_FLOW");
        request.setDataLevel("INTERNAL");
        request.setRequesterId(9L);
        request.setSnapshotJson(snapshotJson);
        return request;
    }

}
