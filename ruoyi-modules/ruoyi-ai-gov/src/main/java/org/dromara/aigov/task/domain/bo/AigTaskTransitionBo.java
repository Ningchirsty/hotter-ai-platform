package org.dromara.aigov.task.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 任务状态迁移入参。
 *
 * <p>{@link #expectedVersion} 必填是刻意的：状态迁移的调用方<b>必须</b>先读到当前版本再提交，
 * 这样并发时的冲突才会暴露成「迁移失败，请重试」，而不是安静地互相覆盖。
 * 允许它为空就等于允许「无条件覆盖」，乐观锁也就白加了。</p>
 *
 * @author ai-gov
 */
@Data
public class AigTaskTransitionBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 任务ID
     */
    @NotNull(message = "任务ID不能为空")
    private Long taskId;

    /**
     * 期望的当前版本（乐观锁；必填）
     */
    @NotNull(message = "期望版本不能为空（状态迁移必须带乐观锁版本）")
    private Integer expectedVersion;

    /**
     * 目标状态（{@code AigTaskStatusEnum}）
     */
    @NotBlank(message = "目标状态不能为空")
    @Size(max = 24, message = "状态长度不能超过 24")
    private String toStatus;

    /**
     * 可读说明（写入事件；拒绝/转人工时不允许空泛文案）
     */
    @Size(max = 1000, message = "说明长度不能超过 1000")
    private String detail;

    /**
     * 事件载荷（JSON；<b>不得含密钥与受限原文</b>）
     */
    private String payloadJson;

}
