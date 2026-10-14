package org.dromara.aigov.task.service;

import org.dromara.aigov.task.enums.AigTaskStepStatusEnum;

/**
 * 任务步骤状态迁移判据（设计 §8.2）。
 *
 * <p>纯函数、可逐条单测：步骤状态是"哪一步跑没跑成"的唯一记录，迁移写错不会报错——
 * 只会让一个失败步骤看起来成功，或让终态被覆盖（丢掉"它曾经失败过"的事实）。</p>
 *
 * @author ai-gov
 */
public final class AigTaskStepTransition {

    private AigTaskStepTransition() {
    }

    /**
     * 是否允许从 {@code from} 迁移到 {@code to}。
     *
     * <p>合法边：{@code PLANNED → RUNNING}、{@code PLANNED → SKIPPED}（条件不满足直接跳过）、
     * {@code RUNNING → SUCCEEDED/FAILED/SKIPPED}。终态不再出边。</p>
     *
     * @param from 当前状态（可空）
     * @param to   目标状态（可空）
     * @return 允许返回 true
     */
    public static boolean canMove(AigTaskStepStatusEnum from, AigTaskStepStatusEnum to) {
        if (from == null || to == null || from.isTerminal()) {
            return false;
        }
        return switch (from) {
            case PLANNED -> to == AigTaskStepStatusEnum.RUNNING || to == AigTaskStepStatusEnum.SKIPPED;
            case RUNNING -> to == AigTaskStepStatusEnum.SUCCEEDED
                || to == AigTaskStepStatusEnum.FAILED
                || to == AigTaskStepStatusEnum.SKIPPED;
            default -> false;
        };
    }

}
