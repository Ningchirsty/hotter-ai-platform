package org.dromara.talent.domain;

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
 * 人才单条访问授权对象 tl_talent_access_grant
 *
 * @author talent
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tl_talent_access_grant")
public class TlTalentAccessGrant extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 授权ID
     */
    @TableId(value = "grant_id")
    private Long grantId;

    /**
     * 人才ID
     */
    private Long talentId;

    /**
     * 被授权主体类型（USER用户 ROLE角色）
     */
    private String granteeType;

    /**
     * 被授权主体ID
     */
    private Long granteeId;

    /**
     * 授权动作（VIEW/DOWNLOAD/VIEW_FULL_PHONE，逗号分隔多值）
     */
    private String permission;

    /**
     * 生效时间（空表示立即生效）
     */
    private LocalDateTime startTime;

    /**
     * 失效时间（空表示长期，需审批）
     */
    private LocalDateTime endTime;

    /**
     * 授权人
     */
    private Long grantBy;

    /**
     * 授权时间
     */
    private LocalDateTime grantTime;

    /**
     * 授权原因（审计用）
     */
    private String reason;

    /**
     * 状态（0正常 1已撤销）
     */
    private String status;

    /**
     * 删除标志（0代表存在 1代表删除）
     */
    @TableLogic
    private String delFlag;

}
