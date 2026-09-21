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
 * 人才共享授权对象 hr_talent_scope_grant（SPEC-P4 §2.4、设计文档 §8.19 / §21.14）。
 *
 * <p><b>语义</b>：一条授权 = 「某个主体（用户 / 角色 / 公司部门 / 部门）在某段有效期内
 * 可以按某个级别查看某位人才」。主体按 {@code grantee_type + grantee_id} <b>成对</b>匹配，
 * 不同类型主体的 ID 绝不允许混入同一集合比较。</p>
 *
 * <p><b>硬约束</b>：</p>
 * <ul>
 *     <li><b>共享只扩大查看范围</b>（设计文档 §8.19）：本表授权<b>不</b>自动授予电话明文、
 *     附件下载、背调查看与导出权限；这些动作仍需各自的按钮权限，并通过
 *     {@code TalentScopeDomainService#checkPermissionLevel} 单独判定。</li>
 *     <li><b>过期立即失效</b>（设计文档 §11.1）：判定必须同时满足
 *     {@code del_flag = '0'}、{@code revoke_flag = '0'}、
 *     {@code valid_from <= now} 且（{@code valid_to} 为空或 {@code valid_to > now}）。</li>
 *     <li>撤销只置 {@link #revokeFlag} 与撤销人/撤销时间，<b>不物理删除</b>，保留授权审计轨迹。</li>
 * </ul>
 *
 * @author hr-talent
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hr_talent_scope_grant")
public class TalentScopeGrant extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 授权ID（主键）
     */
    @TableId(value = "grant_id")
    private Long grantId;

    /**
     * 人才主档ID
     */
    private Long talentId;

    /**
     * 被授权主体类型（user用户/role角色/company_dept公司部门/dept部门等稳定编码，
     * 取 {@code GrantSubject.TYPE_*} 常量）
     */
    private String granteeType;

    /**
     * 被授权主体ID（与 {@link #granteeType} 成对使用）
     */
    private Long granteeId;

    /**
     * 授权级别（summary/detail/attachment，取 {@code TalentPermissionLevelEnum} 的稳定编码，
     * 字典 talent_permission_level）
     */
    private String permissionLevel;

    /**
     * 授权有效期起（为空表示立即生效）
     */
    private LocalDateTime validFrom;

    /**
     * 授权有效期止（为空表示长期有效）
     */
    private LocalDateTime validTo;

    /**
     * 授权事由（必填，用于事后审计追溯）
     */
    private String grantReason;

    /**
     * 授权人用户ID
     */
    private Long grantedBy;

    /**
     * 授权时间
     */
    private LocalDateTime grantedTime;

    /**
     * 是否已撤销（0否 1是，撤销后不再参与可见范围判定）
     */
    private String revokeFlag;

    /**
     * 撤销人用户ID
     */
    private Long revokedBy;

    /**
     * 撤销时间
     */
    private LocalDateTime revokedTime;

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
