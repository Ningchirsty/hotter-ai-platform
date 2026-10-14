package org.dromara.aigov.task.service.impl;

import org.dromara.aigov.execution.AigExecutionPlan;
import org.dromara.aigov.execution.AigFlowNode;
import org.dromara.aigov.task.domain.AigTaskStep;
import org.dromara.aigov.task.enums.AigTaskStepKindEnum;
import org.dromara.aigov.task.enums.AigTaskStepStatusEnum;
import org.dromara.aigov.task.service.AigFlowDriveResult;
import org.dromara.aigov.task.service.AigFlowRuntime;
import org.dromara.aigov.task.service.IAigTaskStepService;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 流程节点运行时驱动测试（设计 §6.5）。
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigFlowDriverServiceImplTest {

    private static final Long TASK_ID = 77L;
    private static final Long SEQ_FLOW_START = 11L;

    private IAigTaskStepService stepService;
    private AigFlowDriverServiceImpl driver;

    @BeforeEach
    void setUp() {
        stepService = mock(IAigTaskStepService.class);
        driver = new AigFlowDriverServiceImpl(stepService);
    }

    private static AigExecutionPlan plan(AigFlowNode... nodes) {
        return AigExecutionPlan.builder()
            .taskId(TASK_ID)
            .flowNodes(List.of(nodes))
            .build();
    }

    private static AigFlowNode node(String type, String ref) {
        return new AigFlowNode(type, ref, ref);
    }

    private static AigTaskStep step(long id, String kind, AigTaskStepStatusEnum status) {
        AigTaskStep step = new AigTaskStep();
        step.setStepId(id);
        step.setKind(kind);
        step.setStatus(status.getCode());
        step.setAttempt(1);
        return step;
    }

    /**
     * 打桩：listSteps 返回给定步骤；startStep/finishStep 返回按目标状态改写后的步骤。
     */
    private void stub(List<AigTaskStep> steps) {
        when(stepService.listSteps(TASK_ID)).thenReturn(new ArrayList<>(steps));
        when(stepService.startStep(anyLong())).thenAnswer(invocation -> {
            AigTaskStep updated = step(invocation.getArgument(0), AigTaskStepKindEnum.FLOW_NODE.getCode(),
                AigTaskStepStatusEnum.RUNNING);
            return updated;
        });
        when(stepService.finishStep(anyLong(), any(), any(), any(), any(), any()))
            .thenAnswer(invocation -> {
                Long id = invocation.getArgument(0);
                AigTaskStepStatusEnum status = invocation.getArgument(1);
                String outputRef = invocation.getArgument(2);
                String approvalRef = invocation.getArgument(3);
                AigTaskStep updated = step(id, AigTaskStepKindEnum.FLOW_NODE.getCode(), status);
                updated.setOutputRef(outputRef);
                updated.setApprovalRef(approvalRef);
                return updated;
            });
    }

    @Test
    @DisplayName("没有流程节点 → 已完成，不写任何状态")
    void noFlowNodesIsDone() {
        AigFlowDriveResult result = driver.advance(TASK_ID, plan(), Map.of());

        assertEquals(AigFlowRuntime.Action.DONE, result.action());
        assertTrue(result.ended());
        verify(stepService, never()).startStep(anyLong());
        verify(stepService, never()).listSteps(anyLong());
    }

    @Test
    @DisplayName("★ STEP 节点：驱动把它置为执行中，并告诉调用方「该你去跑了」")
    void stepNodeIsStartedForCaller() {
        stub(List.of(step(SEQ_FLOW_START, AigTaskStepKindEnum.FLOW_NODE.getCode(),
            AigTaskStepStatusEnum.PLANNED)));

        AigFlowDriveResult result = driver.advance(TASK_ID, plan(node("STEP", "first")), Map.of());

        assertEquals(AigFlowRuntime.Action.START, result.action());
        assertEquals("STEP", result.nodeType());
        assertTrue(result.needsExecution());
        assertEquals(SEQ_FLOW_START, result.stepId());
        assertEquals(1, result.transitions().size());
        verify(stepService).startStep(SEQ_FLOW_START);
        // STEP 节点由调用方跑完后收尾：驱动不得替它写 SUCCEEDED
        verify(stepService, never()).finishStep(anyLong(), eq(AigTaskStepStatusEnum.SUCCEEDED),
            any(), any(), any(), any());
    }

    @Test
    @DisplayName("★ 条件为真：驱动就地通过该节点，输出引用写明结论")
    void conditionTrueIsSettled() {
        stub(List.of(step(SEQ_FLOW_START, AigTaskStepKindEnum.FLOW_NODE.getCode(),
            AigTaskStepStatusEnum.PLANNED)));

        AigFlowDriveResult result = driver.advance(TASK_ID, plan(node("CONDITION", "needVideo")),
            Map.of("needVideo", Boolean.TRUE));

        assertEquals(AigFlowRuntime.Action.START, result.action());
        assertFalse(result.needsExecution(), "条件节点由驱动收尾，不需要调用方执行");
        verify(stepService).finishStep(eq(SEQ_FLOW_START), eq(AigTaskStepStatusEnum.SUCCEEDED),
            eq("condition:true"), any(), any(), any());
    }

    @Test
    @DisplayName("★ 条件为假：本节点与其后节点全部跳过（后续节点不会被静默留在 PLANNED）")
    void conditionFalseSkipsNodeAndRest() {
        stub(List.of(
            step(10L, AigTaskStepKindEnum.FLOW_NODE.getCode(), AigTaskStepStatusEnum.SUCCEEDED),
            step(11L, AigTaskStepKindEnum.FLOW_NODE.getCode(), AigTaskStepStatusEnum.PLANNED),
            step(12L, AigTaskStepKindEnum.FLOW_NODE.getCode(), AigTaskStepStatusEnum.PLANNED)));

        AigFlowDriveResult result = driver.advance(TASK_ID,
            plan(node("STEP", "a"), node("CONDITION", "needVideo"), node("OUTPUT", "out")),
            Map.of("needVideo", Boolean.FALSE));

        assertEquals(AigFlowRuntime.Action.SKIP_REST, result.action());
        assertEquals(2, result.transitions().size(), "条件节点与后一个节点都要跳过");
        verify(stepService).finishStep(eq(11L), eq(AigTaskStepStatusEnum.SKIPPED),
            eq("condition:false"), any(), any(), any());
        verify(stepService).finishStep(eq(12L), eq(AigTaskStepStatusEnum.SKIPPED),
            isNull(), any(), any(), any());
    }

    @Test
    @DisplayName("★ 人工确认未批准：节点被挂起到 RUNNING，等待批准")
    void humanConfirmIsParked() {
        stub(List.of(step(SEQ_FLOW_START, AigTaskStepKindEnum.FLOW_NODE.getCode(),
            AigTaskStepStatusEnum.PLANNED)));

        AigFlowDriveResult result = driver.advance(TASK_ID, plan(node("HUMAN_CONFIRM", "approve")),
            Map.of());

        assertEquals(AigFlowRuntime.Action.WAIT_HUMAN, result.action());
        assertTrue(result.waiting());
        verify(stepService).startStep(SEQ_FLOW_START);
        verify(stepService, never()).finishStep(anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("★ 人工确认已批准：驱动收尾该节点并把批准引用写进步骤")
    void humanConfirmSettlesWithApproval() {
        AigTaskStep parked = step(SEQ_FLOW_START, AigTaskStepKindEnum.FLOW_NODE.getCode(),
            AigTaskStepStatusEnum.RUNNING);
        parked.setApprovalRef("approval-9");
        stub(List.of(parked));

        AigFlowDriveResult result = driver.advance(TASK_ID, plan(node("HUMAN_CONFIRM", "approve")),
            Map.of());

        assertEquals(AigFlowRuntime.Action.START, result.action());
        verify(stepService).finishStep(eq(SEQ_FLOW_START), eq(AigTaskStepStatusEnum.SUCCEEDED),
            isNull(), eq("approval-9"), any(), any());
    }

    @Test
    @DisplayName("★ 恢复：confirm 先置执行中再收尾成功，并写入批准引用")
    void confirmStartsThenSettles() {
        stub(List.of(step(SEQ_FLOW_START, AigTaskStepKindEnum.FLOW_NODE.getCode(),
            AigTaskStepStatusEnum.PLANNED)));

        AigTaskStep done = driver.confirm(TASK_ID, plan(node("HUMAN_CONFIRM", "approve")),
            0, "appr-1");

        assertEquals(AigTaskStepStatusEnum.SUCCEEDED.getCode(), done.getStatus());
        assertEquals("appr-1", done.getApprovalRef());
        verify(stepService).startStep(SEQ_FLOW_START);
        verify(stepService).finishStep(eq(SEQ_FLOW_START), eq(AigTaskStepStatusEnum.SUCCEEDED),
            isNull(), eq("appr-1"), any(), any());
    }

    @Test
    @DisplayName("★ 人工确认必须带批准引用（没有凭据的确认不可追溯）")
    void confirmRequiresApprovalRef() {
        assertThrows(ServiceException.class,
            () -> driver.confirm(TASK_ID, plan(node("HUMAN_CONFIRM", "approve")), 0, "  "));
        verify(stepService, never()).finishStep(anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("★ 非人工确认节点不接受批准（否则等于用一句确认跳过任意节点）")
    void confirmRejectsNonHumanNode() {
        stub(List.of(step(SEQ_FLOW_START, AigTaskStepKindEnum.FLOW_NODE.getCode(),
            AigTaskStepStatusEnum.PLANNED)));

        assertThrows(ServiceException.class,
            () -> driver.confirm(TASK_ID, plan(node("STEP", "a")), 0, "appr-1"));
        verify(stepService, never()).finishStep(anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("★ confirm 遇节点与步骤数量不一致 → 拒绝（错位的批准会批错节点）")
    void confirmRejectsMismatchedSteps() {
        stub(List.of());

        assertThrows(ServiceException.class,
            () -> driver.confirm(TASK_ID, plan(node("HUMAN_CONFIRM", "approve")), 0, "appr-1"));
        verify(stepService, never()).finishStep(anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("★ 只按 FLOW_NODE 步骤驱动：子 Agent 步骤不参与节点对齐")
    void subAgentStepsAreNotAligned() {
        stub(List.of(
            step(1L, AigTaskStepKindEnum.SUB_AGENT.getCode(), AigTaskStepStatusEnum.SUCCEEDED),
            step(2L, AigTaskStepKindEnum.FLOW_NODE.getCode(), AigTaskStepStatusEnum.PLANNED)));

        AigFlowDriveResult result = driver.advance(TASK_ID, plan(node("STEP", "a")), Map.of());

        assertEquals(AigFlowRuntime.Action.START, result.action());
        assertEquals(2L, result.stepId());
    }

    @Test
    @DisplayName("重试产生的第二个 attempt 不参与流程驱动")
    void retryAttemptIsIgnored() {
        AigTaskStep retry = step(9L, AigTaskStepKindEnum.FLOW_NODE.getCode(),
            AigTaskStepStatusEnum.PLANNED);
        retry.setAttempt(2);
        stub(List.of(retry));

        AigFlowDriveResult result = driver.advance(TASK_ID, plan(node("STEP", "a")), Map.of());

        assertEquals(AigFlowRuntime.Action.BLOCKED, result.action(),
            "只剩重试记录时应视为「首次尝试的步骤缺失」，而不是拿重试记录驱动");
    }

    @Test
    @DisplayName("节点执行收尾：成功写 SUCCEEDED，失败写 FAILED（不覆盖已成事实的终态）")
    void settleMapsOutcome() {
        AigTaskStep ok = step(1L, AigTaskStepKindEnum.FLOW_NODE.getCode(),
            AigTaskStepStatusEnum.SUCCEEDED);
        AigTaskStep bad = step(2L, AigTaskStepKindEnum.FLOW_NODE.getCode(),
            AigTaskStepStatusEnum.FAILED);
        when(stepService.finishStep(eq(1L), eq(AigTaskStepStatusEnum.SUCCEEDED), eq("trace:1"),
            any(), any(), any())).thenReturn(ok);
        when(stepService.finishStep(eq(2L), eq(AigTaskStepStatusEnum.FAILED), any(),
            any(), eq("TOOL_ERROR"), any())).thenReturn(bad);

        assertEquals("SUCCEEDED", driver.settle(1L, true, "trace:1", null, null).getStatus());
        assertEquals("FAILED", driver.settle(2L, false, null, "TOOL_ERROR", "调用失败").getStatus());
        assertThrows(ServiceException.class, () -> driver.settle(null, true, null, null, null));
    }

}
