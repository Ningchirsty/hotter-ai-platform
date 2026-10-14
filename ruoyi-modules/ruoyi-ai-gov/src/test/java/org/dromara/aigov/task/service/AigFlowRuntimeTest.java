package org.dromara.aigov.task.service;

import org.dromara.aigov.execution.AigFlowNode;
import org.dromara.aigov.task.domain.AigTaskStep;
import org.dromara.aigov.task.enums.AigTaskStepStatusEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 流程节点运行时判据测试（设计 §6.5）。
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigFlowRuntimeTest {

    private static AigFlowNode node(String type, String ref) {
        return new AigFlowNode(type, ref, ref);
    }

    private static AigTaskStep step(long id, AigTaskStepStatusEnum status) {
        AigTaskStep step = new AigTaskStep();
        step.setStepId(id);
        step.setStatus(status.getCode());
        return step;
    }

    private static AigTaskStep step(long id, AigTaskStepStatusEnum status, String approvalRef) {
        AigTaskStep step = step(id, status);
        step.setApprovalRef(approvalRef);
        return step;
    }

    @Test
    @DisplayName("空流程视为已完成（调用方不该为它空等）")
    void emptyFlowIsDone() {
        AigFlowRuntime.Decision decision = AigFlowRuntime.decide(List.of(), List.of(), Map.of());
        assertEquals(AigFlowRuntime.Action.DONE, decision.action());
        assertEquals(AigFlowRuntime.NO_NODE, decision.nodeIndex());
    }

    @Test
    @DisplayName("★ 节点与步骤数量不一致 → 拒绝推进（错位的状态会落在别的节点上）")
    void countMismatchIsBlocked() {
        AigFlowRuntime.Decision decision = AigFlowRuntime.decide(
            List.of(node("STEP", "a")), List.of(), Map.of());
        assertEquals(AigFlowRuntime.Action.BLOCKED, decision.action());
        assertEquals(AigFlowRuntime.NO_NODE, decision.nodeIndex());
    }

    @Test
    @DisplayName("★ 上游失败 → 整个流程停止（不跳过失败继续跑）")
    void failedUpstreamBlocksFlow() {
        AigFlowRuntime.Decision decision = AigFlowRuntime.decide(
            List.of(node("STEP", "a"), node("OUTPUT", "out")),
            List.of(step(1L, AigTaskStepStatusEnum.FAILED), step(2L, AigTaskStepStatusEnum.PLANNED)),
            Map.of());
        assertEquals(AigFlowRuntime.Action.BLOCKED, decision.action());
        assertEquals(0, decision.nodeIndex(), "要指出失败的是第几个节点");
    }

    @Test
    @DisplayName("步骤状态不可识别 → 拒绝推进（状态字面量不能当成功）")
    void unknownStepStatusIsBlocked() {
        AigTaskStep broken = step(1L, AigTaskStepStatusEnum.PLANNED);
        broken.setStatus("SOMETHING_ELSE");
        AigFlowRuntime.Decision decision = AigFlowRuntime.decide(
            List.of(node("STEP", "a")), List.of(broken), Map.of());
        assertEquals(AigFlowRuntime.Action.BLOCKED, decision.action());
    }

    @Test
    @DisplayName("★ 人工确认：未批准则等待（前面已成功的节点先被跳过）")
    void humanConfirmWaitsUntilApproved() {
        AigFlowRuntime.Decision decision = AigFlowRuntime.decide(
            List.of(node("STEP", "a"), node("HUMAN_CONFIRM", "approve"), node("OUTPUT", "out")),
            List.of(step(1L, AigTaskStepStatusEnum.SUCCEEDED),
                step(2L, AigTaskStepStatusEnum.PLANNED),
                step(3L, AigTaskStepStatusEnum.PLANNED)),
            Map.of());
        assertEquals(AigFlowRuntime.Action.WAIT_HUMAN, decision.action());
        assertEquals(1, decision.nodeIndex());
        assertEquals("approve", decision.nodeRef());
    }

    @Test
    @DisplayName("★ 人工确认：批准已落库（approval_ref 非空）→ 收尾并继续")
    void humanConfirmApprovedContinues() {
        AigFlowRuntime.Decision decision = AigFlowRuntime.decide(
            List.of(node("HUMAN_CONFIRM", "approve")),
            List.of(step(2L, AigTaskStepStatusEnum.RUNNING, "approval-9")),
            Map.of());
        assertEquals(AigFlowRuntime.Action.START, decision.action());
    }

    @Test
    @DisplayName("★ 条件判断：没有结论就等（不默认取真或假），且原因里说明结论从哪来")
    void conditionWithoutResultWaits() {
        AigFlowRuntime.Decision decision = AigFlowRuntime.decide(
            List.of(node("CONDITION", "needVideo")),
            List.of(step(1L, AigTaskStepStatusEnum.PLANNED)),
            Map.of());
        assertEquals(AigFlowRuntime.Action.WAIT_CONDITION, decision.action());
        assertEquals("needVideo", decision.nodeRef());
        assertTrue(decision.reason().contains("冻结输入快照"),
            "拒绝原因必须说清结论从哪来，否则操作者不知道该找谁：" + decision.reason());
    }

    @Test
    @DisplayName("条件为真 → 通过该节点")
    void conditionTruePasses() {
        AigFlowRuntime.Decision decision = AigFlowRuntime.decide(
            List.of(node("CONDITION", "needVideo")),
            List.of(step(1L, AigTaskStepStatusEnum.PLANNED)),
            Map.of("needVideo", Boolean.TRUE));
        assertEquals(AigFlowRuntime.Action.START, decision.action());
    }

    @Test
    @DisplayName("★ 条件为假 → 本节点及其后节点全部跳过（首期只有「门」语义）")
    void conditionFalseSkipsRest() {
        AigFlowRuntime.Decision decision = AigFlowRuntime.decide(
            List.of(node("STEP", "a"), node("CONDITION", "needVideo"), node("OUTPUT", "out")),
            List.of(step(1L, AigTaskStepStatusEnum.SUCCEEDED),
                step(2L, AigTaskStepStatusEnum.PLANNED),
                step(3L, AigTaskStepStatusEnum.PLANNED)),
            Map.of("needVideo", Boolean.FALSE));
        assertEquals(AigFlowRuntime.Action.SKIP_REST, decision.action());
        assertEquals(1, decision.nodeIndex());
    }

    @Test
    @DisplayName("已跳过的节点视为「已过掉」，继续判定后面的节点")
    void skippedNodeIsPassed() {
        AigFlowRuntime.Decision decision = AigFlowRuntime.decide(
            List.of(node("CONDITION", "gate"), node("OUTPUT", "out")),
            List.of(step(1L, AigTaskStepStatusEnum.SKIPPED),
                step(2L, AigTaskStepStatusEnum.PLANNED)),
            Map.of());
        assertEquals(AigFlowRuntime.Action.START, decision.action());
        assertEquals(1, decision.nodeIndex());
    }

    @Test
    @DisplayName("STEP 节点执行中 → 等待其收尾（不重复启动）")
    void runningStepWaits() {
        AigFlowRuntime.Decision decision = AigFlowRuntime.decide(
            List.of(node("STEP", "a")),
            List.of(step(1L, AigTaskStepStatusEnum.RUNNING)),
            Map.of());
        assertEquals(AigFlowRuntime.Action.WAIT_EXECUTION, decision.action());
    }

    @Test
    @DisplayName("输出节点 → 收尾并结束")
    void outputNodeIsSettled() {
        AigFlowRuntime.Decision decision = AigFlowRuntime.decide(
            List.of(node("STEP", "a"), node("OUTPUT", "out")),
            List.of(step(1L, AigTaskStepStatusEnum.SUCCEEDED),
                step(2L, AigTaskStepStatusEnum.PLANNED)),
            Map.of());
        assertEquals(AigFlowRuntime.Action.START, decision.action());
        assertEquals(1, decision.nodeIndex());
    }

    @Test
    @DisplayName("全部节点终态 → 完成")
    void allTerminalIsDone() {
        AigFlowRuntime.Decision decision = AigFlowRuntime.decide(
            List.of(node("STEP", "a"), node("OUTPUT", "out")),
            List.of(step(1L, AigTaskStepStatusEnum.SUCCEEDED),
                step(2L, AigTaskStepStatusEnum.SUCCEEDED)),
            Map.of());
        assertEquals(AigFlowRuntime.Action.DONE, decision.action());
    }

    @Test
    @DisplayName("★ 节点类型不可识别 → 拒绝推进（不做猜测式跳过）")
    void unknownNodeTypeIsBlocked() {
        AigFlowRuntime.Decision decision = AigFlowRuntime.decide(
            List.of(node("PARALLEL", "x")),
            List.of(step(1L, AigTaskStepStatusEnum.PLANNED)),
            Map.of());
        assertEquals(AigFlowRuntime.Action.BLOCKED, decision.action());
    }

    @Test
    @DisplayName("条件结论按节点 ref 取：别的条件的结论不能顶替它")
    void conditionResultIsKeyedByRef() {
        Map<String, Boolean> results = new HashMap<>();
        results.put("other", Boolean.TRUE);
        AigFlowRuntime.Decision decision = AigFlowRuntime.decide(
            List.of(node("CONDITION", "needVideo")),
            List.of(step(1L, AigTaskStepStatusEnum.PLANNED)),
            results);
        assertEquals(AigFlowRuntime.Action.WAIT_CONDITION, decision.action(),
            "别的条件的结论不能顶替这一个");
    }

}
