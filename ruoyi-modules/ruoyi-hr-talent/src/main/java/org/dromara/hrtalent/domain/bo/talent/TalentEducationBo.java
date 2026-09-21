package org.dromara.hrtalent.domain.bo.talent;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.hrtalent.domain.entity.TalentEducation;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 教育经历业务对象 hr_talent_education（SPEC-P4 §2.2 B 线）。
 *
 * <p><b>服务端权威字段</b>：{@link #talentId} 由 Controller 从路径 {@code /talent/profiles/{id}/educations}
 * 注入并覆盖前端传值，防止越权把经历写进别人的人才档案；{@link #resumeId} 只在解析/导入来源时由
 * 服务端（人工确认流程）填写。</p>
 *
 * <p><b>解析边界</b>：本对象只承载<b>已确认</b>的正式经历；简历解析候选结果属于 A 线的
 * {@code hr_talent_parse_result}，在人工确认前不得经本对象写入正式表（设计文档 §8.14）。</p>
 *
 * @author hr-talent
 */
@Data
@AutoMapper(target = TalentEducation.class, reverseConvertGenerate = false)
public class TalentEducationBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 教育经历ID（编辑时必填）
     */
    @NotNull(message = "教育经历ID不能为空", groups = {EditGroup.class})
    private Long educationId;

    /**
     * 人才主档ID（服务端按路径注入，前端传值一律忽略）
     */
    private Long talentId;

    /**
     * 学校名称
     */
    @NotBlank(message = "学校名称不能为空", groups = {AddGroup.class, EditGroup.class})
    @Size(max = 200, message = "学校名称长度不能超过 200", groups = {AddGroup.class, EditGroup.class})
    private String schoolName;

    /**
     * 专业
     */
    @Size(max = 200, message = "专业长度不能超过 200", groups = {AddGroup.class, EditGroup.class})
    private String major;

    /**
     * 学历（字典编码，字典组缺失见实体类注释）
     */
    @Size(max = 32, message = "学历编码长度不能超过 32", groups = {AddGroup.class, EditGroup.class})
    private String education;

    /**
     * 学位（字典编码，字典组缺失见实体类注释）
     */
    @Size(max = 32, message = "学位编码长度不能超过 32", groups = {AddGroup.class, EditGroup.class})
    private String degree;

    /**
     * 入学日期
     */
    private LocalDate startDate;

    /**
     * 毕业日期（不得早于入学日期）
     */
    private LocalDate endDate;

    /**
     * 是否全日制（0否 1是，缺省 1）
     */
    private String fullTimeFlag;

    /**
     * 来源类型（manual/import/resume/parse，缺省 manual）
     */
    @Size(max = 32, message = "来源类型长度不能超过 32", groups = {AddGroup.class, EditGroup.class})
    private String sourceType;

    /**
     * 来源简历版本ID（解析/导入来源必须填写）
     */
    private Long resumeId;

    /**
     * 排序号（倒序展示用）
     */
    private Integer sortNo;

    /**
     * 备注（设计文档 §8.14 的「说明」）
     */
    @Size(max = 500, message = "备注长度不能超过 500", groups = {AddGroup.class, EditGroup.class})
    private String remark;

}
