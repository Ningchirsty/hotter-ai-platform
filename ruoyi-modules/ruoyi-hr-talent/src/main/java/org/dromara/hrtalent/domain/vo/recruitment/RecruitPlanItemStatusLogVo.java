package org.dromara.hrtalent.domain.vo.recruitment;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;
import org.dromara.hrtalent.domain.entity.RecruitPlanItemStatusLog;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 月度计划任务状态变更日志视图对象 hr_recruit_plan_item_status_log
 * （设计文档 §7.1.5 / §21.15）。
 *
 * <p>只读返回，供结转链与状态审计追溯使用。日志为追加型，不提供任何写接口。</p>
 *
 * @author hr-talent
 */
@Data
@AutoMapper(target = RecruitPlanItemStatusLog.class)
public class RecruitPlanItemStatusLogVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 日志ID
     */
    private Long logId;

    /**
     * 计划任务ID
     */
    private Long itemId;

    /**
     * 所属计划表头ID
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
     * 新自动执行阶段标签（字典 recruit_plan_execution_status）
     */
    @Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "toExecutionStatus", other = "recruit_plan_execution_status")
    private String toExecutionStatusLabel;

    /**
     * 原完成状态（字典 recruit_plan_completion_status）
     */
    private String fromCompletionStatus;

    /**
     * 新完成状态（字典 recruit_plan_completion_status）
     */
    private String toCompletionStatus;

    /**
     * 新完成状态标签（字典 recruit_plan_completion_status）
     */
    @Translation(type = TransConstant.DICT_TYPE_TO_LABEL, mapper = "toCompletionStatus", other = "recruit_plan_completion_status")
    private String toCompletionStatusLabel;

    /**
     * 刷新时间
     */
    private LocalDateTime refreshTime;

    /**
     * 操作人用户ID（自动刷新时可为空）
     */
    private Long operatorId;

    /**
     * 操作人昵称（由 {@link #operatorId} 翻译）
     */
    @Translation(type = TransConstant.USER_ID_TO_NICKNAME, mapper = "operatorId")
    private String operatorName;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

}
