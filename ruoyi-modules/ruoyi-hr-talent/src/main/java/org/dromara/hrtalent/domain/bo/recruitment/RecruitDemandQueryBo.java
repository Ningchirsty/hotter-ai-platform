package org.dromara.hrtalent.domain.bo.recruitment;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 招聘需求分页查询业务对象（SPEC-P2 §3.1 GET /recruit/demands）。
 *
 * <p>所有条件均为可选；数据权限由 Mapper 上的 {@code @DataPermission} 自动追加，
 * <b>不接受</b>前端传入部门范围字段。</p>
 *
 * @author hr-talent
 */
@Data
public class RecruitDemandQueryBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 需求编号（模糊匹配）
     */
    private String demandNo;

    /**
     * 需求标题（模糊匹配）
     */
    private String demandTitle;

    /**
     * 公司（平台部门）ID
     */
    private Long companyDeptId;

    /**
     * 用工部门ID
     */
    private Long useDeptId;

    /**
     * 招聘负责人用户ID
     */
    private Long recruiterId;

    /**
     * 需求岗位名称（模糊匹配）
     */
    private String jobName;

    /**
     * 需求状态（draft/submitted/recruiting/paused/completed/closed）
     */
    private String status;

    /**
     * 紧急程度（normal/urgent/very_urgent）
     */
    private String urgency;

    /**
     * 招聘形式（internal/social/campus/headhunter/referral/other）
     */
    private String recruitMode;

    /**
     * 工作城市（模糊匹配）
     */
    private String workCity;

    /**
     * 申请日期起（含）
     */
    private LocalDate applyDateBegin;

    /**
     * 申请日期止（含）
     */
    private LocalDate applyDateEnd;

}
