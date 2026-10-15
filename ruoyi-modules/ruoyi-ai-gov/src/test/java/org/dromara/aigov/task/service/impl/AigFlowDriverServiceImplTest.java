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
import static org.mockito.Mockito.doAnswer;
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
        return step(id, kind, status, null, null);
    }

    private static AigTaskStep step(long id, String kind, AigTaskStepStatusEnum status,
                                    String nodeType, String nodeRef) {
        AigTaskStep step = new AigTaskStep();
        step.setStepId(id);
        step.setTaskId(TASK_ID);
        step.setKind(kind);
        step.setStatus(status.getCode());
        step.setAttempt(1);
        step.setNodeType(nodeType);
        step.setNodeRef(nodeRef);
        return step;
    }

    private static AigTaskStep flowStep(long id, AigTaskStepStatusEnum status, String nodeType,
                                        String nodeRef) {
        return step(id, AigTaskStepKindEnum.FLOW_NODE.getCode(), status, nodeType, nodeRef);
    }

    /**
     * 打桩：listFlowNodes 返回给定步骤；startStep/finishStep 返回按目标状态改写后的步骤。
     */
    private void stub(List<AigTaskStep> steps) {
        when(stepService.listFlowNodes(TASK_ID)).thenReturn(new ArrayList<>(steps));
        // ★ 用 doAnswer/when 而不是 when(...).thenAnswer(...)：后者在"同一个测试里重复打桩"时
        //   会先执行上一版 answer（占位参数是 null），把一次干净的打桩变成 NullPointerException。
        doAnswer(invocation -> step(invocation.getArgument(0),
            AigTaskStepKindEnum.FLOW_NODE.getCode(), AigTaskStepStatusEnum.RUNNING))
            .when(stepService).startStep(anyLong());
        doAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            AigTaskStepStatusEnum status = invocation.getArgument(1);
            String outputRef = invocation.getArgument(2);
            String approvalRef = invocation.getArgument(3);
            AigTaskStep updated = step(id, AigTaskStepKindEnum.FLOW_NODE.getCode(), status);
            updated.setOutputRef(outputRef);
            updated.setApprovalRef(approvalRef);
            return updated;
        }).when(stepService).finishStep(anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("没有流程节点 → 已完成，不写任何状态")
    void noFlowNodesIsDone() {
        AigFlowDriveResult result = driver.advance(TASK_ID, plan(), Map.of());

        assertEquals(AigFlowRuntime.Action.DONE, result.action());
        assertTrue(result.ended());
        verify(stepService, never()).startStep(anyLong());
        verify(stepService, never()).listFlowNodes(anyLong());
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
    @DisplayName("★ peek 只看不写：人工确认节点不会被挂起（假挂起比不挂起更坏）")
    void peekDoesNotParkHumanConfirmNode() {
        stub(List.of(flowStep(SEQ_FLOW_START, AigTaskStepStatusEnum.PLANNED,
            "HUMAN_CONFIRM", "approve")));

        AigFlowDriveResult peek = driver.peek(TASK_ID, plan(node("HUMAN_CONFIRM", "approve")), Map.of());

        assertEquals(AigFlowRuntime.Action.WAIT_HUMAN, peek.action());
        assertTrue(peek.transitions().isEmpty());
        verify(stepService, never()).startStep(anyLong());
        verify(stepService, never()).finishStep(anyLong(), any(), any(), any(), any(), any());

        // 对照：同一个状态下 advance 会把它挂起（这就是 peek 存在的理由）
        AigFlowDriveResult advanced = driver.advance(TASK_ID,
            plan(node("HUMAN_CONFIRM", "approve")), Map.of());
        assertEquals(AigFlowRuntime.Action.WAIT_HUMAN, advanced.action());
        assertEquals(1, advanced.transitions().size());
        verify(stepService).startStep(SEQ_FLOW_START);
    }

    @Test
    @DisplayName("★ peek 与 advance 对「谁该上」的判定一致（否则尾部推进的探路会误导）")
    void peekAgreesWithAdvanceOnNextAction() {
        stub(List.of(flowStep(SEQ_FLOW_START, AigTaskStepStatusEnum.PLANNED, "OUTPUT", "out")));

        AigFlowDriveResult peek = driver.peek(TASK_ID, plan(node("OUTPUT", "out")), Map.of());

        assertEquals(AigFlowRuntime.Action.START, peek.action());
        assertEquals("OUTPUT", peek.nodeType());
        assertEquals(SEQ_FLOW_START, peek.stepId());
        assertFalse(peek.needsExecution(), "输出节点由驱动收尾，不需要调用方执行");
        verify(stepService, never()).startStep(anyLong());
    }

    @Test
    @DisplayName("没有流程节点：peek 与 advance 一样直接给已完成")
    void peekWithoutFlowNodesIsDone() {
        AigFlowDriveResult peek = driver.peek(TASK_ID, plan(), Map.of());

        assertEquals(AigFlowRuntime.Action.DONE, peek.action());
        verify(stepService, never()).listFlowNodes(anyLong());
    }

    @Test
    @DisplayName("★ 恢复：confirm 先置执行中再收尾成功，并写入批准引用（校验靠账本里的节点身份）")
    void confirmStartsThenSettles() {
        stub(List.of(flowStep(SEQ_FLOW_START, AigTaskStepStatusEnum.PLANNED,
            "HUMAN_CONFIRM", "approve")));

        AigTaskStep done = driver.confirm(TASK_ID, 0, "appr-1");

        assertEquals(AigTaskStepStatusEnum.SUCCEEDED.getCode(), done.getStatus());
        assertEquals("appr-1", done.getApprovalRef());
        verify(stepService).startStep(SEQ_FLOW_START);
        verify(stepService).finishStep(eq(SEQ_FLOW_START), eq(AigTaskStepStatusEnum.SUCCEEDED),
            isNull(), eq("appr-1"), any(), any());
    }

    @Test
    @DisplayName("★ 人工确认必须带批准引用（没有凭据的确认不可追溯）")
    void confirmRequiresApprovalRef() {
        assertThrows(ServiceException.class, () -> driver.confirm(TASK_ID, 0, "  "));
        verify(stepService, never()).finishStep(anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("★ 非人工确认节点不接受批准（否则等于用一句确认跳过任意节点）")
    void confirmRejectsNonHumanNode() {
        stub(List.of(flowStep(SEQ_FLOW_START, AigTaskStepStatusEnum.PLANNED, "STEP", "a")));

        assertThrows(ServiceException.class, () -> driver.confirm(TASK_ID, 0, "appr-1"));
        verify(stepService, never()).finishStep(anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("★ 步骤没有登记节点身份时拒绝盲批（存量行不能靠猜类型放行）")
    void confirmRejectsStepWithoutIdentity() {
        stub(List.of(step(SEQ_FLOW_START, AigTaskStepKindEnum.FLOW_NODE.getCode(),
            AigTaskStepStatusEnum.PLANNED)));

        assertThrows(ServiceException.class, () -> driver.confirm(TASK_ID, 0, "appr-1"));
        verify(stepService, never()).finishStep(anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("★ confirm 下标越界 / 没有流程节点 → 可读拒绝，不落任何状态")
    void confirmRejectsBadIndex() {
        stub(List.of());

        assertThrows(ServiceException.class, () -> driver.confirm(TASK_ID, 0, "appr-1"));

        stub(List.of(flowStep(SEQ_FLOW_START, AigTaskStepStatusEnum.PLANNED,
            "HUMAN_CONFIRM", "approve")));
        assertThrows(ServiceException.class, () -> driver.confirm(TASK_ID, 3, "appr-1"));
        verify(stepService, never()).finishStep(anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("★ 重复确认同一个节点：返回既有凭据，不覆盖「谁批的」")
    void confirmIsIdempotentOnTerminalStep() {
        AigTaskStep approved = flowStep(SEQ_FLOW_START, AigTaskStepStatusEnum.SUCCEEDED,
            "HUMAN_CONFIRM", "approve");
        approved.setApprovalRef("appr-first");
        stub(List.of(approved));

        AigTaskStep again = driver.confirm(TASK_ID, 0, "appr-second");

        assertEquals("appr-first", again.getApprovalRef(), "既有的批准引用不能被后来的覆盖");
        verify(stepService, never()).finishStep(anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("★ 账本里的节点身份优先于执行计划（配置在执行后被人改过时以账本为准）")
    void advancePrefersLedgerIdentityOverPlan() {
        stub(List.of(flowStep(SEQ_FLOW_START, AigTaskStepStatusEnum.PLANNED,
            "CONDITION", "fromLedger")));

        // 计划里写的是另一个条件键：若实现读了计划，就会得到 WAIT_CONDITION
        AigFlowDriveResult result = driver.advance(TASK_ID, plan(node("CONDITION", "fromPlan")),
            Map.of("fromLedger", Boolean.TRUE));

        assertEquals(AigFlowRuntime.Action.START, result.action());
        assertEquals("fromLedger", result.nodeRef());
    }

    @Test
    @DisplayName("★ 节点对齐只认步骤服务给出的流程节点（子 Agent 步骤由那层过滤，驱动自己不再过滤）")
    void driverReliesOnStepServiceFlowNodeFiltering() {
        stub(List.of(flowStep(9L, AigTaskStepStatusEnum.PLANNED, "STEP", "a")));

        AigFlowDriveResult result = driver.advance(TASK_ID, plan(node("STEP", "a")), Map.of());

        assertEquals(AigFlowRuntime.Action.START, result.action());
        assertEquals(9L, result.stepId());
        verify(stepService, never()).listSteps(anyLong());
    }

    @Test
    @DisplayName("重试产生的第二个 attempt 不参与对齐：由步骤服务过滤，驱动只按拿到的清单推进")
    void retryAttemptFilteringBelongsToStepService() {
        // 步骤服务只返回首次尝试的那一条（过滤逻辑见 AigTaskStepServiceImplTest）
        stub(List.of(flowStep(SEQ_FLOW_START, AigTaskStepStatusEnum.PLANNED, "STEP", "a")));

        AigFlowDriveResult result = driver.advance(TASK_ID, plan(node("STEP", "a")), Map.of());

        assertEquals(AigFlowRuntime.Action.START, result.action());
        assertEquals(SEQ_FLOW_START, result.stepId());
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
