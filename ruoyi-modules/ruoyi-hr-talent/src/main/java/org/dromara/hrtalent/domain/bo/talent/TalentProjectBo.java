package org.dromara.hrtalent.domain.bo.talent;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.hrtalent.domain.entity.TalentProject;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 项目经历业务对象 hr_talent_project（SPEC-P4 §2.2 B 线）。
 *
 * <p><b>服务端权威字段</b>：{@link #talentId} 由 Controller 从路径注入并覆盖前端传值；
 * {@link #workId} 若填写，服务层校验其存在且<b>属于同一人才</b>。</p>
 *
 * @author hr-talent
 */
@Data
@AutoMapper(target = TalentProject.class, reverseConvertGenerate = false)
public class TalentProjectBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 项目经历ID（编辑时必填）
     */
    @NotNull(message = "项目经历ID不能为空", groups = {EditGroup.class})
    private Long projectId;

    /**
     * 人才主档ID（服务端按路径注入，前端传值一律忽略）
     */
    private Long talentId;

    /**
     * 项目名称
     */
    @NotBlank(message = "项目名称不能为空", groups = {AddGroup.class, EditGroup.class})
    @Size(max = 200, message = "项目名称长度不能超过 200", groups = {AddGroup.class, EditGroup.class})
    private String projectName;

    /**
     * 项目角色
     */
    @Size(max = 100, message = "项目角色长度不能超过 100", groups = {AddGroup.class, EditGroup.class})
    private String projectRole;

    /**
     * 项目开始日期
     */
    private LocalDate startDate;

    /**
     * 项目结束日期（不得早于开始日期）
     */
    private LocalDate endDate;

    /**
     * 项目描述（设计文档 §8.14 的「项目说明」）
     */
    private String description;

    /**
     * 项目职责
     */
    private String responsibility;

    /**
     * 项目业绩（设计文档 §8.14 的「成果」）
     */
    private String achievement;

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
     * 关联工作经历ID（可空，必须属于同一人才）
     */
    private Long workId;

    /**
     * 排序号（倒序展示用）
     */
    private Integer sortNo;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过 500", groups = {AddGroup.class, EditGroup.class})
    private String remark;

}
