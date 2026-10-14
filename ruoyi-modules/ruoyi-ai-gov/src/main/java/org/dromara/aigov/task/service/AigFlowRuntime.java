package org.dromara.aigov.task.service;

import org.dromara.aigov.execution.AigFlowNode;
import org.dromara.aigov.execution.AigFlowNodeTypeEnum;
import org.dromara.aigov.task.domain.AigTaskStep;
import org.dromara.aigov.task.enums.AigTaskStepStatusEnum;
import org.dromara.common.core.utils.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 流程节点运行时判据（设计 §6.5：「流程先提供顺序步骤、条件判断、人工确认和输出节点」）。
 *
 * <h3>它补的是哪条断链</h3>
 * <p>节点此前只被"校验并冻结进执行计划"（{@code AigSequentialFlow}），运行时没有任何东西
 * 读它们——条件判断不会真的拦、人工确认不会真的挂起。结果是<b>声明了人工确认的流程
 * 照样一路跑完</b>，而声明本身看起来是生效的。</p>
 *
 * <h3>首期语义（刻意很小，且都是 fail-closed）</h3>
 * <ul>
 *   <li><b>顺序</b>：节点按声明顺序推进，只处理第一个"还没到终态"的节点；前面的没走完，
 *       后面的不启动。空流程视为已完成（调用方不该为它空等）。</li>
 *   <li><b>条件是一个"门"，不是一个多分支 DAG</b>：结论为真则本节点通过、继续；
 *       为假则本节点与后续节点全部跳过。原因是首期工具链是顺序流程，凭空长出一套
 *       分支选择语言会带来"没人能一眼看出会跑哪条路"的配置。</li>
 *   <li><b>没有条件结论就等</b>（{@link Action#WAIT_CONDITION}）：本模块不含表达式引擎，
 *       结论由调用方给出；缺结论时绝不默认"真"或"假"。</li>
 *   <li><b>人工确认是挂起</b>（{@link Action#WAIT_HUMAN}）：没有批准就不往下走。
 *       批准记录在步骤的 {@code approval_ref} 上——批准本身是"这一步骤的凭据"，
 *       不另立一张表，避免"步骤说没批、批准表说批了"的不一致。</li>
 *   <li><b>上游失败即停</b>（{@link Action#BLOCKED}）：{@code FAILED} 是终态，流程停在
 *       失败那一步上，而不是跳过它继续——跳过会把"某一步没成"藏进一次"整体成功"里。</li>
 * </ul>
 *
 * <p>纯函数、无数据库、无 Spring：节点顺序与状态错位这类问题不会报错，只会让流程
 * 静默地跑错，所以判据必须能逐条单测。</p>
 *
 * @author ai-gov
 */
public final class AigFlowRuntime {

    private AigFlowRuntime() {
    }

    /**
     * 下一步动作。
     */
    public enum Action {

        /** 启动该节点（控制类节点由驱动就地收尾，STEP 节点交给调用方执行） */
        START("START", "启动节点"),

        /** 等待人工确认（节点已挂起） */
        WAIT_HUMAN("WAIT_HUMAN", "等待人工确认"),

        /** 等待条件结论（调用方尚未给出，不猜） */
        WAIT_CONDITION("WAIT_CONDITION", "等待条件结论"),

        /** 该节点执行中，等待它收尾 */
        WAIT_EXECUTION("WAIT_EXECUTION", "等待节点执行结束"),

        /** 条件不满足：本节点与后续节点全部跳过 */
        SKIP_REST("SKIP_REST", "条件不满足，跳过其后节点"),

        /** 全部节点已到终态 */
        DONE("DONE", "流程已完成"),

        /** 无法继续（状态错位 / 类型不可识别 / 上游失败） */
        BLOCKED("BLOCKED", "流程受阻，需人工处理");

        private final String code;

        private final String desc;

        Action(String code, String desc) {
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
    }

    /** 未指向任何节点（空流程、整体受阻） */
    public static final int NO_NODE = -1;

    /**
     * 一次判定结论。
     *
     * @param action    下一步动作
     * @param nodeIndex 节点下标（从 0 开始；无指向时为 {@link #NO_NODE}）
     * @param nodeRef   节点引用（可空）
     * @param reason    人读理由（要能直接讲给操作者听）
     * @author ai-gov
     */
    public record Decision(Action action, int nodeIndex, String nodeRef, String reason) {

        /**
         * 是否需要驱动写状态（等人类动作不需要写）。
         *
         * @return 需要写返回 true
         */
        public boolean writesLedger() {
            return action == Action.START || action == Action.SKIP_REST || action == Action.WAIT_HUMAN;
        }
    }

    /**
     * 判定下一步动作。
     *
     * @param nodes            流程节点（按声明顺序；可空）
     * @param steps            与该顺序一一对应的 FLOW_NODE 步骤（可空）
     * @param conditionResults 条件结论，键为节点 ref（可空=一个都没算出来）
     * @return 结论
     */
    public static Decision decide(List<AigFlowNode> nodes, List<AigTaskStep> steps,
                                  Map<String, Boolean> conditionResults) {
        int count = nodes == null ? 0 : nodes.size();
        if (count == 0) {
            return new Decision(Action.DONE, NO_NODE, null, "执行计划没有流程节点");
        }
        if (steps == null || steps.size() != count) {
            // 数量不一致说明"节点与步骤的对应关系"已经被破坏：拿错位状态驱动流程，
            // 结果会落在别的节点上，而且不会有任何异常提醒
            return new Decision(Action.BLOCKED, NO_NODE, null,
                "流程节点与步骤数量不一致（节点 " + count + " 个、步骤 "
                    + (steps == null ? 0 : steps.size()) + " 个）：状态对应关系已破坏，拒绝推进");
        }
        for (int i = 0; i < count; i++) {
            if (AigTaskStepStatusEnum.FAILED == AigTaskStepStatusEnum.find(steps.get(i).getStatus())) {
                return new Decision(Action.BLOCKED, i, nodes.get(i).ref(),
                    "第 " + (i + 1) + " 个节点已失败，流程停止（跳过失败继续会掩盖它）");
            }
        }
        for (int i = 0; i < count; i++) {
            AigTaskStep step = steps.get(i);
            AigTaskStepStatusEnum status = AigTaskStepStatusEnum.find(step.getStatus());
            if (status == null) {
                return new Decision(Action.BLOCKED, i, nodes.get(i).ref(),
                    "步骤状态不可识别，拒绝推进：" + step.getStatus());
            }
            if (status.isTerminal()) {
                // SUCCEEDED / SKIPPED 都算"这一步已经过掉了"
                continue;
            }
            AigFlowNode node = nodes.get(i);
            AigFlowNodeTypeEnum type = AigFlowNodeTypeEnum.find(node.type());
            if (type == null) {
                return new Decision(Action.BLOCKED, i, node.ref(),
                    "流程节点类型不可识别，拒绝推进：" + node.type() + "（不做猜测式跳过）");
            }
            switch (type) {
                case STEP -> {
                    return status == AigTaskStepStatusEnum.RUNNING
                        ? new Decision(Action.WAIT_EXECUTION, i, node.ref(),
                            "第 " + (i + 1) + " 个节点执行中，等待其收尾")
                        : new Decision(Action.START, i, node.ref(),
                            "启动第 " + (i + 1) + " 个节点");
                }
                case HUMAN_CONFIRM -> {
                    boolean approved = status == AigTaskStepStatusEnum.RUNNING
                        && StringUtils.isNotBlank(step.getApprovalRef());
                    if (approved) {
                        return new Decision(Action.START, i, node.ref(),
                            "第 " + (i + 1) + " 个人工确认已记录，收尾并继续");
                    }
                    return new Decision(Action.WAIT_HUMAN, i, node.ref(),
                        "第 " + (i + 1) + " 个节点需要人工确认，等待批准后继续");
                }
                case CONDITION -> {
                    Boolean result = conditionResults == null ? null : conditionResults.get(node.ref());
                    if (result == null) {
                        return new Decision(Action.WAIT_CONDITION, i, node.ref(),
                            "第 " + (i + 1) + " 个节点是条件判断，但还没有结论（不默认取真或假）");
                    }
                    if (!result) {
                        return new Decision(Action.SKIP_REST, i, node.ref(),
                            "第 " + (i + 1) + " 个节点的条件不满足，本节点及其后节点全部跳过");
                    }
                    return new Decision(Action.START, i, node.ref(),
                        "第 " + (i + 1) + " 个节点的条件已满足，通过");
                }
                case OUTPUT -> {
                    return new Decision(Action.START, i, node.ref(),
                        "收尾第 " + (i + 1) + " 个输出节点");
                }
                default -> {
                    return new Decision(Action.BLOCKED, i, node.ref(),
                        "流程节点类型未支持：" + type.getCode());
                }
            }
        }
        return new Decision(Action.DONE, NO_NODE, null, "全部流程节点已到终态");
    }

}
