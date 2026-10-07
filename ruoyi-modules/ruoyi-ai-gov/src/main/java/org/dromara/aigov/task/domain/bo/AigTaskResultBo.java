package org.dromara.aigov.task.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 任务结果回写入参（候选资产）。
 *
 * @author ai-gov
 */
@Data
public class AigTaskResultBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 任务ID
     */
    @NotNull(message = "任务ID不能为空")
    private Long taskId;

    /**
     * 所属尝试次数
     */
    private Integer attemptNo;

    /**
     * 结果类型（STRUCTURED/ASSET/SESSION）
     */
    @NotBlank(message = "结果类型不能为空")
    @Size(max = 32, message = "结果类型长度不能超过 32")
    private String resultType;

    /**
     * 资产ID/对象引用（<b>禁止填服务器本地路径</b>）
     */
    private Long assetId;

    /**
     * 结构化输出（<b>已通过 Schema 校验才传</b>）
     */
    private String structuredOutputJson;

    /**
     * 结构校验结果（PASS/FAIL）
     */
    @Size(max = 16, message = "校验结果长度不能超过 16")
    private String validationResult;

    /**
     * 校验明细（失败时给字段级原因）
     */
    @Size(max = 1000, message = "校验明细长度不能超过 1000")
    private String validationDetail;

    /**
     * 候选状态（CANDIDATE/APPROVED/REJECTED）
     * <p>自动化流程只允许传 CANDIDATE 或 REJECTED；传 APPROVED 会被拒绝——见服务层说明。</p>
     */
    @Size(max = 24, message = "候选状态长度不能超过 24")
    private String candidateStatus;

    /**
     * 质检结论（CONSISTENT/INCONSISTENT/UNCERTAIN）
     */
    @Size(max = 16, message = "质检结论长度不能超过 16")
    private String qaVerdict;

    /**
     * 质检说明（含本地确定性度量的边界说明）
     */
    @Size(max = 1000, message = "质检说明长度不能超过 1000")
    private String qaDetail;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;

}
