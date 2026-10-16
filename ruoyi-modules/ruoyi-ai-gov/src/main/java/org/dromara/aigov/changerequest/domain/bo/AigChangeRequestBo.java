package org.dromara.aigov.changerequest.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.dromara.aigov.changerequest.domain.AigChangeRequest;

import java.io.Serial;
import java.io.Serializable;

/**
 * 变更单申请入参。
 *
 * <p><b>目标值用显式字段而不是"一段 JSON"</b>：审批人要看清"批的是什么值"，
 * 而一段自由 JSON 在页面上只能原样贴出来；显式字段能逐项对比 before/after，
 * 也能在服务端做枚举校验（拼错一个外发开关的值，审批人肉眼很难发现）。</p>
 *
 * @author ai-gov
 */
@Data
@AutoMapper(target = AigChangeRequest.class)
public class AigChangeRequestBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 变更单ID（审批/撤回时用）
     */
    private Long changeRequestId;

    /**
     * 能力编码
     */
    @NotBlank(message = "能力编码不能为空")
    @Size(max = 64, message = "能力编码长度不能超过 64")
    private String capabilityCode;

    /**
     * 变更类型（POLICY_CREATE / POLICY_UPDATE / POLICY_DELETE）
     */
    @NotBlank(message = "变更类型不能为空")
    private String changeType;

    /**
     * 目标策略行ID（修改/删除必填；新增时留空）
     */
    private Long targetRef;

    /**
     * 数据等级（新增/修改的目标值；删除时留空）
     */
    private String dataLevel;

    /**
     * 允许外发（Y/N 的目标值）
     */
    private String allowExternal;

    /**
     * 优先部署类型（目标值，可空）
     */
    private String preferredDeployment;

    /**
     * 调用前审批（Y/N 的目标值）
     */
    private String requireApproval;

    /**
     * 无可用模型时（Y 转人工 / N 直接失败）
     */
    private String fallbackToManual;

    /**
     * 状态（0 正常 / 1 停用；目标值，可空=沿用默认）
     */
    private String status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 申请理由（为什么必须这么改）
     */
    @Size(max = 500, message = "申请理由长度不能超过 500")
    private String reason;

    /**
     * 审批/撤回意见
     */
    @Size(max = 500, message = "意见长度不能超过 500")
    private String decisionRemark;

    /**
     * 列表筛选：只看我的（由控制层按登录态覆盖）
     */
    private Long requesterId;

    /**
     * 列表筛选：状态
     */
    private String filterStatus;
}
