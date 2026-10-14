package org.dromara.aigov.task.service;

import org.dromara.aigov.task.enums.AigTaskStepStatusEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 任务步骤状态迁移判据测试（设计 §8.2）。
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigTaskStepTransitionTest {

    @Test
    @DisplayName("合法边：已编排 → 执行中 / 跳过；执行中 → 成功 / 失败 / 跳过")
    void allowedEdges() {
        assertTrue(AigTaskStepTransition.canMove(
            AigTaskStepStatusEnum.PLANNED, AigTaskStepStatusEnum.RUNNING));
        assertTrue(AigTaskStepTransition.canMove(
            AigTaskStepStatusEnum.PLANNED, AigTaskStepStatusEnum.SKIPPED), "条件不满足可直接跳过");
        assertTrue(AigTaskStepTransition.canMove(
            AigTaskStepStatusEnum.RUNNING, AigTaskStepStatusEnum.SUCCEEDED));
        assertTrue(AigTaskStepTransition.canMove(
            AigTaskStepStatusEnum.RUNNING, AigTaskStepStatusEnum.FAILED));
        assertTrue(AigTaskStepTransition.canMove(
            AigTaskStepStatusEnum.RUNNING, AigTaskStepStatusEnum.SKIPPED));
    }

    @Test
    @DisplayName("★ 终态不再出边：不能把失败改成成功，也不能把成功改回执行中")
    void terminalStatesDoNotMove() {
        for (AigTaskStepStatusEnum terminal : new AigTaskStepStatusEnum[] {
            AigTaskStepStatusEnum.SUCCEEDED, AigTaskStepStatusEnum.FAILED,
            AigTaskStepStatusEnum.SKIPPED}) {
            for (AigTaskStepStatusEnum to : AigTaskStepStatusEnum.values()) {
                assertFalse(AigTaskStepTransition.canMove(terminal, to),
                    terminal.getCode() + " → " + to.getCode() + " 不应允许");
            }
        }
        assertTrue(AigTaskStepStatusEnum.FAILED.isTerminal());
        assertTrue(AigTaskStepStatusEnum.SUCCEEDED.isTerminal());
        assertTrue(AigTaskStepStatusEnum.SKIPPED.isTerminal());
        assertFalse(AigTaskStepStatusEnum.RUNNING.isTerminal());
    }

    @Test
    @DisplayName("★ 不能从「已编排」直接到成功/失败（跳过执行中会掩盖「有没有真跑」）")
    void cannotFinishWithoutRunning() {
        assertFalse(AigTaskStepTransition.canMove(
            AigTaskStepStatusEnum.PLANNED, AigTaskStepStatusEnum.SUCCEEDED));
        assertFalse(AigTaskStepTransition.canMove(
            AigTaskStepStatusEnum.PLANNED, AigTaskStepStatusEnum.FAILED));
    }

    @Test
    @DisplayName("空值安全：from/to 为空一律不允许")
    void nullSafe() {
        assertFalse(AigTaskStepTransition.canMove(null, AigTaskStepStatusEnum.RUNNING));
        assertFalse(AigTaskStepTransition.canMove(AigTaskStepStatusEnum.PLANNED, null));
    }

}
