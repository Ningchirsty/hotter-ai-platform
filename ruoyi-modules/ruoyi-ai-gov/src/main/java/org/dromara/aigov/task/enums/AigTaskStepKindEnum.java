package org.dromara.aigov.task.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 任务步骤类型（设计 §8.2 {@code aig_task_step}、§6.5）。
 *
 * @author ai-gov
 */
@Getter
@AllArgsConstructor
public enum AigTaskStepKindEnum {

    /** 子 Agent（顺序调用的一步） */
    SUB_AGENT("SUB_AGENT", "子 Agent"),

    /** 顺序流程节点 */
    FLOW_NODE("FLOW_NODE", "流程节点"),

    /** 工具调用 */
    TOOL_CALL("TOOL_CALL", "工具调用");

    private final String code;

    private final String desc;

    /**
     * 按 code 查找。
     *
     * @param code 编码
     * @return 枚举；未命中返回 null
     */
    public static AigTaskStepKindEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AigTaskStepKindEnum item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return null;
    }

}
