package org.dromara.hrtalent.event;

import java.io.Serializable;

/**
 * 人才主档变化事件（设计文档 §21.6）。
 * <p>由人才主档服务发布；消费者为重复检测、搜索索引与变更审计。
 * P3 阶段消费者尚未实现（重复检测与索引属后续阶段），先发布以为后续接入预留。</p>
 *
 * @param talentId   人才主档ID
 * @param changeType 变化类型编码（create/update/archive/status 等稳定编码）
 * @param operatorId 操作人用户ID
 * @author hr-talent
 */
public record TalentProfileChangedEvent(
    Long talentId,
    String changeType,
    Long operatorId
) implements Serializable {
}
