package org.dromara.aigov.task.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.dromara.aigov.constant.AigConstants;
import org.dromara.aigov.task.domain.AigTaskStep;
import org.dromara.aigov.task.domain.vo.AigTaskFlowNodeVo;
import org.dromara.aigov.task.enums.AigTaskStepStatusEnum;
import org.dromara.aigov.task.service.IAigFlowDriverService;
import org.dromara.aigov.task.service.IAigTaskExecutor;
import org.dromara.aigov.task.service.IAigTaskScheduler;
import org.dromara.aigov.task.service.IAigTaskService;
import org.dromara.aigov.task.service.IAigTaskStepService;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 任务流程节点接口测试（设计 §6.5：人工确认的挂起与恢复）。
 *
 * <p>钉住三件事：①"停在第几个节点、它是不是人工确认"有读法（否则 {@code NEED_HUMAN}
 * 只是一句没法操作的话）；②确认走 {@code aig:task:operate}，且批准引用必填；③
 * 确认接口不自己实现状态迁移——它只调驱动，迁移判据（含幂等）仍只有一处实现。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigTaskFlowControllerTest {

    private static final long TASK_ID = 77L;

    private IAigTaskStepService stepService;
    private IAigFlowDriverService flowDriver;
    private AigTaskController controller;

    @BeforeEach
    void setUp() {
        stepService = mock(IAigTaskStepService.class);
        flowDriver = mock(IAigFlowDriverService.class);
        controller = new AigTaskController(mock(IAigTaskService.class), mock(IAigTaskScheduler.class),
            mock(IAigTaskExecutor.class), stepService, flowDriver);
    }

    private static AigTaskStep flowStep(long id, String nodeType, String nodeRef, String status) {
        AigTaskStep step = new AigTaskStep();
        step.setStepId(id);
        step.setTaskId(TASK_ID);
        step.setSequence(11);
        step.setKind("FLOW_NODE");
        step.setAttempt(1);
        step.setNodeType(nodeType);
        step.setNodeRef(nodeRef);
        step.setStatus(status);
        return step;
    }

    @Test
    @DisplayName("流程节点清单：下标从 0 开始，等待确认的节点能被认出来")
    void flowNodesAreIndexedAndFlagged() {
        when(stepService.listFlowNodes(TASK_ID)).thenReturn(List.of(
            flowStep(1L, "STEP", "a", "SUCCEEDED"),
            flowStep(2L, "HUMAN_CONFIRM", "approve", "RUNNING"),
            flowStep(3L, "OUTPUT", null, "PLANNED")));

        R<List<AigTaskFlowNodeVo>> result = controller.flowNodes(TASK_ID);

        List<AigTaskFlowNodeVo> rows = result.getData();
        assertEquals(3, rows.size());
        assertEquals(0, rows.get(0).getNodeIndex());
        assertEquals(1, rows.get(1).getNodeIndex());
        assertTrue(rows.get(1).isAwaitingConfirmation(), "已挂起、未批准 = 等人工确认");
        assertFalse(rows.get(2).isAwaitingConfirmation());
    }

    @Test
    @DisplayName("★ 已批准的节点不再显示为「等确认」（判据是同一条，不在界面各写一遍）")
    void approvedNodeIsNotAwaiting() {
        AigTaskStep approved = flowStep(2L, "HUMAN_CONFIRM", "approve", "SUCCEEDED");
        approved.setApprovalRef("appr-1");
        when(stepService.listFlowNodes(TASK_ID)).thenReturn(List.of(approved));

        AigTaskFlowNodeVo vo = controller.flowNodes(TASK_ID).getData().get(0);

        assertFalse(vo.isAwaitingConfirmation());
        assertEquals("appr-1", vo.getApprovalRef());
    }

    @Test
    @DisplayName("★ 确认：调驱动记录批准，作答时重读账本（返回的是现状，不是写入返回值）")
    void confirmDelegatesAndReReadsLedger() {
        AigTaskStep approved = flowStep(2L, "HUMAN_CONFIRM", "approve", "SUCCEEDED");
        approved.setApprovalRef("appr-1");
        when(stepService.listFlowNodes(TASK_ID)).thenReturn(List.of(approved));

        R<AigTaskFlowNodeVo> result = controller.confirmFlowNode(TASK_ID, 0, "appr-1");

        verify(flowDriver).confirm(TASK_ID, 0, "appr-1");
        assertEquals("appr-1", result.getData().getApprovalRef());
        assertEquals("SUCCEEDED", result.getData().getStatus());
    }

    @Test
    @DisplayName("★ 确认后节点消失（并发删了行）→ 明确报错，而不是回一个空对象")
    void confirmDetectsRowDisappeared() {
        when(stepService.listFlowNodes(TASK_ID)).thenReturn(List.of());

        assertThrows(ServiceException.class, () -> controller.confirmFlowNode(TASK_ID, 0, "appr-1"));
        verify(flowDriver).confirm(TASK_ID, 0, "appr-1");
    }

    @Test
    @DisplayName("★ 两个入口的权限点：读用 aig:task:query，确认用 aig:task:operate")
    void endpointsRequireExpectedPermissions() throws Exception {
        Method list = AigTaskController.class.getMethod("flowNodes", Long.class);
        assertNotNull(list.getAnnotation(GetMapping.class), "流程节点清单必须是 GET");
        assertEquals(AigConstants.PERM_TASK_QUERY,
            list.getAnnotation(SaCheckPermission.class).value()[0]);

        Method confirm = AigTaskController.class.getMethod("confirmFlowNode", Long.class,
            Integer.class, String.class);
        assertNotNull(confirm.getAnnotation(PostMapping.class), "人工确认必须是 POST");
        assertEquals(AigConstants.PERM_TASK_OPERATE,
            confirm.getAnnotation(SaCheckPermission.class).value()[0]);
    }

    @Test
    @DisplayName("确认接口不做自己的状态迁移：只调驱动（迁移判据只有一处实现）")
    void confirmDoesNotTouchStepServiceDirectly() {
        when(stepService.listFlowNodes(TASK_ID)).thenReturn(List.of(
            flowStep(2L, "HUMAN_CONFIRM", "approve", "RUNNING")));

        controller.confirmFlowNode(TASK_ID, 0, "appr-1");

        verify(stepService, never()).finishStep(any(), any(), any(), any(), any(), any());
        verify(stepService, never()).startStep(any(), any());
    }

}
