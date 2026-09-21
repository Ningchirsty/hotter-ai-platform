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
 * 人才标签关系对象 hr_talent_profile_tag（设计文档 §8.15、§9.2）。
 *
 * <p>{@code talent_id + tag_id} 唯一（含逻辑删除行，删除列由 {@link #delFlag} 表达），
 * 同一人才对同一标签只有一条关系记录。</p>
 *
 * <p><b>来源与确认</b>：{@link #sourceType} 区分人工({@code manual})、解析({@code resume})与
 * 系统自动({@code system})标签；自动来源的标签只有经人工确认
 * （{@link #confirmedBy} / {@link #confirmedTime}）后才视为正式特征，
 * 且<b>背调失败、健康、家庭、年龄等敏感内容不得自动生成可被普通用户检索的标签</b>（设计文档 §7.6.4）。</p>
 *
 * @author hr-talent
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hr_talent_profile_tag")
public class TalentProfileTag extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 标签关系ID（主键）
     */
    @TableId(value = "rel_id")
    private Long relId;

    /**
     * 人才主档ID
     */
    private Long talentId;

    /**
     * 标签ID
     */
    private Long tagId;

    /**
     * 来源类型（manual手工/resume解析/system系统等稳定编码）
     */
    private String sourceType;

    /**
     * 确认人用户ID（人工确认为准）
     */
    private Long confirmedBy;

    /**
     * 确认时间
     */
    private LocalDateTime confirmedTime;

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
