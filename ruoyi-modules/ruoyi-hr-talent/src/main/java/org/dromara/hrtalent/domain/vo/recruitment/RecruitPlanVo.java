package org.dromara.hrtalent.domain.vo.recruitment;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.hrtalent.domain.entity.RecruitPlan;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 公司月度招聘计划表头视图对象 hr_recruit_plan（SPEC-P2 §3.2）。
 *
 * <p>字典标签与人员名称通过 {@code @Translation} 回填，前端不需要自行查字典。</p>
 *
 * @author hr-talent
 */
@Data
@AutoMapper(target = RecruitPlan.class)
public class RecruitPlanVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 计划ID
     */
    private Long planId;

    /**
     * 计划编号
     */
    private String planNo;

    /**
     * 公司（平台部门）ID
     */
    private Long companyDeptId;

    /**
     * 公司名称快照
     */
    private String companyName;

    /**
     * 计划月份（yyyy-MM）
     */
    private String planMonth;

    /**
     * 计划状态（字典 recruit_plan_status）
     */
    private String status;

    /**
     * 计划状态标签
     */
    @Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "status", other = "recruit_plan_status")
    private String statusLabel;

    /**
     * 是否已生成计划任务（0否 1是）
     */
    private String generatedFlag;

    /**
     * 生成时间
     */
    private LocalDateTime generatedTime;

    /**
     * 确认人用户ID
     */
    private Long confirmedBy;

    /**
     * 确认人名称
     */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "confirmedBy")
    private String confirmedByName;

    /**
     * 确认时间
     */
    private LocalDateTime confirmedTime;

    /**
     * 关闭人用户ID
     */
    private Long closedBy;

    /**
     * 关闭人名称
     */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "closedBy")
    private String closedByName;

    /**
     * 关闭时间
     */
    private LocalDateTime closedTime;

    /**
     * 计划总人数
     */
    private Integer totalPlanQty;

    /**
     * 累计计入到岗人数
     */
    private Integer totalCreditedQty;

    /**
     * 累计剩余人数
     */
    private Integer totalRemainingQty;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建者用户ID
     */
    private Long createBy;

    /**
     * 创建者名称
     */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "createBy")
    private String createByName;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
