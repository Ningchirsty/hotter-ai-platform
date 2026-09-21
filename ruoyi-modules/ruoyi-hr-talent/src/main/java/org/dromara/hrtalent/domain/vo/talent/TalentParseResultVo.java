package org.dromara.hrtalent.domain.vo.talent;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.hrtalent.domain.entity.TalentParseResult;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 简历候选解析结果视图对象 hr_talent_parse_result（SPEC-P4 §2.1 / 设计文档 §8.21）。
 *
 * <p><b>低置信度默认不勾选</b>：{@link #defaultSelected} 由服务层按置信度阈值计算
 * （阈值常量见 {@code TalentParseServiceImpl.DEFAULT_CONFIDENCE_THRESHOLD}，
 * 可由 {@code hrtalent.resume-parse-confidence-threshold} 配置覆盖），
 * 前端据此预置勾选状态；最终是否写入正式字段由人工确认决定。</p>
 *
 * <p><b>敏感值</b>：{@link #rawValue} 来自简历原文，只允许在受控复核界面展示，
 * 禁止写入日志；{@link #normalizedValue} 是人工确认后可写入正式字段的取值。</p>
 *
 * @author hr-talent
 */
@Data
@AutoMapper(target = TalentParseResult.class)
public class TalentParseResultVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 解析结果ID
     */
    private Long resultId;

    /**
     * 解析任务ID
     */
    private Long taskId;

    /**
     * 字段路径（如 education[0].school_name）
     */
    private String fieldPath;

    /**
     * 简历原始值（禁止写入日志）
     */
    private String rawValue;

    /**
     * 标准化值（人工确认后可写入正式字段）
     */
    private String normalizedValue;

    /**
     * 置信度（0~1）
     */
    private BigDecimal confidence;

    /**
     * 来源位置（页码或文本偏移）
     */
    private String sourceLocation;

    /**
     * 复核状态（字典 {@code talent_resume_review_status} 编码：pending/reviewing/confirmed/rejected）
     */
    private String reviewStatus;

    /**
     * 复核状态标签（字典 {@code talent_resume_review_status}）。
     *
     * <p>字典已补齐 {@code rejected}（已否决），因此标签<b>统一由字典翻译产出</b>，
     * 服务层不再维护第二套映射（避免两套标签来源不一致）。</p>
     */
    @Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "reviewStatus", other = "talent_resume_review_status")
    private String reviewStatusLabel;

    /**
     * 复核人用户ID
     */
    private Long reviewedBy;

    /**
     * 复核人昵称（由 {@link #reviewedBy} 翻译）
     */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "reviewedBy")
    private String reviewedByName;

    /**
     * 复核时间
     */
    private LocalDateTime reviewedTime;

    /**
     * 是否默认勾选（服务层按置信度阈值计算；低置信度默认不勾选）
     */
    private Boolean defaultSelected;

    /**
     * 备注
     */
    private String remark;

}
