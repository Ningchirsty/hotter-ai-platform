package org.dromara.hrtalent.domain.bo.talent;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.hrtalent.domain.entity.TalentScopeGrant;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 人才共享授权业务对象 hr_talent_scope_grant（SPEC-P4 §2.4、设计文档 §8.19）。
 *
 * <p><b>共享只扩大查看范围</b>（设计文档 §8.19）：本对象只表达「谁能看这位人才的哪个级别」，
 * <b>不</b>表达也不授予电话明文、附件下载、背调查看与导出权限——那些动作各有独立按钮权限，
 * 且必须另行通过 {@code TalentScopeDomainService#checkPermissionLevel} 判定。</p>
 *
 * <p><b>服务端权威字段</b>：授权ID、授权人、授权时间、撤销标记与撤销人/撤销时间均由服务端维护，
 * 不在本对象内。</p>
 *
 * @author hr-talent
 */
@Data
@AutoMapper(target = TalentScopeGrant.class, reverseConvertGenerate = false)
public class TalentScopeGrantBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 人才主档ID（新增时必填；{@code POST /talent/profiles/{id}/grants} 的路径参数会覆盖为路径值）
     */
    @NotNull(message = "人才ID不能为空", groups = {AddGroup.class})
    private Long talentId;

    /**
     * 被授权主体类型（user/role/company_dept/dept，取 {@code GrantSubject.TYPE_*} 常量）
     */
    @NotBlank(message = "被授权主体类型不能为空", groups = {AddGroup.class})
    @Size(max = 32, message = "被授权主体类型长度不能超过 32", groups = {AddGroup.class})
    private String granteeType;

    /**
     * 被授权主体ID（与 {@link #granteeType} 成对，不同类型主体的ID不得混用）
     */
    @NotNull(message = "被授权主体ID不能为空", groups = {AddGroup.class})
    private Long granteeId;

    /**
     * 授权级别（summary/detail/attachment，取 {@code TalentPermissionLevelEnum} 的稳定编码）
     */
    @NotBlank(message = "授权级别不能为空", groups = {AddGroup.class})
    @Size(max = 32, message = "授权级别长度不能超过 32", groups = {AddGroup.class})
    private String permissionLevel;

    /**
     * 授权有效期起（为空表示立即生效）
     */
    private LocalDateTime validFrom;

    /**
     * 授权有效期止（为空表示长期有效；不为空时必须晚于有效期起）
     */
    private LocalDateTime validTo;

    /**
     * 授权事由（必填，用于事后审计追溯）
     */
    @NotBlank(message = "授权事由不能为空", groups = {AddGroup.class})
    @Size(max = 500, message = "授权事由长度不能超过 500", groups = {AddGroup.class})
    private String grantReason;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过 500", groups = {AddGroup.class})
    private String remark;

}
