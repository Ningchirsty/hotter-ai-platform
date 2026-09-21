package org.dromara.hrtalent.domain.bo.recruitment;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.hrtalent.converter.AssistantIdsConverter;
import org.dromara.hrtalent.domain.entity.RecruitJob;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 招聘岗位执行项业务对象 hr_recruit_job。
 * <p>入参只覆盖岗位可编辑字段：岗位编号由服务端生成、关闭日期由关闭动作维护，
 * 均不在本 BO 内，避免前端越权改写。</p>
 *
 * <p>招聘形式、紧急程度、状态与薪资周期在服务层按枚举/业务规则校验，不使用裸字符串常量。</p>
 *
 * @author hr-talent
 */
@Data
@AutoMapper(target = RecruitJob.class, reverseConvertGenerate = false, uses = AssistantIdsConverter.class)
public class RecruitJobBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 岗位执行项ID（编辑时必填）
     */
    @NotNull(message = "岗位ID不能为空", groups = {EditGroup.class})
    private Long jobId;

    /**
     * 来源招聘需求ID
     */
    private Long demandId;

    /**
     * 关联月度计划任务ID
     */
    private Long planItemId;

    /**
     * 岗位名称（建议取自招聘岗位标准库，不共用 sys_post）
     */
    @NotBlank(message = "岗位名称不能为空", groups = {AddGroup.class, EditGroup.class})
    @Size(max = 200, message = "岗位名称长度不能超过 200", groups = {AddGroup.class, EditGroup.class})
    private String jobName;

    /**
     * 公司（平台部门）ID
     */
    private Long companyDeptId;

    /**
     * 公司名称快照
     */
    @Size(max = 100, message = "公司名称长度不能超过 100", groups = {AddGroup.class, EditGroup.class})
    private String companyName;

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
     * 岗位职级（字典编码）
     */
    @Size(max = 32, message = "岗位职级长度不能超过 32", groups = {AddGroup.class, EditGroup.class})
    private String jobLevel;

    /**
     * 工作城市
     */
    @Size(max = 64, message = "工作城市长度不能超过 64", groups = {AddGroup.class, EditGroup.class})
    private String workCity;

    /**
     * 岗位职责
     */
    private String responsibility;

    /**
     * 任职要求
     */
    private String qualification;

    /**
     * 薪资低值（不得大于薪资高值）
     */
    @DecimalMin(value = "0", message = "薪资低值不能为负数", groups = {AddGroup.class, EditGroup.class})
    private BigDecimal salaryMin;

    /**
     * 薪资高值（不得小于薪资低值）
     */
    @DecimalMin(value = "0", message = "薪资高值不能为负数", groups = {AddGroup.class, EditGroup.class})
    private BigDecimal salaryMax;

    /**
     * 薪资周期（month/year/day 等稳定编码，填写薪资本项必填）
     */
    @Size(max = 32, message = "薪资周期长度不能超过 32", groups = {AddGroup.class, EditGroup.class})
    private String salaryPeriod;

    /**
     * 招聘形式（{@code RecruitModeEnum} 的 code：internal/social/campus/headhunter/referral/other）
     */
    private String recruitMode;

    /**
     * 紧急程度（{@code UrgencyEnum} 的 code：normal/urgent/very_urgent）
     */
    private String urgency;

    /**
     * 是否需要猎头（0否 1是）
     */
    @Pattern(regexp = "^(0|1)?$", message = "是否需要猎头只能为 0 或 1", groups = {AddGroup.class, EditGroup.class})
    private String headhunterFlag;

    /**
     * 协助人用户ID数组（入库时以英文逗号拼接为 {@code assistant_ids}）
     */
    @Size(max = 20, message = "协助人最多 20 人", groups = {AddGroup.class, EditGroup.class})
    private Long[] assistantIds;

    /**
     * 一面面试官用户ID（岗位计划默认值，实际参与以面试参与人表为准）
     */
    private Long firstInterviewerId;

    /**
     * 二面面试官用户ID（岗位计划默认值，实际参与以面试参与人表为准）
     */
    private Long secondInterviewerId;

    /**
     * 招聘期限标准天数
     */
    @PositiveOrZero(message = "招聘期限标准天数不能为负数", groups = {AddGroup.class, EditGroup.class})
    private Integer standardDays;

    /**
     * 预计到岗日期
     */
    private LocalDate expectArrivalDate;

    /**
     * 岗位招聘人数（非负整数）
     */
    @PositiveOrZero(message = "岗位招聘人数不能为负数", groups = {AddGroup.class, EditGroup.class})
    private Integer recruitCount;

    /**
     * 招聘负责人用户ID
     */
    private Long ownerId;

    /**
     * 发布日期
     */
    private LocalDate publishDate;

    /**
     * 岗位状态（draft/open/paused/closed）；服务层按状态机校验流转
     */
    private String status;

    /**
     * 乐观锁版本号（编辑时必填，命中冲突返回中文提示）
     */
    @NotNull(message = "版本号不能为空，请刷新后重试", groups = {EditGroup.class})
    private Integer version;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过 500", groups = {AddGroup.class, EditGroup.class})
    private String remark;

}
