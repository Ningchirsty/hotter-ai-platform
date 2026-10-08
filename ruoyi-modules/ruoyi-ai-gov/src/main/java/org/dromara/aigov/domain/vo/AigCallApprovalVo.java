package org.dromara.aigov.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.aigov.domain.AigCallApproval;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 调用授权审批视图对象 aig_call_approval（只读视图）。
 *
 * <p>直接把两个时间都给出来，让页面能自己回答两个不同的问题：
 * 「还能不能批」（看 {@link #expireTime}）与「现在还管不管用」（看 {@link #validUntil}）。
 * 只给一个状态字段会让「已批准但已过期」看起来像"授权还在"。</p>
 *
 * @author ai-gov
 */
@Data
@AutoMapper(target = AigCallApproval.class)
public class AigCallApprovalVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 审批单ID
     */
    private Long approvalId;

    /**
     * 申请人用户ID
     */
    private Long requesterId;

    /**
     * 申请人账号
     */
    private String requesterName;

    /**
     * 能力编码
     */
    private String capabilityCode;

    /**
     * 数据等级
     */
    private String dataLevel;

    /**
     * 申请理由
     */
    private String reason;

    /**
     * 状态（PENDING/APPROVED/REJECTED/EXPIRED/CANCELLED）
     */
    private String status;

    /**
     * 审批时限（PENDING 超过它不得再批准）
     */
    private LocalDateTime expireTime;

    /**
     * 审批人用户ID
     */
    private Long approverId;

    /**
     * 审批人账号
     */
    private String approverName;

    /**
     * 审批时间
     */
    private LocalDateTime decidedAt;

    /**
     * 审批意见
     */
    private String decisionRemark;

    /**
     * 授权有效期止
     */
    private LocalDateTime validUntil;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
