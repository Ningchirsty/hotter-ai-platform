package org.dromara.hrtalent.domain.vo.talent;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.hrtalent.domain.entity.TalentPoolMember;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 人才池成员视图对象 hr_talent_pool_member（SPEC-P4 §2.3 C 线）。
 *
 * <p>包含加入原因、推荐岗位、适配等级、加入人、加入时间与下次联系时间；
 * 人才姓名/编号由服务层按<b>可见范围内</b>的主档批量回填，
 * 不可见的人才不会出现在成员列表中（设计文档 §8.17、§11.1）。</p>
 *
 * <p><b>移出说明</b>：{@link #memberStatus} 为 {@code removed} 表示关系已结束，
 * 人才主档与简历数据依然存在（设计文档 §8.15）。</p>
 *
 * @author hr-talent
 */
@Data
@AutoMapper(target = TalentPoolMember.class)
public class TalentPoolMemberVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 成员关系ID
     */
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
     * 人才姓名（服务层按可见范围回填，仅展示用）
     */
    private String talentName;

    /**
     * 人才编号（服务层按可见范围回填，仅展示用）
     */
    private String talentNo;

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
     * 加入人昵称（由 {@link #joinedBy} 翻译）
     */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "joinedBy")
    private String joinedByName;

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
