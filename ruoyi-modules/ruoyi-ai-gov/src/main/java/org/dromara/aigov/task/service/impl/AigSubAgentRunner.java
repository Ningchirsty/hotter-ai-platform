package org.dromara.aigov.task.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.execution.AigExecutionPlan;
import org.dromara.aigov.task.domain.AigTask;
import org.dromara.aigov.task.domain.AigTaskStep;
import org.dromara.aigov.task.domain.bo.AigTaskCreateBo;
import org.dromara.aigov.task.enums.AigTaskStatusEnum;
import org.dromara.aigov.task.enums.AigTaskStepKindEnum;
import org.dromara.aigov.task.enums.AigTaskStepStatusEnum;
import org.dromara.aigov.task.service.AigSubAgentDispatch;
import org.dromara.aigov.task.service.IAigSubAgentRunner;
import org.dromara.aigov.task.service.IAigTaskService;
import org.dromara.aigov.task.service.IAigTaskStepService;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 子 Agent 运行时驱动实现（设计 §6.5、§7.6）。
 *
 * @author ai-gov
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigSubAgentRunner implements IAigSubAgentRunner {

    /**
     * 子任务引用前缀（存在步骤 {@code output_ref} 上）
     */
    private static final String TASK_REF_PREFIX = "task:";

    private final IAigTaskService taskService;

    private final IAigTaskStepService stepService;

    @Override
    public List<Long> dispatchChildren(Long parentTaskId, String taskType, String projectType,
                                       String dataLevel, String snapshotJson, BigDecimal budget,
                                       AigExecutionPlan plan) {
        List<AigSubAgentDispatch.ChildSpec> specs =
            AigSubAgentDispatch.plan(parentTaskId, projectType, dataLevel, plan);
        List<Long> childIds = new ArrayList<>();
        for (AigSubAgentDispatch.ChildSpec spec : specs) {
            AigTaskCreateBo bo = new AigTaskCreateBo();
            bo.setTaskType(taskType);
            bo.setAgentVersionId(spec.agentVersionId());
            bo.setProjectType(spec.projectType());
            bo.setDataLevel(spec.dataLevel());
            bo.setSnapshotJson(snapshotJson);
            bo.setBudgetAmount(budget);
            // 幂等键来自派发规格：父任务重试不得建出第二套子任务
            bo.setIdempotencyKey(spec.idempotencyKey());
            bo.setRemark("子 Agent 步骤（父任务 " + parentTaskId + " 第 " + spec.sequence() + " 步）");
            Long childId = taskService.create(bo);
            if (childId == null) {
                log.warn("子任务未取回ID，跳过步骤登记：parentTaskId={}, sequence={}",
                    parentTaskId, spec.sequence());
                continue;
            }
            childIds.add(childId);
            // 把父步骤置为执行中并记录子任务引用（供对账）
            AigTaskStep step = stepService.findBySequence(parentTaskId, spec.sequence());
            if (step != null) {
                stepService.startStep(step.getStepId(), TASK_REF_PREFIX + childId);
            }
        }
        if (!childIds.isEmpty()) {
            log.info("子 Agent 已派发, parentTaskId={}, children={}", parentTaskId, childIds);
        }
        return childIds;
    }

    @Override
    public int reconcile(Long parentTaskId) {
        if (parentTaskId == null) {
            return 0;
        }
        int finished = 0;
        for (AigTaskStep step : stepService.listSteps(parentTaskId)) {
            if (!AigTaskStepKindEnum.SUB_AGENT.getCode().equals(step.getKind())
                || !AigTaskStepStatusEnum.RUNNING.getCode().equals(step.getStatus())) {
                continue;
            }
            Long childId = childTaskId(step.getOutputRef());
            if (childId == null) {
                continue;
            }
            AigTask child;
            try {
                child = taskService.getTask(childId);
            } catch (Exception e) {
                // 子任务读不到（已删/库异常）：本轮不动这一步骤，下轮再对账
                log.warn("对账读取子任务失败，跳过：parentTaskId={}, childTaskId={}", parentTaskId, childId);
                continue;
            }
            AigTaskStepStatusEnum mapped = mapChildStatus(
                AigTaskStatusEnum.find(child == null ? null : child.getStatus()));
            if (mapped == null) {
                // 子任务仍在跑（或状态不可识别）：保持执行中
                continue;
            }
            stepService.finishStep(step.getStepId(), mapped, step.getOutputRef(), null,
                child.getErrorCode(), child.getErrorMessage());
            finished++;
        }
        if (finished > 0) {
            log.info("子 Agent 步骤已按子任务终态收尾, parentTaskId={}, finished={}", parentTaskId, finished);
        }
        return finished;
    }

    /**
     * 从步骤引用解析子任务ID（{@code task:123} → 123）。
     *
     * @param outputRef 引用（可空）
     * @return 子任务ID；无法解析返回 null
     */
    static Long childTaskId(String outputRef) {
        if (StringUtils.isBlank(outputRef) || !outputRef.startsWith(TASK_REF_PREFIX)) {
            return null;
        }
        String raw = outputRef.substring(TASK_REF_PREFIX.length()).trim();
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 子任务终态 → 父步骤状态。
     *
     * <p>只映射<b>终态</b>；未终态返回 null（父步骤保持执行中）。把未终态当成失败会让
     * "还在跑"看起来像"已经失败"。</p>
     *
     * @param childStatus 子任务状态（可空）
     * @return 父步骤状态；未终态返回 null
     */
    static AigTaskStepStatusEnum mapChildStatus(AigTaskStatusEnum childStatus) {
        if (childStatus == null) {
            return null;
        }
        return switch (childStatus) {
            case SUCCEEDED, APPROVED, REJECTED -> AigTaskStepStatusEnum.SUCCEEDED;
            case FAILED, CANCELLED -> AigTaskStepStatusEnum.FAILED;
            default -> null;
        };
    }

}
