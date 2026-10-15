package org.dromara.aigov.task.service;

import org.dromara.aigov.execution.AigExecutionPlan;

import java.util.ArrayList;
import java.util.List;

/**
 * 子 Agent 派发规格（设计 §6.5：「由父任务记录子任务、输入摘要与结果引用，设最大深度、超时和调用预算」）。
 *
 * <p><b>纯函数</b>：把"父任务 + 执行计划"翻译成一组<b>子任务创建规格</b>，不落库、不执行。
 * 抽成纯函数的原因是它承载两条"错了不会报错"的规则：</p>
 * <ol>
 *     <li><b>数据等级必须继承父任务，且不能更低</b>（设计 §7.2「有效数据等级取最高值」）——
 *         子任务若悄悄降级，受限资料会经由子 Agent 流出；</li>
 *     <li><b>幂等键必须稳定且按步骤唯一</b>（{@code sub:{parentTaskId}:{sequence}}）——
 *         父任务重试时不得创建出第二套子任务（重复执行 = 两次真实调用与两次计费）。</li>
 * </ol>
 *
 * <p>真正创建子任务与回写步骤状态由运行时驱动完成；本类只保证"要建什么"的口径唯一。</p>
 *
 * @author ai-gov
 */
public final class AigSubAgentDispatch {

    private AigSubAgentDispatch() {
    }

    /**
     * 一个子任务创建规格。
     *
     * @param agentVersionId 子 Agent 版本ID
     * @param sequence       步骤序号（从 1 开始，与任务步骤账本对齐）
     * @param parentTaskId   父任务ID
     * @param idempotencyKey 幂等键（父任务 + 步骤序号，重试不复用新键）
     * @param projectType    业务域（继承父任务）
     * @param dataLevel      数据等级（继承父任务，不降低）
     */
    public record ChildSpec(Long agentVersionId, int sequence, Long parentTaskId,
                            String idempotencyKey, String projectType, String dataLevel) {
    }

    /**
     * 生成子任务创建规格。
     *
     * @param parentTaskId      父任务ID（为空则不派发）
     * @param parentProjectType 父任务业务域（可空）
     * @param parentDataLevel   父任务数据等级（可空）
     * @param plan              执行计划（可空表示没有子 Agent）
     * @return 规格列表（按计划顺序；无子 Agent 返回空）
     */
    public static List<ChildSpec> plan(Long parentTaskId, String parentProjectType,
                                       String parentDataLevel, AigExecutionPlan plan) {
        List<ChildSpec> specs = new ArrayList<>();
        if (parentTaskId == null || plan == null || plan.getSubAgentVersionIds().isEmpty()) {
            return specs;
        }
        int sequence = 0;
        for (Long versionId : plan.getSubAgentVersionIds()) {
            if (versionId == null) {
                continue;
            }
            sequence++;
            specs.add(new ChildSpec(versionId, sequence, parentTaskId,
                "sub:" + parentTaskId + ":" + sequence, parentProjectType, parentDataLevel));
        }
        return specs;
    }

}
