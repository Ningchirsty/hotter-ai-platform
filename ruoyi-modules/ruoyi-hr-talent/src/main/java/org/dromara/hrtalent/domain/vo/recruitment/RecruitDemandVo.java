package org.dromara.hrtalent.domain.vo.recruitment;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.hrtalent.domain.entity.RecruitDemand;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 招聘需求视图对象 hr_recruit_demand（SPEC-P2 §3.1）。
 *
 * <p>{@code remainingCount}（待招聘人数）为派生字段，不落库，取值
 * {@code max(demandCount - hiredCount, 0)}（SPEC §4.1）。</p>
 *
 * @author hr-talent
 */
@Data
@AutoMapper(target = RecruitDemand.class)
public class RecruitDemandVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 需求ID
     */
    private Long demandId;

    /**
     * 需求编号
     */
    private String demandNo;

    /**
     * 需求标题
     */
    private String demandTitle;

    /**
     * 公司（平台部门）ID
     */
    private Long companyDeptId;

    /**
     * 公司名称快照
     */
    private String companyName;

    /**
     * 用工部门ID
     */
    private Long useDeptId;

    /**
     * 用工部门名称快照
     */
    private String useDeptName;

    /**
     * 招聘负责人用户ID
     */
    private Long recruiterId;

    /**
     * 招聘负责人昵称（字典/用户翻译，仅出参）
     */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "recruiterId")
    private String recruiterName;

    /**
     * 需求申请日期
     */
    private LocalDate applyDate;

    /**
     * 期望到岗日期
     */
    private LocalDate expectArrivalDate;

    /**
     * 需求人数
     */
    private Integer demandCount;

    /**
     * 已到岗人数
     */
    private Integer hiredCount;

    /**
     * 待招聘人数（派生 = max(需求人数 - 已到岗人数, 0)）
     */
    private Integer remainingCount;

    /**
     * 紧急程度（字典 recruit_urgency）
     */
    private String urgency;

    /**
     * 紧急程度标签（字典 recruit_urgency）
     */
    @Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "urgency", other = "recruit_urgency")
    private String urgencyLabel;

    /**
     * 招聘形式（字典 recruit_mode）
     */
    private String recruitMode;

    /**
     * 招聘形式标签（字典 recruit_mode）
     */
    @Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "recruitMode", other = "recruit_mode")
    private String recruitModeLabel;

    /**
     * 需求岗位名称
     */
    private String jobName;

    /**
     * 岗位职级（字典编码）
     */
    private String jobLevel;

    /**
     * 工作城市
     */
    private String workCity;

    /**
     * 需求原因说明
     */
    private String demandReason;

    /**
     * 需求状态（字典 recruit_demand_status）
     */
    private String status;

    /**
     * 需求状态标签（字典 recruit_demand_status）
     */
    @Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "status", other = "recruit_demand_status")
    private String statusLabel;

    /**
     * 提交人用户ID
     */
    private Long submittedBy;

    /**
     * 提交时间
     */
    private LocalDateTime submittedTime;

    /**
     * 确认人用户ID
     */
    private Long confirmedBy;

    /**
     * 确认时间
     */
    private LocalDateTime confirmedTime;

    /**
     * 关闭人用户ID
     */
    private Long closedBy;

    /**
     * 关闭时间
     */
    private LocalDateTime closedTime;

    /**
     * 乐观锁版本号（编辑时必须原样回传）
     */
    private Integer version;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
