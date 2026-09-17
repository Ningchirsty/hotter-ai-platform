package org.dromara.talent.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 人才库敏感操作审计对象 tl_sensitive_audit
 *
 * @author talent
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("tl_sensitive_audit")
public class TlSensitiveAudit implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 审计ID
     */
    @TableId(value = "audit_id")
    private Long auditId;

    /**
     * 操作人用户ID
     */
    private Long operatorId;

    /**
     * 操作人账号（冗余，便于离线审计）
     */
    private String operatorName;

    /**
     * 动作（VIEW_DETAIL/VIEW_FULL_PHONE/DOWNLOAD/EXPORT/CREATE_GRANT/DELETE/ARCHIVE/UPLOAD）
     */
    private String action;

    /**
     * 对象类型（TALENT/ATTACHMENT/EXPORT/GRANT/PARSE_TASK）
     */
    private String targetType;

    /**
     * 对象ID
     */
    private Long targetId;

    /**
     * 关联人才ID（便于按人追溯）
     */
    private Long talentId;

    /**
     * 结果（0成功 1失败）
     */
    private String result;

    /**
     * 原因/失败摘要
     */
    private String reason;

    /**
     * 客户端IP摘要（不存原始IP）
     */
    private String ipDigest;

    /**
     * User-Agent 摘要
     */
    private String userAgentDigest;

    /**
     * 操作时间
     */
    private LocalDateTime operateTime;

}
