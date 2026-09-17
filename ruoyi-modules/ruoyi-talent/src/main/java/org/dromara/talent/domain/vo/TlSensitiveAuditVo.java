package org.dromara.talent.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.talent.domain.TlSensitiveAudit;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 人才库敏感操作审计视图对象 tl_sensitive_audit
 *
 * @author talent
 */
@Data
@AutoMapper(target = TlSensitiveAudit.class)
public class TlSensitiveAuditVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 审计ID
     */
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
     * 动作说明（服务端按 AuditActionEnum 生成）
     */
    private String actionLabel;

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
     * 原因/失败摘要（禁止写入完整手机号、Token、简历正文、对象存储 URL）
     */
    private String reason;

    /**
     * 客户端IP摘要（不存原始IP）
     */
    private String ipDigest;

    /**
     * 操作时间
     */
    private LocalDateTime operateTime;

}
