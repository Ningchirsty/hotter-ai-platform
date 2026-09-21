package org.dromara.hrtalent.event;

import java.io.Serializable;

/**
 * 应聘阶段变化事件（设计文档 §21.6）。
 * <p>由应聘阶段服务在**事务提交后**发布；消费者为计划状态刷新、人才状态刷新与提醒服务。</p>
 *
 * <p><b>注意</b>：阶段流转本身与阶段历史的写入必须在同一事务内原子完成，
 * 本事件只负责后续派生刷新，不得用于承载必须原子完成的数据一致性（§21.6）。</p>
 *
 * @param applicationId 应聘记录ID
 * @param talentId      人才主档ID
 * @param jobId         岗位执行项ID，可为空
 * @param planItemId    关联月度计划任务ID，可为空（由岗位或计入关系解析得出）
 * @param fromStage     原阶段编码
 * @param toStage       新阶段编码
 * @param actionType    操作类型编码
 * @param operatorId    操作人用户ID
 * @author hr-talent
 */
public record ApplicationStageChangedEvent(
    Long applicationId,
    Long talentId,
    Long jobId,
    Long planItemId,
    String fromStage,
    String toStage,
    String actionType,
    Long operatorId
) implements Serializable {
}
