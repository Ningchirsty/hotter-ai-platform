package org.dromara.ai.image.domain;

/**
 * 契约里的一条字段 → 节点输入白名单映射。
 *
 * <p>后端只允许覆写这里声明的节点输入键，其余输入（模型路径、sampler、steps、cfg 等）
 * 一律保持模板原值。</p>
 *
 * @param field    前端字段名（必须与能力声明的字段一致）
 * @param nodeId   模板中的节点 ID
 * @param inputKey 该节点上允许覆写的输入键
 * @param note     说明（可为 null）
 */
public record ImageWorkflowMapping(String field, String nodeId, String inputKey, String note) {
}
