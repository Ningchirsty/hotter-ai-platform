package org.dromara.aigov.task.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.domain.bo.AigInvokeBo;
import org.dromara.aigov.domain.vo.AigInvokeVo;
import org.dromara.aigov.enums.AigErrorClassEnum;
import org.dromara.aigov.enums.AigRouteDecisionEnum;
import org.dromara.aigov.execution.AigExecutionCitation;
import org.dromara.aigov.execution.AigExecutionPlan;
import org.dromara.aigov.execution.AigExecutionPurposeEnum;
import org.dromara.aigov.execution.IAigAgentExecutionResolver;
import org.dromara.aigov.service.IAigInvokeService;
import org.dromara.aigov.task.domain.AigTask;
import org.dromara.aigov.task.domain.bo.AigTaskExecuteBo;
import org.dromara.aigov.task.domain.vo.AigTaskDetailVo;
import org.dromara.aigov.task.domain.vo.AigTaskExecuteVo;
import org.dromara.aigov.task.domain.vo.AigTaskSnapshotVo;
import org.dromara.aigov.task.domain.vo.AigTaskVo;
import org.dromara.aigov.task.enums.AigTaskPolicyResultEnum;
import org.dromara.aigov.task.enums.AigTaskStatusEnum;
import org.dromara.aigov.task.service.IAigTaskExecutor;
import org.dromara.aigov.task.service.IAigSubAgentRunner;
import org.dromara.aigov.task.service.IAigFlowDriverService;
import org.dromara.aigov.task.service.AigFlowCallPolicy;
import org.dromara.aigov.task.service.AigFlowConditions;
import org.dromara.aigov.task.service.AigFlowDriveResult;
import org.dromara.aigov.task.service.AigFlowRuntime;
import org.dromara.aigov.task.service.IAigTaskService;
import org.dromara.aigov.task.service.IAigTaskStepService;
import org.dromara.aigov.workspace.domain.AigScenarioVersion;
import org.dromara.aigov.workspace.helper.AigScenarioDispatch;
import org.dromara.aigov.workspace.scenario.config.AigScenarioDispatchProperties;
import org.dromara.aigov.workspace.scenario.service.IAigScenarioFlowDispatcher;
import org.dromara.aigov.workspace.scenario.service.IAigScenarioVersionResolver;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.scenario.api.domain.AigScenarioFlowRequest;
import org.dromara.scenario.api.domain.AigScenarioFlowResult;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务执行实现（任务层 → 统一调用入口）。
 *
 * @author ai-gov
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigTaskExecutorImpl implements IAigTaskExecutor {

    private final IAigTaskService taskService;
    private final IAigInvokeService invokeService;
    private final AigScenarioDispatchProperties scenarioDispatchProperties;
    private final IAigScenarioVersionResolver scenarioVersionResolver;
    private final IAigScenarioFlowDispatcher scenarioFlowDispatcher;
    private final IAigAgentExecutionResolver executionResolver;
    private final IAigTaskStepService taskStepService;
    private final IAigSubAgentRunner subAgentRunner;
    private final IAigFlowDriverService flowDriver;
    private final JsonMapper jsonMapper;

    @Override
    public AigTaskExecuteVo execute(AigTaskExecuteBo bo) {
        if (bo == null || bo.getTaskId() == null) {
            throw new ServiceException("任务ID不能为空");
        }
        AigTaskDetailVo detail = taskService.getDetail(bo.getTaskId());
        AigTaskVo task = detail.getTask();
        AigTaskSnapshotVo snapshot = detail.getSnapshot();
        if (snapshot == null) {
            throw new ServiceException("任务没有输入快照，拒绝执行：taskId=" + bo.getTaskId());
        }
        // 快照不可变性的校验：哈希不等于冻结值时说明快照被改过，
        // 此时执行出来的结果无法归因，宁可停下来说清楚
        assertSnapshotUntouched(snapshot, bo.getTaskId());

        AigTaskStatusEnum current = AigTaskStatusEnum.find(task.getStatus());
        if (current != AigTaskStatusEnum.QUEUED) {
            throw new ServiceException("只有「已入队」的任务可以执行，当前状态="
                + task.getStatus() + "：taskId=" + bo.getTaskId()
                + "（若在等待重试，请等调度器重新入队；若已失败待人工，请先人工重新入队）");
        }

        // 1) 入队 → 执行中。同步 Provider 没有「派发」这一可观测中间态，故直接进 RUNNING。
        AigTask running = taskService.transition(bo.getTaskId(), task.getVersion(),
            AigTaskStatusEnum.RUNNING, "开始执行（走统一调用入口）", null);

        // ★ D04 统一失败收口：从这一刻起任务已经被置为 RUNNING，**任何**异常都必须落成
        //   一次可解释的失败，而不是直接冒出去让状态停在 RUNNING——那要等超时清扫
        //   （默认 1800 秒）才被判失败，且会被记成 TIMEOUT（**假账**：明明是缺能力编码、
        //   数据库或空指针问题）。过去的缺口有两处：①"未登记能力编码"直接抛 ServiceException；
        //   ②调用入口抛出的 ServiceException 被原样 rethrow。现在两者都走这同一条收口。
        try {
            return runOnceRunning(bo, task, snapshot, running);
        } catch (RuntimeException e) {
            throw settleFailure(bo.getTaskId(), e);
        }
    }

    /**
     * 执行态内的实际工作：场景派发 / 统一调用 / 落执行事实与策略结论。
     *
     * <p>这里刻意<b>不</b>逐段捕获异常——由 {@link #execute} 的单一 catch 统一收口（设计 D04）。
     * 逐段捕获的问题是"漏了某条 throw 路径"没人能发现；单一收口让"进入执行态后必然有落点"
     * 成为结构性保证，而不是靠人工核对每个 throw 点。</p>
     *
     * @param bo       执行入参
     * @param task     任务视图
     * @param snapshot 输入快照
     * @param running  已置为 RUNNING 的任务
     * @return 执行结果
     */
    private AigTaskExecuteVo runOnceRunning(AigTaskExecuteBo bo, AigTaskVo task,
                                            AigTaskSnapshotVo snapshot, AigTask running) {
        // 1.5) 场景任务：交给"既有链路"执行，而不是普通模型调用（增量 13）。
        //      由 aigov.scenario.dispatch.enabled 控制，默认关闭——关着时行为与以前完全一致。
        //      走到这里说明任务带 scenarioCode：场景类卡片的任务不带能力编码，
        //      普通模型调用路径本来也走不通（会报"未登记能力编码"）。
        if (scenarioDispatchProperties.isEnabled() && StringUtils.isNotBlank(task.getScenarioCode())) {
            return dispatchScenario(bo, task, snapshot, running);
        }

        // 2) 组装调用入参：能力、数据等级、场景都取自任务本身——
        //    这样「任务是怎么登记的」与「实际怎么调用的」不可能不一致
        AigInvokeBo invokeBo = new AigInvokeBo();
        invokeBo.setCapabilityCode(task.getCapabilityCode());
        invokeBo.setDataLevel(task.getDataLevel());
        invokeBo.setScenarioCode(task.getScenarioCode());
        // 任务身份要带进调用入参：策略决策账本按它把「为什么」接到具体任务上
        // （那一列此前恒空，只能靠 traceId 去任务表里碰运气）
        invokeBo.setTaskId(bo.getTaskId());
        // 任务知道自己挂在哪个 Agent 版本上，必须带进调用入参，否则审计里就没有这个维度，
        // 灰度的「按版本统计调用次数/失败率」永远拿不到任务发起的那些调用
        invokeBo.setAgentVersionId(task.getAgentVersionId());
        invokeBo.setPrompt(bo.getPrompt());
        invokeBo.setPayload(bo.getPayload());
        invokeBo.setInputSnapshotRef("task:" + bo.getTaskId() + "/snapshot:" + snapshot.getSnapshotId());
        invokeBo.setMaxCost(bo.getMaxCost() != null ? bo.getMaxCost() : snapshot.getBudgetAmount());

        // ★ D01：Agent 执行模式——从<b>冻结版本</b>编译系统 Prompt，调用方提交的 prompt
        //   只作为系统指令之后的业务输入。非 v2 配置 / 无目标版本时解析器返回 null，
        //   本方法退回原来的"调用方提供 prompt"语义（不改变任何存量版本的行为）。
        // ★ D05：本次执行对应流程里的哪个 STEP 节点（无流程 / 首节点是控制类节点时为 null）
        Long flowStepId = null;
        AigExecutionPlan plan = executionResolver.resolve(bo.getTaskId(), task.getAgentVersionId(),
            task.getDataLevel(), snapshot.getSnapshotJson(), bo.getPrompt(),
            invokeBo.getMaxCost(), AigExecutionPurposeEnum.PRODUCTION);
        if (plan != null) {
            // 系统 Prompt 一律以版本冻结内容为准，客户端不能提交或覆盖它（设计 §7.2）
            invokeBo.setPrompt(plan.composePrompt(bo.getPrompt()));
            invokeBo.setCapabilityCode(plan.getCapabilityCode());
            // D02：有效外发许可是硬约束，必须传给路由引擎（与策略取与）
            invokeBo.setAllowExternal(plan.getAllowExternal());
            if (plan.getMaxCost() != null) {
                invokeBo.setMaxCost(plan.getMaxCost());
            }
            // ★ D05：把"这次编排有哪些步骤"登记成任务步骤（子 Agent / 流程节点）。
            //   可追溯性记录：写失败不应让一次执行变成失败，但必须留痕。
            try {
                taskStepService.recordPlannedSteps(bo.getTaskId(), plan);
            } catch (Exception e) {
                log.error("登记任务步骤失败（不影响本次执行）, taskId={}", bo.getTaskId(), e);
            }
            // ★ D05 运行时闸门：**先问流程能不能往下走，再派发子 Agent、再调模型**。
            //   顺序不能反——闸门没通过（等人工确认 / 等条件结论 / 受阻）时先把子 Agent 派出去，
            //   等于用一次"尚未获准"的编排去动外部世界；而先把模型调了再记 NEED_HUMAN，
            //   等于人工确认只挂了个牌子、什么都没拦住（假闸门比没有闸门更坏）。
            FlowGate gate = checkFlowGate(bo, plan, running, snapshot);
            if (gate.stopped() != null) {
                return gate.stopped();
            }
            flowStepId = gate.stepId();
            // ★ D05 运行时：按派发规格创建子任务（幂等键稳定，父任务重试不会建出第二套）。
            //   子任务由平台调度执行；父步骤由对账（reconcile）按子任务终态收尾。
            try {
                subAgentRunner.dispatchChildren(bo.getTaskId(), task.getTaskType(),
                    task.getProjectType(), task.getDataLevel(), snapshot.getSnapshotJson(),
                    invokeBo.getMaxCost(), plan);
            } catch (Exception e) {
                log.error("派发子 Agent 失败（不影响本次执行）, taskId={}", bo.getTaskId(), e);
            }
        }

        if (StringUtils.isBlank(invokeBo.getCapabilityCode())) {
            throw new ServiceException("任务未登记能力编码，无法路由：taskId=" + bo.getTaskId());
        }

        // ★ D05（用户 2026-10-14 授权）：顺序流程的**多步驱动**。
        //   在此之前"一次任务执行 = 一次模型调用"，声明了多个 STEP 节点的流程只有第一个会跑。
        //   现在按声明的顺序逐步调用：每步一次模型调用，第 k 步以上一步的输出作为业务输入；
        //   次数上限见 AigFlowCallPolicy（声明节点数 ∩ limits.maxFlowSteps ∩ 平台默认 10）。
        //   ⚠️ 累计成本**不可控**：调用结果里没有成本字段，maxCost 只能管"每一次"，
        //   任务级累计上限要等成本回执接入（与"模型成本指标无数据源"同一处缺口）——写在 policy 的类注释里。
        List<AigInvokeVo> calls = new ArrayList<>();
        int maxStepCalls = plan == null
            ? 0 : AigFlowCallPolicy.maxStepCalls(plan.getFlowNodes(), plan.getMaxFlowSteps());
        int rounds = Math.max(1, maxStepCalls);
        Long stepIdToSettle = flowStepId;
        String businessInput = bo.getPrompt();
        AigInvokeVo invoked = null;
        for (int round = 0; round < rounds; round++) {
            // 3) 统一调用入口：路由 / 有序 fallback / 退避重试 / 逐次审计都在里面。
            invoked = invokeOnce(task, bo, snapshot, plan, businessInput, invokeBo);
            calls.add(invoked);
            boolean callOk = StringUtils.isNotBlank(invoked.getOutput())
                && StringUtils.isBlank(invoked.getErrorCode());
            settleFlowStep(bo.getTaskId(), stepIdToSettle, callOk, invoked);
            // ★ D05：把"不需要人、也不需要再执行"的尾部节点推进掉（典型是收尾的输出节点）。
            if (plan != null) {
                drainSettleableFlowTail(bo.getTaskId(), plan,
                    AigFlowConditions.fromSnapshotJson(snapshot.getSnapshotJson(), plan.getFlowNodes(),
                        jsonMapper));
            }
            if (!callOk) {
                // 这一步失败：不再往下跑（后面每一步都要花一次调用，且失败原因通常对后续同样成立）
                break;
            }
            if (round + 1 >= maxStepCalls) {
                if (maxStepCalls > 1) {
                    log.info("顺序流程按声明步数执行完成, taskId={}, calls={}", bo.getTaskId(), calls.size());
                }
                break;
            }
            // 下一步：先过闸门（等人工/缺条件/受阻就停），再取该走哪个 STEP 节点
            FlowGate next = checkFlowGate(bo, plan, running, snapshot);
            if (next.stopped() != null) {
                return stopAfterFlowCalls(bo, next.stopped(), calls);
            }
            if (next.stepId() == null) {
                break;
            }
            stepIdToSettle = next.stepId();
            businessInput = AigFlowCallPolicy.nextInput(invoked.getOutput(), bo.getPrompt());
        }

        // 4) 记录执行事实（路由快照 / traceId / 是否外发 / 耗时）。
        //    路由快照是执行与排障的唯一依据：治理配置会变，只有当时那一刻的结论能解释「为什么跑的是它」
        AigTask afterFacts = taskService.recordExecutionFacts(bo.getTaskId(), running.getVersion(),
            invoked.getTraceId(), buildRouteSnapshot(invoked, plan, calls),
            Boolean.TRUE.equals(invoked.getExternalCall()), invoked.getLatencyMs());

        // 4.5 记录策略结论（aig_task.policy_result/policy_reason + 事件 AI_TASK_POLICY_DECIDED）。
        //     放在这里而不是创建任务时：结论来自路由引擎，而路由只在真正要调用时发生——
        //     在创建阶段写一个「还没算出来的结论」，只能靠猜。
        //     词表映射只有一处实现（拒绝写 REJECT、转人工写 MANUAL、其余 PASS）。
        AigTask afterPolicy = recordPolicyDecision(bo.getTaskId(), afterFacts.getVersion(), invoked);

        // ★ D05：这次模型调用就是流程里那个 STEP 节点的执行，按结果收尾它（多步时每步各收尾一次）。
        boolean success = StringUtils.isNotBlank(invoked.getOutput()) && StringUtils.isBlank(invoked.getErrorCode());

        AigTaskExecuteVo vo = new AigTaskExecuteVo();
        vo.setTaskId(bo.getTaskId());
        vo.setTraceId(invoked.getTraceId());
        vo.setModelKey(invoked.getModelKey());
        vo.setDeploymentType(invoked.getDeploymentType());
        vo.setInvoker(invoked.getInvoker());
        vo.setExternalCall(Boolean.TRUE.equals(invoked.getExternalCall()));
        vo.setLatencyMs(invoked.getLatencyMs());
        vo.setErrorCode(invoked.getErrorCode());

        if (success) {
            AigTask succeeded = taskService.transition(bo.getTaskId(), afterPolicy.getVersion(),
                AigTaskStatusEnum.SUCCEEDED, "调用成功，产出待复核（执行成功不等于审核通过）",
                buildRouteSnapshot(invoked, plan, calls));
            vo.setSuccess(true);
            vo.setOutput(invoked.getOutput());
            vo.setStatus(succeeded.getStatus());
            log.info("任务执行成功, taskId={}, traceId={}, modelKey={}, externalCall={}",
                bo.getTaskId(), invoked.getTraceId(), invoked.getModelKey(), vo.isExternalCall());
            return vo;
        }

        // 失败：把调用层已算好的错误分类原样交给任务层（不让它去解析中文文案猜）
        AigErrorClassEnum errorClass = AigErrorClassEnum.find(invoked.getErrorCode());
        String reason = StringUtils.blankToDefault(invoked.getReason(),
            StringUtils.blankToDefault(invoked.getOutput(), "模型调用未产出结果"));
        AigTaskStatusEnum resting = taskService.recordFailure(bo.getTaskId(), afterPolicy.getVersion(),
            errorClass, StringUtils.substring(reason, 0, 500));
        vo.setSuccess(false);
        vo.setReason(reason);
        vo.setStatus(resting == null ? null : resting.getCode());
        // 调用层没给出分类时补一个 UNKNOWN，避免返回体里出现「失败了但不知道为什么」的空白
        if (StringUtils.isBlank(vo.getErrorCode())) {
            vo.setErrorCode(AigErrorClassEnum.UNKNOWN.getCode());
        }
        log.warn("任务执行失败, taskId={}, traceId={}, errorCode={}, 落点={}",
            bo.getTaskId(), invoked.getTraceId(), vo.getErrorCode(), vo.getStatus());
        return vo;
    }

    /**
     * 把尾部"可自行收尾"的流程节点推进掉（不需要人、也不需要再发起执行）。
     *
     * <p><b>为什么只推这几类</b>：一次任务执行只发起一次模型调用（既有语义，改它要动调度与计费口径），
     * 所以本次能推的只有：条件判断（已有结论 → 通过或跳过其后）与输出节点（直接收尾）。
     * 遇到需要执行的 `STEP`、需要人工确认的节点、缺结论的条件或受阻状态，一律<b>停下来</b>并留痕——
     * 尤其不能在这里"顺手挂起"人工确认节点：任务紧接着就会被记成成功，
     * 而一个没有相应任务态支撑的挂起节点，读起来就是"有东西在跑"，其实什么都没跑
     * （这正是先 {@code peek} 再 {@code advance} 的原因）。</p>
     *
     * @param taskId           任务ID
     * @param plan             执行计划
     * @param conditionResults 条件结论（与闸门同一份：都取自冻结输入快照）
     */
    private void drainSettleableFlowTail(Long taskId, AigExecutionPlan plan,
                                        Map<String, Boolean> conditionResults) {
        int budget = plan.getFlowNodes().size() + 1;
        for (int i = 0; i < budget; i++) {
            AigFlowDriveResult peek;
            try {
                peek = flowDriver.peek(taskId, plan, conditionResults);
            } catch (Exception e) {
                log.error("读取流程下一步失败（尾部推进停止）, taskId={}", taskId, e);
                return;
            }
            if (peek == null || peek.ended()) {
                return;
            }
            boolean settleable = peek.action() == AigFlowRuntime.Action.SKIP_REST
                || (peek.action() == AigFlowRuntime.Action.START && !peek.needsExecution());
            if (!settleable) {
                log.info("流程尾部本轮不能自行推进（需人确认/需执行/缺条件结论），留给下一次推进："
                    + "taskId={}, action={}, nodeType={}", taskId,
                    peek.action().getCode(), peek.nodeType());
                return;
            }
            flowDriver.advance(taskId, plan, conditionResults);
        }
    }

    /**
     * 发起一次模型调用（多步流程里每个 {@code STEP} 节点一次）。
     *
     * <p>入参模板（能力/等级/场景/任务身份/预算）在循环外组好，这里只替换
     * <b>业务输入</b>：有冻结计划时按 {@code 系统指令 → 资料节 → 业务输入} 组装（设计 §7.2/§7.3），
     * 没有计划时沿用"调用方提供 prompt"的旧语义。</p>
     *
     * @param task         任务视图
     * @param bo           执行入参
     * @param snapshot     输入快照（引用用）
     * @param plan         冻结执行计划（可空）
     * @param businessInput 本次的业务输入
     * @param template     循环外组好的入参模板（会被复制）
     * @return 调用结果
     */
    private AigInvokeVo invokeOnce(AigTaskVo task, AigTaskExecuteBo bo, AigTaskSnapshotVo snapshot,
                                   AigExecutionPlan plan, String businessInput,
                                   AigInvokeBo template) {
        AigInvokeBo each = new AigInvokeBo();
        each.setCapabilityCode(template.getCapabilityCode());
        each.setDataLevel(template.getDataLevel());
        each.setScenarioCode(template.getScenarioCode());
        each.setTaskId(template.getTaskId());
        each.setAgentVersionId(template.getAgentVersionId());
        each.setPayload(template.getPayload());
        each.setInputSnapshotRef(template.getInputSnapshotRef());
        each.setMaxCost(template.getMaxCost());
        each.setAllowExternal(template.getAllowExternal());
        each.setPrompt(plan == null ? businessInput : plan.composePrompt(businessInput));
        return invokeService.invoke(each);
    }

    /**
     * 按调用结果收尾本次对应的流程 STEP 节点（写失败不影响任务结论，但必须留痕）。
     *
     * @param taskId  任务ID
     * @param stepId  步骤ID（可空=本次调用不对应流程节点）
     * @param success 是否成功
     * @param invoked 调用结果
     */
    private void settleFlowStep(Long taskId, Long stepId, boolean success, AigInvokeVo invoked) {
        if (stepId == null) {
            return;
        }
        try {
            flowDriver.settle(stepId, success, invoked.getTraceId(), invoked.getErrorCode(),
                StringUtils.substring(invoked.getReason(), 0, 500));
        } catch (Exception e) {
            log.error("收尾流程步骤失败, taskId={}, stepId={}", taskId, stepId, e);
        }
    }

    /**
     * 多步流程中途遇到闸门（等人工确认 / 缺条件结论 / 受阻）：停下，但**不丢掉已经产出的结果**。
     *
     * <p>闸门那一侧已经通过 {@code recordFailure} 把任务落成 {@code NEED_HUMAN}
     * （并用 {@code APPROVAL_REQUIRED}/{@code INVALID_REQUEST} 说清为什么）。
     * 这里只把**最后一次成功调用的产出与身份**补进返回体：此前跑完的那几步是真花钱做出来的，
     * 只回一句"待人工处理"会让人以为什么都没发生。</p>
     *
     * @param bo      执行入参
     * @param stopped 闸门给出的停留结果（非空）
     * @param calls   本次执行已经发出的调用（≥1）
     * @return 补上产出信息的结果
     */
    private AigTaskExecuteVo stopAfterFlowCalls(AigTaskExecuteBo bo, AigTaskExecuteVo stopped,
                                                List<AigInvokeVo> calls) {
        AigInvokeVo last = calls.isEmpty() ? null : calls.get(calls.size() - 1);
        if (last != null) {
            stopped.setTraceId(last.getTraceId());
            stopped.setModelKey(last.getModelKey());
            stopped.setDeploymentType(last.getDeploymentType());
            stopped.setInvoker(last.getInvoker());
            stopped.setExternalCall(Boolean.TRUE.equals(last.getExternalCall()));
            stopped.setLatencyMs(last.getLatencyMs());
            stopped.setOutput(last.getOutput());
        }
        log.info("顺序流程中途停留（已调用 {} 次）, taskId={}, 落点={}",
            calls.size(), bo.getTaskId(), stopped.getStatus());
        return stopped;
    }

    /**
     * 流程闸门结论。
     *
     * @param stopped 已停下的执行结果（非空=不要调模型，直接返回它）
     * @param stepId  本次执行对应的流程 STEP 节点（可空=本次执行不对应流程节点）
     * @author ai-gov
     */
    private record FlowGate(AigTaskExecuteVo stopped, Long stepId) {
    }

    /**
     * 检查流程能不能往下走（设计 §6.5：条件判断与人工确认必须真的拦得住）。
     *
     * <p>三种"不放行"都落在 {@link AigTaskStatusEnum#NEED_HUMAN}（经
     * {@code RUNNING → FAILED → NEED_HUMAN} 的既有链路）：
     * 等人工确认用 {@link AigErrorClassEnum#APPROVAL_REQUIRED}，等条件结论或流程受阻用
     * {@link AigErrorClassEnum#INVALID_REQUEST}——两者都是"要人来看一眼"而不是"重试就好"，
     * 因此不会拿预算去反复重试一个配置问题。</p>
     *
     * <p><b>条件结论目前恒为空</b>：本模块不含表达式引擎，结论要由业务域/人工给出；
     * 缺结论时流程停在 NEED_HUMAN，这是可见的"待接"而不是默认放行。</p>
     *
     * <p><b>条件结论取自冻结输入快照</b>（{@link AigFlowConditions}）：节点 ref 就是快照里
     * 顶层的布尔字段名。取不到结论（字段缺失/不是布尔/快照不可解析）时不猜真假，
     * 流程停在 NEED_HUMAN——猜错的方向恰好是"跳过了一个本该跑的节点"。</p>
     *
     * @param bo       执行入参
     * @param plan     冻结的执行计划
     * @param running  已置为 RUNNING 的任务（提供乐观锁版本）
     * @param snapshot 输入快照（条件结论的来源）
     * @return 闸门结论
     */
    private FlowGate checkFlowGate(AigTaskExecuteBo bo, AigExecutionPlan plan, AigTask running,
                                   AigTaskSnapshotVo snapshot) {
        AigFlowDriveResult drive;
        try {
            Map<String, Boolean> conditionResults = AigFlowConditions.fromSnapshotJson(
                snapshot.getSnapshotJson(), plan.getFlowNodes(), jsonMapper);
            drive = flowDriver.advance(bo.getTaskId(), plan, conditionResults);
        } catch (Exception e) {
            log.error("流程推进失败（按受阻处理，不放行）, taskId={}", bo.getTaskId(), e);
            return new FlowGate(stopForFlow(bo.getTaskId(), running.getVersion(),
                AigErrorClassEnum.INVALID_REQUEST, "流程推进失败：" + e.getMessage()), null);
        }
        if (drive == null) {
            // 驱动没给结论就不能假装"没事发生"：流程节点是否存在都不清楚
            return new FlowGate(stopForFlow(bo.getTaskId(), running.getVersion(),
                AigErrorClassEnum.INVALID_REQUEST, "流程推进没有返回结论，拒绝继续执行"), null);
        }
        if (drive.action() == AigFlowRuntime.Action.BLOCKED) {
            return new FlowGate(stopForFlow(bo.getTaskId(), running.getVersion(),
                AigErrorClassEnum.INVALID_REQUEST, "流程无法继续：" + drive.reason()), null);
        }
        if (drive.action() == AigFlowRuntime.Action.WAIT_HUMAN
            || drive.action() == AigFlowRuntime.Action.WAIT_CONDITION) {
            AigErrorClassEnum errorClass = drive.action() == AigFlowRuntime.Action.WAIT_HUMAN
                ? AigErrorClassEnum.APPROVAL_REQUIRED : AigErrorClassEnum.INVALID_REQUEST;
            return new FlowGate(stopForFlow(bo.getTaskId(), running.getVersion(), errorClass,
                drive.reason()), null);
        }
        // WAIT_EXECUTION（节点已在执行中，本次是重试）与 START 都要在本次调用后收尾该节点
        Long stepId = "STEP".equalsIgnoreCase(drive.nodeType()) ? drive.stepId() : null;
        return new FlowGate(null, stepId);
    }

    /**
     * 流程闸门未通过：把任务落成 NEED_HUMAN，并返回"没有调模型"的结果。
     *
     * @param taskId     任务ID
     * @param version    期望版本（乐观锁）
     * @param errorClass 错误分类（决定落点）
     * @param reason     可读原因
     * @return 执行结果
     */
    private AigTaskExecuteVo stopForFlow(Long taskId, Integer version, AigErrorClassEnum errorClass,
                                         String reason) {
        String text = StringUtils.substring(reason, 0, 500);
        AigTaskStatusEnum resting = taskService.recordFailure(taskId, version, errorClass, text);
        log.info("流程闸门未通过，未调用模型, taskId={}, errorClass={}, 落点={}",
            taskId, errorClass.getCode(), resting == null ? null : resting.getCode());
        AigTaskExecuteVo vo = new AigTaskExecuteVo();
        vo.setTaskId(taskId);
        vo.setSuccess(false);
        vo.setErrorCode(errorClass.getCode());
        vo.setReason(text);
        vo.setStatus(resting == null ? null : resting.getCode());
        return vo;
    }

    /**
     * 场景任务的跨模块派发（增量 13；用户拍板：按 {@code scenarioCode} 解析当时的 STABLE 版本）。
     *
     * <p><b>为什么失败要落到任务上而不是抛异常</b>：任务已经被置为 RUNNING；抛出去会让它停在
     * RUNNING 等超时清扫，并被记成 TIMEOUT（假账）。所以这里一律把结论写进任务（SUCCEEDED/FAILED），
     * 再在返回体里说明。</p>
     *
     * <p><b>"纯登记"（适配器 NONE）不造假输出</b>：不挂流程也没有模型能力可调，任务记成"已登记"，
     * 而不是回一段编出来的 output。</p>
     *
     * @param bo       执行入参
     * @param task     任务视图
     * @param snapshot 输入快照
     * @param running  已置为 RUNNING 的任务
     * @return 执行结果
     */
    private AigTaskExecuteVo dispatchScenario(AigTaskExecuteBo bo, AigTaskVo task,
                                              AigTaskSnapshotVo snapshot, AigTask running) {
        AigScenarioVersion version = scenarioVersionResolver.stableVersion(task.getScenarioCode());
        if (version == null) {
            return failDispatch(bo.getTaskId(), running.getVersion(),
                "场景没有 STABLE 版本，无法确定由谁执行：scenarioCode=" + task.getScenarioCode());
        }
        String adapter = AigScenarioDispatch.normalize(version.getWorkflowAdapter());
        if (adapter == null) {
            return failDispatch(bo.getTaskId(), running.getVersion(),
                "场景版本的流程适配器不可识别，拒绝执行：scenarioCode=" + task.getScenarioCode()
                    + "，adapter=" + version.getWorkflowAdapter());
        }
        if (!AigScenarioDispatch.requiresWorkflow(adapter)) {
            AigTask done = taskService.transition(bo.getTaskId(), running.getVersion(),
                AigTaskStatusEnum.SUCCEEDED, "纯登记场景（适配器 NONE）：不挂流程，无需执行",
                dispatchSnapshot(adapter, null, "REGISTERED_ONLY"));
            AigTaskExecuteVo vo = new AigTaskExecuteVo();
            vo.setTaskId(bo.getTaskId());
            vo.setSuccess(true);
            vo.setStatus(done == null ? null : done.getStatus());
            vo.setReason("纯登记场景：不挂流程");
            return vo;
        }

        AigScenarioFlowRequest request = new AigScenarioFlowRequest();
        request.setTaskId(bo.getTaskId());
        request.setTaskNo(task.getTaskNo());
        request.setScenarioCode(task.getScenarioCode());
        request.setScenarioVersion(version.getVersion());
        request.setAdapter(adapter);
        request.setDataLevel(task.getDataLevel());
        request.setProjectType(task.getProjectType());
        request.setProjectId(task.getProjectId());
        request.setSnapshotJson(snapshot.getSnapshotJson());
        AigTask entity = taskService.getTask(bo.getTaskId());
        request.setRequesterId(entity == null ? null : entity.getCreateBy());

        AigScenarioFlowResult result;
        try {
            result = scenarioFlowDispatcher.dispatch(request);
        } catch (Exception e) {
            return failDispatch(bo.getTaskId(), running.getVersion(), "场景派发失败：" + e.getMessage());
        }
        if (result == null || !result.isAccepted()) {
            return failDispatch(bo.getTaskId(), running.getVersion(),
                StringUtils.blankToDefault(result == null ? null : result.getMessage(), "业务域拒绝了这次派发"));
        }

        // 受理 = 交接完成，但**不置终态**：域内是长跑作业（内容侧：解析 → 闸门），
        // 由域通过回执服务（IAigScenarioReportService）来收尾平台任务。
        // 这里只记录"交给了谁"，任务保持 RUNNING。没有回执时会被既有清扫按 TIMEOUT 处理——
        // 那正好是"域没回执"的信号，而不是把交接当成"已完成"。
        AigTask afterFacts = taskService.recordExecutionFacts(bo.getTaskId(), running.getVersion(), null,
            dispatchSnapshot(adapter, result.getExternalRef(), "ACCEPTED"), false, null);
        AigTaskExecuteVo vo = new AigTaskExecuteVo();
        vo.setTaskId(bo.getTaskId());
        vo.setSuccess(true);

        if (result.isHandoffComplete()) {
            // 域**自己声明**"受理即完成交接"（例：创作域建出创意项目即交给设计部）。
            // 顺序不能反：先记派发事实（乐观锁用 running 的版本），再 transition（用记完事实后的新版本）；
            // 反过来会让这次 transition 撞乐观锁，任务反被记成一次假失败。
            AigTask done = taskService.transition(bo.getTaskId(),
                afterFacts == null ? running.getVersion() : afterFacts.getVersion(),
                AigTaskStatusEnum.SUCCEEDED,
                "域受理即完成交接（" + adapter + "，externalRef=" + result.getExternalRef() + "）",
                dispatchSnapshot(adapter, result.getExternalRef(), "HANDOFF_COMPLETE"));
            vo.setStatus(done == null ? null : done.getStatus());
            vo.setReason("已派发且域报告交接完成（" + adapter + "，externalRef=" + result.getExternalRef() + "）");
            log.info("场景任务已派发且交接完成, taskId={}, adapter={}, externalRef={}",
                bo.getTaskId(), adapter, result.getExternalRef());
            return vo;
        }

        vo.setStatus(afterFacts == null ? running.getStatus() : afterFacts.getStatus());
        vo.setReason("已派发，等待域回执（" + adapter + "，externalRef=" + result.getExternalRef() + "）");
        log.info("场景任务已派发（等待回执）, taskId={}, adapter={}, externalRef={}",
            bo.getTaskId(), adapter, result.getExternalRef());
        return vo;
    }

    /**
     * 场景派发失败：把结论写进任务，返回失败结果（不抛异常，理由见 {@link #dispatchScenario}）。
     *
     * @param taskId  任务ID
     * @param version 期望版本（乐观锁）
     * @param reason  可读原因
     * @return 执行结果
     */
    private AigTaskExecuteVo failDispatch(Long taskId, Integer version, String reason) {
        String text = StringUtils.substring(reason, 0, 500);
        // 失败分类留空：域拒绝/配置缺失不是"可编程的粗分类"，猜一个只会误导重试策略
        AigTaskStatusEnum resting = taskService.recordFailure(taskId, version, null, text);
        log.warn("场景派发失败, taskId={}, reason={}", taskId, text);
        AigTaskExecuteVo vo = new AigTaskExecuteVo();
        vo.setTaskId(taskId);
        vo.setSuccess(false);
        vo.setStatus(resting == null ? null : resting.getCode());
        vo.setReason(text);
        vo.setErrorCode(AigErrorClassEnum.UNKNOWN.getCode());
        return vo;
    }

    /**
     * 场景派发的路由快照（写进任务的 route_snapshot：事后能回答"这次交给了谁"）。
     *
     * @param adapter     适配器编码
     * @param externalRef 业务域的任务/作业引用（可空）
     * @param handoff     交接状态（ACCEPTED / REGISTERED_ONLY）
     * @return JSON 文本；组装失败返回 null
     */
    private String dispatchSnapshot(String adapter, String externalRef, String handoff) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("dispatch", adapter);
        snapshot.put("externalRef", externalRef);
        snapshot.put("handoff", handoff);
        try {
            return jsonMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            log.error("组装场景派发快照失败, adapter={}", adapter, e);
            return null;
        }
    }

    /**
     * 把执行态内的一次异常收口成失败（设计 D04）。
     *
     * <p>先尝试把任务记成失败（含真实分类 UNKNOWN 与截断后的原因），再把一个可读的
     * {@link ServiceException} 抛给调用方。这样调用方拿到的不只是"500"，而是"任务落点在哪"。</p>
     *
     * @param taskId 任务ID
     * @param cause  执行态内抛出的异常
     * @return 可读异常（已记录任务落点）
     */
    private ServiceException settleFailure(Long taskId, RuntimeException cause) {
        String reason = "执行过程中发生未预期异常：" + cause.getClass().getSimpleName()
            + StringUtils.blankToDefault(StringUtils.substring(cause.getMessage(), 0, 300), "");
        log.error("任务执行发生未预期异常, taskId={}", taskId, cause);
        AigTaskStatusEnum resting = recordUnexpectedFailure(taskId, reason);
        return new ServiceException(reason + "；已按未知错误记录，任务落点="
            + (resting == null ? "未变化" : resting.getCode()), cause);
    }

    /**
     * 记录一次「未预期异常」导致的失败。
     *
     * <p>用<b>当场重读的版本</b>而不是执行前的版本：异常可能发生在几次写入之后
     * （调用事实、策略结论），拿旧版本去做乐观锁只会再抛一次冲突，把真正的原因盖掉。</p>
     *
     * @param taskId 任务ID
     * @param reason 可读原因
     * @return 失败后的落点；连记账都失败时返回 null（此时只保留原始异常，不再叠一层错）
     */
    private AigTaskStatusEnum recordUnexpectedFailure(Long taskId, String reason) {
        try {
            AigTask fresh = taskService.getTask(taskId);
            return taskService.recordFailure(taskId, fresh.getVersion(), AigErrorClassEnum.UNKNOWN,
                StringUtils.substring(reason, 0, 500));
        } catch (Exception e) {
            log.error("未预期异常后补记失败状态也失败了, taskId={}", taskId, e);
            return null;
        }
    }

    /**
     * 把本次调用的策略结论落到任务上（列 + 事件）。
     *
     * <p><b>不写的情况只有一种</b>：调用入口没给出路由结论（{@code decision} 为空）——
     * 那时任务层没有可写的结论，写一个「PASS」会是编的。其余情况下即使调用失败也要写：
     * 「被策略拒绝」正是最需要留在任务上的结论。</p>
     *
     * @param taskId  任务ID
     * @param version 期望版本（乐观锁）
     * @param invoked 调用结果
     * @return 更新后的任务
     */
    private AigTask recordPolicyDecision(Long taskId, Integer version, AigInvokeVo invoked) {
        AigRouteDecisionEnum decision = AigRouteDecisionEnum.find(invoked.getDecision());
        AigTaskPolicyResultEnum policyResult = AigTaskPolicyResultEnum.fromDecision(decision);
        if (policyResult == null) {
            log.warn("调用结果未给出路由结论，策略结论跳过写入（不编造）, taskId={}, traceId={}",
                taskId, invoked.getTraceId());
            return taskService.getTask(taskId);
        }
        return taskService.recordPolicyDecision(taskId, version, policyResult.getCode(),
            StringUtils.blankToDefault(invoked.getReason(), decision.getDesc()), invoked.getReasonCode());
    }

    /**
     * 校验快照未被改写：重算哈希并与冻结值比对。
     *
     * @param snapshot 快照视图
     * @param taskId   任务ID（用于报错定位）
     */
    private void assertSnapshotUntouched(AigTaskSnapshotVo snapshot, Long taskId) {
        if (StringUtils.isBlank(snapshot.getSnapshotHash())) {
            throw new ServiceException("快照缺少哈希，无法校验其未被改写，拒绝执行：taskId=" + taskId);
        }
        String actual = DigestUtil.sha256Hex(StringUtils.blankToDefault(snapshot.getSnapshotJson(), ""));
        if (!snapshot.getSnapshotHash().equalsIgnoreCase(actual)) {
            throw new ServiceException("输入快照哈希不匹配（冻结值 " + snapshot.getSnapshotHash()
                + "，实算 " + actual + "）：快照已被改写，执行结果将无法归因，拒绝执行 taskId=" + taskId);
        }
    }

    /**
     * 组装路由快照：执行与排障的唯一依据。
     *
     * @param invoked 最后一次调用结果（任务上的 traceId/模型等字段取自它）
     * @param plan    冻结的执行计划（可为空=非 Agent 模式）
     * @param calls   本次执行发出的全部调用（顺序流程多步时 &gt;1；单次调用时长度 1）
     * @return JSON 文本
     */
    private String buildRouteSnapshot(AigInvokeVo invoked, AigExecutionPlan plan,
                                      List<AigInvokeVo> calls) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("decision", invoked.getDecision());
        snapshot.put("modelId", invoked.getModelId());
        snapshot.put("modelKey", invoked.getModelKey());
        snapshot.put("deploymentType", invoked.getDeploymentType());
        snapshot.put("invoker", invoked.getInvoker());
        snapshot.put("externalCall", invoked.getExternalCall());
        snapshot.put("traceId", invoked.getTraceId());
        // 策略命中明细一并冻结：只看「用了哪个模型」回答不了「当时为什么是它」
        snapshot.put("policyHits", invoked.getPolicyHits());
        // ★ 顺序流程多步驱动（用户 2026-10-14 授权）：把**每一次**调用都冻结下来。
        //   任务行上的 traceId 只是最后一次——只看它，"这一步是谁跑的"就无从回答；
        //   单次调用时不写这一段（保持既有快照形状，便于与历史数据比对）。
        if (calls != null && calls.size() > 1) {
            List<Map<String, Object>> executed = new ArrayList<>();
            int stepNo = 0;
            for (AigInvokeVo call : calls) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("step", ++stepNo);
                item.put("traceId", call.getTraceId());
                item.put("decision", call.getDecision());
                item.put("modelKey", call.getModelKey());
                item.put("deploymentType", call.getDeploymentType());
                item.put("externalCall", call.getExternalCall());
                item.put("latencyMs", call.getLatencyMs());
                item.put("errorCode", call.getErrorCode());
                executed.add(item);
            }
            snapshot.put("executedCalls", executed);
        }
        // D01：把冻结计划的身份一并写进快照——这是"这次执行依据哪份配置"的持久证据。
        // 只记 agentVersionId 不够：同一版本的内容也可能被后续修订影响，configHash 才是锚点
        if (plan != null) {
            Map<String, Object> execution = new LinkedHashMap<>();
            execution.put("planId", plan.getPlanId());
            execution.put("purpose", plan.getPurpose() == null ? null : plan.getPurpose().getCode());
            execution.put("agentVersionId", plan.getAgentVersionId());
            execution.put("configHash", plan.getConfigHash());
            execution.put("inputHash", plan.getInputHash());
            snapshot.put("executionPlan", execution);
            // C-3：把这次采用的知识引用一并冻结——事后能回答"回答依据了哪几段资料"
            if (!plan.getKnowledgeCitations().isEmpty()) {
                java.util.List<Long> chunkIds = new java.util.ArrayList<>();
                for (AigExecutionCitation citation : plan.getKnowledgeCitations()) {
                    chunkIds.add(citation.chunkId());
                }
                snapshot.put("knowledgeCitations", chunkIds);
            }
        }
        try {
            return jsonMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            // 快照组装失败不该让一次已经成功的调用变成失败，但要留痕
            log.error("组装路由快照失败, traceId={}", invoked.getTraceId(), e);
            return null;
        }
    }

}
