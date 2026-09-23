package org.dromara.hrtalent.domain.bo.recruitment;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.hrtalent.domain.entity.RecruitStandard;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 招聘期限标准业务对象 hr_recruit_standard。
 *
 * @author hr-talent
 */
@Data
@AutoMapper(target = RecruitStandard.class, reverseConvertGenerate = false)
public class RecruitStandardBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 标准ID（编辑时必填）
     */
    @NotNull(message = "标准ID不能为空", groups = {EditGroup.class})
    private Long standardId;

    /**
     * 适用岗位名称（必填）
     */
    @NotBlank(message = "岗位名称不能为空", groups = {AddGroup.class, EditGroup.class})
    @Size(max = 200, message = "岗位名称长度不能超过 200", groups = {AddGroup.class, EditGroup.class})
    private String jobName;

    /**
     * 公司（平台部门）ID；留空表示集团通用
     */
    private Long companyDeptId;

    /**
     * 公司名称快照
     */
    @Size(max = 100, message = "公司名称长度不能超过 100", groups = {AddGroup.class, EditGroup.class})
    private String companyName;

    /**
     * 招聘期限标准天数（必填，非负）
     */
    @NotNull(message = "招聘期限标准天数不能为空", groups = {AddGroup.class, EditGroup.class})
    @Min(value = 0, message = "招聘期限标准天数不能为负数", groups = {AddGroup.class, EditGroup.class})
    private Integer standardDays;

    /**
     * 生效日期
     */
    private LocalDate effectiveDate;

    /**
     * 失效日期（不得早于生效日期，由服务层校验）
     */
    private LocalDate expiryDate;

    /**
     * 状态（active 生效 / inactive 停用）
     */
    @Pattern(regexp = "^(active|inactive)?$", message = "状态只能为 active 或 inactive",
        groups = {AddGroup.class, EditGroup.class})
    private String status;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过 500", groups = {AddGroup.class, EditGroup.class})
    private String remark;

}
