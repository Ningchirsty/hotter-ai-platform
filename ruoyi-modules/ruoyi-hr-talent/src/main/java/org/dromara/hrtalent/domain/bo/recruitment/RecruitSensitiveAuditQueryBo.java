package org.dromara.hrtalent.domain.bo.recruitment;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 敏感操作审计查询业务对象 hr_recruit_sensitive_audit。
 *
 * <p>审计表是<b>追加型</b>表：只插入、不修改、不物理删除，因此本对象只承载查询条件
 * （SPEC-P3 §2.5、§3.6）。</p>
 *
 * @author hr-talent
 */
@Data
public class RecruitSensitiveAuditQueryBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 事件类型（phone_view/attachment_preview/attachment_download/background_view/export 等稳定编码）
     */
    private String eventType;

    /**
     * 业务类型（talent/application/interview/background/attachment 等稳定编码）
     */
    private String bizType;

    /**
     * 业务对象ID
     */
    private Long bizId;

    /**
     * 操作人用户ID
     */
    private Long operatorId;

    /**
     * 操作结果（success/denied/failed 等稳定编码）
     */
    private String result;

    /**
     * 事件时间起（含）
     */
    private LocalDateTime eventTimeBegin;

    /**
     * 事件时间止（含）
     */
    private LocalDateTime eventTimeEnd;

}
