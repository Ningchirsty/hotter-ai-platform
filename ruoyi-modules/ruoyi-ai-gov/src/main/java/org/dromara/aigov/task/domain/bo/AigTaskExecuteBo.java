package org.dromara.aigov.task.domain.bo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Map;

/**
 * 执行 AI 任务入参。
 *
 * <p><b>为什么 prompt/payload 由调用方给，而不是从快照里推导</b>：快照内容是业务域自己组装的
 * JSON，治理层不知道它的字段含义（不同任务类型的快照结构完全不同）。由业务域在发起执行时
 * 直接给出真实请求，语义唯一；而快照的角色是<b>不可变记录与校验对象</b>——
 * 执行前会重算 snapshot_hash 与冻结值比对，不一致就拒绝执行（见执行器说明）。</p>
 *
 * @author ai-gov
 */
@Data
public class AigTaskExecuteBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 任务ID
     */
    @NotNull(message = "任务ID不能为空")
    private Long taskId;

    /**
     * 业务提示词
     */
    private String prompt;

    /**
     * 结构化业务载荷
     */
    private Map<String, Object> payload;

    /**
     * 本次预算（可空；为空则用快照里的 {@code budget_amount}）。
     *
     * <p>单位＝<b>美元 USD</b>（全平台唯一金额口径，见 {@code AigConstants#COST_CURRENCY}）；
     * 它就是这个<b>任务项</b>的预算——不是项目级累计预算，也不会跨任务项累计。</p>
     */
    private BigDecimal maxCost;

}
