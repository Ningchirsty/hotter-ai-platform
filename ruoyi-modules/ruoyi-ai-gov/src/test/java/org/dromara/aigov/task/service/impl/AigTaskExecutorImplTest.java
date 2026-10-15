package org.dromara.aigov.task.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import org.dromara.aigov.domain.bo.AigInvokeBo;
import org.dromara.aigov.domain.vo.AigInvokeVo;
import org.dromara.aigov.enums.AigErrorClassEnum;
import org.dromara.aigov.execution.AigExecutionPlan;
import org.dromara.aigov.execution.AigExecutionPurposeEnum;
import org.dromara.aigov.execution.AigFlowNode;
import org.dromara.aigov.task.service.AigFlowDriveResult;
import org.dromara.aigov.task.service.AigFlowRuntime;
import org.dromara.aigov.service.IAigInvokeService;
import org.dromara.aigov.task.domain.AigTask;
import org.dromara.aigov.task.domain.bo.AigTaskExecuteBo;
import org.dromara.aigov.task.domain.vo.AigTaskDetailVo;
import org.dromara.aigov.task.domain.vo.AigTaskExecuteVo;
import org.dromara.aigov.task.domain.vo.AigTaskSnapshotVo;
import org.dromara.aigov.task.domain.vo.AigTaskVo;
import org.dromara.aigov.task.enums.AigTaskStatusEnum;
import org.dromara.aigov.task.service.IAigTaskService;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 任务执行器（任务层 → 统一调用入口）行为测试。
 *
 * <p><b>这段是「两套入口是否真串起来了」的唯一证据</b>：在它之前，
 * 任务层与调用层各自都能跑，但没有任何东西把它们连起来——所以出现「任务建好了却永远没人调模型」
 * 这种既没人报错、也没有日志的静默断链。因此这里逐条钉住：</p>
 * <ul>
 *     <li>状态序列必须是 QUEUED → RUNNING → SUCCEEDED/失败落点（同步 Provider 没有派发中间态）；</li>
 *     <li>错误分类必须<b>原样</b>从调用层传给任务层（不许解析中文文案去猜）；</li>
 *     <li>快照哈希对不上必须拒绝执行（结果无法归因的产物宁可不要）；</li>
 *     <li>只有「已入队」能执行——否则一个已完成的任务会被重复调用、重复计费。</li>
 * </ul>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigTaskExecutorImplTest {

    private static final long TASK_ID = 1L;
    private static final long AGENT_VERSION_ID = 4242L;
    private static final String SNAPSHOT_JSON = "{\"facts\":\"v1\"}";

    private IAigTaskService taskService;
    private IAigInvokeService invokeService;
    private org.dromara.aigov.workspace.scenario.config.AigScenarioDispatchProperties scenarioDispatchProperties;
    private org.dromara.aigov.workspace.scenario.service.IAigScenarioVersionResolver scenarioVersionResolver;
    private org.dromara.aigov.workspace.scenario.service.IAigScenarioFlowDispatcher scenarioFlowDispatcher;
    private org.dromara.aigov.execution.IAigAgentExecutionResolver executionResolver;
    private org.dromara.aigov.task.service.IAigFlowDriverService flowDriver;
    private AigTaskExecutorImpl executor;

    @BeforeEach
    void setUp() {
        taskService = mock(IAigTaskService.class);
        invokeService = mock(IAigInvokeService.class);
        // 场景派发默认关闭：既有用例（任务带 scenarioCode）仍然走普通模型调用路径
        scenarioDispatchProperties = new org.dromara.aigov.workspace.scenario.config.AigScenarioDispatchProperties();
        scenarioVersionResolver =
            mock(org.dromara.aigov.workspace.scenario.service.IAigScenarioVersionResolver.class);
        scenarioFlowDispatcher =
            mock(org.dromara.aigov.workspace.scenario.service.IAigScenarioFlowDispatcher.class);
        // 解析器默认返回 null = 非 Agent 模式：既有用例的行为完全不变（D01 用专门的用例覆盖）
        executionResolver = mock(org.dromara.aigov.execution.IAigAgentExecutionResolver.class);
        // 流程驱动默认返回「没有流程节点 = 已完成」：既有用例（无流程）行为完全不变，
        // D05 的闸门行为用专门的用例覆盖
        flowDriver = mock(org.dromara.aigov.task.service.IAigFlowDriverService.class);
        when(flowDriver.advance(any(), any(), any())).thenReturn(new org.dromara.aigov.task.service.AigFlowDriveResult(
            org.dromara.aigov.task.service.AigFlowRuntime.Action.DONE,
            org.dromara.aigov.task.service.AigFlowRuntime.NO_NODE, null, null, null,
            "执行计划没有流程节点", List.of()));
        executor = new AigTaskExecutorImpl(taskService, invokeService, scenarioDispatchProperties,
            scenarioVersionResolver, scenarioFlowDispatcher, executionResolver,
            mock(org.dromara.aigov.task.service.IAigTaskStepService.class),
            mock(org.dromara.aigov.task.service.IAigSubAgentRunner.class), flowDriver,
            JsonMapper.builder().build());
    }

    /**
     * 造一个「已入队且快照未被动过」的详情。
     *
     * @param status 任务状态
     * @param hashOverride 强制指定快照哈希（用于构造被改写的场景），null 表示用正确的哈希
     * @return 详情
     */
    private static AigTaskDetailVo detail(String status, String hashOverride) {
        return detail(status, hashOverride, SNAPSHOT_JSON);
    }

    /**
     * 造一个指定快照内容的详情（用于条件结论/路由快照这类"看快照里有什么"的用例）。
     *
     * @param status       任务状态
     * @param hashOverride 强制哈希（null=按实际内容算）
     * @param snapshotJson 快照 JSON
     * @return 详情
     */
    private static AigTaskDetailVo detail(String status, String hashOverride, String snapshotJson) {
        AigTaskVo task = new AigTaskVo();
        task.setTaskId(TASK_ID);
        task.setStatus(status);
        task.setVersion(2);
        task.setCapabilityCode("image_generation");
        task.setDataLevel("INTERNAL");
        task.setScenarioCode("POSTER");
        task.setAttemptNo(0);
        task.setAgentVersionId(AGENT_VERSION_ID);

        AigTaskSnapshotVo snapshot = new AigTaskSnapshotVo();
        snapshot.setSnapshotId(77L);
        snapshot.setSnapshotJson(snapshotJson);
        snapshot.setSnapshotHash(hashOverride != null ? hashOverride : DigestUtil.sha256Hex(snapshotJson));
        snapshot.setBudgetAmount(new BigDecimal("0.80"));

        AigTaskDetailVo detail = new AigTaskDetailVo();
        detail.setTask(task);
        detail.setSnapshot(snapshot);
        return detail;
    }

    private static AigTask task(String status, int version) {
        AigTask task = new AigTask();
        task.setTaskId(TASK_ID);
        task.setStatus(status);
        task.setVersion(version);
        task.setAttemptNo(0);
        task.setMaxAttempt(3);
        return task;
    }

    private static AigInvokeVo invoked(String output, String errorCode, String reason) {
        AigInvokeVo vo = new AigInvokeVo();
        vo.setTraceId("trace-1");
        vo.setDecision(errorCode == null ? "MODEL" : "DENIED");
        vo.setModelId(11L);
        vo.setModelKey("flux-2-pro");
        vo.setDeploymentType("EXTERNAL_API");
        vo.setInvoker("OpenAiImageInvoker");
        vo.setExternalCall(true);
        vo.setOutput(output);
        vo.setErrorCode(errorCode);
        vo.setReason(reason);
        vo.setLatencyMs(1200L);
        vo.setPolicyHits(List.of("命中路由策略 policyId=9"));
        return vo;
    }

    private static AigTaskExecuteBo executedBo() {
        AigTaskExecuteBo bo = new AigTaskExecuteBo();
        bo.setTaskId(TASK_ID);
        bo.setPrompt("a cute cat");
        return bo;
    }

    /**
     * 打桩「已入队 + 快照完好」的成功路径骨架（版本 2 → 3 → 4 → 5 → 6）。
     *
     * <p>抽出来的原因：状态迁移与两次事实写入是多次调用，逐个打桩重复四遍；
     * 而漏掉最后一次会让 transition 返回 null，NPE 看起来像实现崩了。</p>
     *
     * @param invoked 调用层返回值
     */
    private void stubHappyPath(AigInvokeVo invoked) {
        when(taskService.getDetail(TASK_ID)).thenReturn(detail("QUEUED", null));
        when(taskService.transition(eq(TASK_ID), eq(2), eq(AigTaskStatusEnum.RUNNING), any(), isNull()))
            .thenReturn(task("RUNNING", 3));
        when(taskService.recordExecutionFacts(any(), any(), any(), any(), anyBoolean(), any()))
            .thenReturn(task("RUNNING", 4));
        when(taskService.recordPolicyDecision(any(), any(), any(), any(), any()))
            .thenReturn(task("RUNNING", 5));
        when(taskService.transition(eq(TASK_ID), eq(5), eq(AigTaskStatusEnum.SUCCEEDED), any(), any()))
            .thenReturn(task("SUCCEEDED", 6));
        when(invokeService.invoke(any())).thenReturn(invoked);
    }

    @Test
    @DisplayName("成功路径：QUEUED→RUNNING→SUCCEEDED，且 traceId 落进任务")
    void successPathWalksTheStateMachine() {
        stubHappyPath(invoked("{\"image\":\"x\"}", null, null));

        AigTaskExecuteVo vo = executor.execute(executedBo());

        assertTrue(vo.isSuccess());
        assertEquals("{\"image\":\"x\"}", vo.getOutput());
        assertEquals("SUCCEEDED", vo.getStatus());
        assertEquals("trace-1", vo.getTraceId());
        assertEquals("flux-2-pro", vo.getModelKey());
        assertTrue(vo.isExternalCall());
        verify(taskService).transition(eq(TASK_ID), eq(2), eq(AigTaskStatusEnum.RUNNING), any(), isNull());
        verify(taskService).transition(eq(TASK_ID), eq(5), eq(AigTaskStatusEnum.SUCCEEDED), any(), any());
    }

    @Test
    @DisplayName("★ 策略结论必须落到任务上（含细因）：治理台那一格此前永远是「-」")
    void policyDecisionIsRecordedOnTheTask() {
        AigInvokeVo denied = invoked(null, "POLICY_DENIED", "未配置该数据等级的路由策略");
        denied.setDecision("DENIED");
        denied.setReasonCode("NO_ROUTE_POLICY");
        stubHappyPath(denied);
        when(taskService.recordFailure(eq(TASK_ID), eq(5), eq(AigErrorClassEnum.POLICY_DENIED), any()))
            .thenReturn(AigTaskStatusEnum.NEED_HUMAN);

        executor.execute(executedBo());

        ArgumentCaptor<String> resultCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(taskService).recordPolicyDecision(eq(TASK_ID), eq(4), resultCaptor.capture(),
            reasonCaptor.capture(), codeCaptor.capture());
        assertEquals("REJECT", resultCaptor.getValue(), "路由 DENIED 对应任务级 REJECT");
        assertEquals("未配置该数据等级的路由策略", reasonCaptor.getValue());
        assertEquals("NO_ROUTE_POLICY", codeCaptor.getValue(), "细因要原样带下去，不能被任务层改写");
    }

    @Test
    @DisplayName("路由 MODEL 对应任务级 PASS；结论与失败原因分列，不互相顶替")
    void modelDecisionMapsToPass() {
        stubHappyPath(invoked("{}", null, null));

        executor.execute(executedBo());

        verify(taskService).recordPolicyDecision(eq(TASK_ID), eq(4), eq("PASS"), any(), isNull());
    }

    @Test
    @DisplayName("★ 调用层抛未预期异常：必须记一条 UNKNOWN 失败，不能让任务停在 RUNNING 等超时清扫")
    void unexpectedInvokeFailureIsRecordedAndReported() {
        when(taskService.getDetail(TASK_ID)).thenReturn(detail("QUEUED", null));
        when(taskService.transition(eq(TASK_ID), eq(2), eq(AigTaskStatusEnum.RUNNING), any(), isNull()))
            .thenReturn(task("RUNNING", 3));
        when(invokeService.invoke(any())).thenThrow(new IllegalStateException("Table 'x' doesn't exist"));
        // 补记失败时会重读一次任务拿最新版本
        when(taskService.getTask(TASK_ID)).thenReturn(task("RUNNING", 3));
        when(taskService.recordFailure(eq(TASK_ID), eq(3), eq(AigErrorClassEnum.UNKNOWN), any()))
            .thenReturn(AigTaskStatusEnum.FAILED);

        ServiceException ex = assertThrows(ServiceException.class, () -> executor.execute(executedBo()));

        assertTrue(ex.getMessage().contains("未预期异常"), ex.getMessage());
        assertTrue(ex.getMessage().contains("FAILED"), "要告诉调用方任务落到哪：" + ex.getMessage());
        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(taskService).recordFailure(eq(TASK_ID), eq(3), eq(AigErrorClassEnum.UNKNOWN),
            reasonCaptor.capture());
        assertTrue(reasonCaptor.getValue().contains("IllegalStateException"), reasonCaptor.getValue());
        verify(taskService, never()).recordExecutionFacts(any(), any(), any(), any(), anyBoolean(), any());
    }

    @Test
    @DisplayName("★ D04：执行态内抛出的 ServiceException 也要收口成失败并给出落点（不再原样上抛停在 RUNNING）")
    void serviceExceptionFromInvokeIsSettledAsFailure() {
        when(taskService.getDetail(TASK_ID)).thenReturn(detail("QUEUED", null));
        when(taskService.transition(eq(TASK_ID), eq(2), eq(AigTaskStatusEnum.RUNNING), any(), isNull()))
            .thenReturn(task("RUNNING", 3));
        when(invokeService.invoke(any())).thenThrow(new ServiceException("能力未登记"));
        when(taskService.getTask(TASK_ID)).thenReturn(task("RUNNING", 3));
        when(taskService.recordFailure(eq(TASK_ID), eq(3), eq(AigErrorClassEnum.UNKNOWN), any()))
            .thenReturn(AigTaskStatusEnum.NEED_HUMAN);

        ServiceException ex = assertThrows(ServiceException.class, () -> executor.execute(executedBo()));

        assertTrue(ex.getMessage().contains("能力未登记"), ex.getMessage());
        assertTrue(ex.getMessage().contains("NEED_HUMAN"), "要告诉调用方任务落到哪：" + ex.getMessage());
        verify(taskService).recordFailure(eq(TASK_ID), eq(3), eq(AigErrorClassEnum.UNKNOWN), any());
    }

    @Test
    @DisplayName("★ D04：缺能力编码属执行态内异常，必须收口成失败，不能停在 RUNNING 等超时")
    void missingCapabilityIsSettledAsFailure() {
        AigTaskDetailVo detail = detail("QUEUED", null);
        detail.getTask().setCapabilityCode("   ");
        when(taskService.getDetail(TASK_ID)).thenReturn(detail);
        when(taskService.transition(eq(TASK_ID), eq(2), eq(AigTaskStatusEnum.RUNNING), any(), isNull()))
            .thenReturn(task("RUNNING", 3));
        when(taskService.getTask(TASK_ID)).thenReturn(task("RUNNING", 3));
        when(taskService.recordFailure(eq(TASK_ID), eq(3), eq(AigErrorClassEnum.UNKNOWN), any()))
            .thenReturn(AigTaskStatusEnum.NEED_HUMAN);

        ServiceException ex = assertThrows(ServiceException.class, () -> executor.execute(executedBo()));

        assertTrue(ex.getMessage().contains("未登记能力编码"), ex.getMessage());
        assertTrue(ex.getMessage().contains("NEED_HUMAN"), ex.getMessage());
        verify(taskService).recordFailure(eq(TASK_ID), eq(3), eq(AigErrorClassEnum.UNKNOWN), any());
        verify(invokeService, never()).invoke(any());
    }

    @Test
    @DisplayName("★ D01：Agent 模式下用冻结版本的 Prompt 与能力，客户端 prompt 只作业务输入；外发许可作硬约束")
    void agentModeUsesFrozenExecutionPlan() {
        stubHappyPath(invoked("{}", null, null));
        AigExecutionPlan plan = AigExecutionPlan.builder()
            .planId("plan-1")
            .taskId(TASK_ID)
            .agentVersionId(AGENT_VERSION_ID)
            .purpose(AigExecutionPurposeEnum.PRODUCTION)
            .configHash("cfg-hash")
            .systemPrompt("SYS-INSTRUCTION")
            .inputHash("input-hash")
            .capabilityCode("text_generation")
            .dataLevel("INTERNAL")
            .allowExternal(Boolean.FALSE)
            .build();
        when(executionResolver.resolve(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(plan);

        executor.execute(executedBo());

        ArgumentCaptor<AigInvokeBo> captor = ArgumentCaptor.forClass(AigInvokeBo.class);
        verify(invokeService).invoke(captor.capture());
        assertTrue(captor.getValue().getPrompt().startsWith("SYS-INSTRUCTION"),
            "系统指令必须来自冻结版本：" + captor.getValue().getPrompt());
        assertTrue(captor.getValue().getPrompt().contains("a cute cat"),
            "客户端内容只能作业务输入分节：" + captor.getValue().getPrompt());
        assertEquals("text_generation", captor.getValue().getCapabilityCode());
        assertEquals(Boolean.FALSE, captor.getValue().getAllowExternal(),
            "版本声明不外发时必须作为硬约束传下去");
    }

    /**
     * 打桩「解析出带流程节点的冻结计划」。
     *
     * @param nodes 流程节点
     */
    private void stubPlanWithFlow(AigFlowNode... nodes) {
        when(executionResolver.resolve(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(AigExecutionPlan.builder()
                .planId("plan-flow")
                .taskId(TASK_ID)
                .agentVersionId(AGENT_VERSION_ID)
                .capabilityCode("text_generation")
                .flowNodes(List.of(nodes))
                .build());
    }

    private static AigFlowNode flowNode(String type, String ref) {
        return new AigFlowNode(type, ref, ref);
    }

    private static AigFlowDriveResult drive(AigFlowRuntime.Action action, String ref, String nodeType,
                                            Long stepId, String reason) {
        return new AigFlowDriveResult(action,
            action == AigFlowRuntime.Action.DONE ? AigFlowRuntime.NO_NODE : 0,
            ref, nodeType, stepId, reason, List.of());
    }

    @Test
    @DisplayName("★ D05：首节点是人工确认 → 不调模型，任务落 NEED_HUMAN（假闸门比没有闸门更坏）")
    void humanConfirmGateStopsBeforeModelCall() {
        stubHappyPath(invoked("{}", null, null));
        stubPlanWithFlow(flowNode("HUMAN_CONFIRM", "approve"));
        when(flowDriver.advance(any(), any(), any())).thenReturn(
            drive(AigFlowRuntime.Action.WAIT_HUMAN, "approve", "HUMAN_CONFIRM", null, "待人工确认"));
        when(taskService.recordFailure(eq(TASK_ID), eq(3), eq(AigErrorClassEnum.APPROVAL_REQUIRED), any()))
            .thenReturn(AigTaskStatusEnum.NEED_HUMAN);

        AigTaskExecuteVo vo = executor.execute(executedBo());

        assertFalse(vo.isSuccess());
        assertEquals("NEED_HUMAN", vo.getStatus());
        assertEquals("APPROVAL_REQUIRED", vo.getErrorCode());
        verify(invokeService, never()).invoke(any());
        verify(taskService, never()).recordExecutionFacts(any(), any(), any(), any(), anyBoolean(), any());
    }

    @Test
    @DisplayName("★ D05：条件结论还没有来源 → 停在 NEED_HUMAN，同样一次模型都不调")
    void conditionWithoutResultStopsBeforeModelCall() {
        stubHappyPath(invoked("{}", null, null));
        stubPlanWithFlow(flowNode("CONDITION", "needVideo"));
        when(flowDriver.advance(any(), any(), any())).thenReturn(
            drive(AigFlowRuntime.Action.WAIT_CONDITION, "needVideo", "CONDITION", null, "还没有结论"));
        when(taskService.recordFailure(eq(TASK_ID), eq(3), eq(AigErrorClassEnum.INVALID_REQUEST), any()))
            .thenReturn(AigTaskStatusEnum.NEED_HUMAN);

        AigTaskExecuteVo vo = executor.execute(executedBo());

        assertEquals("NEED_HUMAN", vo.getStatus());
        verify(invokeService, never()).invoke(any());
    }

    @Test
    @DisplayName("★ D05：流程受阻（节点与步骤错位等）→ 不调模型，按配置问题转人工")
    void blockedFlowStopsBeforeModelCall() {
        stubHappyPath(invoked("{}", null, null));
        stubPlanWithFlow(flowNode("STEP", "a"));
        when(flowDriver.advance(any(), any(), any())).thenReturn(
            drive(AigFlowRuntime.Action.BLOCKED, "a", "STEP", null, "状态对应关系已破坏"));
        when(taskService.recordFailure(eq(TASK_ID), eq(3), eq(AigErrorClassEnum.INVALID_REQUEST), any()))
            .thenReturn(AigTaskStatusEnum.NEED_HUMAN);

        AigTaskExecuteVo vo = executor.execute(executedBo());

        assertEquals("NEED_HUMAN", vo.getStatus());
        assertTrue(vo.getReason().contains("状态对应关系已破坏"));
        verify(invokeService, never()).invoke(any());
    }

    @Test
    @DisplayName("★ D05：放行的 STEP 节点被本次调用收尾（成功写 SUCCEEDED，带 traceId）")
    void stepNodeIsSettledWithCallOutcome() {
        stubHappyPath(invoked("{\"image\":\"x\"}", null, null));
        stubPlanWithFlow(flowNode("STEP", "a"), flowNode("OUTPUT", "out"));
        when(flowDriver.advance(any(), any(), any())).thenReturn(
            drive(AigFlowRuntime.Action.START, "a", "STEP", 555L, "启动第 1 个节点"));

        AigTaskExecuteVo vo = executor.execute(executedBo());

        assertTrue(vo.isSuccess());
        verify(flowDriver).settle(eq(555L), eq(true), eq("trace-1"), isNull(), isNull());
    }

    @Test
    @DisplayName("调用失败时，STEP 节点也必须收尾成 FAILED（否则看起来还挂在执行中）")
    void stepNodeIsSettledOnFailure() {
        AigInvokeVo failed = invoked(null, "TIMEOUT", "调用超时");
        stubHappyPath(failed);
        when(taskService.recordFailure(eq(TASK_ID), eq(5), eq(AigErrorClassEnum.TIMEOUT), any()))
            .thenReturn(AigTaskStatusEnum.RETRY_WAIT);
        stubPlanWithFlow(flowNode("STEP", "a"));
        when(flowDriver.advance(any(), any(), any())).thenReturn(
            drive(AigFlowRuntime.Action.START, "a", "STEP", 555L, "启动第 1 个节点"));

        executor.execute(executedBo());

        verify(flowDriver).settle(eq(555L), eq(false), eq("trace-1"), eq("TIMEOUT"), eq("调用超时"));
    }

    @Test
    @DisplayName("★ 条件结论取自冻结输入快照的布尔字段：为真则放行（不再一律停在待人工处理）")
    void conditionResultIsReadFromFrozenSnapshot() {
        stubHappyPath(invoked("{\"image\":\"x\"}", null, null));
        when(taskService.getDetail(TASK_ID))
            .thenReturn(detail("QUEUED", null, "{\"facts\":\"v1\",\"needVideo\":true}"));
        stubPlanWithFlow(flowNode("CONDITION", "needVideo"), flowNode("OUTPUT", "out"));
        when(flowDriver.advance(any(), any(), any())).thenReturn(
            drive(AigFlowRuntime.Action.START, "needVideo", "CONDITION", null, "条件已满足"));

        AigTaskExecuteVo vo = executor.execute(executedBo());

        assertTrue(vo.isSuccess());
        verify(flowDriver).advance(eq(TASK_ID), any(),
            argThat(conditions -> Boolean.TRUE.equals(conditions.get("needVideo"))));
        verify(invokeService).invoke(any());
    }

    @Test
    @DisplayName("★ 快照里是字符串 \"true\" 时不给结论：照旧停下，绝不猜一个真假")
    void conditionStringValueIsNotTreatedAsBoolean() {
        stubHappyPath(invoked("{}", null, null));
        when(taskService.getDetail(TASK_ID))
            .thenReturn(detail("QUEUED", null, "{\"facts\":\"v1\",\"needVideo\":\"true\"}"));
        stubPlanWithFlow(flowNode("CONDITION", "needVideo"));
        when(flowDriver.advance(any(), any(), any())).thenReturn(
            drive(AigFlowRuntime.Action.WAIT_CONDITION, "needVideo", "CONDITION", null, "还没有结论"));
        when(taskService.recordFailure(eq(TASK_ID), eq(3), eq(AigErrorClassEnum.INVALID_REQUEST), any()))
            .thenReturn(AigTaskStatusEnum.NEED_HUMAN);

        AigTaskExecuteVo vo = executor.execute(executedBo());

        assertEquals("NEED_HUMAN", vo.getStatus());
        verify(flowDriver).advance(eq(TASK_ID), any(), argThat(Map::isEmpty));
        verify(invokeService, never()).invoke(any());
    }

    @Test
    @DisplayName("★ 多步流程：按声明顺序逐步调用，第 2 步以上一步输出为业务输入（用户 2026-10-14 授权）")
    void drivesEveryStepNodeInOrder() {
        stubHappyPath(invoked("{\"step1\":\"初稿\"}", null, null));
        stubPlanWithFlow(flowNode("STEP", "a"), flowNode("STEP", "b"), flowNode("OUTPUT", "out"));
        when(invokeService.invoke(any())).thenReturn(
            invoked("{\"step1\":\"初稿\"}", null, null),
            invoked("{\"step2\":\"终稿\"}", null, null));
        when(flowDriver.advance(any(), any(), any())).thenReturn(
            drive(AigFlowRuntime.Action.START, "a", "STEP", 555L, "启动第 1 个节点"),
            drive(AigFlowRuntime.Action.START, "b", "STEP", 556L, "启动第 2 个节点"));

        AigTaskExecuteVo vo = executor.execute(executedBo());

        assertTrue(vo.isSuccess(), "两步都成功 → 任务成功");
        assertEquals("{\"step2\":\"终稿\"}", vo.getOutput(), "任务输出取最后一步的产出");

        ArgumentCaptor<AigInvokeBo> captor = ArgumentCaptor.forClass(AigInvokeBo.class);
        verify(invokeService, times(2)).invoke(captor.capture());
        List<AigInvokeBo> calls = captor.getAllValues();
        assertTrue(calls.get(0).getPrompt().contains("a cute cat"), "第 1 步用调用方给的业务输入");
        assertTrue(calls.get(1).getPrompt().contains("初稿"),
            "第 2 步的业务输入必须是第 1 步的输出（否则只是把同一句话问两遍）");
        verify(flowDriver).settle(eq(555L), eq(true), eq("trace-1"), isNull(), isNull());
        verify(flowDriver).settle(eq(556L), eq(true), eq("trace-1"), isNull(), isNull());
        // 路由快照要能回答"每一步是谁跑的"（任务行上的 traceId 只是最后一次）
        ArgumentCaptor<String> snapshotCaptor = ArgumentCaptor.forClass(String.class);
        verify(taskService).recordExecutionFacts(any(), any(), any(), snapshotCaptor.capture(),
            anyBoolean(), any());
        assertTrue(snapshotCaptor.getValue().contains("executedCalls"),
            "多步执行要把每次调用都冻结进路由快照：" + snapshotCaptor.getValue());
    }

    @Test
    @DisplayName("★ 多步流程：第一步失败就不再往下跑（后面每步都要花一次调用）")
    void stopsAfterFirstFailedStep() {
        stubHappyPath(invoked(null, "TIMEOUT", "调用超时"));
        stubPlanWithFlow(flowNode("STEP", "a"), flowNode("STEP", "b"));
        when(taskService.recordFailure(eq(TASK_ID), eq(5), eq(AigErrorClassEnum.TIMEOUT), any()))
            .thenReturn(AigTaskStatusEnum.RETRY_WAIT);
        when(flowDriver.advance(any(), any(), any())).thenReturn(
            drive(AigFlowRuntime.Action.START, "a", "STEP", 555L, "启动第 1 个节点"));

        executor.execute(executedBo());

        verify(invokeService, times(1)).invoke(any());
        verify(flowDriver).settle(eq(555L), eq(false), eq("trace-1"), eq("TIMEOUT"), eq("调用超时"));
    }

    @Test
    @DisplayName("★ 多步流程：中途要人工确认 → 停下，但已产出的结果如实带回（不假装什么都没发生）")
    void midFlowHumanConfirmKeepsProducedOutput() {
        stubHappyPath(invoked("{\"step1\":\"初稿\"}", null, null));
        // 人工确认夹在两步之间：第 2 步还等着执行，所以闸门必须真的拦住
        stubPlanWithFlow(flowNode("STEP", "a"), flowNode("HUMAN_CONFIRM", "approve"),
            flowNode("STEP", "b"));
        when(taskService.recordFailure(eq(TASK_ID), eq(3), eq(AigErrorClassEnum.APPROVAL_REQUIRED), any()))
            .thenReturn(AigTaskStatusEnum.NEED_HUMAN);
        when(flowDriver.advance(any(), any(), any())).thenReturn(
            drive(AigFlowRuntime.Action.START, "a", "STEP", 555L, "启动第 1 个节点"),
            drive(AigFlowRuntime.Action.WAIT_HUMAN, "approve", "HUMAN_CONFIRM", 556L, "待人工确认"));

        AigTaskExecuteVo vo = executor.execute(executedBo());

        assertEquals("NEED_HUMAN", vo.getStatus(), "闸门拦住 → 任务等人工");
        assertFalse(vo.isSuccess());
        assertEquals("{\"step1\":\"初稿\"}", vo.getOutput(), "已经跑完那一步的产出要带回来");
        assertEquals("trace-1", vo.getTraceId());
        verify(invokeService, times(1)).invoke(any());
    }

    @Test
    @DisplayName("★ 多步流程：limits.maxFlowSteps 收紧到 1 → 只发一次调用（预算类约束必须生效）")
    void respectsDeclaredMaxFlowSteps() {
        stubHappyPath(invoked("{\"step1\":\"初稿\"}", null, null));
        when(executionResolver.resolve(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(AigExecutionPlan.builder()
                .planId("plan-capped")
                .taskId(TASK_ID)
                .capabilityCode("text_generation")
                .flowNodes(List.of(flowNode("STEP", "a"), flowNode("STEP", "b")))
                .maxFlowSteps(1)
                .build());
        when(flowDriver.advance(any(), any(), any())).thenReturn(
            drive(AigFlowRuntime.Action.START, "a", "STEP", 555L, "启动第 1 个节点"));

        AigTaskExecuteVo vo = executor.execute(executedBo());

        assertTrue(vo.isSuccess());
        verify(invokeService, times(1)).invoke(any());
        verify(flowDriver, times(1)).settle(any(), anyBoolean(), any(), any(), any());
    }

    @Test
    @DisplayName("★ D05：尾部可自行收尾的节点（输出节点）会在本轮推到完成，不留「没跑完」的假象")
    void settleableFlowTailIsDrainedToDone() {
        stubHappyPath(invoked("{\"image\":\"x\"}", null, null));
        stubPlanWithFlow(flowNode("STEP", "a"), flowNode("OUTPUT", "out"));
        // 闸门：第一个 STEP 交给调用方执行
        when(flowDriver.advance(any(), any(), any())).thenReturn(
            drive(AigFlowRuntime.Action.START, "a", "STEP", 555L, "启动第 1 个节点"),
            drive(AigFlowRuntime.Action.START, "out", "OUTPUT", 556L, "收尾第 2 个输出节点"));
        // 尾部推进用 peek 探路：先给出"输出节点可自行收尾"，收尾后再给"已完成"
        when(flowDriver.peek(any(), any(), any())).thenReturn(
            drive(AigFlowRuntime.Action.START, "out", "OUTPUT", 556L, "收尾第 2 个输出节点"),
            drive(AigFlowRuntime.Action.DONE, null, null, null, "全部流程节点已到终态"));

        AigTaskExecuteVo vo = executor.execute(executedBo());

        assertTrue(vo.isSuccess());
        verify(flowDriver, times(2)).advance(any(), any(), any());
        verify(flowDriver, times(2)).peek(any(), any(), any());
    }

    @Test
    @DisplayName("★ D05：尾部是要人工确认的节点时「只探不写」——不挂起、任务照旧记成功")
    void flowTailWithHumanConfirmIsNotParked() {
        stubHappyPath(invoked("{\"image\":\"x\"}", null, null));
        stubPlanWithFlow(flowNode("STEP", "a"), flowNode("HUMAN_CONFIRM", "approve"));
        when(flowDriver.advance(any(), any(), any())).thenReturn(
            drive(AigFlowRuntime.Action.START, "a", "STEP", 555L, "启动第 1 个节点"));
        when(flowDriver.peek(any(), any(), any())).thenReturn(
            drive(AigFlowRuntime.Action.WAIT_HUMAN, "approve", "HUMAN_CONFIRM", 556L, "待人工确认"));

        AigTaskExecuteVo vo = executor.execute(executedBo());

        assertTrue(vo.isSuccess(), "模型调用已成功，不能因为「尾部有人工确认节点」就改判");
        verify(flowDriver, times(1)).advance(any(), any(), any());
        verify(taskService, never()).recordFailure(eq(TASK_ID), any(), any(), any());
    }

    @Test
    @DisplayName("★ 调用入参必须带任务ID：策略决策账本靠它把「为什么」接到任务上")
    void taskIdIsCarriedIntoInvoke() {        stubHappyPath(invoked("{}", null, null));

        executor.execute(executedBo());

        ArgumentCaptor<AigInvokeBo> captor = ArgumentCaptor.forClass(AigInvokeBo.class);
        verify(invokeService).invoke(captor.capture());
        assertEquals(TASK_ID, captor.getValue().getTaskId(),
            "不带任务ID，aig_policy_decision_log.task_id 就永远为空");
    }

    @Test
    @DisplayName("调用入参取自任务本身：能力/数据等级/场景不可能与登记不一致")
    void invokeRequestIsBuiltFromTheTask() {
        stubHappyPath(invoked("{}", null, null));

        executor.execute(executedBo());

        ArgumentCaptor<AigInvokeBo> captor = ArgumentCaptor.forClass(AigInvokeBo.class);
        verify(invokeService).invoke(captor.capture());
        AigInvokeBo sent = captor.getValue();
        assertEquals("image_generation", sent.getCapabilityCode());
        assertEquals("INTERNAL", sent.getDataLevel());
        assertEquals("POSTER", sent.getScenarioCode());
        assertEquals("a cute cat", sent.getPrompt());
        assertNotNull(sent.getInputSnapshotRef(), "要带上快照引用：它是事后复现的唯一入口");
        assertTrue(sent.getInputSnapshotRef().contains("77"), "引用里要能定位到具体快照；实际="
            + sent.getInputSnapshotRef());
    }

    @Test
    @DisplayName("Agent 版本必须从任务带进调用入参：任务域本来就知道，过去在这一跳丢了")
    void agentVersionIsCarriedFromTaskIntoInvoke() {
        stubHappyPath(invoked("{}", null, null));

        executor.execute(executedBo());

        ArgumentCaptor<AigInvokeBo> captor = ArgumentCaptor.forClass(AigInvokeBo.class);
        verify(invokeService).invoke(captor.capture());
        assertEquals(AGENT_VERSION_ID, captor.getValue().getAgentVersionId(),
            "任务知道自己在哪个 Agent 版本上，漏带会让审计里这一维度恒为空，"
                + "灰度的「按版本统计调用次数/失败率」永远拿不到任务发起的调用");
    }

    @Test
    @DisplayName("预算是「入参优先、否则用快照冻结值」——快照里的预算才是当时承诺的口径")
    void budgetFallsBackToSnapshot() {
        stubHappyPath(invoked("{}", null, null));

        executor.execute(executedBo());

        ArgumentCaptor<AigInvokeBo> captor = ArgumentCaptor.forClass(AigInvokeBo.class);
        verify(invokeService).invoke(captor.capture());
        assertEquals(0, new BigDecimal("0.80").compareTo(captor.getValue().getMaxCost()),
            "入参没给预算时应回落快照的 budget_amount，而不是「没有预算」");
    }

    @Test
    @DisplayName("失败路径：调用层的错误分类原样传给任务层（不许解析中文文案去猜）")
    void failureCarriesTheErrorClassThrough() {
        when(taskService.getDetail(TASK_ID)).thenReturn(detail("QUEUED", null));
        when(taskService.transition(eq(TASK_ID), eq(2), eq(AigTaskStatusEnum.RUNNING), any(), isNull()))
            .thenReturn(task("RUNNING", 3));
        when(taskService.recordExecutionFacts(any(), any(), any(), any(), anyBoolean(), any()))
            .thenReturn(task("RUNNING", 4));
        when(taskService.recordPolicyDecision(any(), any(), any(), any(), any()))
            .thenReturn(task("RUNNING", 5));
        when(invokeService.invoke(any())).thenReturn(invoked(null, "TIMEOUT", "上游超时"));
        when(taskService.recordFailure(eq(TASK_ID), eq(5), eq(AigErrorClassEnum.TIMEOUT), any()))
            .thenReturn(AigTaskStatusEnum.RETRY_WAIT);

        AigTaskExecuteVo vo = executor.execute(executedBo());

        assertFalse(vo.isSuccess());
        assertEquals("TIMEOUT", vo.getErrorCode());
        assertEquals("RETRY_WAIT", vo.getStatus());
        verify(taskService).recordFailure(eq(TASK_ID), eq(5), eq(AigErrorClassEnum.TIMEOUT), any());
    }

    @Test
    @DisplayName("失败但没有错误码：补 UNKNOWN，不能让返回体出现「失败了却不知道为什么」")
    void failureWithoutErrorCodeFallsBackToUnknown() {
        when(taskService.getDetail(TASK_ID)).thenReturn(detail("QUEUED", null));
        when(taskService.transition(eq(TASK_ID), eq(2), eq(AigTaskStatusEnum.RUNNING), any(), isNull()))
            .thenReturn(task("RUNNING", 3));
        when(taskService.recordExecutionFacts(any(), any(), any(), any(), anyBoolean(), any()))
            .thenReturn(task("RUNNING", 4));
        when(taskService.recordPolicyDecision(any(), any(), any(), any(), any()))
            .thenReturn(task("RUNNING", 5));
        when(invokeService.invoke(any())).thenReturn(invoked(null, null, "什么也没说"));
        when(taskService.recordFailure(eq(TASK_ID), eq(5), isNull(), any()))
            .thenReturn(AigTaskStatusEnum.NEED_HUMAN);

        AigTaskExecuteVo vo = executor.execute(executedBo());

        assertFalse(vo.isSuccess());
        assertEquals("UNKNOWN", vo.getErrorCode());
        verify(taskService).recordFailure(eq(TASK_ID), any(), isNull(), any());
    }

    @Test
    @DisplayName("有输出但同时有错误码 → 判失败（把错误响应当成功会产出坏结果）")
    void outputWithErrorCodeIsStillAFailure() {
        when(taskService.getDetail(TASK_ID)).thenReturn(detail("QUEUED", null));
        when(taskService.transition(eq(TASK_ID), eq(2), eq(AigTaskStatusEnum.RUNNING), any(), isNull()))
            .thenReturn(task("RUNNING", 3));
        when(taskService.recordExecutionFacts(any(), any(), any(), any(), anyBoolean(), any()))
            .thenReturn(task("RUNNING", 4));
        when(taskService.recordPolicyDecision(any(), any(), any(), any(), any()))
            .thenReturn(task("RUNNING", 5));
        // 典型形态：网关 200 回了一段错误说明，或输出不符合 Schema
        when(invokeService.invoke(any())).thenReturn(invoked("错误：余额不足", "POLICY_DENIED", "输出不符合 Schema"));
        when(taskService.recordFailure(any(), eq(5), eq(AigErrorClassEnum.POLICY_DENIED), any()))
            .thenReturn(AigTaskStatusEnum.NEED_HUMAN);

        AigTaskExecuteVo vo = executor.execute(executedBo());

        assertFalse(vo.isSuccess());
        assertEquals("POLICY_DENIED", vo.getErrorCode());
    }

    @Test
    @DisplayName("快照哈希对不上 → 拒绝执行且一步都不动（结果无法归因的产物宁可不要）")
    void refusesWhenSnapshotTampered() {
        when(taskService.getDetail(TASK_ID)).thenReturn(detail("QUEUED", "deadbeef"));

        ServiceException ex = assertThrows(ServiceException.class, () -> executor.execute(executedBo()));

        assertTrue(ex.getMessage().contains("快照已被改写"), "实际=" + ex.getMessage());
        verify(invokeService, never()).invoke(any());
        verify(taskService, never()).transition(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("非「已入队」状态一律拒绝执行（否则已完成的任务会被重复调用、重复计费）")
    void refusesUnlessQueued() {
        when(taskService.getDetail(TASK_ID)).thenReturn(detail("SUCCEEDED", null));

        ServiceException ex = assertThrows(ServiceException.class, () -> executor.execute(executedBo()));

        assertTrue(ex.getMessage().contains("只有「已入队」"), "实际=" + ex.getMessage());
        verify(invokeService, never()).invoke(any());
    }

    @Test
    @DisplayName("没有快照 → 拒绝执行（没有快照就没有「当时按什么跑的」）")
    void refusesWithoutSnapshot() {
        AigTaskDetailVo noSnapshot = detail("QUEUED", null);
        noSnapshot.setSnapshot(null);
        when(taskService.getDetail(TASK_ID)).thenReturn(noSnapshot);

        assertThrows(ServiceException.class, () -> executor.execute(executedBo()));
        verify(invokeService, never()).invoke(any());
    }

    @Test
    @DisplayName("路由快照要能回答「为什么跑的是它」：含模型、调用器、traceId 与策略命中")
    void routeSnapshotIsRecorded() {
        stubHappyPath(invoked("{}", null, null));

        executor.execute(executedBo());

        ArgumentCaptor<String> snapshotCaptor = ArgumentCaptor.forClass(String.class);
        verify(taskService).recordExecutionFacts(eq(TASK_ID), eq(3), eq("trace-1"), snapshotCaptor.capture(),
            eq(true), any());
        String routeSnapshot = snapshotCaptor.getValue();
        assertNotNull(routeSnapshot);
        assertTrue(routeSnapshot.contains("flux-2-pro"), "要含实际模型；实际=" + routeSnapshot);
        assertTrue(routeSnapshot.contains("OpenAiImageInvoker"), "要含实际调用器；实际=" + routeSnapshot);
        assertTrue(routeSnapshot.contains("policyHits"),
            "要含策略命中：只看「用了哪个模型」回答不了「当时为什么是它」；实际=" + routeSnapshot);
    }

    @Test
    @DisplayName("★场景派发开启：没有 STABLE 版本 → 任务落失败，且不调模型")
    void scenarioWithoutStableVersionFails() {
        scenarioDispatchProperties.setEnabled(true);
        stubDispatchSkeleton();
        when(scenarioVersionResolver.stableVersion("POSTER")).thenReturn(null);
        when(taskService.recordFailure(eq(TASK_ID), eq(3), isNull(), any()))
            .thenReturn(AigTaskStatusEnum.FAILED);

        AigTaskExecuteVo vo = executor.execute(executedBo());

        assertFalse(vo.isSuccess());
        assertTrue(vo.getReason().contains("STABLE"), vo.getReason());
        verify(invokeService, never()).invoke(any());
        verify(scenarioFlowDispatcher, never()).dispatch(any());
    }

    @Test
    @DisplayName("适配器 NONE（纯登记）：记成成功但不造假输出，也不调模型/不派发")
    void noneAdapterIsRegistrationOnly() {
        scenarioDispatchProperties.setEnabled(true);
        stubDispatchSkeleton();
        when(scenarioVersionResolver.stableVersion("POSTER")).thenReturn(scenarioVersion("1.0.0", "NONE"));
        when(taskService.transition(eq(TASK_ID), eq(3), eq(AigTaskStatusEnum.SUCCEEDED), any(), any()))
            .thenReturn(task("SUCCEEDED", 4));

        AigTaskExecuteVo vo = executor.execute(executedBo());

        assertTrue(vo.isSuccess());
        assertNull(vo.getOutput(), "纯登记场景不该编一段 output");
        verify(invokeService, never()).invoke(any());
        verify(scenarioFlowDispatcher, never()).dispatch(any());
    }

    @Test
    @DisplayName("★有实现且被受理：任务保持 RUNNING + 派发快照（等域回执），不调模型、不置终态")
    void acceptedDispatchWaitsForReport() {
        scenarioDispatchProperties.setEnabled(true);
        stubDispatchSkeleton();
        when(scenarioVersionResolver.stableVersion("POSTER"))
            .thenReturn(scenarioVersion("1.0.0", "CREATIVE_EXISTING_FLOW"));
        when(scenarioFlowDispatcher.dispatch(any()))
            .thenReturn(org.dromara.scenario.api.domain.AigScenarioFlowResult.accepted("creative-9"));
        // 受理后只写执行事实（派发快照），**不**做 SUCCEEDED 迁移
        when(taskService.recordExecutionFacts(eq(TASK_ID), eq(3), isNull(), any(), eq(false), isNull()))
            .thenReturn(task("RUNNING", 4));

        AigTaskExecuteVo vo = executor.execute(executedBo());

        assertTrue(vo.isSuccess());
        assertEquals("RUNNING", vo.getStatus(), "交接完成不等于域内作业完成：等回执收尾");
        assertTrue(vo.getReason().contains("等待域回执"), vo.getReason());
        ArgumentCaptor<org.dromara.scenario.api.domain.AigScenarioFlowRequest> requestCaptor =
            ArgumentCaptor.forClass(org.dromara.scenario.api.domain.AigScenarioFlowRequest.class);
        verify(scenarioFlowDispatcher).dispatch(requestCaptor.capture());
        var request = requestCaptor.getValue();
        assertEquals("POSTER", request.getScenarioCode());
        assertEquals("1.0.0", request.getScenarioVersion());
        assertEquals("CREATIVE_EXISTING_FLOW", request.getAdapter());
        assertEquals(SNAPSHOT_JSON, request.getSnapshotJson());
        ArgumentCaptor<String> snapshotCaptor = ArgumentCaptor.forClass(String.class);
        verify(taskService).recordExecutionFacts(eq(TASK_ID), eq(3), isNull(), snapshotCaptor.capture(),
            eq(false), isNull());
        assertTrue(snapshotCaptor.getValue().contains("creative-9"),
            "派发快照要留 externalRef；实际=" + snapshotCaptor.getValue());
        verify(taskService, never()).transition(eq(TASK_ID), eq(3), eq(AigTaskStatusEnum.SUCCEEDED), any(), any());
        verify(invokeService, never()).invoke(any());
    }

    @Test
    @DisplayName("★域声明「受理即完成交接」：记完派发事实后立即收尾 SUCCEEDED（不再等回执）")
    void handoffCompleteDispatchClosesTask() {
        scenarioDispatchProperties.setEnabled(true);
        stubDispatchSkeleton();
        when(scenarioVersionResolver.stableVersion("POSTER"))
            .thenReturn(scenarioVersion("1.0.0", "CREATIVE_EXISTING_FLOW"));
        when(scenarioFlowDispatcher.dispatch(any()))
            .thenReturn(org.dromara.scenario.api.domain.AigScenarioFlowResult
                .acceptedWithHandoffComplete("creative-project-9"));
        // 先记执行事实（乐观锁用 running 的版本 3，返回新版本 4）
        when(taskService.recordExecutionFacts(eq(TASK_ID), eq(3), isNull(), any(), eq(false), isNull()))
            .thenReturn(task("RUNNING", 4));
        // 再用记完事实后的版本 4 收尾，避免撞乐观锁
        when(taskService.transition(eq(TASK_ID), eq(4), eq(AigTaskStatusEnum.SUCCEEDED), any(), any()))
            .thenReturn(task("SUCCEEDED", 5));

        AigTaskExecuteVo vo = executor.execute(executedBo());

        assertTrue(vo.isSuccess());
        assertEquals("SUCCEEDED", vo.getStatus(), "域声明交接完成，平台任务应立即收尾");
        assertTrue(vo.getReason().contains("交接完成"), vo.getReason());
        ArgumentCaptor<String> snapshotCaptor = ArgumentCaptor.forClass(String.class);
        verify(taskService).transition(eq(TASK_ID), eq(4), eq(AigTaskStatusEnum.SUCCEEDED), any(),
            snapshotCaptor.capture());
        assertTrue(snapshotCaptor.getValue().contains("HANDOFF_COMPLETE"), snapshotCaptor.getValue());
        verify(invokeService, never()).invoke(any());
    }

    @Test
    @DisplayName("派发被业务域拒绝：任务落失败并带上域给的可读原因，不调模型")
    void dispatchRejectionIsRecordedOnTask() {
        scenarioDispatchProperties.setEnabled(true);
        stubDispatchSkeleton();
        when(scenarioVersionResolver.stableVersion("POSTER"))
            .thenReturn(scenarioVersion("1.0.0", "CONTENT_EXISTING_FLOW"));
        when(scenarioFlowDispatcher.dispatch(any()))
            .thenReturn(org.dromara.scenario.api.domain.AigScenarioFlowResult.rejected("快照缺少 productId"));
        when(taskService.recordFailure(eq(TASK_ID), eq(3), isNull(), any()))
            .thenReturn(AigTaskStatusEnum.FAILED);

        AigTaskExecuteVo vo = executor.execute(executedBo());

        assertFalse(vo.isSuccess());
        assertTrue(vo.getReason().contains("productId"), vo.getReason());
        verify(invokeService, never()).invoke(any());
    }

    /**
     * 场景派发路径的骨架：详情 + QUEUED→RUNNING。
     */
    private void stubDispatchSkeleton() {
        when(taskService.getDetail(TASK_ID)).thenReturn(detail("QUEUED", null));
        when(taskService.transition(eq(TASK_ID), eq(2), eq(AigTaskStatusEnum.RUNNING), any(), isNull()))
            .thenReturn(task("RUNNING", 3));
    }

    /**
     * 造一个场景版本行。
     *
     * @param version 版本号
     * @param adapter 适配器
     * @return 版本
     */
    private static org.dromara.aigov.workspace.domain.AigScenarioVersion scenarioVersion(String version,
                                                                                          String adapter) {
        var row = new org.dromara.aigov.workspace.domain.AigScenarioVersion();
        row.setVersion(version);
        row.setWorkflowAdapter(adapter);
        row.setReleaseStatus("STABLE");
        return row;
    }

}
