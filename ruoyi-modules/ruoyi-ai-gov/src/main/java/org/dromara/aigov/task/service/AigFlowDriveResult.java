package org.dromara.aigov.task.service;

import java.util.List;

/**
 * 流程推进结论（{@link AigFlowRuntime.Decision} 加上"驱动真正写了什么"）。
 *
 * @param action      判定出的下一步动作
 * @param nodeIndex   节点下标（{@link AigFlowRuntime#NO_NODE} 表示无指向）
 * @param nodeRef     节点引用（可空）
 * @param nodeType    节点类型编码（可空）
 * @param stepId      该节点对应的步骤ID（可空=没有登记过步骤）
 * @param reason      人读理由
 * @param transitions 本次真正落库的状态迁移（可读文本；空列表=没有写任何东西）
 * @author ai-gov
 */
public record AigFlowDriveResult(AigFlowRuntime.Action action, int nodeIndex, String nodeRef,
                                 String nodeType, Long stepId, String reason, List<String> transitions) {

    /**
     * 是否在等外部输入（人工确认 / 条件结论 / 节点收尾）。
     *
     * @return 等待中返回 true
     */
    public boolean waiting() {
        return action == AigFlowRuntime.Action.WAIT_HUMAN
            || action == AigFlowRuntime.Action.WAIT_CONDITION
            || action == AigFlowRuntime.Action.WAIT_EXECUTION;
    }

    /**
     * 流程是否已经停在终局（完成或受阻）。
     *
     * @return 终局返回 true
     */
    public boolean ended() {
        return action == AigFlowRuntime.Action.DONE || action == AigFlowRuntime.Action.BLOCKED;
    }

    /**
     * 调用方是否还需要去执行该节点的工作。
     *
     * <p>只有 {@code STEP} 节点的"启动"需要调用方接着跑业务动作：条件判断、人工确认与
     * 输出节点都由驱动就地把步骤收尾。</p>
     *
     * @return 需要调用方执行返回 true
     */
    public boolean needsExecution() {
        return action == AigFlowRuntime.Action.START && "STEP".equalsIgnoreCase(nodeType);
    }

}
