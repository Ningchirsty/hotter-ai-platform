package org.dromara.aigov.task.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.execution.AigExecutionPlan;
import org.dromara.aigov.execution.AigFlowNode;
import org.dromara.aigov.execution.AigFlowNodeTypeEnum;
import org.dromara.aigov.task.domain.AigTaskStep;
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
import java.util.Objects;

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
        Prepared prepared = prepare(taskId, plan, conditionResults);
        if (prepared.early() != null) {
            return prepared.early();
        }
        List<String> transitions = new ArrayList<>();
        apply(prepared, transitions);
        if (!transitions.isEmpty()) {
            log.info("流程推进, taskId={}, action={}, nodeIndex={}, transitions={}",
                taskId, prepared.decision().action().getCode(), prepared.decision().nodeIndex(),
                transitions);
        }
        return toResult(prepared, transitions);
    }

    @Override
    public AigFlowDriveResult peek(Long taskId, AigExecutionPlan plan,
                                   Map<String, Boolean> conditionResults) {
        Prepared prepared = prepare(taskId, plan, conditionResults);
        if (prepared.early() != null) {
            return prepared.early();
        }
        // 只看不写：调用方据此决定"要不要真的推进"，避免"只想看看"却把节点挂起了
        return toResult(prepared, List.of());
    }

    /**
     * 校验入参、对齐账本、判定下一步（<b>不写任何状态</b>）。
     *
     * @param taskId           任务ID
     * @param plan             执行计划
     * @param conditionResults 条件结论
     * @return 准备结果；{@code early} 非空表示"没什么可推进的"（直接返回它）
     */
    private Prepared prepare(Long taskId, AigExecutionPlan plan,
                             Map<String, Boolean> conditionResults) {
        if (taskId == null || plan == null) {
            return new Prepared(null, null, null, doneResult("没有执行计划，无需推进流程"));
        }
        List<AigFlowNode> declared = plan.getFlowNodes();
        if (declared == null || declared.isEmpty()) {
            return new Prepared(null, null, null, doneResult("执行计划没有流程节点"));
        }
        List<AigTaskStep> steps = stepService.listFlowNodes(taskId);
        // 账本里记下的节点身份优先于执行计划：计划不落库，账本才是"当时到底声明了哪些节点"的证据
        List<AigFlowNode> nodes = alignWithLedger(declared, steps, taskId);
        AigFlowRuntime.Decision decision = AigFlowRuntime.decide(nodes, steps, conditionResults);
        return new Prepared(nodes, steps, decision, null);
    }

    /**
     * 按判定结果写状态。
     *
     * @param prepared    准备结果
     * @param transitions 迁移记录累加器
     */
    private void apply(Prepared prepared, List<String> transitions) {
        int index = prepared.decision().nodeIndex();
        AigFlowNode node = nodeAt(prepared.nodes(), index);
        AigTaskStep step = stepAt(prepared.steps(), index);
        switch (prepared.decision().action()) {
            case DONE, BLOCKED, WAIT_CONDITION, WAIT_EXECUTION -> {
                // 无状态可写：完成/受阻/等条件/等收尾都不是"驱动能推进"的状态
            }
            case WAIT_HUMAN -> park(step, transitions);
            case START -> start(node, step, transitions);
            case SKIP_REST -> skipRest(prepared.steps(), index, transitions);
        }
    }

    private static AigFlowDriveResult doneResult(String reason) {
        return new AigFlowDriveResult(AigFlowRuntime.Action.DONE, AigFlowRuntime.NO_NODE, null,
            null, null, reason, List.of());
    }

    private static AigFlowDriveResult toResult(Prepared prepared, List<String> transitions) {
        AigFlowRuntime.Decision decision = prepared.decision();
        int index = decision.nodeIndex();
        AigFlowNode node = nodeAt(prepared.nodes(), index);
        AigTaskStep step = stepAt(prepared.steps(), index);
        return new AigFlowDriveResult(decision.action(), index, decision.nodeRef(),
            node == null ? null : node.type(), step == null ? null : step.getStepId(),
            decision.reason(), List.copyOf(transitions));
    }

    /**
     * 一次推进所需的中间态。
     *
     * @param nodes    对齐后的节点清单
     * @param steps    账本里的流程节点步骤
     * @param decision 判定结果
     * @param early    提前返回的结果（非空表示无需推进）
     * @author ai-gov
     */
    private record Prepared(List<AigFlowNode> nodes, List<AigTaskStep> steps,
                            AigFlowRuntime.Decision decision, AigFlowDriveResult early) {
    }

    @Override
    public AigTaskStep confirm(Long taskId, int nodeIndex, String approvalRef) {
        if (StringUtils.isBlank(approvalRef)) {
            throw new ServiceException("人工确认必须带批准引用（没有凭据的确认不可追溯）");
        }
        if (taskId == null) {
            throw new ServiceException("任务ID不能为空");
        }
        List<AigTaskStep> steps = stepService.listFlowNodes(taskId);
        if (steps.isEmpty()) {
            throw new ServiceException("该任务没有流程节点，无法记录人工确认：taskId=" + taskId);
        }
        if (nodeIndex < 0 || nodeIndex >= steps.size()) {
            throw new ServiceException("流程节点下标越界：" + nodeIndex
                + "（该任务共 " + steps.size() + " 个流程节点，下标从 0 开始）");
        }
        AigTaskStep step = steps.get(nodeIndex);
        // 身份来自账本：没有它就无法回答"你批的是不是人工确认节点"，此时拒绝盲批
        AigFlowNodeTypeEnum type = AigFlowNodeTypeEnum.find(step.getNodeType());
        if (type == null) {
            throw new ServiceException("该步骤没有登记流程节点身份（node_type 为空），"
                + "无法校验批准对象是否为人工确认节点，拒绝记录：stepId=" + step.getStepId()
                + "（存量步骤需要重新执行一次以登记身份）");
        }
        if (type != AigFlowNodeTypeEnum.HUMAN_CONFIRM) {
            throw new ServiceException("该节点不是人工确认节点，不接受批准：" + type.getCode()
                + "（nodeIndex=" + nodeIndex + "，stepId=" + step.getStepId() + "）");
        }
        AigTaskStepStatusEnum status = AigTaskStepStatusEnum.find(step.getStatus());
        if (status != null && status.isTerminal()) {
            // 幂等：已批准过就返回既有凭据，不覆盖（覆盖会让"谁批的"失真）
            log.info("人工确认重复提交（步骤已是终态，返回既有凭据）, taskId={}, nodeIndex={}, status={}",
                taskId, nodeIndex, step.getStatus());
            return step;
        }
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
     * 把执行计划里的节点与账本里记下的身份对齐（账本优先）。
     *
     * <p>两处不一致说明"这次执行声明的节点"与"当时登记进账本的节点"对不上——可能是配置在
     * 执行后被人改过。此时<b>以账本为准</b>（它是当时那一刻的记录），并 WARN 留痕；
     * 数量不一致则原样返回，交给引擎报 BLOCKED（这里不做"能对上几个算几个"的妥协）。</p>
     *
     * @param declared 计划里声明的节点
     * @param steps    账本里的流程节点步骤
     * @param taskId   任务ID（日志定位）
     * @return 对齐后的节点清单
     */
    private List<AigFlowNode> alignWithLedger(List<AigFlowNode> declared, List<AigTaskStep> steps,
                                             Long taskId) {
        if (declared == null || steps == null || declared.size() != steps.size()) {
            return declared;
        }
        List<AigFlowNode> aligned = new ArrayList<>(declared.size());
        for (int i = 0; i < declared.size(); i++) {
            AigTaskStep step = steps.get(i);
            AigFlowNode planned = declared.get(i);
            if (step == null || StringUtils.isBlank(step.getNodeType())) {
                // 存量步骤没登记身份：用计划里的节点（同一次执行内仍然可用）
                aligned.add(planned);
                continue;
            }
            if (planned == null || !step.getNodeType().equalsIgnoreCase(planned.type())
                || !Objects.equals(step.getNodeRef(), planned.ref())) {
                log.warn("流程节点身份与执行计划不一致（以账本为准）：taskId={}, nodeIndex={}, "
                        + "账本={}/{}, 计划={}/{}", taskId, i, step.getNodeType(), step.getNodeRef(),
                    planned == null ? null : planned.type(), planned == null ? null : planned.ref());
            }
            aligned.add(new AigFlowNode(step.getNodeType(), step.getNodeRef(),
                planned == null ? null : planned.label()));
        }
        return aligned;
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
