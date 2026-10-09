package org.dromara.aigov.task.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import org.dromara.aigov.domain.bo.AigInvokeBo;
import org.dromara.aigov.domain.vo.AigInvokeVo;
import org.dromara.aigov.enums.AigErrorClassEnum;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    private AigTaskExecutorImpl executor;

    @BeforeEach
    void setUp() {
        taskService = mock(IAigTaskService.class);
        invokeService = mock(IAigInvokeService.class);
        executor = new AigTaskExecutorImpl(taskService, invokeService, JsonMapper.builder().build());
    }

    /**
     * 造一个「已入队且快照未被动过」的详情。
     *
     * @param status 任务状态
     * @param hashOverride 强制指定快照哈希（用于构造被改写的场景），null 表示用正确的哈希
     * @return 详情
     */
    private static AigTaskDetailVo detail(String status, String hashOverride) {
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
        snapshot.setSnapshotJson(SNAPSHOT_JSON);
        snapshot.setSnapshotHash(hashOverride != null ? hashOverride : DigestUtil.sha256Hex(SNAPSHOT_JSON));
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
    @DisplayName("业务型 ServiceException 原样上抛：不重复记账（它不是「执行崩了」）")
    void serviceExceptionFromInvokeIsNotRecordedTwice() {
        when(taskService.getDetail(TASK_ID)).thenReturn(detail("QUEUED", null));
        when(taskService.transition(eq(TASK_ID), eq(2), eq(AigTaskStatusEnum.RUNNING), any(), isNull()))
            .thenReturn(task("RUNNING", 3));
        when(invokeService.invoke(any())).thenThrow(new ServiceException("能力未登记"));

        ServiceException ex = assertThrows(ServiceException.class, () -> executor.execute(executedBo()));

        assertEquals("能力未登记", ex.getMessage());
        verify(taskService, never()).recordFailure(any(), any(), any(), any());
    }

    @Test
    @DisplayName("★ 调用入参必须带任务ID：策略决策账本靠它把「为什么」接到任务上")
    void taskIdIsCarriedIntoInvoke() {
        stubHappyPath(invoked("{}", null, null));

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

}
