package org.dromara.hrtalent.domain.bo.recruitment;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 月度招聘计划任务查询对象（SPEC-P2 §3.2）。
 *
 * <p>同时用于三处查询：表头下任务分页、跨计划任务分页、相似计划提示
 * （{@code /recruit/plan-items/similar} 只使用 {@code companyDeptId}、{@code useDeptId}、
 * {@code jobName} 与 {@code excludeItemId} 四个条件）。</p>
 *
 * @author hr-talent
 */
@Data
public class RecruitPlanItemQueryBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 计划任务编号（模糊匹配）
     */
    private String itemNo;

    /**
     * 所属月度计划表头ID
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
     * 公司（平台部门）ID
     */
    private Long companyDeptId;

    /**
     * 用工部门ID
     */
    private Long useDeptId;

    /**
     * 岗位名称快照（模糊匹配）
     */
    private String jobName;

    /**
     * 计划月份（yyyy-MM，精确匹配）
     */
    private String planMonth;

    /**
     * 计划月份下限（含，yyyy-MM）
     */
    private String planMonthBegin;

    /**
     * 计划月份上限（含，yyyy-MM）
     */
    private String planMonthEnd;

    /**
     * 来源类型（new/carryover）
     */
    private String sourceType;

    /**
     * 人工控制状态（normal/paused/cancelled）
     */
    private String controlStatus;

    /**
     * 自动执行阶段（pending/recruiting/interviewing/offer/pending_arrival）
     */
    private String executionStatus;

    /**
     * 完成与结转状态（unfinished/partial_completed/completed/rolled_over）
     */
    private String completionStatus;

    /**
     * 任务负责人用户ID
     */
    private Long ownerId;

    /**
     * 紧急程度（normal/urgent/very_urgent）
     */
    private String urgency;

    /**
     * 相似计划提示时排除的任务ID（通常是当前正在编辑的任务）
     */
    private Long excludeItemId;

}
