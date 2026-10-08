package org.dromara.aigov.service.impl;

import org.dromara.aigov.config.AigRetryProperties;
import org.dromara.aigov.domain.bo.AigInvokeBo;
import org.dromara.aigov.domain.vo.AigModelVo;
import org.dromara.aigov.domain.vo.AigRouteCandidate;
import org.dromara.aigov.domain.vo.AigRouteDecision;
import org.dromara.aigov.domain.vo.AigRouteHint;
import org.dromara.aigov.enums.AigDataLevelEnum;
import org.dromara.aigov.enums.AigDeploymentTypeEnum;
import org.dromara.aigov.enums.AigRouteDecisionEnum;
import org.dromara.aigov.helper.AigAuditContext;
import org.dromara.aigov.helper.AigAuditRecorder;
import org.dromara.aigov.mapper.AigModelViewMapper;
import org.dromara.aigov.service.IAigRouteService;
import org.dromara.aigov.service.invoker.ModelInvokeRequest;
import org.dromara.aigov.service.invoker.ModelInvokeResult;
import org.dromara.aigov.service.invoker.ModelInvoker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.dromara.aigov.service.IAigUserQuotaService;

/**
 * 审计「供应商 / 用量 / 输入快照」三列的采集行为测试。
 *
 * <p><b>为什么这些列值得单独测</b>：它们是「事后能不能算账」的地基，而三类错误
 * 都不会让调用失败、也不会有任何报错：</p>
 * <ul>
 *     <li><b>供应商跟着主候选走</b>：有序 fallback 之后真正执行的是备选模型，供应商可能
 *         完全不同。审计若停在主候选，费用与合规就按错的供应商统计——这是<b>假账</b>，
 *         比没有审计更坏；</li>
 *     <li><b>用量被丢掉</b>：文本调用器早就解析出了 token 数，但此前没有承载它的列，
 *         等于解析了却扔掉。没有用量，「预算」只剩一个说法；</li>
 *     <li><b>无用量的调用写了空对象</b>：图像模型不回执 token，若给每次出图都写
 *         {@code {}}，这一列会变成噪音，且「没有用量」与「用量为零」再也分不开。</li>
 * </ul>
 *
 * <p>纯 Mockito：不加载 Spring 上下文。刻意传 {@code outputSchema=null}，
 * 避免走到需要 {@code JsonUtils} 的输出校验分支（那需要额外的 Spring 引导）。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigInvokeServiceImplAuditColumnsTest {

    private static final String CAPABILITY = "image_generation";

    private static final long PRIMARY_MODEL_ID = 101L;
    private static final long FALLBACK_MODEL_ID = 102L;

    private static final long PRIMARY_PROVIDER_ID = 1001L;
    private static final long FALLBACK_PROVIDER_ID = 1002L;

    private static final String SNAPSHOT_REF = "oss://ai-input/snap/2026/10/07/abc.json";

    private IAigRouteService routeService;
    private AigAuditRecorder auditRecorder;
    private AigModelViewMapper modelViewMapper;
    private AigRetryProperties retryProperties;

    @BeforeEach
    void setUp() {
        routeService = mock(IAigRouteService.class);
        auditRecorder = mock(AigAuditRecorder.class);
        modelViewMapper = mock(AigModelViewMapper.class);
        retryProperties = new AigRetryProperties();
        retryProperties.setMaxAttempts(1);
        retryProperties.setBaseBackoffMs(1L);
        retryProperties.setMaxBackoffMs(2L);
        // 两个候选分属不同供应商：只有这样才能区分「记主候选」与「记实际执行的那个」
        when(modelViewMapper.selectModelById(PRIMARY_MODEL_ID)).thenReturn(model("modelA", PRIMARY_PROVIDER_ID));
        when(modelViewMapper.selectModelById(FALLBACK_MODEL_ID)).thenReturn(model("modelB", FALLBACK_PROVIDER_ID));
    }

    private static AigModelVo model(String modelKey, long providerId) {
        AigModelVo vo = new AigModelVo();
        vo.setModelType("IMAGE");
        vo.setModelKey(modelKey);
        vo.setProviderId(providerId);
        return vo;
    }

    private static AigRouteCandidate candidate(int order, long modelId, String modelKey, String invokerName) {
        AigRouteCandidate candidate = new AigRouteCandidate();
        candidate.setOrder(order);
        candidate.setModelId(modelId);
        candidate.setModelKey(modelKey);
        candidate.setModelType("IMAGE");
        candidate.setDeploymentType(AigDeploymentTypeEnum.EXTERNAL_API.getCode());
        candidate.setInvoker(invokerName);
        candidate.setUsageType(order == 1 ? "PRIMARY" : "FALLBACK");
        candidate.setPriority(order);
        return candidate;
    }

    /**
     * 驱动一次调用。
     *
     * @param invokers         可用调用器
     * @param snapshotRef      入参里的输入快照引用（可空）
     * @param twoCandidates    true 时提供主/备两个候选，false 时只有一个主候选
     * @return 审计上下文
     */
    private AigAuditContext invoke(List<ModelInvoker> invokers, String snapshotRef, boolean twoCandidates) {
        return invoke(invokers, snapshotRef, twoCandidates, null);
    }

    /**
     * 驱动一次调用（可指定 Agent 版本）。
     *
     * @param invokers         可用调用器
     * @param snapshotRef      入参里的输入快照引用（可空）
     * @param twoCandidates    true 时提供主/备两个候选，false 时只有一个主候选
     * @param agentVersionId   入参里的 Agent 版本ID（可空）
     * @return 审计上下文
     */
    private AigAuditContext invoke(List<ModelInvoker> invokers, String snapshotRef, boolean twoCandidates,
                                   Long agentVersionId) {
        AigRouteDecision decision = new AigRouteDecision();
        decision.setCapabilityCode(CAPABILITY);
        decision.setDecision(AigRouteDecisionEnum.MODEL.getCode());
        decision.setAuditLevel("SUMMARY");
        decision.getCandidates().add(candidate(1, PRIMARY_MODEL_ID, "modelA", invokers.get(0).invokerName()));
        if (twoCandidates) {
            decision.getCandidates().add(candidate(2, FALLBACK_MODEL_ID, "modelB", invokers.get(1).invokerName()));
        }
        decision.setModelId(PRIMARY_MODEL_ID);
        decision.setModelKey("modelA");
        decision.setDeploymentType(AigDeploymentTypeEnum.EXTERNAL_API.getCode());
        // 带 AigRouteHint 的重载才是真实入口（String 那个是 default 方法，Mockito 不会代跑到它）。
        // 用 nullable 而不是 any(Class)：这些用例没有场景也没有预算，提示就是 null，
        // 而 any(Class) 走的是 instanceof 语义、**不匹配 null**，会静默打不中桩。
        when(routeService.decide(any(), any(), nullable(AigRouteHint.class))).thenReturn(decision);

        AigInvokeServiceImpl service = new AigInvokeServiceImpl(routeService, auditRecorder,
            invokers, modelViewMapper, retryProperties, mock(IAigUserQuotaService.class));
        AigInvokeBo bo = new AigInvokeBo();
        bo.setCapabilityCode(CAPABILITY);
        bo.setDataLevel(AigDataLevelEnum.INTERNAL.getCode());
        bo.setPrompt("生成一张图");
        bo.setInputSnapshotRef(snapshotRef);
        bo.setAgentVersionId(agentVersionId);
        service.invoke(bo);

        ArgumentCaptor<AigAuditContext> captor = ArgumentCaptor.forClass(AigAuditContext.class);
        verify(auditRecorder).record(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("供应商必须跟着实际执行的候选：主候选 401 顺延后，审计要记备选那家")
    void providerFollowsTheExecutedCandidate() {
        StubInvoker primary = new StubInvoker("InvokerA",
            ModelInvokeResult.failure(null, 401, "unauthorized", 5L));
        StubInvoker fallback = new StubInvoker("InvokerB",
            ModelInvokeResult.success("{\"image\":\"x\"}", 5L));

        AigAuditContext audit = invoke(List.of(primary, fallback), SNAPSHOT_REF, true);

        assertEquals(FALLBACK_MODEL_ID, audit.getModelId(), "模型要记实际执行的那个（既有约束）");
        assertEquals(FALLBACK_PROVIDER_ID, audit.getProviderId(),
            "供应商同样要跟着实际执行的候选；停在主候选会让费用与合规按错的供应商统计");
    }

    @Test
    @DisplayName("单一候选：供应商照常记录")
    void providerIsRecordedForSingleCandidate() {
        StubInvoker only = new StubInvoker("InvokerA", ModelInvokeResult.success("{\"image\":\"x\"}", 5L));

        AigAuditContext audit = invoke(List.of(only), null, false);

        assertEquals(PRIMARY_PROVIDER_ID, audit.getProviderId(), "单候选场景也要记供应商");
    }

    @Test
    @DisplayName("用量：调用器回填的 tokens 必须落进 usage_json（此前解析了却扔掉）")
    void tokensAreRecordedIntoUsageJson() {
        ModelInvokeResult result = ModelInvokeResult.success("{\"image\":\"x\"}", 12L);
        result.setTokensUsed(1234L);
        StubInvoker only = new StubInvoker("InvokerA", result);

        AigAuditContext audit = invoke(List.of(only), null, false);

        assertEquals("{\"tokensUsed\":1234}", audit.getUsageJson(),
            "token 数必须落库；没有它，「预算」既无法对账也无法据此限流");
    }

    @Test
    @DisplayName("用量：tokens 与 cost 同时存在时都要写出，且 cost 用十进制原样表达")
    void bothTokensAndCostAreRecorded() {
        ModelInvokeResult result = ModelInvokeResult.success("{\"image\":\"x\"}", 12L);
        result.setTokensUsed(7L);
        result.setCost(new BigDecimal("0.5"));
        StubInvoker only = new StubInvoker("InvokerA", result);

        AigAuditContext audit = invoke(List.of(only), null, false);

        assertEquals("{\"tokensUsed\":7,\"cost\":0.5}", audit.getUsageJson(),
            "不能写成科学计数法或带尾零，否则按 JSON 解析与对账都会出岔子");
        assertEquals(0, new BigDecimal("0.5").compareTo(audit.getCost()), "cost 列照旧单独落一份");
    }

    @Test
    @DisplayName("没有用量时必须留空，而不是写 {}：图像模型不回执 token，空对象会把「没有」说成「为零」")
    void noUsageLeavesTheColumnNull() {
        StubInvoker only = new StubInvoker("InvokerA", ModelInvokeResult.success("{\"image\":\"x\"}", 5L));

        AigAuditContext audit = invoke(List.of(only), null, false);

        assertNull(audit.getUsageJson(),
            "无用量写 null；写 {} 会让「未拿到用量」与「用量为零」混为一谈，且把该列变成噪音");
    }

    @Test
    @DisplayName("输入快照引用透传进审计（只存引用，不存副本）")
    void inputSnapshotRefIsRecorded() {
        StubInvoker only = new StubInvoker("InvokerA", ModelInvokeResult.success("{\"image\":\"x\"}", 5L));

        AigAuditContext audit = invoke(List.of(only), SNAPSHOT_REF, false);

        assertEquals(SNAPSHOT_REF, audit.getInputSnapshotRef(),
            "没有它，同一个 traceId 只能看到用了什么模型，看不到当时喂进去的是什么");
    }

    @Test
    @DisplayName("Agent 版本透传进审计上下文（灰度按版本统计的前提）")
    void agentVersionIsRecorded() {
        StubInvoker only = new StubInvoker("InvokerA", ModelInvokeResult.success("{\"image\":\"x\"}", 5L));

        AigAuditContext audit = invoke(List.of(only), null, false, 4242L);

        assertEquals(4242L, audit.getAgentVersionId(),
            "没有这一维度，灰度就统计不出「这个 Agent 版本被跑了多少次、失败几次」，"
                + "达标与否只剩调用方声明");
    }

    @Test
    @DisplayName("不经任务的调用没有 Agent 版本：留空表示「本次未绑定」，不是「不知道」")
    void absentAgentVersionStaysNull() {
        StubInvoker only = new StubInvoker("InvokerA", ModelInvokeResult.success("{\"image\":\"x\"}", 5L));

        AigAuditContext audit = invoke(List.of(only), null, false);

        assertNull(audit.getAgentVersionId(),
            "直接调能力本来就没绑定 Agent 版本；伪造一个会让「按版本统计」把无归属的调用算到别人头上");
    }

    @Test
    @DisplayName("未提供快照引用时留空，不伪造默认值")
    void absentSnapshotRefStaysNull() {
        StubInvoker only = new StubInvoker("InvokerA", ModelInvokeResult.success("{\"image\":\"x\"}", 5L));

        AigAuditContext audit = invoke(List.of(only), null, false);

        assertTrue(audit.getInputSnapshotRef() == null,
            "不填就不写：伪造一个引用会让「当时能复现」这句话变成假的");
    }

    /**
     * 具名桩调用器：只认 EXTERNAL_API，按脚本返回结果（用尽后重复最后一条）。
     */
    private static final class StubInvoker implements ModelInvoker {

        private final String name;
        private final List<ModelInvokeResult> scripted;
        private int calls;

        StubInvoker(String name, ModelInvokeResult... scripted) {
            this.name = name;
            this.scripted = List.of(scripted);
        }

        @Override
        public boolean supports(AigDeploymentTypeEnum deploymentType) {
            return deploymentType == AigDeploymentTypeEnum.EXTERNAL_API;
        }

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public String invokerName() {
            return name;
        }

        @Override
        public ModelInvokeResult invoke(ModelInvokeRequest request) {
            int index = Math.min(calls, scripted.size() - 1);
            calls++;
            return scripted.get(index);
        }
    }

}

