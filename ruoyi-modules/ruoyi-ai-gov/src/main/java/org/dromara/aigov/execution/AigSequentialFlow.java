package org.dromara.aigov.execution;

import java.util.List;

/**
 * 顺序流程校验（设计 §6.5）。
 *
 * <p>纯函数：校验声明的节点序列是否构成一个可执行的顺序流程。首期只有顺序执行，
 * 因此判据只有三条——</p>
 * <ol>
 *     <li><b>节点类型必须可识别</b>：拼错的类型不会报错，只会让那一步被静默跳过；</li>
 *     <li><b>必须有输出节点，且输出节点只能是最后一步</b>：没有输出的流程跑完没有交付物；
 *         输出后面再挂步骤则是"交付了又继续改"，结果与交付物不一致；</li>
 *     <li><b>步骤数不超过上限</b>：上限是"这次编排有多长"的硬约束，超了要拒绝而不是截断
 *         （截断会静默少跑一段，看起来像成功）。</li>
 * </ol>
 *
 * @author ai-gov
 */
public final class AigSequentialFlow {

    private AigSequentialFlow() {
    }

    /**
     * 校验结果。
     *
     * @param accepted 是否通过
     * @param reason   拒绝原因（通过时为 null）
     * @param nodes    规范化后的节点（通过时可用）
     */
    public record Result(boolean accepted, String reason, List<AigFlowNode> nodes) {

        static Result rejected(String reason) {
            return new Result(false, reason, List.of());
        }

        static Result accepted(List<AigFlowNode> nodes) {
            return new Result(true, null, List.copyOf(nodes));
        }
    }

    /**
     * 校验顺序流程。
     *
     * @param nodes    节点序列（可空/空表示没有声明流程）
     * @param maxSteps 最大步骤数（必须为正）
     * @return 结果
     */
    public static Result validate(List<AigFlowNode> nodes, int maxSteps) {
        if (nodes == null || nodes.isEmpty()) {
            return Result.accepted(List.of());
        }
        if (maxSteps <= 0) {
            return Result.rejected("最大步骤数必须为正数");
        }
        if (nodes.size() > maxSteps) {
            return Result.rejected("流程步骤数 " + nodes.size() + " 超过上限 " + maxSteps
                + "（超限要拒绝，不能截断——截断会静默少跑一段）");
        }
        boolean hasOutput = false;
        for (int i = 0; i < nodes.size(); i++) {
            AigFlowNode node = nodes.get(i);
            if (node == null || AigFlowNodeTypeEnum.find(node.type()) == null) {
                return Result.rejected("第 " + (i + 1) + " 个流程节点类型不可识别："
                    + (node == null ? null : node.type()));
            }
            AigFlowNodeTypeEnum type = AigFlowNodeTypeEnum.find(node.type());
            if (type == AigFlowNodeTypeEnum.OUTPUT) {
                hasOutput = true;
                if (i != nodes.size() - 1) {
                    return Result.rejected("输出节点必须是最后一步（后面还有步骤意味着"
                        + "「交付了又继续改」，结果与交付物会不一致）");
                }
            }
        }
        if (!hasOutput) {
            return Result.rejected("流程必须有输出节点：没有输出的流程跑完没有交付物");
        }
        return Result.accepted(nodes);
    }

}
