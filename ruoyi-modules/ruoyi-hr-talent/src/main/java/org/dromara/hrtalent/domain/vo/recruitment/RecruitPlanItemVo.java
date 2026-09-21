package org.dromara.hrtalent.domain.vo.recruitment;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.hrtalent.domain.entity.RecruitPlanItem;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 月度招聘计划任务视图对象 hr_recruit_plan_item（SPEC-P2 §3.2 / §4.2）。
 *
 * <p>除三个状态维度各自的字典标签外，额外给出<b>主展示状态</b>
 * （{@code displayStatus}/{@code displayStatusLabel}）与「部分完成」标记
 * （{@code partialCompleted}），二者由 {@code PlanItemStatusDomainService} 纯函数计算：
 * 主展示状态优先级为「已取消 &gt; 暂停 &gt; 已完成 &gt; 已结转 &gt; 待报到 &gt; 待录用 &gt; 面试中 &gt; 招聘中 &gt; 待启动」；
 * {@code credited_arrival_qty > 0 && remaining_qty > 0} 时另给「部分完成」标签，<b>不覆盖</b>执行阶段。</p>
 *
 * @author hr-talent
 */
@Data
@AutoMapper(target = RecruitPlanItem.class)
public class RecruitPlanItemVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 计划任务ID
     */
    private Long itemId;

    /**
     * 计划任务编号
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
     * 来源导入批次ID
     */
    private Long importBatchId;

    /**
     * 来源表名
     */
    private String sourceTable;

    /**
     * 来源序号
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
     * 来源类型（字典 recruit_plan_source_type）
     */
    private String sourceType;

    /**
     * 来源类型标签
     */
    @Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "sourceType", other = "recruit_plan_source_type")
    private String sourceTypeLabel;

    /**
     * 计划人数
     */
    private Integer planQty;

    /**
     * 已计入到岗人数
     */
    private Integer creditedArrivalQty;

    /**
     * 剩余人数
     */
    private Integer remainingQty;

    /**
     * 人工控制状态（字典 recruit_plan_control_status）
     */
    private String controlStatus;

    /**
     * 人工控制状态标签
     */
    @Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "controlStatus", other = "recruit_plan_control_status")
    private String controlStatusLabel;

    /**
     * 人工暂停或取消原因
     */
    private String controlReason;

    /**
     * 自动执行阶段（字典 recruit_plan_execution_status）
     */
    private String executionStatus;

    /**
     * 自动执行阶段标签
     */
    @Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "executionStatus", other = "recruit_plan_execution_status")
    private String executionStatusLabel;

    /**
     * 完成与结转状态（字典 recruit_plan_completion_status）
     */
    private String completionStatus;

    /**
     * 完成与结转状态标签
     */
    @Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "completionStatus", other = "recruit_plan_completion_status")
    private String completionStatusLabel;

    /**
     * 主展示状态编码（服务端按优先级计算，前端直接展示）
     */
    private String displayStatus;

    /**
     * 主展示状态中文名（服务端按优先级计算，前端直接展示）
     */
    private String displayStatusLabel;

    /**
     * 是否显示「部分完成」标签（到岗大于 0 且仍有剩余；不覆盖执行阶段）
     */
    private Boolean partialCompleted;

    /**
     * 最后状态刷新时间
     */
    private LocalDateTime lastRefreshTime;

    /**
     * 是否允许自动结转（0否 1是）
     */
    private String carryoverEnabled;

    /**
     * 前置（来源）计划任务ID
     */
    private Long previousPlanItemId;

    /**
     * 根计划任务ID（结转链起点）
     */
    private Long rootPlanItemId;

    /**
     * 结转批次ID
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
     * 任务负责人名称
     */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "ownerId")
    private String ownerName;

    /**
     * 紧急程度（字典 recruit_urgency）
     */
    private String urgency;

    /**
     * 紧急程度标签
     */
    @Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "urgency", other = "recruit_urgency")
    private String urgencyLabel;

    /**
     * 招聘期限标准天数
     */
    private Integer standardDays;

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
