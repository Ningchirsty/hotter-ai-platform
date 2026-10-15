package org.dromara.aigov.execution;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 子 Agent 顺序编排（设计 §6.5：「子 Agent 首期仅支持明确版本引用的顺序调用…设最大深度、
 * 超时和调用预算，禁止循环引用与默认无限重试」）。
 *
 * <p><b>纯函数</b>：它只做"把声明的子 Agent 展开成有序步骤并校验"，不查库、不执行。
 * 展开后的每一步都带 {@code depth} 与 {@code parentVersionId}，父任务据此记录子任务、
 * 输入摘要与结果引用（设计 §6.5）。</p>
 *
 * <p><b>两条必须拦住的错</b>：</p>
 * <ol>
 *     <li><b>循环引用</b>：A→B→A 会让编排在运行时无限展开——必须在展开时就报错，
 *         而不是等运行超时；</li>
 *     <li><b>超过最大深度</b>：深度是"调用链长度"的硬上限，超了要拒绝而不是截断
 *         （截断会静默少跑一段，看起来像成功）。</li>
 * </ol>
 *
 * <p>声明顺序即执行顺序：前一个成功了才谈得上后一个（顺序调用，不是并行）。</p>
 *
 * @author ai-gov
 */
public final class AigSubAgentOrchestration {

    private AigSubAgentOrchestration() {
    }

    /**
     * 一个编排步骤。
     *
     * @param versionId       子 Agent 版本ID
     * @param depth           深度（根的直接子为 1）
     * @param parentVersionId 父版本ID（根的直接子为 null）
     */
    public record Step(Long versionId, int depth, Long parentVersionId) {
    }

    /**
     * 编排结果。
     *
     * @param steps           有序步骤（可为空）
     * @param rejectionReason 拒绝原因（通过时为 null）
     */
    public record Result(List<Step> steps, String rejectionReason) {

        /**
         * 是否通过校验。
         *
         * @return 通过返回 true
         */
        public boolean accepted() {
            return rejectionReason == null;
        }

        static Result rejected(String reason) {
            return new Result(List.of(), reason);
        }
    }

    /**
     * 展开并校验编排。
     *
     * @param rootVersionId 根 Agent 版本ID
     * @param childrenOf    取某版本的直接子版本（按声明顺序）；返回 null/空表示没有子 Agent
     * @param maxDepth      最大深度（必须为正；1 = 只允许一层子 Agent）
     * @return 结果
     */
    public static Result plan(Long rootVersionId, Function<Long, List<Long>> childrenOf, int maxDepth) {
        if (rootVersionId == null) {
            return Result.rejected("子 Agent 编排缺少根版本");
        }
        if (maxDepth <= 0) {
            return Result.rejected("最大深度必须为正数（1 = 只允许一层子 Agent）");
        }
        if (childrenOf == null) {
            return Result.rejected("缺少子 Agent 解析器，无法展开编排");
        }
        List<Step> steps = new ArrayList<>();
        List<Long> path = new ArrayList<>();
        path.add(rootVersionId);
        String reason = expand(rootVersionId, childrenOf, maxDepth, 1, path, steps);
        if (reason != null) {
            return Result.rejected(reason);
        }
        return new Result(List.copyOf(steps), null);
    }

    private static String expand(Long parent, Function<Long, List<Long>> childrenOf, int maxDepth,
                                 int depth, List<Long> path, List<Step> steps) {
        List<Long> children = childrenOf.apply(parent);
        if (children == null || children.isEmpty()) {
            return null;
        }
        for (Long child : children) {
            if (child == null) {
                continue;
            }
            if (depth > maxDepth) {
                return "超过最大深度 " + maxDepth + "：子 Agent 版本 " + child
                    + "（超限要拒绝，不能截断——截断会静默少跑一段）";
            }
            if (path.contains(child)) {
                return "存在循环引用：" + renderPath(path, child)
                    + "（循环会让编排在运行时无限展开，必须在展开时就拒绝）";
            }
            steps.add(new Step(child, depth, parent));
            path.add(child);
            String reason = expand(child, childrenOf, maxDepth, depth + 1, path, steps);
            if (reason != null) {
                return reason;
            }
            path.remove(path.size() - 1);
        }
        return null;
    }

    private static String renderPath(List<Long> path, Long repeated) {
        StringBuilder text = new StringBuilder();
        for (Long item : path) {
            text.append(item).append(" → ");
        }
        return text.append(repeated).toString();
    }

}
