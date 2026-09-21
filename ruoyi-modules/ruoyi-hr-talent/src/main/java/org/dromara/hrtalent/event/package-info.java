/**
 * 领域事件包。
 * <p>建议事件：{@code ApplicationStageChangedEvent}、{@code CandidateArrivedEvent}、
 * {@code TalentProfileChangedEvent}、{@code ResumeUploadedEvent}（设计文档 §21.6）。
 * 涉及数据库一致性的监听器使用事务提交后机制。</p>
 *
 * @author hr-talent
 */
package org.dromara.hrtalent.event;
