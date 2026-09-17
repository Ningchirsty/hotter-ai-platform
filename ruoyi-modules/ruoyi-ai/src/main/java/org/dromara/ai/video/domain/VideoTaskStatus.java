package org.dromara.ai.video.domain;

/**
 * 任务状态。与 {@code video_task.status} 列取值一致。
 *
 * <p>状态机：QUEUED → RUNNING → SUCCEEDED / FAILED / TIMEOUT；QUEUED 可被 CANCELED。
 * 终态（SUCCEEDED/FAILED/CANCELED/TIMEOUT）不可再流转。</p>
 */
public enum VideoTaskStatus {

    /**
     * 已入库，等待提交 ComfyUI。
     */
    QUEUED,
    /**
     * 已提交并在 ComfyUI 执行中。
     */
    RUNNING,
    /**
     * 成片已归档并通过实测校验。
     */
    SUCCEEDED,
    /**
     * 契约、网络、ComfyUI 执行或输出校验失败。
     */
    FAILED,
    /**
     * 用户在排队阶段取消。
     */
    CANCELED,
    /**
     * 超过契约 timeoutSeconds 仍未产出。
     */
    TIMEOUT;

    /**
     * 是否为终态。
     */
    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELED || this == TIMEOUT;
    }

    /**
     * 是否允许从当前状态流转到目标状态。
     */
    public boolean canTransitionTo(VideoTaskStatus target) {
        if (this.isTerminal()) {
            return false;
        }
        if (target == null) {
            return false;
        }
        return switch (this) {
            case QUEUED -> target == RUNNING || target == CANCELED || target == FAILED || target == TIMEOUT;
            case RUNNING -> target == SUCCEEDED || target == FAILED || target == TIMEOUT;
            default -> false;
        };
    }
}
