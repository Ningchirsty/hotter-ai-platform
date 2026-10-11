package org.dromara.content.api;

import org.dromara.aigov.workspace.scenario.domain.bo.AigScenarioReportBo;
import org.dromara.aigov.workspace.scenario.service.IAigScenarioReportService;
import org.dromara.content.domain.CpTask;
import org.dromara.content.mapper.CpTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 内容任务 → 平台任务回执测试（增量 16）。
 *
 * <p>钉住的是映射与"不越界"：①只有**派发创建**的任务（{@code platform_task_id} 非空）才回报；
 * ②{@code READY}/{@code CONDITIONAL_READY}→SUCCEEDED、{@code PENDING_CONFIRM}→FAILED 且带闸门原因、
 * 其它状态**不上报**（作业还在跑）；③回执失败**不影响内容侧**（吞异常只记日志）。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class ContentScenarioReporterTest {

    private CpTaskMapper taskMapper;
    private IAigScenarioReportService reportService;
    private ContentScenarioReporter reporter;

    @BeforeEach
    void setUp() {
        taskMapper = mock(CpTaskMapper.class);
        reportService = mock(IAigScenarioReportService.class);
        reporter = new ContentScenarioReporter(taskMapper, reportService);
    }

    @Test
    @DisplayName("READY / CONDITIONAL_READY → SUCCEEDED（交接完成），externalRef=内容任务ID")
    void readyReportsSucceeded() {
        when(taskMapper.selectById(401L)).thenReturn(dispatched("READY", null));

        reporter.reportIfDispatched(401L);

        AigScenarioReportBo bo = capture();
        assertEquals(88L, bo.getPlatformTaskId());
        assertEquals("SUCCEEDED", bo.getOutcome());
        assertEquals("401", bo.getExternalRef());
    }

    @Test
    @DisplayName("CONDITIONAL_READY 也算交接完成（条件项待补，但链路已接手）")
    void conditionalReadyReportsSucceeded() {
        when(taskMapper.selectById(401L)).thenReturn(dispatched("CONDITIONAL_READY", null));

        reporter.reportIfDispatched(401L);

        assertEquals("SUCCEEDED", capture().getOutcome());
    }

    @Test
    @DisplayName("PENDING_CONFIRM → FAILED，并带上闸门给的原因")
    void pendingConfirmReportsFailedWithReason() {
        when(taskMapper.selectById(401L)).thenReturn(dispatched("PENDING_CONFIRM", "缺少产品图（强制项）"));

        reporter.reportIfDispatched(401L);

        AigScenarioReportBo bo = capture();
        assertEquals("FAILED", bo.getOutcome());
        assertEquals("缺少产品图（强制项）", bo.getMessage());
    }

    @Test
    @DisplayName("非派发任务（platform_task_id 为空）与进行中状态：不上报")
    void notDispatchedOrStillRunningDoesNotReport() {
        CpTask manual = dispatched("READY", null);
        manual.setPlatformTaskId(null);
        when(taskMapper.selectById(1L)).thenReturn(manual);
        when(taskMapper.selectById(2L)).thenReturn(dispatched("DRAFT", null));
        when(taskMapper.selectById(3L)).thenReturn(dispatched("PARSING", null));
        when(taskMapper.selectById(4L)).thenReturn(null);

        reporter.reportIfDispatched(1L);
        reporter.reportIfDispatched(2L);
        reporter.reportIfDispatched(3L);
        reporter.reportIfDispatched(4L);
        reporter.reportIfDispatched(null);

        verify(reportService, never()).report(any());
    }

    @Test
    @DisplayName("★回执失败不影响内容侧（吞异常只记日志）")
    void reportFailureIsSwallowed() {
        when(taskMapper.selectById(401L)).thenReturn(dispatched("READY", null));
        when(reportService.report(any())).thenThrow(new RuntimeException("platform down"));

        assertDoesNotThrow(() -> reporter.reportIfDispatched(401L));
    }

    private AigScenarioReportBo capture() {
        ArgumentCaptor<AigScenarioReportBo> captor = ArgumentCaptor.forClass(AigScenarioReportBo.class);
        verify(reportService).report(captor.capture());
        return captor.getValue();
    }

    private static CpTask dispatched(String status, String blockReason) {
        CpTask task = new CpTask();
        task.setTaskId(401L);
        task.setTaskNo("CT202610110001");
        task.setStatus(status);
        task.setBlockReason(blockReason);
        task.setPlatformTaskId(88L);
        return task;
    }

}
