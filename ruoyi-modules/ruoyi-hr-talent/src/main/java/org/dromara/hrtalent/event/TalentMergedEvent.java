package org.dromara.hrtalent.event;

import java.io.Serializable;

/**
 * 人才合并完成事件（设计文档 §21.6）。
 *
 * <p>由人才合并服务在<b>事务提交前的同一事务方法末尾</b>发布；消费者为缓存失效、
 * 搜索索引重建与合并审计（消费者由后续阶段接入，本阶段先发布）。
 * 事件只承担「后续派生刷新」，不承担必须原子完成的写操作（§21.6）。</p>
 *
 * @param keepTalentId   保留（主）人才主档ID
 * @param mergedTalentId 被合并（从）人才主档ID
 * @param mergeId        合并日志ID（合并快照主键，便于消费者追溯）
 * @param operatorId     操作人用户ID
 * @author hr-talent
 */
public record TalentMergedEvent(
    Long keepTalentId,
    Long mergedTalentId,
    Long mergeId,
    Long operatorId
) implements Serializable {
}
