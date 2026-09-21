package org.dromara.hrtalent.domain.bo.recruitment;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.hrtalent.domain.entity.RecruitPlan;

import java.io.Serial;
import java.io.Serializable;

/**
 * 公司月度招聘计划表头业务对象 hr_recruit_plan（SPEC-P2 §3.2 / §4.2）。
 *
 * <p>{@code company_dept_id + plan_month} 唯一：服务层在新增时判定重复并给出中文提示。
 * 计划月份一旦落库<b>不允许修改</b>（禁止通过改月份迁移原月任务）。</p>
 *
 * @author hr-talent
 */
@Data
@AutoMapper(target = RecruitPlan.class, reverseConvertGenerate = false)
public class RecruitPlanBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 计划ID（编辑时必填）
     */
    @NotNull(message = "计划ID不能为空", groups = {EditGroup.class})
    private Long planId;

    /**
     * 公司（平台部门）ID（新增时必填）
     */
    @NotNull(message = "公司不能为空", groups = {AddGroup.class})
    private Long companyDeptId;

    /**
     * 公司名称快照
     */
    @Size(max = 100, message = "公司名称长度不能超过 100", groups = {AddGroup.class, EditGroup.class})
    private String companyName;

    /**
     * 计划月份（yyyy-MM，新增时必填；落库后不允许修改）
     */
    @NotBlank(message = "计划月份不能为空", groups = {AddGroup.class})
    @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$", message = "计划月份格式必须为 yyyy-MM",
        groups = {AddGroup.class})
    private String planMonth;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过 500", groups = {AddGroup.class, EditGroup.class})
    private String remark;

}
