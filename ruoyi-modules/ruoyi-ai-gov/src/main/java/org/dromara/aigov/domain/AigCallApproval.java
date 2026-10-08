package org.dromara.aigov.domain;

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
 * 调用授权审批对象 aig_call_approval（C3：把 {@code aig_route_policy.require_approval} 做实）。
 *
 * <p><b>它解决的问题</b>：路由策略上那个「调用前是否需要审批」的开关，此前<b>没有任何效果</b>——
 * 全仓库唯一读它的地方是把它拼进 {@code policyHits} 的说明文本。管理员打开开关、页面显示
 * 「需要」，调用照样跑。本表是那个开关真正生效所需的「授权事实」。</p>
 *
 * <p><b>授权粒度＝人 × 能力 × 数据等级</b>（刻意）：审批本身就是按 (能力,数据等级) 的策略触发的，
 * 批准 INTERNAL 不该顺带放行 RESTRICTED/STRICT——数据等级是安全维度，越高越严；
 * 粒度取宽（只按能力）会让一次低等级审批变成一张覆盖全部等级的通行证。</p>
 *
 * <p><b>一张单子两个时间，别混</b>：{@link #expireTime} 是<b>审批时限</b>（PENDING 超过它
 * 不得再批准，由扫描置为 EXPIRED）；{@link #validUntil} 是<b>授权有效期止</b>
 * （批准时＝审批时间＋有效时长）。"能不能批准"看前者，"能不能调用"看后者。</p>
 *
 * <p><b>有效授权＝status=APPROVED 且 valid_until &gt; now</b>。其余状态一律不放行；
 * 授权过期<b>不改状态</b>（过期是时间的函数，不是需要落库的事件），所以
 * 「批准过」这个事实永远查得到。</p>
 *
 * <p><b>申请人不得自审</b>：在服务层强制，不靠页面藏按钮。</p>
 *
 * @author ai-gov
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aig_call_approval")
public class AigCallApproval extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 审批单ID
     */
    @TableId(value = "approval_id")
    private Long approvalId;

    /**
     * 申请人用户ID（{@code sys_user.user_id}）
     */
    private Long requesterId;

    /**
     * 申请人账号（冗余，便于离线核对；以 {@link #requesterId} 为准）
     */
    private String requesterName;

    /**
     * 能力编码（授权粒度：能力）
     */
    private String capabilityCode;

    /**
     * 数据等级（授权粒度：数据等级）
     */
    private String dataLevel;

    /**
     * 申请理由（为什么需要这次授权）
     */
    private String reason;

    /**
     * 状态（{@code AigApprovalStatusEnum} 的 code：PENDING/APPROVED/REJECTED/EXPIRED/CANCELLED）
     */
    private String status;

    /**
     * 审批时限：PENDING 超过此时间不得再批准，由扫描置为 EXPIRED
     */
    private LocalDateTime expireTime;

    /**
     * 审批人用户ID（申请人不得自审）
     */
    private Long approverId;

    /**
     * 审批人账号（冗余）
     */
    private String approverName;

    /**
     * 审批时间
     */
    private LocalDateTime decidedAt;

    /**
     * 审批意见（驳回时必填）
     */
    private String decisionRemark;

    /**
     * 授权有效期止（批准时=审批时间+有效时长；为空表示这张单从未获批）
     */
    private LocalDateTime validUntil;

    /**
     * 删除标志（0代表存在 1代表删除）
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注
     */
    private String remark;

}
