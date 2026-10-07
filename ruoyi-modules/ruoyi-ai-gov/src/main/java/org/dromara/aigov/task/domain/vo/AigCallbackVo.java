package org.dromara.aigov.task.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 回调处理结果。
 *
 * <p>含 {@code processResult}（与回调账本同口径）与可读 {@code detail}，
 * 供适配层直接回给 Provider：对方据此判断是否需要重推。
 * <b>验签失败也必须给出明确原因</b>——否则对接方只能反复重推同一个被拒的回调，
 * 而问题其实在自己没配密钥。</p>
 *
 * @author ai-gov
 */
@Data
public class AigCallbackVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 处理结果（ACCEPTED/DUPLICATE/REJECTED_UNSIGNED/TASK_NOT_FOUND/ORDER_STALE）
     */
    private String processResult;

    /**
     * 处理说明
     */
    private String detail;

    /**
     * 关联任务ID（定位不到时为空）
     */
    private Long taskId;

    /**
     * 推进后的任务状态（未推进时为空）
     */
    private String taskStatus;

    /**
     * 是否命中重复投递
     */
    private boolean duplicate;

    /**
     * 是否被接受并推进了状态
     */
    private boolean accepted;

}
