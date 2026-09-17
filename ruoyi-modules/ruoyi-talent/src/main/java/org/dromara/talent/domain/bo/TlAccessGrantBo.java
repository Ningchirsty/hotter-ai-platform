package org.dromara.talent.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.talent.domain.TlTalentAccessGrant;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 人才单条访问授权业务对象 tl_talent_access_grant
 *
 * @author talent
 */
@Data
@AutoMapper(target = TlTalentAccessGrant.class, reverseConvertGenerate = false)
public class TlAccessGrantBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 授权ID
     */
    @NotNull(message = "授权ID不能为空", groups = {EditGroup.class})
    private Long grantId;

    /**
     * 人才ID
     */
    @NotNull(message = "人才ID不能为空", groups = {AddGroup.class, EditGroup.class})
    private Long talentId;

    /**
     * 被授权主体类型（USER用户 ROLE角色）
     */
    @NotBlank(message = "被授权主体类型不能为空", groups = {AddGroup.class, EditGroup.class})
    private String granteeType;

    /**
     * 被授权主体ID（USER 时为 user_id，ROLE 时为 role_id）
     */
    @NotNull(message = "被授权主体ID不能为空", groups = {AddGroup.class, EditGroup.class})
    private Long granteeId;

    /**
     * 授权动作集合（VIEW/DOWNLOAD/VIEW_FULL_PHONE，落库时以逗号拼接）
     */
    @NotEmpty(message = "授权动作不能为空", groups = {AddGroup.class, EditGroup.class})
    private List<String> permissions;

    /**
     * 生效时间（空表示立即生效）
     */
    private LocalDateTime startTime;

    /**
     * 失效时间（空表示长期）
     */
    private LocalDateTime endTime;

    /**
     * 授权原因（审计用）
     */
    @Size(max = 255, message = "授权原因长度不能超过255个字符", groups = {AddGroup.class, EditGroup.class})
    private String reason;

}
