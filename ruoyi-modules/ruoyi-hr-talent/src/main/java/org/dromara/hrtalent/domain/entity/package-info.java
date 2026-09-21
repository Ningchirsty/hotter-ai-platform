/**
 * 持久化实体包。
 * <p>Entity 继承 {@code BaseEntity}，使用 {@code @TableName}、{@code @TableId}、
 * {@code @TableLogic}，关键聚合根（需求、岗位、应聘记录、人才主档）使用 {@code @Version}
 * 乐观锁。实体禁止直接作为出参，敏感字段不得复用（设计文档 §21.2）。</p>
 *
 * @author hr-talent
 */
package org.dromara.hrtalent.domain.entity;
