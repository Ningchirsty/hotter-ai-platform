package org.dromara.hrtalent.event;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 候选人报到事件（设计文档 §21.6）。
 * <p>由报到服务发布；消费者为计划人数服务、需求完成判断与人才状态服务。</p>
 *
 * <p><b>注意</b>：按 §21.7，「候选人实际报到 + 计划到岗计数 + 状态刷新」必须在**同一事务**内完成，
 * 因此报到服务需在事务内直接调用计划状态领域服务完成计数与刷新，
 * 本事件只用于人才状态、需求完成判断等后续派生动作。</p>
 *
 * @param applicationId 应聘记录ID
 * @param talentId      人才主档ID
 * @param planItemId    计入的月度计划任务ID，可为空（无关联任务时）
 * @param arrivalDate   实际报到日期
 * @param operatorId    操作人用户ID
 * @author hr-talent
 */
public record CandidateArrivedEvent(
    Long applicationId,
    Long talentId,
    Long planItemId,
    LocalDate arrivalDate,
    Long operatorId
) implements Serializable {
}
