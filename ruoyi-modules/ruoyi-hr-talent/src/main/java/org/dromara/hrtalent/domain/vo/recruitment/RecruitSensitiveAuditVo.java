package org.dromara.hrtalent.domain.vo.recruitment;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.hrtalent.domain.entity.RecruitSensitiveAudit;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 敏感操作审计出参 hr_recruit_sensitive_audit。
 * <p>审计记录本身不含电话明文、简历正文与背调明细，因此可按 {@code PERM_AUDIT_LIST} 直接返回。</p>
 *
 * @author hr-talent
 */
@Data
@AutoMapper(target = RecruitSensitiveAudit.class)
public class RecruitSensitiveAuditVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 审计ID
     */
    @ExcelProperty(value = "审计ID")
    private Long auditId;

    /**
     * 事件类型（稳定编码）
     */
    @ExcelProperty(value = "事件类型")
    private String eventType;

    /**
     * 业务类型（稳定编码）
     */
    @ExcelProperty(value = "业务类型")
    private String bizType;

    /**
     * 业务对象ID
     */
    @ExcelProperty(value = "业务对象ID")
    private Long bizId;

    /**
     * 操作人用户ID
     */
    private Long operatorId;

    /**
     * 操作人账号
     */
    @ExcelProperty(value = "操作人")
    private String operatorName;

    /**
     * 操作事由/用途
     */
    @ExcelProperty(value = "用途")
    private String purpose;

    /**
     * 操作结果（稳定编码）
     */
    @ExcelProperty(value = "结果")
    private String result;

    /**
     * 客户端IP
     */
    @ExcelProperty(value = "IP")
    private String ip;

    /**
     * 客户端 User-Agent
     */
    private String userAgent;

    /**
     * 附加明细快照（脱敏 JSON）
     */
    private String detailJson;

    /**
     * 事件时间
     */
    @ExcelProperty(value = "事件时间")
    private LocalDateTime eventTime;

    /**
     * 事件时间文本（导出用，避免导出格式随环境变化）
     */
    @ExcelProperty(value = "事件时间文本")
    private String eventTimeText;

    /**
     * 创建人用户ID
     */
    private Long createBy;

    /**
     * 创建人昵称（由 createBy 翻译，登录态缺失时不回填）
     */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "createBy")
    private String createByName;

}
