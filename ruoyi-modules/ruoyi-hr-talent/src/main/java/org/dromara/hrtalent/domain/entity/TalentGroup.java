package org.dromara.hrtalent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;

/**
 * 人才分组对象 hr_talent_group（设计文档 §8.15、§5.1 菜单「人才池与分组」）。
 *
 * <p><b>两类分组（语义必须严格区分）</b>：</p>
 * <ul>
 *     <li>{@code public} 公共分组：团队共同整理，由人才池管理员维护；</li>
 *     <li>{@code personal} 个人收藏：用户个人快速访问，
 *     <b>不改变人才数据权限</b>——把人才加入个人收藏不会让任何人获得该人才的查看权，
 *     个人收藏列表同样要叠加 {@code TalentScopeDomainService} 的可见范围。</li>
 * </ul>
 *
 * <p><b>可见性</b>：分组的可见范围判定统一走
 * {@code org.dromara.hrtalent.domainservice.TalentScopeDomainService}，
 * 本实体只提供判定所需的 {@link #visibilityType}、{@link #ownerDeptId}、{@link #ownerId} 字段。</p>
 *
 * @author hr-talent
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hr_talent_group")
public class TalentGroup extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 分组ID（主键）
     */
    @TableId(value = "group_id")
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
     * 归属部门ID（公共分组的维护部门）
     */
    private Long ownerDeptId;

    /**
     * 负责人/收藏人用户ID
     */
    private Long ownerId;

    /**
     * 可见范围（group/company/department/owner/explicit，字典 talent_visibility_type）
     */
    private String visibilityType;

    /**
     * 状态（active生效/inactive停用等稳定编码）
     */
    private String status;

    /**
     * 成员数量冗余计数（只由服务层重算）
     */
    private Integer talentCount;

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
