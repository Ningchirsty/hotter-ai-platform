package org.dromara.hrtalent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 人才教育经历对象 hr_talent_education（设计文档 §8.14、SPEC-P4 §2.2 B 线）。
 *
 * <p><b>归属关系</b>：本表是人才主档 {@code hr_talent_profile} 的子表，通过 {@link #talentId} 关联，
 * 一条主档可有零到多条教育经历；访问、写入、删除前都必须先经人才可见范围校验
 * （{@code TalentScopeDomainService} 为唯一权威，见 {@code TalentExperienceServiceImpl}）。</p>
 *
 * <p><b>来源与解析边界</b>（设计文档 §8.14）：{@link #sourceType} 表达本条经历的来源
 * （{@code manual} 人工录入 / {@code import} Excel 导入 / {@code resume} 简历解析 /
 * {@code parse} 解析任务），{@link #resumeId} 记录来源简历版本ID。
 * <b>解析产物在人工确认前保存在 {@code hr_talent_parse_result} 候选结果中，绝不直接写入本表</b>。</p>
 *
 * <p><b>字段口径</b>：学历 {@link #education} 与学位 {@link #degree} 在 DDL 注释中标注为「字典编码」，
 * 但设计文档 §10 与 {@code hr_talent_menu.sql} 均<b>未定义</b>对应字典组，
 * 因此本实体只保存稳定编码、不在此处做编码枚举约束（同 P3 {@code TalentProfile.highestEducation} 口径）。</p>
 *
 * @author hr-talent
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hr_talent_education")
public class TalentEducation extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 教育经历ID（主键）
     */
    @TableId(value = "education_id")
    private Long educationId;

    /**
     * 人才主档ID（归属主档，不可为空）
     */
    private Long talentId;

    /**
     * 学校名称
     */
    private String schoolName;

    /**
     * 专业
     */
    private String major;

    /**
     * 学历（字典编码，字典组见类注释说明）
     */
    private String education;

    /**
     * 学位（字典编码，字典组见类注释说明）
     */
    private String degree;

    /**
     * 入学日期
     */
    private LocalDate startDate;

    /**
     * 毕业日期
     */
    private LocalDate endDate;

    /**
     * 是否全日制（0否 1是）
     */
    private String fullTimeFlag;

    /**
     * 来源类型（manual/import/resume/parse 等稳定编码）
     */
    private String sourceType;

    /**
     * 来源简历版本ID（人工确认为准，仅解析/导入来源填写）
     */
    private Long resumeId;

    /**
     * 排序号（倒序展示用）
     */
    private Integer sortNo;

    /**
     * 删除标志（0代表存在 1代表删除）
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注（设计文档 §8.14 的「说明」）
     */
    private String remark;

}
