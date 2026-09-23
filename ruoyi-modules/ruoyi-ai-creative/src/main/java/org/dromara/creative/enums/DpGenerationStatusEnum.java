package org.dromara.creative.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 生成记录状态（{@code dp_generation.status}）。
 *
 * <p>前六个与图像内核 {@code ImageTaskStatus} 同名同义，便于页面直接展示内核真相；
 * 后两个是视觉工厂自己的判断：{@link #REJECTED}（被质检筛掉或人工否决）、
 * {@link #APPROVED}（人工选定为这一屏的采用图）。</p>
 *
 * <p><b>为什么 APPROVED 不是自动的</b>：决策③明确「自动 QA 只筛除、不放行」，
 * 因此 {@code CONSISTENT} 不会让候选变成 APPROVED——必须有人按下「选定」。</p>
 *
 * @author creative
 */
@Getter
@AllArgsConstructor
public enum DpGenerationStatusEnum {

    /**
     * 已入库待执行
     */
    QUEUED("QUEUED", "排队中"),
    /**
     * 执行中
     */
    RUNNING("RUNNING", "出图中"),
    /**
     * 成功
     */
    SUCCEEDED("SUCCEEDED", "已出图"),
    /**
     * 失败（可重试：重试=新建一次候选，内核任务本身不可复活）
     */
    FAILED("FAILED", "失败"),
    /**
     * 已取消
     */
    CANCELED("CANCELED", "已取消"),
    /**
     * 超时
     */
    TIMEOUT("TIMEOUT", "超时"),
    /**
     * 被筛除（质检不一致 / 人工否决）
     */
    REJECTED("REJECTED", "已筛除"),
    /**
     * 已选定（该屏采用这张）
     */
    APPROVED("APPROVED", "已选定");

    /**
     * 编码（入库值）
     */
    private final String code;
    /**
     * 描述
     */
    private final String desc;

    /**
     * 按 code 查找。
     *
     * @param code 编码
     * @return 枚举；未命中返回 null
     */
    public static DpGenerationStatusEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (DpGenerationStatusEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        return null;
    }

    /**
     * 是否终态（终态不再轮询内核状态；重试不复活，而是新建候选）。
     *
     * @return 是否终态
     */
    public boolean isTerminal() {
        return this != QUEUED && this != RUNNING;
    }

    /**
     * 是否可被质检筛除（只有成功出图的候选才有意义去筛）。
     *
     * @return 是否可筛除
     */
    public boolean isRejectable() {
        return this == SUCCEEDED;
    }

    /**
     * 是否可重试（失败/超时/被筛除都可以再来一次）。
     *
     * @return 是否可重试
     */
    public boolean isRetryable() {
        return this == FAILED || this == TIMEOUT || this == REJECTED;
    }

}
