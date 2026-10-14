package org.dromara.aigov.task.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.aigov.task.config.AigSubAgentReconcileProperties;
import org.dromara.aigov.task.domain.AigTaskStep;
import org.dromara.aigov.task.mapper.AigTaskStepMapper;
import org.dromara.aigov.task.service.IAigSubAgentRunner;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 子 Agent 对账作业测试（设计 §6.5）。
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigSubAgentReconcileServiceImplTest {

    private AigTaskStepMapper stepMapper;
    private IAigSubAgentRunner runner;
    private AigSubAgentReconcileServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, AigTaskStep.class);
    }

    @BeforeEach
    void setUp() {
        stepMapper = mock(AigTaskStepMapper.class);
        runner = mock(IAigSubAgentRunner.class);
        service = new AigSubAgentReconcileServiceImpl(stepMapper, runner,
            new AigSubAgentReconcileProperties());
    }

    private static AigTaskStep step(Long taskId) {
        AigTaskStep step = new AigTaskStep();
        step.setTaskId(taskId);
        step.setKind("SUB_AGENT");
        step.setStatus("RUNNING");
        return step;
    }

    @Test
    @DisplayName("★ 按父任务去重后逐个对账，返回累计收尾数")
    void sweepsDistinctParents() {
        when(stepMapper.selectList(any())).thenReturn(List.of(step(7L), step(7L), step(8L)));
        when(runner.reconcile(7L)).thenReturn(2);
        when(runner.reconcile(8L)).thenReturn(1);

        assertEquals(3, service.sweep());
        verify(runner).reconcile(eq(7L));
        verify(runner).reconcile(eq(8L));
    }

    @Test
    @DisplayName("没有执行中的子 Agent 步骤：不做任何对账")
    void nothingToReconcile() {
        when(stepMapper.selectList(any())).thenReturn(List.of());
        assertEquals(0, service.sweep());
    }

    @Test
    @DisplayName("★ 单个父任务失败不影响其它父任务（对账可重复，下一轮还会看到它）")
    void oneFailureDoesNotStopOthers() {
        when(stepMapper.selectList(any())).thenReturn(List.of(step(7L), step(8L)));
        when(runner.reconcile(7L)).thenThrow(new IllegalStateException("boom"));
        when(runner.reconcile(8L)).thenReturn(1);

        assertEquals(1, service.sweep());
        verify(runner).reconcile(eq(8L));
    }

}
