package org.dromara.aigov.task.service;

import org.dromara.aigov.execution.AigExecutionPlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 子 Agent 派发规格测试（设计 §6.5、§7.2）。
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigSubAgentDispatchTest {

    private static AigExecutionPlan plan(List<Long> subAgents) {
        return AigExecutionPlan.builder().taskId(1L).subAgentVersionIds(subAgents).build();
    }

    @Test
    @DisplayName("无子 Agent / 无父任务：不派发")
    void nothingToDispatch() {
        assertTrue(AigSubAgentDispatch.plan(1L, "creative", "INTERNAL", plan(List.of())).isEmpty());
        assertTrue(AigSubAgentDispatch.plan(1L, "creative", "INTERNAL", null).isEmpty());
        assertTrue(AigSubAgentDispatch.plan(null, "creative", "INTERNAL", plan(List.of(501L))).isEmpty());
    }

    @Test
    @DisplayName("★ 顺序与计划一致，序号从 1 开始（与步骤账本对齐）")
    void preservesOrderAndSequence() {
        List<AigSubAgentDispatch.ChildSpec> specs =
            AigSubAgentDispatch.plan(7L, "creative", "INTERNAL", plan(List.of(501L, 502L, 503L)));

        assertEquals(3, specs.size());
        assertEquals(501L, specs.get(0).agentVersionId());
        assertEquals(1, specs.get(0).sequence());
        assertEquals(503L, specs.get(2).agentVersionId());
        assertEquals(3, specs.get(2).sequence());
    }

    @Test
    @DisplayName("★ 幂等键稳定且按步骤唯一（父任务重试不得建出第二套子任务）")
    void idempotencyKeysAreStableAndUnique() {
        List<AigSubAgentDispatch.ChildSpec> first =
            AigSubAgentDispatch.plan(7L, "creative", "INTERNAL", plan(List.of(501L, 502L)));
        List<AigSubAgentDispatch.ChildSpec> second =
            AigSubAgentDispatch.plan(7L, "creative", "INTERNAL", plan(List.of(501L, 502L)));

        assertEquals("sub:7:1", first.get(0).idempotencyKey());
        assertEquals("sub:7:2", first.get(1).idempotencyKey());
        assertEquals(first.get(0).idempotencyKey(), second.get(0).idempotencyKey(), "同一父任务+步骤必须同键");
        assertEquals(2, first.stream().map(AigSubAgentDispatch.ChildSpec::idempotencyKey).distinct().count());
    }

    @Test
    @DisplayName("★ 业务域与数据等级继承父任务（子任务不得降低等级）")
    void inheritsProjectTypeAndDataLevel() {
        List<AigSubAgentDispatch.ChildSpec> specs =
            AigSubAgentDispatch.plan(9L, "creative", "STRICT", plan(List.of(501L)));

        assertEquals("creative", specs.get(0).projectType());
        assertEquals("STRICT", specs.get(0).dataLevel(), "子任务等级不能低于父任务");
        assertEquals(9L, specs.get(0).parentTaskId());
    }

}
