package org.dromara.hrtalent.domain.entity;

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
 * 人才池成员关系对象 hr_talent_pool_member（设计文档 §8.15、§9.2）。
 *
 * <p><b>关系语义</b>：{@code pool_id + talent_id} 唯一，同一人才在同一池内只有一条关系记录。</p>
 *
 * <p><b>移出只结束关系</b>：移出人才池只把 {@link #memberStatus} 置为
 * {@code removed} 并写入 {@link #removedTime} / {@link #removedReason}，
 * <b>绝不删除</b>人才主档（{@code hr_talent_profile}）与任何简历、经历数据（设计文档 §8.15）。</p>
 *
 * <p><b>重复加入幂等</b>：再次加入同一人才时直接返回已有关系（必要时把已移出的关系恢复为在池），
 * 不抛异常、不产生第二条记录。</p>
 *
 * @author hr-talent
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hr_talent_pool_member")
public class TalentPoolMember extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 成员关系ID（主键）
     */
    @TableId(value = "member_id")
    private Long memberId;

    /**
     * 人才池ID
     */
    private Long poolId;

    /**
     * 人才主档ID
     */
    private Long talentId;

    /**
     * 适配等级（high/medium/low 等稳定编码）
     */
    private String fitLevel;

    /**
     * 推荐岗位
     */
    private String recommendedJob;

    /**
     * 加入原因
     */
    private String joinReason;

    /**
     * 下次联系时间
     */
    private LocalDateTime nextContactTime;

    /**
     * 成员状态（active/paused/removed/converted，字典 talent_pool_member_status）
     */
    private String memberStatus;

    /**
     * 加入操作人用户ID
     */
    private Long joinedBy;

    /**
     * 加入时间
     */
    private LocalDateTime joinedTime;

    /**
     * 移出时间
     */
    private LocalDateTime removedTime;

    /**
     * 移出原因
     */
    private String removedReason;

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
