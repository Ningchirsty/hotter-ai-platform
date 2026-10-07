package org.dromara.aigov.task.domain.bo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 人工选定候选资产入参。
 *
 * <p><b>为什么必须有这个入口</b>：设计 §9.2 写着「SUCCEEDED 只代表 Provider 返回成功；
 * 候选资产仍需 QA/人工审核」，而 {@code AigCandidateStatusEnum} 已经规定
 * 「自动流程只筛除、不放行」（{@code APPROVED} 恒不可自动写入）。
 * 于是此前系统能产出一堆候选、却<b>没有任何途径把其中一张定为交付物</b>——
 * 人工闸门只关不开，流程走不到 APPROVED。</p>
 *
 * @author ai-gov
 */
@Data
public class AigTaskResultSelectBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 任务ID
     */
    @NotNull(message = "任务ID不能为空")
    private Long taskId;

    /**
     * 要选定的候选结果ID
     */
    @NotNull(message = "候选结果ID不能为空")
    private Long resultId;

    /**
     * 选定说明（写入事件流，便于事后回答「为什么选了它」）
     */
    @Size(max = 500, message = "选定说明长度不能超过 500")
    private String remark;

}
