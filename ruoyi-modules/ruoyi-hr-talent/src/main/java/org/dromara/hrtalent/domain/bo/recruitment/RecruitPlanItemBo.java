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
import org.dromara.hrtalent.domain.entity.RecruitPlanItem;

import java.io.Serial;
import java.io.Serializable;

/**
 * 月度招聘计划任务业务对象 hr_recruit_plan_item（SPEC-P2 §3.2 / §4.2）。
 *
 * <p><b>服务端权威字段</b>：{@code planId}、{@code planMonth}、{@code companyDeptId}、
 * {@code sourceType}、{@code itemNo}、三个状态维度与人数统计均不接受前端写入；
 * 编辑接口只允许调整 {@code jobName}、{@code planQty}、{@code ownerId}、{@code urgency}、
 * {@code standardDays}、{@code carryoverEnabled}、{@code useDeptId}/{@code useDeptName}
 * 与 {@code remark}（SPEC-P2 §3.2「编辑（仅可改允许字段）」）。</p>
 *
 * <p>每次新增都创建独立记录，即使公司、部门、岗位、人数完全相同也<b>不合并</b>；
 * 相似性只通过 {@code /recruit/plan-items/similar} 提示。</p>
 *
 * @author hr-talent
 */
@Data
@AutoMapper(target = RecruitPlanItem.class, reverseConvertGenerate = false)
public class RecruitPlanItemBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 计划任务ID（编辑时必填）
     */
    @NotNull(message = "计划任务ID不能为空", groups = {EditGroup.class})
    private Long itemId;

    /**
     * 所属月度计划表头ID（新增时由路径提供，建议必填）
     */
    private Long planId;

    /**
     * 来源招聘需求ID
     */
    private Long demandId;

    /**
     * 关联岗位执行项ID
     */
    private Long jobId;

    /**
     * 用工部门ID
     */
    private Long useDeptId;

    /**
     * 用工部门名称快照
     */
    @Size(max = 100, message = "用工部门名称长度不能超过 100", groups = {AddGroup.class, EditGroup.class})
    private String useDeptName;

    /**
     * 岗位名称快照
     */
    @NotBlank(message = "岗位名称不能为空", groups = {AddGroup.class})
    @Size(max = 200, message = "岗位名称长度不能超过 200", groups = {AddGroup.class, EditGroup.class})
    private String jobName;

    /**
     * 计划人数（新增必填且必须大于 0）
     */
    @NotNull(message = "计划人数不能为空", groups = {AddGroup.class})
    @Min(value = 1, message = "计划人数必须大于 0", groups = {AddGroup.class, EditGroup.class})
    private Integer planQty;

    /**
     * 任务负责人用户ID
     */
    private Long ownerId;

    /**
     * 紧急程度（字典 recruit_urgency 的稳定编码：normal/urgent/very_urgent）
     */
    @Pattern(regexp = "^(normal|urgent|very_urgent)?$",
        message = "紧急程度不合法，请使用字典 recruit_urgency 的编码（normal/urgent/very_urgent）",
        groups = {AddGroup.class, EditGroup.class})
    private String urgency;

    /**
     * 招聘期限标准天数
     */
    @Min(value = 0, message = "招聘期限标准天数不能为负数", groups = {AddGroup.class, EditGroup.class})
    private Integer standardDays;

    /**
     * 是否允许自动结转（0否 1是）
     */
    @Pattern(regexp = "^(0|1)?$", message = "是否允许自动结转只能为 0 或 1", groups = {AddGroup.class, EditGroup.class})
    private String carryoverEnabled;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过 500", groups = {AddGroup.class, EditGroup.class})
    private String remark;

}
