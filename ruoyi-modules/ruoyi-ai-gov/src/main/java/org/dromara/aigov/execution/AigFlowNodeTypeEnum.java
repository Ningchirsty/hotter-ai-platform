package org.dromara.aigov.execution;

/**
 * 流程节点类型（设计 §6.5：「流程先提供顺序步骤、条件判断、人工确认和输出节点」）。
 *
 * <p>首期只有顺序执行，因此节点类型是"这一步要人做什么"的声明，而不是一套可视化编排语言。
 * 需要复杂审批时复用既有 WarmFlow；确定性任务可评估 LiteFlow——两种执行机制各自声明适配器，
 * 不在首期叠加另一套 DAG 平台（设计 §6.5）。</p>
 *
 * @author ai-gov
 */
public enum AigFlowNodeTypeEnum {

    /** 顺序步骤 */
    STEP("STEP", "顺序步骤"),

    /** 条件判断 */
    CONDITION("CONDITION", "条件判断"),

    /** 人工确认（有业务后果的动作必须经过它） */
    HUMAN_CONFIRM("HUMAN_CONFIRM", "人工确认"),

    /** 输出节点（流程的终点） */
    OUTPUT("OUTPUT", "输出节点");

    private final String code;

    private final String desc;

    AigFlowNodeTypeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 编码。
     *
     * @return 编码
     */
    public String getCode() {
        return code;
    }

    /**
     * 描述。
     *
     * @return 描述
     */
    public String getDesc() {
        return desc;
    }

    /**
     * 按 code 查找。
     *
     * @param code 编码
     * @return 枚举；未命中返回 null
     */
    public static AigFlowNodeTypeEnum find(String code) {
        if (code == null) {
            return null;
        }
        for (AigFlowNodeTypeEnum item : values()) {
            if (item.code.equalsIgnoreCase(code.trim())) {
                return item;
            }
        }
        return null;
    }

}
