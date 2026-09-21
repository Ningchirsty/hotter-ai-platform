package org.dromara.hrtalent.domain.bo.talent;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 人才分组检索业务对象（SPEC-P4 §2.3 {@code GET /talent/groups}）。
 *
 * <p>不承载任何授权标记：公共分组按 {@code TalentScopeDomainService} 的可见范围过滤，
 * 个人收藏只返回当前用户自己的分组（设计文档 §8.15、§11.1）。</p>
 *
 * @author hr-talent
 */
@Data
public class TalentGroupQueryBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 分组名称（模糊匹配）
     */
    private String groupName;

    /**
     * 分组类型（public/personal，精确匹配）
     */
    private String groupType;

    /**
     * 分组编码（模糊匹配）
     */
    private String groupCode;

    /**
     * 负责人/收藏人用户ID（精确匹配；仅在有权限时生效）
     */
    private Long ownerId;

    /**
     * 归属部门ID（精确匹配）
     */
    private Long ownerDeptId;

    /**
     * 可见范围（字典 talent_visibility_type 编码，精确匹配）
     */
    private String visibilityType;

    /**
     * 状态（active/inactive 等稳定编码，精确匹配）
     */
    private String status;

}
