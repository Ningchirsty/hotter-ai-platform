package org.dromara.aigov.workspace.scenario.service.impl;

import org.dromara.aigov.task.domain.AigTask;
import org.dromara.aigov.task.enums.AigTaskStatusEnum;
import org.dromara.aigov.task.service.IAigTaskService;
import org.dromara.aigov.workspace.scenario.domain.bo.AigScenarioReportBo;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 场景任务回执服务测试（增量 16）。
 *
 * <p>钉住的是"回执的边界"：①只收尾 **RUNNING** 的任务；终态任务的重复回执是**幂等无操作**（不报错）；
 * ②{@code RUNNING} 结论只记进度、不改变状态；③认不出的结论**直接拒绝**（跨模块写操作不许"当成成功"）；
 * ④缺任务ID / 任务不存在要报错。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigScenarioReportServiceImplTest {

    private static final long TASK_ID = 88L;

    private IAigTaskService taskService;
    private AigScenarioReportServiceImpl service;

    @BeforeEach
    void setUp() {
        taskService = mock(IAigTaskService.class);
        service = new AigScenarioReportServiceImpl(taskService, JsonMapper.builder().build());
    }

    @Test
    @DisplayName("允许的结论：RUNNING→SUCCEEDED，带 externalRef 快照")
    void succeededOutcomeClosesTask() {
        when(taskService.getTask(TASK_ID)).thenReturn(task("RUNNING", 4));

        boolean changed = service.report(report("SUCCEEDED", "content-401", "内容任务已可开工"));

        assertTrue(changed);
        ArgumentCaptor<String> remark = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> snapshot = ArgumentCaptor.forClass(String.class);
        verify(taskService).transition(eq(TASK_ID), eq(4), eq(AigTaskStatusEnum.SUCCEEDED),
            remark.capture(), snapshot.capture());
        assertTrue(remark.getValue().contains("content-401"), remark.getValue());
        assertTrue(snapshot.getValue().contains("SUCCEEDED"), snapshot.getValue());
        assertTrue(snapshot.getValue().contains("content-401"), snapshot.getValue());
    }

    @Test
    @DisplayName("失败结论：recordFailure 带可读原因（错误分类留空，不猜）")
    void failedOutcomeRecordsFailure() {
        when(taskService.getTask(TASK_ID)).thenReturn(task("RUNNING", 4));

        boolean changed = service.report(report("FAILED", "content-401", "闸门未通过：缺少产品图"));

        assertTrue(changed);
        verify(taskService).recordFailure(eq(TASK_ID), eq(4), isNull(), eq("闸门未通过：缺少产品图"));
    }

    @Test
    @DisplayName("★幂等：终态任务的重复回执是无操作，不报错")
    void reportOnTerminalTaskIsNoOp() {
        when(taskService.getTask(TASK_ID)).thenReturn(task("SUCCEEDED", 9));

        boolean changed = service.report(report("SUCCEEDED", "content-401", "重复回执"));

        assertFalse(changed);
        verify(taskService, never()).transition(any(), any(), any(), any(), any());
        verify(taskService, never()).recordFailure(any(), any(), any(), any());
    }

    @Test
    @DisplayName("RUNNING 结论只记进度，不改变状态")
    void runningOutcomeDoesNotChangeStatus() {
        when(taskService.getTask(TASK_ID)).thenReturn(task("RUNNING", 4));

        boolean changed = service.report(report("RUNNING", "content-401", "解析中"));

        assertFalse(changed);
        verify(taskService, never()).transition(any(), any(), any(), any(), any());
        verify(taskService, never()).recordFailure(any(), any(), any(), any());
    }

    @Test
    @DisplayName("非法结论 / 缺任务ID / 任务不存在：报错，绝不静默")
    void invalidReportsAreRejected() {
        when(taskService.getTask(TASK_ID)).thenReturn(task("RUNNING", 4));

        assertThrows(ServiceException.class, () -> service.report(null));
        assertThrows(ServiceException.class, () -> service.report(report(null, null, null)));
        assertThrows(ServiceException.class, () -> service.report(report("NOT_AN_OUTCOME", null, null)));

        when(taskService.getTask(TASK_ID)).thenReturn(null);
        assertThrows(ServiceException.class, () -> service.report(report("SUCCEEDED", null, null)));

        verify(taskService, never()).transition(any(), any(), any(), any(), any());
    }

    private static AigTask task(String status, int version) {
        AigTask task = new AigTask();
        task.setTaskId(TASK_ID);
        task.setStatus(status);
        task.setVersion(version);
        return task;
    }

    private static AigScenarioReportBo report(String outcome, String externalRef, String message) {
        AigScenarioReportBo bo = new AigScenarioReportBo();
        bo.setPlatformTaskId(TASK_ID);
        bo.setOutcome(outcome);
        bo.setExternalRef(externalRef);
        bo.setMessage(message);
        return bo;
    }

}
