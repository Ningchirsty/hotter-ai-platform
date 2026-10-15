package org.dromara.aigov.task.domain.vo;

import lombok.Data;
import org.dromara.aigov.task.domain.AigTaskStep;
import org.dromara.aigov.task.enums.AigTaskStepStatusEnum;

import java.io.Serial;
import java.io.Serializable;

/**
 * 任务流程节点视图（设计 §6.5）。
 *
 * <p><b>它为什么存在</b>：流程节点的状态此前只能靠"看数据库"——任务停在
 * {@code NEED_HUMAN} 时，界面上看不出"停在第几个节点、它是不是人工确认、批准引用有没有"。
 * 于是"需要人工确认"就成了一句没法操作的话。本视图把流程节点暴露成可读、可核对的形状，
 * 人工确认的入口（{@code POST /aigov/task/{taskId}/flow/confirm}）也以它作答。</p>
 *
 * <p>只暴露流程节点（{@code kind=FLOW_NODE}）：子 Agent 步骤有自己的对账链路，
 * 混在一起会让"第几个节点"这个下标失去意义。</p>
 *
 * @author ai-gov
 */
@Data
public class AigTaskFlowNodeVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 任务ID
     */
    private Long taskId;

    /**
     * 节点下标（从 0 开始；与冻结计划里的节点顺序一致，人工确认按它指定对象）
     */
    private Integer nodeIndex;

    /**
     * 步骤序号（账本里的全局序号，含子 Agent 步骤，用于在事件流/日志里对齐）
     */
    private Integer sequence;

    /**
     * 流程节点类型（{@code AigFlowNodeTypeEnum}；存量行可能为空=当时未登记身份）
     */
    private String nodeType;

    /**
     * 流程节点引用（条件键/步骤标识/输出契约键）
     */
    private String nodeRef;

    /**
     * 步骤状态（{@code AigTaskStepStatusEnum}）
     */
    private String status;

    /**
     * 结果引用
     */
    private String outputRef;

    /**
     * 批准引用（人工确认被记录后非空；与状态一起构成"已批准"的判据）
     */
    private String approvalRef;

    /**
     * 失败分类（失败时）
     */
    private String errorCode;

    /**
     * 失败原因（失败时）
     */
    private String errorMessage;

    /**
     * 是否处于"等人工确认"（挂起状态：执行中且没有批准引用）。
     *
     * <p>把这个判断放在服务端：界面自己拼"RUNNING + approvalRef 为空"这种判据，
     * 迟早和驱动里的判据不一致，而不一致的方向是"界面说已批准、服务端说没有"。</p>
     *
     * @return 等待人工确认返回 true
     */
    public boolean isAwaitingConfirmation() {
        return "HUMAN_CONFIRM".equalsIgnoreCase(nodeType)
            && AigTaskStepStatusEnum.RUNNING == AigTaskStepStatusEnum.find(status)
            && (approvalRef == null || approvalRef.isBlank());
    }

    /**
     * 账本行 → 视图。
     *
     * @param nodeIndex 节点下标
     * @param step      账本行
     * @return 视图
     */
    public static AigTaskFlowNodeVo of(Integer nodeIndex, AigTaskStep step) {
        AigTaskFlowNodeVo vo = new AigTaskFlowNodeVo();
        vo.setNodeIndex(nodeIndex);
        vo.setTaskId(step.getTaskId());
        vo.setSequence(step.getSequence());
        vo.setNodeType(step.getNodeType());
        vo.setNodeRef(step.getNodeRef());
        vo.setStatus(step.getStatus());
        vo.setOutputRef(step.getOutputRef());
        vo.setApprovalRef(step.getApprovalRef());
        vo.setErrorCode(step.getErrorCode());
        vo.setErrorMessage(step.getErrorMessage());
        return vo;
    }

}
