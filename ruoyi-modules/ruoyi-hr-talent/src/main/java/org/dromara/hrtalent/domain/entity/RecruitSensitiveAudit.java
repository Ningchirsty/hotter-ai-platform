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
 * 敏感操作审计对象 hr_recruit_sensitive_audit。
 * <p>追加型审计表：只插入、不做物理删除（{@code del_flag} 保留以对齐通用字段）。字段严格对齐
 * {@code script/sql/hr_recruit.sql} 的建表语句。</p>
 *
 * <p><b>安全约束</b>：{@link #detailJson} 只允许写入脱敏后的结构化明细，
 * <b>禁止</b>写入电话明文、简历正文、背调明细或对象存储长期地址（设计文档 §15.3、§21.9）。</p>
 *
 * <p>业务代码请统一通过 {@code org.dromara.hrtalent.support.SensitiveAuditRecorder} 写入，
 * 不要各自拼装本实体。</p>
 *
 * @author hr-talent
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hr_recruit_sensitive_audit")
public class RecruitSensitiveAudit extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 审计ID（主键）
     */
    @TableId(value = "audit_id")
    private Long auditId;

    /**
     * 事件类型（phone_view/attachment_download/background_view/export 等稳定编码）
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
     * 操作人姓名快照
     */
    private String operatorName;

    /**
     * 操作事由/用途（授权与审计追溯用）
     */
    private String purpose;

    /**
     * 操作结果（success/denied/failed 等稳定编码）
     */
    private String result;

    /**
     * 客户端IP
     */
    private String ip;

    /**
     * 客户端 User-Agent
     */
    private String userAgent;

    /**
     * 附加明细快照（结构化 JSON，禁止写入敏感明文）
     */
    private String detailJson;

    /**
     * 事件时间
     */
    private LocalDateTime eventTime;

    /**
     * 删除标志（0代表存在 1代表删除；审计表不物理删除）
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注
     */
    private String remark;

}
