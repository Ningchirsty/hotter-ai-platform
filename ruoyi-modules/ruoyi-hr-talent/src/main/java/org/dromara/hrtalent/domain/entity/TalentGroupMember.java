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
 * 人才分组成员关系对象 hr_talent_group_member（设计文档 §8.15）。
 *
 * <p>唯一索引 {@code (group_id, talent_id)} 防止重复加入；重复加入按<b>幂等</b>处理
 * （返回已有关系，必要时恢复已移出的关系），不抛异常。</p>
 *
 * <p><b>移出分组只结束关系</b>：不删除人才主档与任何简历、经历、标签数据；
 * 本表以逻辑删除表达「已移出」。</p>
 *
 * @author hr-talent
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hr_talent_group_member")
public class TalentGroupMember extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 分组成员ID（主键，与人才池成员表同名但表不同）
     */
    @TableId(value = "member_id")
    private Long memberId;

    /**
     * 分组ID
     */
    private Long groupId;

    /**
     * 人才主档ID
     */
    private Long talentId;

    /**
     * 加入人用户ID
     */
    private Long addedBy;

    /**
     * 加入时间
     */
    private LocalDateTime addedTime;

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
