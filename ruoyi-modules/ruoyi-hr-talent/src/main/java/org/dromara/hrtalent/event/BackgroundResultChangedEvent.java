package org.dromara.hrtalent.event;

import java.io.Serializable;

/**
 * 背调结论变化事件（设计文档 §21.6，SPEC-P3 §3.4）。
 * <p>由背调服务在结论发生变化（新增即带回执结论，或更新后结论与库内不一致）时发布；
 * 消费者为应聘阶段服务（「进入待录用前必须有背调结论或经授权的免背调原因」）与
 * 计划任务状态刷新服务。</p>
 *
 * <p><b>注意</b>：本事件只承载派生刷新所需的标识，<b>不含背调明细</b>，
 * 也不允许消费方向背调服务索要明细密文（明细必须走独立权限接口并写审计）。</p>
 *
 * @param backgroundId  背调记录ID
 * @param applicationId 应聘记录ID
 * @param result        背调结论编码（对应字典 recruit_background_result）
 * @param status        背调状态编码（draft/checking/finished/cancelled）
 * @param checkerId     背调负责人用户ID，可为空
 * @param operatorId    操作人用户ID，可为空（定时或系统触发时）
 * @author hr-talent
 */
public record BackgroundResultChangedEvent(
    Long backgroundId,
    Long applicationId,
    String result,
    String status,
    Long checkerId,
    Long operatorId
) implements Serializable {
}
