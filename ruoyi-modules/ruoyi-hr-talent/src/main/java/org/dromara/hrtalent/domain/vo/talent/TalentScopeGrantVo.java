package org.dromara.hrtalent.domain.vo.talent;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.hrtalent.domain.entity.TalentScopeGrant;
import org.dromara.hrtalent.enums.TalentPermissionLevelEnum;
import org.dromara.hrtalent.support.GrantSubject;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 人才共享授权视图对象 hr_talent_scope_grant（SPEC-P4 §2.4、设计文档 §8.19）。
 *
 * <p><b>共享只扩大查看范围</b>（设计文档 §8.19）：本 VO 的 {@code permissionLevel} 只表示
 * 「查看该人才的资料级别」，<b>不</b>代表已获得电话明文、附件下载、背调查看或导出权限；
 * 上述动作仍需各自的按钮权限与 {@code TalentScopeDomainService#checkPermissionLevel} 判定。</p>
 *
 * <p>被授权主体为「用户」时回填昵称（由 {@link #getGranteeIdText()} 取数）；
 * 角色/公司部门/部门的名称由前端按 ID 展示或后续补翻译，本 VO 不臆造名称。</p>
 *
 * @author hr-talent
 */
@Data
@AutoMapper(target = TalentScopeGrant.class, reverseConvertGenerate = false)
public class TalentScopeGrantVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 授权ID
     */
    private Long grantId;

    /**
     * 人才主档ID
     */
    private Long talentId;

    /**
     * 被授权主体类型（user/role/company_dept/dept）
     */
    private String granteeType;

    /**
     * 被授权主体类型中文标签
     */
    private String granteeTypeLabel;

    /**
     * 被授权主体ID
     */
    private Long granteeId;

    /**
     * 被授权主体为「用户」时的昵称（由 {@link #getGranteeIdText()} 翻译，非用户主体为 null）
     */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "granteeIdText")
    private String granteeName;

    /**
     * 授权级别（summary/detail/attachment）
     */
    private String permissionLevel;

    /**
     * 授权级别中文标签
     */
    private String permissionLevelLabel;

    /**
     * 授权有效期起
     */
    private LocalDateTime validFrom;

    /**
     * 授权有效期止（为空表示长期有效）
     */
    private LocalDateTime validTo;

    /**
     * 授权事由
     */
    private String grantReason;

    /**
     * 授权人用户ID
     */
    private Long grantedBy;

    /**
     * 授权人昵称（由 {@link #grantedBy} 翻译）
     */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "grantedBy")
    private String grantedByName;

    /**
     * 授权时间
     */
    private LocalDateTime grantedTime;

    /**
     * 是否已撤销（0否 1是）
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
     * 被授权主体为「用户」时返回其ID串，其它主体类型返回 null。
     *
     * <p>只读属性：作为 {@link #granteeName} 翻译的取数来源；主体类型不匹配时返回 null，
     * 可避免把「角色ID / 部门ID」误当用户ID翻译成无关昵称。</p>
     *
     * @return 用户ID字符串，非用户主体返回 null
     */
    @JsonIgnore
    public String getGranteeIdText() {
        if (!GrantSubject.TYPE_USER.equals(granteeType) || granteeId == null) {
            return null;
        }
        return String.valueOf(granteeId);
    }

    /**
     * 被授权主体类型中文标签。
     *
     * @return 中文标签，未知编码返回 null
     */
    public String getGranteeTypeLabel() {
        return switch (granteeType == null ? "" : granteeType) {
            case GrantSubject.TYPE_USER -> "用户";
            case GrantSubject.TYPE_ROLE -> "角色";
            case GrantSubject.TYPE_COMPANY_DEPT -> "公司部门";
            case GrantSubject.TYPE_DEPT -> "部门";
            default -> null;
        };
    }

    /**
     * 授权级别中文标签。
     *
     * @return 中文标签，未知编码返回 null
     */
    public String getPermissionLevelLabel() {
        TalentPermissionLevelEnum item = TalentPermissionLevelEnum.find(permissionLevel);
        return item == null ? null : item.getDesc();
    }

}
