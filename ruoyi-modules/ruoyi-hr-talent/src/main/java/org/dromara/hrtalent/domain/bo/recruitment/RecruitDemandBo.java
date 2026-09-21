package org.dromara.hrtalent.domain.bo.recruitment;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.hrtalent.domain.entity.RecruitDemand;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 招聘需求业务对象 hr_recruit_demand（SPEC-P2 §3.1 / §4.1）。
 *
 * <p>状态、已到岗人数、提交/确认/关闭痕迹与版本号以外的派生字段均不接受前端写入；
 * {@code version} 在编辑场景必填，用于乐观锁校验。</p>
 *
 * @author hr-talent
 */
@Data
@AutoMapper(target = RecruitDemand.class, reverseConvertGenerate = false)
public class RecruitDemandBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 需求ID（编辑场景由路径变量 {@code /recruit/demands/{id}} 注入，请求体可不传）
     */
    private Long demandId;

    /**
     * 乐观锁版本号（编辑时必填，取自详情返回值）
     */
    @NotNull(message = "版本号不能为空，请刷新后重试", groups = {EditGroup.class})
    private Integer version;

    /**
     * 需求标题
     */
    @Size(max = 200, message = "需求标题长度不能超过 200", groups = {AddGroup.class, EditGroup.class})
    private String demandTitle;

    /**
     * 公司（平台部门）ID
     */
    @NotNull(message = "公司不能为空", groups = {AddGroup.class, EditGroup.class})
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
     * 招聘负责人用户ID
     */
    private Long recruiterId;

    /**
     * 需求申请日期
     */
    @NotNull(message = "申请日期不能为空", groups = {AddGroup.class, EditGroup.class})
    private LocalDate applyDate;

    /**
     * 期望到岗日期（不得早于申请日期）
     */
    private LocalDate expectArrivalDate;

    /**
     * 需求人数（必须大于 0）
     */
    @NotNull(message = "需求人数不能为空", groups = {AddGroup.class, EditGroup.class})
    @Min(value = 1, message = "需求人数必须大于 0", groups = {AddGroup.class, EditGroup.class})
    private Integer demandCount;

    /**
     * 紧急程度（normal/urgent/very_urgent）
     */
    @Size(max = 32, message = "紧急程度长度不能超过 32", groups = {AddGroup.class, EditGroup.class})
    private String urgency;

    /**
     * 招聘形式（internal/social/campus/headhunter/referral/other）
     */
    @Size(max = 32, message = "招聘形式长度不能超过 32", groups = {AddGroup.class, EditGroup.class})
    private String recruitMode;

    /**
     * 需求岗位名称
     */
    @Size(max = 200, message = "岗位名称长度不能超过 200", groups = {AddGroup.class, EditGroup.class})
    private String jobName;

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
     * 需求原因说明
     */
    private String demandReason;

    /**
     * 变更原因（仅用于进入招聘中之后的关键字段变更留痕，不落 hr_recruit_demand）
     */
    @Size(max = 500, message = "变更原因长度不能超过 500", groups = {AddGroup.class, EditGroup.class})
    private String reason;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过 500", groups = {AddGroup.class, EditGroup.class})
    private String remark;

}
