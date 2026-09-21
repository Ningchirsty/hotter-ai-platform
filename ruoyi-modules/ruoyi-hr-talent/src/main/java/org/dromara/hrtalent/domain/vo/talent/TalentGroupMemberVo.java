package org.dromara.hrtalent.domain.vo.talent;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.hrtalent.domain.entity.TalentGroupMember;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 人才分组成员视图对象 hr_talent_group_member（SPEC-P4 §2.3 C 线）。
 *
 * <p>人才姓名/编号由服务层按<b>可见范围内</b>的主档批量回填；
 * 个人收藏不会让任何人获得其收藏人才的数据权限（设计文档 §8.15）。</p>
 *
 * @author hr-talent
 */
@Data
@AutoMapper(target = TalentGroupMember.class)
public class TalentGroupMemberVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 分组成员ID
     */
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
     * 人才姓名（服务层按可见范围回填，仅展示用）
     */
    private String talentName;

    /**
     * 人才编号（服务层按可见范围回填，仅展示用）
     */
    private String talentNo;

    /**
     * 加入人用户ID
     */
    private Long addedBy;

    /**
     * 加入人昵称（由 {@link #addedBy} 翻译）
     */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "addedBy")
    private String addedByName;

    /**
     * 加入时间
     */
    private LocalDateTime addedTime;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
