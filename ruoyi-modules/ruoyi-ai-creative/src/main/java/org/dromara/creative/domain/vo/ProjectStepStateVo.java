package org.dromara.creative.domain.vo;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 项目步骤状态视图（V0.2 D2）。
 *
 * @param stepCode    步骤编码
 * @param stepName    步骤名称
 * @param sortNo      顺序
 * @param status      状态（PENDING/ACTIVE/DONE/SKIPPED）
 * @param stageCode   让它处于该状态的阶段（DERIVED 时=当前阶段）
 * @param startedAt   开始时间（仅持久化行有）
 * @param completedAt 完成时间（仅持久化行有）
 * @param source      这个状态从哪来：{@code PERSISTED}=步骤状态表里有行；{@code DERIVED}=按当前阶段推导（没写库）
 * @author creative
 */
public record ProjectStepStateVo(String stepCode, String stepName, Integer sortNo, String status,
                                 String stageCode, LocalDateTime startedAt, LocalDateTime completedAt,
                                 String source) implements Serializable {
}
