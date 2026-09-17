package org.dromara.ai.video.domain;

import java.util.List;

/**
 * 契约中的字段映射白名单条目。
 *
 * <p>服务端深拷贝 API Format 模板后，<b>只允许</b>覆写本条目声明的节点输入键。
 * 其余输入一律不动，防止越权改动采样参数或模型路径。</p>
 *
 * @param field    前端字段名（img/desc/first/last/tier/dur 等）
 * @param nodeId   API Format 模板中的节点 ID
 * @param inputKey 节点输入键
 * @param note     契约备注
 */
public record WorkflowMapping(String field, String nodeId, String inputKey, String note) {
}
