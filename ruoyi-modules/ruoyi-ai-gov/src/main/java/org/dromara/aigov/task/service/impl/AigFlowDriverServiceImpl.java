package org.dromara.aigov.task.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.execution.AigExecutionPlan;
import org.dromara.aigov.execution.AigFlowNode;
import org.dromara.aigov.execution.AigFlowNodeTypeEnum;
import org.dromara.aigov.task.domain.AigTaskStep;
import org.dromara.aigov.task.enums.AigTaskStepKindEnum;
import org.dromara.aigov.task.enums.AigTaskStepStatusEnum;
import org.dromara.aigov.task.service.AigFlowDriveResult;
import org.dromara.aigov.task.service.AigFlowRuntime;
import org.dromara.aigov.task.service.IAigFlowDriverService;
import org.dromara.aigov.task.service.IAigTaskStepService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 流程节点运行时驱动实现（设计 §6.5）。
 *
 * <h3>节点与步骤怎么对应（这里最容易静默出错）</h3>
 * <p>{@code recordPlannedSteps} 按"子 Agent 步骤在前、流程节点在后"的顺序登记，
 * 流程节点各占一步。驱动按 {@code kind=FLOW_NODE} 过滤并按序号升序取出，
 * 于是第 k 个 {@code FLOW_NODE} 步骤就是第 k 个流程节点——这条对应关系是两张表之间
 * 唯一的桥。数量不一致时驱动<b>拒绝推进</b>（{@link AigFlowRuntime} 会给出 BLOCKED），
 * 而不是按"能对上几个算几个"继续：错位的状态会落在别的节点上，且不会有任何异常。</p>
 *
 * <h3>为什么批准不另立一张表</h3>
 * <p>人工确认在步骤账本上表达为：挂起 = {@code RUNNING} + 空 {@code approval_ref}；
 * 批准 = {@code SUCCEEDED} + 非空 {@code approval_ref}。确认与推进之间没有额外的内存状态，
 * 进程重启也不丢"已经批过"这件事，也不会出现"步骤说没批、批准表说批了"。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigFlowDriverServiceImpl implements IAigFlowDriverService {

    private final IAigTaskStepService stepService;

    @Override
    public AigFlowDriveResult advance(Long taskId, AigExecutionPlan plan,
                                      Map<String, Boolean> conditionResults) {
        if (taskId == null || plan == null) {
            return new AigFlowDriveResult(AigFlowRuntime.Action.DONE, AigFlowRuntime.NO_NODE, null,
                null, null, "没有执行计划，无需推进流程", List.of());
        }
        List<AigFlowNode> nodes = plan.getFlowNodes();
        if (nodes == null || nodes.isEmpty()) {
            return new AigFlowDriveResult(AigFlowRuntime.Action.DONE, AigFlowRuntime.NO_NODE, null,
                null, null, "执行计划没有流程节点", List.of());
        }
        List<AigTaskStep> steps = flowNodeSteps(taskId);
        AigFlowRuntime.Decision decision = AigFlowRuntime.decide(nodes, steps, conditionResults);
        int index = decision.nodeIndex();
        List<String> transitions = new ArrayList<>();
        AigFlowNode node = nodeAt(nodes, index);
        AigTaskStep step = stepAt(steps, index);
        switch (decision.action()) {
            case DONE, BLOCKED, WAIT_CONDITION, WAIT_EXECUTION -> {
                // 无状态可写：完成/受阻/等条件/等收尾都不是"驱动能推进"的状态
            }
            case WAIT_HUMAN -> park(step, transitions);
            case START -> start(node, step, transitions);
            case SKIP_REST -> skipRest(steps, index, transitions);
        }
        if (!transitions.isEmpty()) {
            log.info("流程推进, taskId={}, action={}, nodeIndex={}, transitions={}",
                taskId, decision.action().getCode(), index, transitions);
        }
        return new AigFlowDriveResult(decision.action(), index, decision.nodeRef(),
            node == null ? null : node.type(), step == null ? null : step.getStepId(),
            decision.reason(), List.copyOf(transitions));
    }

    @Override
    public AigTaskStep confirm(Long taskId, AigExecutionPlan plan, int nodeIndex, String approvalRef) {
        if (StringUtils.isBlank(approvalRef)) {
            throw new ServiceException("人工确认必须带批准引用（没有凭据的确认不可追溯）");
        }
        if (plan == null || plan.getFlowNodes() == null || plan.getFlowNodes().isEmpty()) {
            throw new ServiceException("该任务没有流程节点，无法记录人工确认：taskId=" + taskId);
        }
        AigFlowNode node = nodeAt(plan.getFlowNodes(), nodeIndex);
        if (node == null) {
            throw new ServiceException("流程节点下标越界：" + nodeIndex);
        }
        if (AigFlowNodeTypeEnum.HUMAN_CONFIRM != AigFlowNodeTypeEnum.find(node.type())) {
            throw new ServiceException("该节点不是人工确认节点，不接受批准：" + node.type()
                + "（nodeIndex=" + nodeIndex + "）");
        }
        List<AigTaskStep> steps = flowNodeSteps(taskId);
        if (steps.size() != plan.getFlowNodes().size()) {
            throw new ServiceException("流程节点与步骤数量不一致（节点 " + plan.getFlowNodes().size()
                + " 个、步骤 " + steps.size() + " 个）：状态对应关系已破坏，拒绝记录确认");
        }
        AigTaskStep step = steps.get(nodeIndex);
        startIfPlanned(step);
        AigTaskStep done = stepService.finishStep(step.getStepId(), AigTaskStepStatusEnum.SUCCEEDED,
            null, approvalRef.trim(), null, null);
        log.info("人工确认已记录, taskId={}, nodeIndex={}, stepId={}, approvalRef={}",
            taskId, nodeIndex, step.getStepId(), approvalRef);
        return done;
    }

    @Override
    public AigTaskStep settle(Long stepId, boolean succeeded, String outputRef, String errorCode,
                              String errorMessage) {
        if (stepId == null) {
            throw new ServiceException("步骤ID不能为空");
        }
        return succeeded
            ? stepService.finishStep(stepId, AigTaskStepStatusEnum.SUCCEEDED, outputRef, null, null, null)
            : stepService.finishStep(stepId, AigTaskStepStatusEnum.FAILED, outputRef, null,
                errorCode, errorMessage);
    }

    /**
     * 启动节点：先置执行中，控制类节点随即就地收尾。
     *
     * @param node        节点（非空）
     * @param step        对应步骤（非空）
     * @param transitions 迁移记录累加器
     */
    private void start(AigFlowNode node, AigTaskStep step, List<String> transitions) {
        if (step == null) {
            return;
        }
        startIfPlanned(step, transitions);
        AigFlowNodeTypeEnum type = AigFlowNodeTypeEnum.find(node.type());
        if (type == AigFlowNodeTypeEnum.STEP) {
            // 业务动作由调用方执行，驱动只负责"把它置为执行中"
            return;
        }
        String outputRef = type == AigFlowNodeTypeEnum.CONDITION ? "condition:true" : null;
        String approvalRef = type == AigFlowNodeTypeEnum.HUMAN_CONFIRM ? step.getApprovalRef() : null;
        AigTaskStep done = stepService.finishStep(step.getStepId(), AigTaskStepStatusEnum.SUCCEEDED,
            outputRef, approvalRef, null, null);
        transitions.add(describe(done.getStepId(), "RUNNING", AigTaskStepStatusEnum.SUCCEEDED.getCode()));
    }

    /**
     * 挂起人工确认节点（可见地停在 {@code RUNNING}）。
     *
     * @param step        步骤（可空）
     * @param transitions 迁移记录累加器
     */
    private void park(AigTaskStep step, List<String> transitions) {
        startIfPlanned(step, transitions);
    }

    /**
     * 条件不满足：本节点与其后未到终态的节点全部跳过。
     *
     * @param steps       步骤清单
     * @param from        起始下标
     * @param transitions 迁移记录累加器
     */
    private void skipRest(List<AigTaskStep> steps, int from, List<String> transitions) {
        for (int i = from; i < steps.size(); i++) {
            AigTaskStep step = steps.get(i);
            AigTaskStepStatusEnum status = AigTaskStepStatusEnum.find(step.getStatus());
            if (status == null || status.isTerminal()) {
                continue;
            }
            AigTaskStep done = stepService.finishStep(step.getStepId(), AigTaskStepStatusEnum.SKIPPED,
                i == from ? "condition:false" : null, null, null, null);
            transitions.add(describe(done.getStepId(), status.getCode(),
                AigTaskStepStatusEnum.SKIPPED.getCode()));
        }
    }

    /**
     * {@code PLANNED → RUNNING}（已是执行中则不动，判据幂等）。
     *
     * @param step 步骤（可空）
     */
    private void startIfPlanned(AigTaskStep step) {
        startIfPlanned(step, null);
    }

    private void startIfPlanned(AigTaskStep step, List<String> transitions) {
        if (step == null || AigTaskStepStatusEnum.PLANNED != AigTaskStepStatusEnum.find(step.getStatus())) {
            return;
        }
        AigTaskStep running = stepService.startStep(step.getStepId());
        step.setStatus(running.getStatus());
        if (transitions != null) {
            transitions.add(describe(step.getStepId(), AigTaskStepStatusEnum.PLANNED.getCode(),
                AigTaskStepStatusEnum.RUNNING.getCode()));
        }
    }

    /**
     * 取某任务的 FLOW_NODE 步骤（按序号升序，只取首次尝试）。
     *
     * @param taskId 任务ID
     * @return 步骤清单
     */
    private List<AigTaskStep> flowNodeSteps(Long taskId) {
        List<AigTaskStep> all = stepService.listSteps(taskId);
        List<AigTaskStep> result = new ArrayList<>();
        for (AigTaskStep step : all) {
            if (step == null || !AigTaskStepKindEnum.FLOW_NODE.getCode().equals(step.getKind())) {
                continue;
            }
            // 重试是新的 attempt：流程节点只按首次尝试的账本驱动，重试记录不参与
            if (step.getAttempt() != null && step.getAttempt() != 1) {
                continue;
            }
            result.add(step);
        }
        return result;
    }

    private static AigFlowNode nodeAt(List<AigFlowNode> nodes, int index) {
        return index >= 0 && index < nodes.size() ? nodes.get(index) : null;
    }

    private static AigTaskStep stepAt(List<AigTaskStep> steps, int index) {
        return index >= 0 && index < steps.size() ? steps.get(index) : null;
    }

    private static String describe(Long stepId, String from, String to) {
        return "step:" + stepId + " " + from + "→" + to;
    }

}
