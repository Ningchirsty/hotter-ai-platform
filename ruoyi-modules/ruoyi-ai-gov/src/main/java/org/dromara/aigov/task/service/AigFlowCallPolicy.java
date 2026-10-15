package org.dromara.aigov.task.service;

import org.dromara.aigov.execution.AigFlowNode;
import org.dromara.aigov.execution.AigFlowNodeTypeEnum;
import org.dromara.common.core.utils.StringUtils;

import java.util.List;

/**
 * 顺序流程"一次执行要发几次模型调用"的口径（设计 §6.5、§7.2）。
 *
 * <h3>这条规矩是用户拍板后才有的（2026-10-14 授权）</h3>
 * <p>在此之前，平台语义是"**一次任务执行 = 一次模型调用**"：流程里声明了多个 {@code STEP} 节点时，
 * 只有第一个会被执行，其余永远停在 {@code PLANNED}。用户明确授权改成"每步一次调用"后，
 * 这里把随之而来的三件事固定下来——它们都会影响钱和账，所以必须是可单测的纯判据：</p>
 * <ol>
 *   <li><b>调用次数上限</b>：取"声明的 {@code STEP} 节点数"与"配置里的 {@code limits.maxFlowSteps}"
 *       的较小值，再兜一个平台默认上限。**没有这条，一个写错的流程就能发出几十次调用**；
 *       {@code STEP} 节点数为 0 时返回 0（调用方据此保持"无流程节点时仍调用一次"的既有语义）。</li>
 *   <li><b>下一步的输入</b>：顺序流程的第 k 步（k&gt;1）以上一步的**输出**作为业务输入——
 *       否则"顺序执行"只是把同一句话问三遍。上一步输出为空时退回调用方给的原始输入
 *       （防御：正常路径上"有输出"是成功判据的一部分）。</li>
 *   <li><b>累计成本不可控这件事如实说清</b>：调用结果里**没有成本字段**，
 *       所以 {@code maxCost} 只能是"每一次调用"的上限，无法在调用之间累计扣减；
 *       任务级累计上限要等成本回执接入（与"模型成本指标暂无可聚合数据源"同一处缺口）。</li>
 * </ol>
 *
 * @author ai-gov
 */
public final class AigFlowCallPolicy {

    /**
     * 一次执行最多发起的流程调用次数（平台默认上限；可用 {@code limits.maxFlowSteps} 收紧）。
     *
     * <p>10 是"够跑一条业务流水线、又不至于把一次误配置放大成几十次计费"的折中。</p>
     */
    public static final int DEFAULT_MAX_STEP_CALLS = 10;

    private AigFlowCallPolicy() {
    }

    /**
     * 计划里声明了几个 {@code STEP} 节点（可执行的步骤）。
     *
     * @param nodes 流程节点（可空）
     * @return 数量
     */
    public static int stepNodeCount(List<AigFlowNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (AigFlowNode node : nodes) {
            if (node != null && AigFlowNodeTypeEnum.STEP == AigFlowNodeTypeEnum.find(node.type())) {
                count++;
            }
        }
        return count;
    }

    /**
     * 本次执行最多发起几次流程调用。
     *
     * @param nodes               流程节点（可空）
     * @param declaredMaxFlowSteps 配置声明的最大流程步骤数（可空）
     * @return 0=没有可执行的 STEP 节点；否则 ≥1
     */
    public static int maxStepCalls(List<AigFlowNode> nodes, Integer declaredMaxFlowSteps) {
        int declared = stepNodeCount(nodes);
        if (declared == 0) {
            return 0;
        }
        int configured = declaredMaxFlowSteps == null || declaredMaxFlowSteps <= 0
            ? DEFAULT_MAX_STEP_CALLS
            : Math.min(declaredMaxFlowSteps, DEFAULT_MAX_STEP_CALLS);
        return Math.min(declared, configured);
    }

    /**
     * 下一步的业务输入。
     *
     * @param previousOutput 上一步的输出（可空）
     * @param clientPrompt   调用方给的原始业务输入（可空）
     * @return 下一步的输入
     */
    public static String nextInput(String previousOutput, String clientPrompt) {
        return StringUtils.isBlank(previousOutput) ? clientPrompt : previousOutput;
    }

}
