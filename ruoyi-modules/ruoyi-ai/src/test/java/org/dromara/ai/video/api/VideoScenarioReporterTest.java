package org.dromara.ai.video.api;

import org.dromara.ai.video.domain.VideoTaskStatus;
import org.dromara.aigov.workspace.scenario.domain.bo.AigScenarioReportBo;
import org.dromara.aigov.workspace.scenario.service.IAigScenarioReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 视频任务 → 平台任务回执测试（增量 17，与内容域同形）。
 *
 * <p>钉住的是映射与"不越界"：①{@code SUCCEEDED}→SUCCEEDED、{@code FAILED}/{@code TIMEOUT}/
 * {@code CANCELED}→FAILED（带原因）；②{@code QUEUED}/{@code RUNNING} 与无来源平台任务ID
 * **不上报**；③取不到回执服务时安静跳过；④回执失败**不影响视频侧**（吞异常只记日志）。</p>
 *
 * @author ai-gov
 */
class VideoScenarioReporterTest {

    private IAigScenarioReportService reportService;
    private ObjectProvider<IAigScenarioReportService> provider;
    private VideoScenarioReporter reporter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        reportService = mock(IAigScenarioReportService.class);
        provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(reportService);
        reporter = new VideoScenarioReporter(provider);
    }

    @Test
    @DisplayName("SUCCEEDED → SUCCEEDED，externalRef=视频任务ID")
    void succeededReportsSucceeded() {
        reporter.report(88L, 501L, VideoTaskStatus.SUCCEEDED, "成片已落盘");

        AigScenarioReportBo bo = capture();
        assertEquals(88L, bo.getPlatformTaskId());
        assertEquals("SUCCEEDED", bo.getOutcome());
        assertEquals("501", bo.getExternalRef());
    }

    @Test
    @DisplayName("FAILED / TIMEOUT / CANCELED → FAILED，并带上原因")
    void failureStatesReportFailed() {
        reporter.report(88L, 501L, VideoTaskStatus.FAILED, "ComfyUI 执行失败");
        assertEquals("FAILED", capture().getOutcome());

        reporter.report(88L, 501L, VideoTaskStatus.TIMEOUT, "等待 ComfyUI 结果超时");
        AigScenarioReportBo timeout = captureAtLeast(2);
        assertEquals("FAILED", timeout.getOutcome());
        assertEquals("等待 ComfyUI 结果超时", timeout.getMessage());

        reporter.report(88L, 501L, VideoTaskStatus.CANCELED, "视频任务已取消");
        assertEquals("FAILED", captureAtLeast(3).getOutcome());
    }

    @Test
    @DisplayName("进行中状态（QUEUED/RUNNING）与无来源平台任务ID：不上报")
    void runningOrUndispatchedDoesNotReport() {
        reporter.report(88L, 501L, VideoTaskStatus.QUEUED, null);
        reporter.report(88L, 501L, VideoTaskStatus.RUNNING, null);
        reporter.report(null, 501L, VideoTaskStatus.SUCCEEDED, null);
        reporter.report(88L, 501L, null, null);

        verify(reportService, never()).report(any());
    }

    @Test
    @DisplayName("取不到回执服务（脱离 aigov 部署）：安静跳过，不抛异常")
    @SuppressWarnings("unchecked")
    void absentReportServiceIsSkipped() {
        ObjectProvider<IAigScenarioReportService> empty = mock(ObjectProvider.class);
        VideoScenarioReporter lonely = new VideoScenarioReporter(empty);

        assertDoesNotThrow(() -> lonely.report(88L, 501L, VideoTaskStatus.SUCCEEDED, null));
    }

    @Test
    @DisplayName("★回执失败不影响视频侧（吞异常只记日志）")
    void reportFailureIsSwallowed() {
        when(reportService.report(any())).thenThrow(new RuntimeException("platform down"));

        assertDoesNotThrow(() -> reporter.report(88L, 501L, VideoTaskStatus.FAILED, "x"));
    }

    private AigScenarioReportBo capture() {
        ArgumentCaptor<AigScenarioReportBo> captor = ArgumentCaptor.forClass(AigScenarioReportBo.class);
        verify(reportService).report(captor.capture());
        return captor.getValue();
    }

    /**
     * 捕获第 {@code n} 次（含之前）调用里最后一次的回执。
     */
    private AigScenarioReportBo captureAtLeast(int n) {
        ArgumentCaptor<AigScenarioReportBo> captor = ArgumentCaptor.forClass(AigScenarioReportBo.class);
        verify(reportService, org.mockito.Mockito.atLeastOnce()).report(captor.capture());
        return captor.getAllValues().get(n - 1);
    }

}
