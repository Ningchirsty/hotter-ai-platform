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
import java.util.LinkedHashMap;
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

        // 3) 统一调用入口：路由 / 有序 fallback / 退避重试 / 逐次审计都在里面。
        AigInvokeVo invoked = invokeService.invoke(invokeBo);

        // 4) 记录执行事实（路由快照 / traceId / 是否外发 / 耗时）。
        //    路由快照是执行与排障的唯一依据：治理配置会变，只有当时那一刻的结论能解释「为什么跑的是它」
        AigTask afterFacts = taskService.recordExecutionFacts(bo.getTaskId(), running.getVersion(),
            invoked.getTraceId(), buildRouteSnapshot(invoked, plan),
            Boolean.TRUE.equals(invoked.getExternalCall()), invoked.getLatencyMs());

        // 4.5 记录策略结论（aig_task.policy_result/policy_reason + 事件 AI_TASK_POLICY_DECIDED）。
        //     放在这里而不是创建任务时：结论来自路由引擎，而路由只在真正要调用时发生——
        //     在创建阶段写一个「还没算出来的结论」，只能靠猜。
        //     词表映射只有一处实现（拒绝写 REJECT、转人工写 MANUAL、其余 PASS）。
        AigTask afterPolicy = recordPolicyDecision(bo.getTaskId(), afterFacts.getVersion(), invoked);

        // 5) 成功判定：**必须有输出且没有错误码**。
        //    只看 errorCode 会漏掉「决策为 MANUAL 但既无输出也无错误码」这类空转；
        //    只看 output 又可能把「恰好回了一段文本的错误响应」当成功。
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
                buildRouteSnapshot(invoked, plan));
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
     * @param invoked 调用结果
     * @param plan    冻结的执行计划（可为空=非 Agent 模式）
     * @return JSON 文本
     */
    private String buildRouteSnapshot(AigInvokeVo invoked, AigExecutionPlan plan) {
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
