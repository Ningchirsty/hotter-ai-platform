package org.dromara.aigov.task.service.impl;

import org.dromara.aigov.execution.AigExecutionPlan;
import org.dromara.aigov.task.domain.AigTask;
import org.dromara.aigov.task.domain.AigTaskStep;
import org.dromara.aigov.task.domain.bo.AigTaskCreateBo;
import org.dromara.aigov.task.enums.AigTaskStatusEnum;
import org.dromara.aigov.task.enums.AigTaskStepStatusEnum;
import org.dromara.aigov.task.service.IAigTaskService;
import org.dromara.aigov.task.service.IAigTaskStepService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 子 Agent 运行时驱动测试（设计 §6.5、§7.6）。
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigSubAgentRunnerTest {

    private static final long PARENT_TASK_ID = 7L;

    private IAigTaskService taskService;
    private IAigTaskStepService stepService;
    private AigSubAgentRunner runner;

    @BeforeEach
    void setUp() {
        taskService = mock(IAigTaskService.class);
        stepService = mock(IAigTaskStepService.class);
        runner = new AigSubAgentRunner(taskService, stepService);
    }

    private static AigExecutionPlan plan(List<Long> subAgents) {
        return AigExecutionPlan.builder().taskId(PARENT_TASK_ID).subAgentVersionIds(subAgents).build();
    }

    private static AigTaskStep runningStep(Long stepId, String outputRef) {
        AigTaskStep step = new AigTaskStep();
        step.setStepId(stepId);
        step.setTaskId(PARENT_TASK_ID);
        step.setKind("SUB_AGENT");
        step.setStatus(AigTaskStepStatusEnum.RUNNING.getCode());
        step.setOutputRef(outputRef);
        return step;
    }

    @Test
    @DisplayName("★ 派发：按计划创建子任务并置父步骤执行中（引用子任务）")
    void dispatchCreatesChildrenAndStartsSteps() {
        when(taskService.create(any(AigTaskCreateBo.class))).thenReturn(101L, 102L);
        when(stepService.findBySequence(eq(PARENT_TASK_ID), any(Integer.class)))
            .thenReturn(runningStep(7001L, null), runningStep(7002L, null));

        List<Long> children = runner.dispatchChildren(PARENT_TASK_ID, "TEXT_GENERATION", "creative",
            "STRICT", "{\"k\":1}", null, plan(List.of(501L, 502L)));

        assertEquals(List.of(101L, 102L), children);
        ArgumentCaptor<AigTaskCreateBo> captor = ArgumentCaptor.forClass(AigTaskCreateBo.class);
        verify(taskService, org.mockito.Mockito.times(2)).create(captor.capture());
        AigTaskCreateBo first = captor.getAllValues().get(0);
        assertEquals(501L, first.getAgentVersionId());
        assertEquals("sub:7:1", first.getIdempotencyKey(), "幂等键按父任务+步骤稳定");
        assertEquals("STRICT", first.getDataLevel(), "子任务等级继承父任务");
        verify(stepService).startStep(7001L, "task:101");
        verify(stepService).startStep(7002L, "task:102");
    }

    @Test
    @DisplayName("无子 Agent：不创建任何子任务")
    void dispatchWithoutChildrenDoesNothing() {
        assertTrue(runner.dispatchChildren(PARENT_TASK_ID, "T", "creative", "INTERNAL", "{}", null,
            plan(List.of())).isEmpty());
        verify(taskService, never()).create(any(AigTaskCreateBo.class));
    }

    @Test
    @DisplayName("★ 对账：子任务终态 → 父步骤按终态收尾")
    void reconcileFinishesStepsFromChildStatus() {
        when(stepService.listSteps(PARENT_TASK_ID))
            .thenReturn(List.of(runningStep(7001L, "task:101")));
        AigTask child = new AigTask();
        child.setTaskId(101L);
        child.setStatus(AigTaskStatusEnum.SUCCEEDED.getCode());
        when(taskService.getTask(101L)).thenReturn(child);

        assertEquals(1, runner.reconcile(PARENT_TASK_ID));
        verify(stepService).finishStep(eq(7001L), eq(AigTaskStepStatusEnum.SUCCEEDED),
            eq("task:101"), any(), any(), any());
    }

    @Test
    @DisplayName("★ 对账：子任务仍在跑 → 父步骤保持执行中（不当成失败）")
    void reconcileSkipsNonTerminalChild() {
        when(stepService.listSteps(PARENT_TASK_ID))
            .thenReturn(List.of(runningStep(7001L, "task:101")));
        AigTask child = new AigTask();
        child.setStatus(AigTaskStatusEnum.RUNNING.getCode());
        when(taskService.getTask(101L)).thenReturn(child);

        assertEquals(0, runner.reconcile(PARENT_TASK_ID));
        verify(stepService, never()).finishStep(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("子任务终态映射：成功/已通过/已拒绝 → 成功；失败/已取消 → 失败；其余 → null")
    void childStatusMapping() {
        assertEquals(AigTaskStepStatusEnum.SUCCEEDED,
            AigSubAgentRunner.mapChildStatus(AigTaskStatusEnum.SUCCEEDED));
        assertEquals(AigTaskStepStatusEnum.SUCCEEDED,
            AigSubAgentRunner.mapChildStatus(AigTaskStatusEnum.APPROVED));
        assertEquals(AigTaskStepStatusEnum.SUCCEEDED,
            AigSubAgentRunner.mapChildStatus(AigTaskStatusEnum.REJECTED));
        assertEquals(AigTaskStepStatusEnum.FAILED,
            AigSubAgentRunner.mapChildStatus(AigTaskStatusEnum.FAILED));
        assertEquals(AigTaskStepStatusEnum.FAILED,
            AigSubAgentRunner.mapChildStatus(AigTaskStatusEnum.CANCELLED));
        assertNull(AigSubAgentRunner.mapChildStatus(AigTaskStatusEnum.RUNNING));
        assertNull(AigSubAgentRunner.mapChildStatus(null));
    }

    @Test
    @DisplayName("子任务引用解析：task:123 → 123；非法引用返回 null")
    void childTaskRefParsing() {
        assertEquals(123L, AigSubAgentRunner.childTaskId("task:123"));
        assertNull(AigSubAgentRunner.childTaskId("123"));
        assertNull(AigSubAgentRunner.childTaskId("task:x"));
        assertNull(AigSubAgentRunner.childTaskId(null));
        assertNull(AigSubAgentRunner.childTaskId("  "));
    }

}
