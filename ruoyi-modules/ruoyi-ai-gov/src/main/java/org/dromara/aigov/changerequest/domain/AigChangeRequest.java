package org.dromara.aigov.changerequest.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 高风险变更单对象 aig_change_request（Q1C 完整版：申请 → 批准 → 生效记录）。
 *
 * <p><b>它补的是轻量版补不了的两件事</b>：①没有 {@code aig:capability:high-risk} 的人
 * <b>提不出</b>这类改动（只能去 IM 上找管理员，改动无据可查）；②只有"谁改的"，
 * 没有"<b>谁批的</b>"与"<b>批的到底是什么值</b>"。</p>
 *
 * <p><b>批准即执行</b>：审批的意义就是"批了就生效"。执行前用 {@link #beforeJson} 与当前行
 * <b>逐字段比对</b>（乐观并发）：不一致就置为 {@code CONFLICTED} 并写明原因，
 * <b>绝不覆盖别人的改动</b>。执行走既有服务方法，因此 Q1C 那道闸门仍然生效——
 * 批准人持有 high-risk，闸门放行；本表<b>不提供任何绕过闸门的通道</b>。</p>
 *
 * <p><b>申请人不得自批</b>（与 {@code aig_call_approval} 同规矩，服务端强制）。</p>
 *
 * @author ai-gov
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aig_change_request")
public class AigChangeRequest extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 变更单ID
     */
    @TableId("change_request_id")
    private Long changeRequestId;

    /**
     * 能力编码
     */
    private String capabilityCode;

    /**
     * 变更类型（{@code AigChangeTypeEnum}：POLICY_CREATE / POLICY_UPDATE / POLICY_DELETE）
     */
    private String changeType;

    /**
     * 目标对象ID（策略行 policyId；新增时为空）
     */
    private Long targetRef;

    /**
     * 变更前快照（JSON；新增时为空）。执行前用它做乐观并发校验。
     */
    private String beforeJson;

    /**
     * 变更后目标值（JSON；删除时为空——删除不需要目标值）
     */
    private String afterJson;

    /**
     * 申请理由
     */
    private String reason;

    /**
     * 状态（{@code AigChangeRequestStatusEnum}）
     */
    private String status;

    /**
     * 申请人用户ID
     */
    private Long requesterId;

    /**
     * 申请人账号（冗余，便于离线核对）
     */
    private String requesterName;

    /**
     * 批准人用户ID（申请人不得自批）
     */
    private Long approverId;

    /**
     * 批准人账号（冗余）
     */
    private String approverName;

    /**
     * 批准/驳回时间
     */
    private LocalDateTime decidedAt;

    /**
     * 审批意见（驳回必填）
     */
    private String decisionRemark;

    /**
     * 生效时间（即批准时执行成功的时间）
     */
    private LocalDateTime appliedAt;

    /**
     * 执行结果说明（冲突/失败原因，成功时为空）
     */
    private String resultRemark;

    /**
     * 删除标志（0 存在 / 1 删除）
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注（BaseEntity 不含 remark，本表 DDL 有这一列）
     */
    private String remark;

}
