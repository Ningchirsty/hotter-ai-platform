package org.dromara.aigov.execution;

import java.io.Serializable;

/**
 * 流程节点（设计 §6.5）。
 *
 * @param type  节点类型编码（{@link AigFlowNodeTypeEnum}）
 * @param ref   引用（步骤标识 / 条件表达式键 / 输出契约键，可空）
 * @param label 人读标签（可空）
 * @author ai-gov
 */
public record AigFlowNode(String type, String ref, String label) implements Serializable {
}
