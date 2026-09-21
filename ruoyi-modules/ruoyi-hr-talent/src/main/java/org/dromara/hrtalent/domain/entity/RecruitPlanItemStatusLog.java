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
 * 月度计划任务状态变更日志对象 hr_recruit_plan_item_status_log
 * （设计文档 §7.1.5「记录触发事件、原状态、新状态和刷新时间」、§21.15 伪代码「append status change log」）。
 *
 * <p><b>追加型日志</b>：本表只插入，<b>不更新、不删除</b>（{@code del_flag} 仅供系统一致性修复使用）。
 * 每次状态刷新时，只有当 {@code execution_status} 或 {@code completion_status}
 * <b>实际发生变化</b>才追加一条；状态未变化的刷新不写日志，避免无意义刷表。</p>
 *
 * <p>本表是设计文档 §9.2 表清单之外的补表（缺口台账 §4 第 1 项，经用户批准，表数 36 → 37），
 * 用于补齐「触发事件 / 原状态 / 新状态 / 刷新时间」的落库能力。</p>
 *
 * <p>字段与 {@code script/sql/hr_recruit.sql} 的建表语句逐列一致；主键列为 {@code log_id}。</p>
 *
 * @author hr-talent
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hr_recruit_plan_item_status_log")
public class RecruitPlanItemStatusLog extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 日志ID（主键）
     */
    @TableId(value = "log_id")
    private Long logId;

    /**
     * 计划任务ID（hr_recruit_plan_item.item_id）
     */
    private Long itemId;

    /**
     * 所属计划表头ID（hr_recruit_plan.plan_id，便于按计划查询）
     */
    private Long planId;

    /**
     * 触发事件稳定编码
     * （application_stage_changed/interview_result_changed/candidate_arrived/background_result_changed/manual_refresh）
     */
    private String triggerEvent;

    /**
     * 原自动执行阶段（字典 recruit_plan_execution_status）
     */
    private String fromExecutionStatus;

    /**
     * 新自动执行阶段（字典 recruit_plan_execution_status）
     */
    private String toExecutionStatus;

    /**
     * 原完成状态（字典 recruit_plan_completion_status）
     */
    private String fromCompletionStatus;

    /**
     * 新完成状态（字典 recruit_plan_completion_status）
     */
    private String toCompletionStatus;

    /**
     * 刷新时间（与 hr_recruit_plan_item.last_refresh_time 同源）
     */
    private LocalDateTime refreshTime;

    /**
     * 操作人用户ID（自动刷新时可为空）
     */
    private Long operatorId;

    /**
     * 删除标志（0代表存在 1代表删除；日志表不物理删除）
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注
     */
    private String remark;

}
