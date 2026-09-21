package org.dromara.hrtalent.domain.bo.recruitment;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 招聘业务附件列表查询业务对象。
 *
 * <p>按 {@code biz_type + biz_id + current_flag} 组合过滤（表上有对应索引
 * {@code idx_hr_recruit_attachment_biz}）；业务类型与业务对象ID为必填，
 * 避免在无索引条件下做全表扫描（SPEC-P3 §3.5）。</p>
 *
 * @author hr-talent
 */
@Data
public class RecruitAttachmentQueryBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 业务类型（必填）
     */
    private String bizType;

    /**
     * 业务对象ID（必填）
     */
    private Long bizId;

    /**
     * 附件类型（字典 recruit_attachment_type 编码，精确匹配）
     */
    private String fileType;

    /**
     * 是否当前版本（0否 1是，精确匹配）
     */
    private String currentFlag;

    /**
     * 安全级别（字典 recruit_data_level 编码，精确匹配）
     */
    private String securityLevel;

    /**
     * 上传人用户ID（精确匹配）
     */
    private Long uploadedBy;

    /**
     * 上传时间起（含）
     */
    private LocalDateTime uploadedTimeBegin;

    /**
     * 上传时间止（含）
     */
    private LocalDateTime uploadedTimeEnd;

}
