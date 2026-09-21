package org.dromara.hrtalent.domain.vo.talent;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.hrtalent.domain.entity.TalentGroup;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 人才分组视图对象 hr_talent_group（SPEC-P4 §2.3 C 线）。
 *
 * <p>{@link #groupType} 为 {@code public} 表示公共分组（团队共同整理，由人才池管理员维护）；
 * 为 {@code personal} 表示个人收藏（用户个人快速访问，<b>不改变人才数据权限</b>，
 * 设计文档 §8.15）。</p>
 *
 * @author hr-talent
 */
@Data
@AutoMapper(target = TalentGroup.class)
public class TalentGroupVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 分组ID
     */
    private Long groupId;

    /**
     * 分组编码（业务编码，可选）
     */
    private String groupCode;

    /**
     * 分组名称
     */
    private String groupName;

    /**
     * 分组类型（public公共分组/personal个人收藏）
     */
    private String groupType;

    /**
     * 归属部门ID
     */
    private Long ownerDeptId;

    /**
     * 负责人/收藏人用户ID
     */
    private Long ownerId;

    /**
     * 负责人/收藏人昵称（由 {@link #ownerId} 翻译）
     */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "ownerId")
    private String ownerName;

    /**
     * 可见范围（字典 talent_visibility_type 编码）
     */
    private String visibilityType;

    /**
     * 可见范围标签（字典 talent_visibility_type）
     */
    @Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "visibilityType", other = "talent_visibility_type")
    private String visibilityTypeLabel;

    /**
     * 状态（active/inactive 等稳定编码）
     */
    private String status;

    /**
     * 成员数量（冗余计数，服务层维护）
     */
    private Integer talentCount;

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

    /**
     * 分组类型中文兜底（设计文档 §10 未定义分组类型字典，编码为稳定字面量）。
     *
     * @return 中文类型名，未知/空编码返回 null
     */
    public String getGroupTypeText() {
        return switch (groupType == null ? "" : groupType) {
            case "public" -> "公共分组";
            case "personal" -> "个人收藏";
            default -> null;
        };
    }

}
