package org.dromara.aigov.agent.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 发布状态推进入参（设计 §5.4、§6.3）。
 *
 * <p><b>{@link #expectedStatus} 是必填的，不是可选的乐观锁字段</b>：调用方必须声明
 * 「我以为它现在是什么状态」。服务层先比对，不一致就报「你的视图已过期」——
 * 这比只靠条件更新的 0 行返回更早、更可读地暴露问题：审批页面停留十分钟后
 * 另一个人已经把它推到灰度了，此时点「批准」应当收到明确提示，
 * 而不是一条「更新影响 0 行」的并发异常。</p>
 *
 * <p><b>{@link #toStatus} 对「门槛驱动的推进」会被交叉校验</b>：若目标是
 * VALIDATED/SANDBOX_TESTED/CANDIDATE/STABLE 之一，服务层会先按
 * {@code nextIfAllGatesPassed} 算出「当前状态真正该去的下一步」，
 * 与之不符即拒绝。因此调用方无法指定一个更大的跳步目标，也无法在门槛不足时前进。</p>
 *
 * @author ai-gov
 */
@Data
public class AigReleaseAdvanceBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 对象类型（{@code AigReleaseTargetTypeEnum}）
     */
    @NotBlank(message = "对象类型不能为空")
    private String targetType;

    /**
     * 对象版本ID
     */
    @NotNull(message = "对象版本ID不能为空")
    private Long targetVersionId;

    /**
     * 调用方所见的当前状态（比对不一致即报「视图已过期」）
     */
    @NotBlank(message = "期望的当前状态不能为空")
    private String expectedStatus;

    /**
     * 目标状态（{@code AigReleaseStatusEnum}）
     */
    @NotBlank(message = "目标状态不能为空")
    private String toStatus;

    /**
     * 本次已通过的门槛（{@code AigReleaseGateEnum} 的 code）
     * <p>只影响「门槛驱动的推进」；停用/归档这类运维动作不需要门槛。</p>
     */
    private List<String> passedGates;

    /**
     * 操作人（发布永远是人工动作，由控制层从登录态填入；系统触发可空）
     */
    private Long operatorId;

    /**
     * 说明（写入发布事件账本；停用/归档建议写明原因）
     */
    @Size(max = 500, message = "说明长度不能超过 500")
    private String detail;

}
