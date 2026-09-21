package org.dromara.hrtalent.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 月度招聘计划任务对象 hr_recruit_plan_item（设计文档 §9.2 / §7.1.4 / §8.2.1）。
 *
 * <p><b>硬性规则</b>：</p>
 * <ul>
 *     <li>每次新增招聘任务都创建独立记录，即使公司、部门、岗位、人数完全相同也
 *     <b>绝不合并、覆盖或复用</b>（设计文档 §7.1.1）；只能在查询侧提示「存在相似计划」。</li>
 *     <li>三个状态维度分开维护：{@code control_status}（人工）、{@code execution_status}（自动执行阶段）、
 *     {@code completion_status}（按人数自动完成度）。</li>
 *     <li>{@code remaining_qty = max(plan_qty - credited_arrival_qty, 0)}，由服务维护，不得为负。</li>
 *     <li>禁止通过修改 {@code plan_month} 把原月任务迁移到下月；跨月只能新增结转任务
 *     （{@code source_type = carryover} + {@code previous_plan_item_id} + {@code root_plan_item_id}）。</li>
 * </ul>
 *
 * <p>字段与 {@code script/sql/hr_recruit.sql} 的建表语句逐字一致；主键列为 {@code item_id}，
 * 业务编号列为 {@code item_no}。</p>
 *
 * @author hr-talent
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hr_recruit_plan_item")
public class RecruitPlanItem extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 计划任务ID（主键）
     */
    @TableId(value = "item_id")
    private Long itemId;

    /**
     * 计划任务编号（业务编号，唯一）
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
     * 来源导入批次ID（导入生成时记录，P2 仅保留字段不使用）
     */
    private Long importBatchId;

    /**
     * 来源表名（历史迁移来源，P2 仅保留字段不使用）
     */
    private String sourceTable;

    /**
     * 来源序号（原表行号，P2 仅保留字段不使用）
     */
    private String sourceSeq;

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
     * 岗位名称快照
     */
    private String jobName;

    /**
     * 计划月份（yyyy-MM）
     */
    private String planMonth;

    /**
     * 来源类型（new当月新增 carryover上月结转，字典 recruit_plan_source_type）
     */
    private String sourceType;

    /**
     * 计划人数（非负整数；新增取确认人数，结转取原任务未完成人数）
     */
    private Integer planQty;

    /**
     * 已计入到岗人数（非负整数，一个到岗结果只计入一条有效任务）
     */
    private Integer creditedArrivalQty;

    /**
     * 剩余人数（非负整数）
     */
    private Integer remainingQty;

    /**
     * 人工控制状态（normal/paused/cancelled，字典 recruit_plan_control_status）
     */
    private String controlStatus;

    /**
     * 人工暂停或取消原因（与人工控制状态同时展示）
     */
    private String controlReason;

    /**
     * 自动执行阶段（pending/recruiting/interviewing/offer/pending_arrival，字典 recruit_plan_execution_status）
     */
    private String executionStatus;

    /**
     * 完成与结转状态（unfinished/partial_completed/completed/rolled_over，字典 recruit_plan_completion_status）
     */
    private String completionStatus;

    /**
     * 最后状态刷新时间
     */
    private LocalDateTime lastRefreshTime;

    /**
     * 是否允许自动结转（0否 1是）
     */
    private String carryoverEnabled;

    /**
     * 前置（来源）计划任务ID，构成跨月结转链
     */
    private Long previousPlanItemId;

    /**
     * 根计划任务ID（结转链起点）
     */
    private Long rootPlanItemId;

    /**
     * 结转批次ID（关联 hr_recruit_plan_rollover.batch_id）
     */
    private Long carryoverBatchId;

    /**
     * 结转生成时间
     */
    private LocalDateTime carryoverTime;

    /**
     * 任务负责人用户ID
     */
    private Long ownerId;

    /**
     * 紧急程度（结转时自原任务复制，字典 recruit_urgency）
     */
    private String urgency;

    /**
     * 招聘期限标准天数（结转时自原任务复制）
     */
    private Integer standardDays;

    /**
     * 删除标志（0代表存在 1代表删除）
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注
     */
    private String remark;

}
