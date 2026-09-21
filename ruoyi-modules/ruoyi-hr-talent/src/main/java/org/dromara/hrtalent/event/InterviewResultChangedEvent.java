package org.dromara.hrtalent.event;

import java.io.Serializable;

/**
 * 面试结果变化事件（设计文档 §21.6）。
 * <p>由面试服务发布；消费者为应聘阶段服务与计划状态服务。</p>
 *
 * @param interviewId   面试记录ID
 * @param applicationId 应聘记录ID
 * @param planItemId    关联月度计划任务ID，可为空
 * @param roundNo       面试轮次
 * @param result        面试结果编码（对应字典 recruit_interview_result）
 * @param operatorId    操作人用户ID
 * @author hr-talent
 */
public record InterviewResultChangedEvent(
    Long interviewId,
    Long applicationId,
    Long planItemId,
    Integer roundNo,
    String result,
    Long operatorId
) implements Serializable {
}
