package org.dromara.hrtalent.domain.bo.talent;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.hrtalent.domain.entity.TalentGroup;

import java.io.Serial;
import java.io.Serializable;

/**
 * 人才分组业务对象 hr_talent_group（SPEC-P4 §2.3 {@code GET/POST/PUT/DELETE /talent/groups}）。
 *
 * <p>{@code public} 公共分组由人才池管理员维护；{@code personal} 个人收藏仅本人可见可维护，
 * 且<b>不改变人才数据权限</b>（设计文档 §8.15）。可见范围由服务层经
 * {@code TalentScopeDomainService} 判定，本对象不承载任何授权标记。</p>
 *
 * @author hr-talent
 */
@Data
@AutoMapper(target = TalentGroup.class, reverseConvertGenerate = false)
public class TalentGroupBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 分组ID（编辑时必填）
     */
    @NotNull(message = "分组ID不能为空", groups = {EditGroup.class})
    private Long groupId;

    /**
     * 分组名称
     */
    @NotBlank(message = "分组名称不能为空", groups = {AddGroup.class, EditGroup.class})
    @Size(max = 128, message = "分组名称长度不能超过 128", groups = {AddGroup.class, EditGroup.class})
    private String groupName;

    /**
     * 分组类型（public公共分组 / personal个人收藏，默认 personal）
     */
    @Size(max = 32, message = "分组类型长度不能超过 32", groups = {AddGroup.class, EditGroup.class})
    private String groupType;

    /**
     * 归属部门ID（公共分组的维护部门）
     */
    private Long ownerDeptId;

    /**
     * 负责人/收藏人用户ID（不填时由服务端取登录用户）
     */
    private Long ownerId;

    /**
     * 可见范围（字典 talent_visibility_type 编码）
     */
    @Size(max = 32, message = "可见范围长度不能超过 32", groups = {AddGroup.class, EditGroup.class})
    private String visibilityType;

    /**
     * 状态（active生效/inactive停用等稳定编码）
     */
    @Size(max = 32, message = "状态长度不能超过 32", groups = {AddGroup.class, EditGroup.class})
    private String status;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过 500", groups = {AddGroup.class, EditGroup.class})
    private String remark;

}
