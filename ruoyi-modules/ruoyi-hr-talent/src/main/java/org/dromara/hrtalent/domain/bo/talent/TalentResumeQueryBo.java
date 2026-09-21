package org.dromara.hrtalent.domain.bo.talent;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 人才简历版本列表查询业务对象（SPEC-P4 §2.1）。
 *
 * <p>按 {@code talent_id} 主子过滤（表上有 {@code idx_hr_talent_resume_current} 与
 * {@code uk_hr_talent_resume_version} 支撑），其余条件均为可选精确匹配。</p>
 *
 * @author hr-talent
 */
@Data
public class TalentResumeQueryBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 是否当前版本（0否 1是，精确匹配）
     */
    private String currentFlag;

    /**
     * 解析状态（字典 talent_resume_parse_status 编码，精确匹配）
     */
    private String parseStatus;

    /**
     * 复核状态（字典 talent_resume_parse_status 编码，精确匹配）
     */
    private String reviewStatus;

    /**
     * 文件安全扫描状态（精确匹配）
     */
    private String scanStatus;

    /**
     * 文件后缀（小写、不含点，精确匹配）
     */
    private String fileSuffix;

    /**
     * 简历来源（精确匹配）
     */
    private String sourceType;

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
