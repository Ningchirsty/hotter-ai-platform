package org.dromara.aigov.task.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.aigov.execution.AigExecutionPlan;
import org.dromara.aigov.execution.AigFlowNode;
import org.dromara.aigov.task.domain.AigTaskStep;
import org.dromara.aigov.task.enums.AigTaskStepStatusEnum;
import org.dromara.aigov.task.mapper.AigTaskStepMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 任务步骤账本测试（设计 §8.2、§6.5）。
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigTaskStepServiceImplTest {

    private static final long TASK_ID = 9001L;

    private AigTaskStepMapper stepMapper;
    private AigTaskStepServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, AigTaskStep.class);
    }

    @BeforeEach
    void setUp() {
        stepMapper = mock(AigTaskStepMapper.class);
        service = new AigTaskStepServiceImpl(stepMapper);
        when(stepMapper.insert(any(AigTaskStep.class))).thenReturn(1);
    }

    private static AigExecutionPlan plan() {
        return AigExecutionPlan.builder()
            .taskId(TASK_ID)
            .inputHash("hash-input")
            .subAgentVersionIds(List.of(501L))
            // ★ 节点身份（类型 + 引用）必须落库：否则任务因"需要人工确认"停下之后，
            //   没人能回答"这个步骤是人工确认还是普通步骤"
            .flowNodes(List.of(new AigFlowNode("STEP", "first", "第一步"),
                new AigFlowNode("OUTPUT", null, null)))
            .build();
    }

    @Test
    @DisplayName("★ 登记编排步骤：子 Agent 一步一版本，流程节点各占一步并记下节点身份，序号连续")
    void recordsPlannedSteps() {
        when(stepMapper.selectCount(any())).thenReturn(0L);

        int created = service.recordPlannedSteps(TASK_ID, plan());

        assertEquals(3, created);
        ArgumentCaptor<AigTaskStep> captor = ArgumentCaptor.forClass(AigTaskStep.class);
        verify(stepMapper, times(3)).insert(captor.capture());
        List<AigTaskStep> steps = captor.getAllValues();

        assertEquals(List.of(1, 2, 3), steps.stream().map(AigTaskStep::getSequence).toList());
        assertEquals("SUB_AGENT", steps.get(0).getKind());
        assertEquals(501L, steps.get(0).getResourceVersionId());
        assertEquals("FLOW_NODE", steps.get(1).getKind());
        assertEquals(null, steps.get(1).getResourceVersionId(), "流程节点不是资源版本");
        assertEquals("STEP", steps.get(1).getNodeType(), "流程节点身份必须落库");
        assertEquals("first", steps.get(1).getNodeRef());
        assertEquals("OUTPUT", steps.get(2).getNodeType());
        assertEquals(null, steps.get(2).getNodeRef(), "无引用节点留空，不臆造");
        assertEquals(null, steps.get(0).getNodeType(), "子 Agent 步骤没有流程节点身份");
        assertEquals("PLANNED", steps.get(0).getStatus());
        assertEquals("hash-input", steps.get(0).getInputHash());
        assertEquals(1, steps.get(0).getAttempt());
    }

    @Test
    @DisplayName("★ 只列流程节点：子 Agent 步骤与重试 attempt 都不参与节点对齐")
    void listFlowNodesFiltersKindAndAttempt() {
        AigTaskStep subAgent = new AigTaskStep();
        subAgent.setKind("SUB_AGENT");
        subAgent.setAttempt(1);
        AigTaskStep node = new AigTaskStep();
        node.setKind("FLOW_NODE");
        node.setAttempt(1);
        node.setNodeType("HUMAN_CONFIRM");
        AigTaskStep retry = new AigTaskStep();
        retry.setKind("FLOW_NODE");
        retry.setAttempt(2);
        when(stepMapper.selectList(any())).thenReturn(List.of(subAgent, node, retry));

        List<AigTaskStep> result = service.listFlowNodes(TASK_ID);

        assertEquals(1, result.size());
        assertEquals("HUMAN_CONFIRM", result.get(0).getNodeType());
    }

    @Test
    @DisplayName("★ 幂等：同一 (task, sequence) 已有记录则不再登记（重复登记会把步骤翻倍）")
    void recordingIsIdempotent() {
        when(stepMapper.selectCount(any())).thenReturn(1L);

        assertEquals(0, service.recordPlannedSteps(TASK_ID, plan()));
        verify(stepMapper, never()).insert(any(AigTaskStep.class));
    }

    @Test
    @DisplayName("空输入安全：taskId/plan 为空不写任何行")
    void nullSafe() {
        assertEquals(0, service.recordPlannedSteps(null, plan()));
        assertEquals(0, service.recordPlannedSteps(TASK_ID, null));
        verify(stepMapper, never()).insert(any(AigTaskStep.class));
    }

    @Test
    @DisplayName("没有子 Agent 也没有流程：不写任何步骤")
    void emptyPlanWritesNothing() {
        when(stepMapper.selectCount(any())).thenReturn(0L);
        assertEquals(0, service.recordPlannedSteps(TASK_ID, AigExecutionPlan.builder().taskId(TASK_ID).build()));
        verify(stepMapper, never()).insert(any(AigTaskStep.class));
    }

    private AigTaskStep step(String status) {
        AigTaskStep step = new AigTaskStep();
        step.setStepId(7001L);
        step.setTaskId(TASK_ID);
        step.setSequence(1);
        step.setStatus(status);
        return step;
    }

    @Test
    @DisplayName("★ 开始步骤：已编排 → 执行中（CAS）")
    void startStepMovesToRunning() {
        when(stepMapper.selectById(7001L)).thenReturn(step("PLANNED"));
        when(stepMapper.update(any(), any())).thenReturn(1);

        AigTaskStep result = service.startStep(7001L);

        assertEquals("RUNNING", result.getStatus());
        verify(stepMapper).update(any(AigTaskStep.class), any());
    }

    @Test
    @DisplayName("开始步骤幂等：已是执行中则不报错也不改")
    void startStepIsIdempotentOnRunning() {
        when(stepMapper.selectById(7001L)).thenReturn(step("RUNNING"));

        assertEquals("RUNNING", service.startStep(7001L).getStatus());
        verify(stepMapper, never()).update(any(AigTaskStep.class), any());
    }

    @Test
    @DisplayName("★ 终态不允许开始（不能把失败/成功改回执行中）")
    void startStepRejectsTerminal() {
        when(stepMapper.selectById(7001L)).thenReturn(step("SUCCEEDED"));
        assertThrows(RuntimeException.class, () -> service.startStep(7001L));
    }

    @Test
    @DisplayName("★ 收尾步骤：执行中 → 成功，落结果引用")
    void finishStepMovesToSucceeded() {
        when(stepMapper.selectById(7001L)).thenReturn(step("RUNNING"));
        when(stepMapper.update(any(), any())).thenReturn(1);

        AigTaskStep result = service.finishStep(7001L, AigTaskStepStatusEnum.SUCCEEDED,
            "oss://out", "approval-1", null, null);

        assertEquals("SUCCEEDED", result.getStatus());
        assertEquals("oss://out", result.getOutputRef());
        assertEquals("approval-1", result.getApprovalRef());
    }

    @Test
    @DisplayName("收尾幂等：已是终态时不覆盖（保留「它曾经失败过」的事实）")
    void finishStepIsIdempotentOnTerminal() {
        when(stepMapper.selectById(7001L)).thenReturn(step("FAILED"));

        assertEquals("FAILED", service.finishStep(7001L, AigTaskStepStatusEnum.SUCCEEDED,
            null, null, null, null).getStatus());
        verify(stepMapper, never()).update(any(AigTaskStep.class), any());
    }

    @Test
    @DisplayName("★ CAS 冲突：状态被并发改过时报可读冲突")
    void finishStepCasConflictThrows() {
        when(stepMapper.selectById(7001L)).thenReturn(step("RUNNING"));
        when(stepMapper.update(any(), any())).thenReturn(0);

        assertThrows(RuntimeException.class, () -> service.finishStep(7001L,
            AigTaskStepStatusEnum.SUCCEEDED, null, null, null, null));
    }

    @Test
    @DisplayName("步骤不存在：可读拒绝")
    void missingStepThrows() {
        when(stepMapper.selectById(7001L)).thenReturn(null);
        assertThrows(RuntimeException.class, () -> service.startStep(7001L));
    }

}
