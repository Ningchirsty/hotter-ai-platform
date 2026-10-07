package org.dromara.aigov.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;

/**
 * 调用人均配额对象 aig_user_quota（C3：用量配额按人）。
 *
 * <p><b>它回答的问题</b>：这个人还能不能再发起一次模型调用。此前治理台只有
 * {@code aig_model_governance.cost_limit_amount}（按模型的<b>单次</b>成本上限）——
 * 那是单次约束，不是累计约束：一个人一天调一千次、每次都在单次上限内，没有任何地方会拦。</p>
 *
 * <p><b>计量单位是「调用次数」，不是钱</b>（刻意，别改）：{@code aig_invocation_audit.cost}
 * 的注释写明「为空表示未知而非免费」，而经 snail-ai 的链路根本不返回 tokens/cost。
 * 用一列经常是「未知」的数字做配额，会算出一本对不上的账——那正是当初「累计预算刻意不做」
 * 的理由。次数是每条调用都有的、可核对的事实。</p>
 *
 * <p><b>语义边界</b>：无配额行 = 不限（新表上线不改变既有行为）；状态停用 = 不参与判定；
 * 日/月上限可各为空（为空的那条不限）。计数只统计 <b>aig_invocation_audit 里该调用人的行数</b>
 * （含失败——调用发生过就算），周期为自然日/自然月。</p>
 *
 * <p><b>与审计的关系</b>：被配额拦下的调用<b>不写调用审计</b>——它不是一次调用，
 * 而那张表是「逐次调用」的账本。否则一旦超限，每次被拒都再记一行，用量只会越滚越大，
 * 账本也不再表示「发生过哪些调用」。拒绝原因与当前用量在错误消息与日志里可见。</p>
 *
 * @author ai-gov
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aig_user_quota")
public class AigUserQuota extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 配额ID
     */
    @TableId(value = "quota_id")
    private Long quotaId;

    /**
     * 用户ID（{@code sys_user.user_id}）：按「调用人」计
     */
    private Long userId;

    /**
     * 调用人账号（冗余，便于离线核对；以 {@link #userId} 为准）
     *
     * <p>与 {@code aig_invocation_audit.caller_name} 同口径：审计侧也冗余了一份账号名，
     * 目的是不必为了显示一行而跨模块 join {@code sys_user}，也避免用户改名后
     * 历史记录跟着变。</p>
     */
    private String userName;

    /**
     * 每自然日调用次数上限（null = 不限）
     */
    private Integer dailyLimit;

    /**
     * 每自然月调用次数上限（null = 不限）
     */
    private Integer monthlyLimit;

    /**
     * 状态（0正常 1停用；停用 = 不参与判定，等同于不限）
     */
    private String status;

    /**
     * 删除标志（0代表存在 1代表删除）
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注（为什么给他设这个额度）
     */
    private String remark;

}
