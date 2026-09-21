package org.dromara.ai.image.domain;

/**
 * 图像任务状态机。
 *
 * <p>与视频任务保持同一套语义：{@code QUEUED → RUNNING → 终态}，终态不可再流转。
 * 状态推进全部通过带 {@code expectedFrom} 条件的 SQL 完成，因此并发下不会出现
 * 「两个线程同时把 QUEUED 改成 RUNNING」。</p>
 */
public enum ImageTaskStatus {

    /**
     * 已入库，等待执行。
     */
    QUEUED,
    /**
     * 已被认领，正在调用 ComfyUI。
     */
    RUNNING,
    /**
     * 成功，产出已归档。
     */
    SUCCEEDED,
    /**
     * 失败（含契约拒绝、ComfyUI 故障、输出非法）。
     */
    FAILED,
    /**
     * 用户取消（仅排队中可取消）。
     */
    CANCELED,
    /**
     * 等待 ComfyUI 输出超时。
     */
    TIMEOUT;

    /**
     * 终态不可再流转。
     */
    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELED || this == TIMEOUT;
    }

    /**
     * 是否允许从当前状态流转到目标状态。
     */
    public boolean canTransitionTo(ImageTaskStatus target) {
        if (target == null) {
            return false;
        }
        return switch (this) {
            case QUEUED -> target == RUNNING || target == CANCELED || target == FAILED || target == TIMEOUT;
            case RUNNING -> target == SUCCEEDED || target == FAILED || target == TIMEOUT || target == QUEUED;
            default -> false;
        };
    }

    /**
     * 所有终态，供 SQL 的 {@code status NOT IN (...)} 使用。
     */
    public static String[] terminalNames() {
        return new String[]{SUCCEEDED.name(), FAILED.name(), CANCELED.name(), TIMEOUT.name()};
    }
}
