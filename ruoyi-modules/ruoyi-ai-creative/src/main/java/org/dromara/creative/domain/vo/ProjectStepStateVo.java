package org.dromara.creative.domain.vo;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 项目步骤状态视图（V0.2 D2；R36 起带"能不能跳过 / 为什么跳过"）。
 *
 * @param stepCode    步骤编码
 * @param stepName    步骤名称
 * @param sortNo      顺序
 * @param status      状态（PENDING/ACTIVE/DONE/SKIPPED）
 * @param stageCode   让它处于该状态的阶段（DERIVED 时=当前阶段）
 * @param startedAt   开始时间（仅持久化行有）
 * @param completedAt 完成时间（仅持久化行有）
 * @param source      这个状态从哪来：{@code PERSISTED}=步骤状态表里有行；{@code DERIVED}=按当前阶段推导（没写库）
 * @param required    配置里这一步是不是必填（{@code '1'}=必填；必填步骤不允许跳过）
 * @param gated       配置里这一步有没有闸门（有闸门不允许跳过——跳过等于绕过门禁）
 * @param skippable    现在能不能跳过（后端算好的判据：非必填 + 无闸门 + 还没做完）
 * @param skipReason  跳过原因（仅 SKIPPED 有；存在 {@code dp_project_step_state.remark}）
 * @param updatedAt   最后一次状态变更时间（跳过/取消跳过都会更新）
 * @author creative
 */
public record ProjectStepStateVo(String stepCode, String stepName, Integer sortNo, String status,
                                 String stageCode, LocalDateTime startedAt, LocalDateTime completedAt,
                                 String source, String required, Boolean gated, Boolean skippable,
                                 String skipReason, LocalDateTime updatedAt) implements Serializable {
}
