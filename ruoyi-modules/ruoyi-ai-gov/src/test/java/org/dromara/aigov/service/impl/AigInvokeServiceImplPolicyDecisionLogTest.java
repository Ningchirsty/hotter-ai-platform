package org.dromara.aigov.service.impl;

import org.dromara.aigov.config.AigRetryProperties;
import org.dromara.aigov.domain.AigPolicyDecisionLog;
import org.dromara.aigov.domain.bo.AigInvokeBo;
import org.dromara.aigov.domain.vo.AigModelVo;
import org.dromara.aigov.domain.vo.AigRouteCandidate;
import org.dromara.aigov.domain.vo.AigRouteDecision;
import org.dromara.aigov.domain.vo.AigRouteHint;
import org.dromara.aigov.enums.AigDataLevelEnum;
import org.dromara.aigov.enums.AigRouteDecisionEnum;
import org.dromara.aigov.helper.AigAuditContext;
import org.dromara.aigov.helper.AigAuditRecorder;
import org.dromara.aigov.mapper.AigModelViewMapper;
import org.dromara.aigov.mapper.AigPolicyDecisionLogMapper;
import org.dromara.aigov.service.IAigCallApprovalService;
import org.dromara.aigov.service.IAigRouteService;
import org.dromara.aigov.service.IAigUserQuotaService;
import org.dromara.aigov.service.invoker.ModelInvokeRequest;
import org.dromara.aigov.service.invoker.ModelInvokeResult;
import org.dromara.aigov.service.invoker.ModelInvoker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M-005 策略决策账本：可举证性锁定测试。
 *
 * <p><b>它守的是什么</b>：ADR-006 要求「外发管控在执行点强制且<b>可举证</b>」。
 * 此前 {@code decide()} 的结论只进内存 + {@code policyHits} 文本，调用被拒时除了应用日志
 * 没有可查询的结构化证据，无法回答"上周三那次外发是谁批准的、命中了哪条策略"。</p>
 *
 * <p>三条口径必须同时钉住，缺一条账本就不可信：</p>
 * <ol>
 *     <li><b>被拒也要留证</b>：DENIED / MANUAL 是决策结论，恰恰最需要举证——
 *         如果只在「确实调用了模型」的分支里写，这些早期 return 会全部漏记；</li>
 *     <li><b>记实际执行者</b>：有序 fallback 后真正跑的是备选。账本若停在主候选，
 *         {@code model_id} 与 {@code external_call} 都是假账——后者尤其严重：
 *         主候选本地、备选外部时，假账会把「数据外发过」记成「没外发」；</li>
 *     <li><b>dryRun 不写</b>：预检不是决策执行，写进去会让账本无法回答"实际发生过什么"。</li>
 * </ol>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigInvokeServiceImplPolicyDecisionLogTest {

    private static final String CAPABILITY = "image_generation";

    private IAigRouteService routeService;
    private AigAuditRecorder auditRecorder;
    private AigModelViewMapper modelViewMapper;
    private AigRetryProperties retryProperties;
    private AigPolicyDecisionLogMapper ledger;
    private final List<AigPolicyDecisionLog> inserted = new ArrayList<>();

    @BeforeEach
    void setUp() {
        routeService = mock(IAigRouteService.class);
        auditRecorder = mock(AigAuditRecorder.class);
        modelViewMapper = mock(AigModelViewMapper.class);
        retryProperties = new AigRetryProperties();
        retryProperties.setMaxAttempts(1);
        retryProperties.setBaseBackoffMs(1L);
        retryProperties.setMaxBackoffMs(2L);
        inserted.clear();
        ledger = mock(AigPolicyDecisionLogMapper.class);
        // 模拟 MyBatis-Plus 插入后回填主键；不回填的话 finalize 会提前 return，回填路径就测不到了
        when(ledger.insert(any(AigPolicyDecisionLog.class))).thenAnswer(inv -> {
            AigPolicyDecisionLog row = inv.getArgument(0);
            inserted.add(row);
            row.setDecisionId(9000L + inserted.size());
            return 1;
        });
        AigModelVo model = new AigModelVo();
        model.setModelType("CHAT");
        model.setModelKey("stub");
        when(modelViewMapper.selectModelById(any())).thenReturn(model);
    }

    @Test
    @DisplayName("★ 被拒的调用必须留证：DENIED 落一行、external_call=N、且真的没调用模型")
    void deniedCallIsRecordedAndNeverReachesAModel() {
        AigRouteDecision decision = baseDecision(AigRouteDecisionEnum.DENIED.getCode(),
            "策略 allowExternal='N'，该模型属于外部调用，数据外发被策略禁止");
        decision.setPolicyId(2019L);
        decision.setAllowExternal("N");
        decision.setModelId(null);
        decision.setModelKey(null);
        when(routeService.decide(any(), any(), nullable(AigRouteHint.class))).thenReturn(decision);

        NamedStubInvoker invoker = new NamedStubInvoker("InvokerA",
            ModelInvokeResult.success("{}", 5L));
        service(List.of(invoker)).invoke(bo(AigDataLevelEnum.RESTRICTED.getCode()));

        assertEquals(1, inserted.size(), "被拒的调用也必须且只落一行——它最需要举证");
        AigPolicyDecisionLog row = inserted.get(0);
        assertEquals("DENIED", row.getDecision());
        assertEquals("N", row.getExternalCall(), "被拒=没发出去，账本不能记成外发");
        assertEquals(2019L, row.getPolicyId(), "命中的策略要结构化可查，不能只躺在文案里");
        assertEquals("N", row.getAllowExternal(), "记的是实际生效值");
        assertEquals(0, invoker.calls(), "DENIED 不该调用模型");
    }

    @Test
    @DisplayName("★ 转人工（MANUAL）同样留证")
    void manualDecisionIsAlsoRecorded() {
        AigRouteDecision decision = baseDecision(AigRouteDecisionEnum.MANUAL.getCode(), "无可用模型，转人工");
        when(routeService.decide(any(), any(), nullable(AigRouteHint.class))).thenReturn(decision);

        service(List.of()).invoke(bo(AigDataLevelEnum.PUBLIC.getCode()));

        assertEquals(1, inserted.size(), "转人工是决策结论，必须留证");
        assertEquals("MANUAL", inserted.get(0).getDecision());
        assertEquals("N", inserted.get(0).getExternalCall());
    }

    @Test
    @DisplayName("★ fallback 后账本要记【实际执行】的候选，否则 model_id 与 external_call 都是假账")
    void ledgerFollowsTheCandidateThatActuallyRan() {
        AigRouteDecision decision = baseDecision(AigRouteDecisionEnum.MODEL.getCode(), "命中模型 modelA");
        decision.setModelId(101L);
        decision.setModelKey("modelA");
        decision.setDeploymentType("LOCAL");
        decision.setCandidates(List.of(
            candidate(1, 101L, "modelA", "LOCAL", "InvokerA"),
            candidate(2, 102L, "modelB", "EXTERNAL_API", "InvokerB")));
        when(routeService.decide(any(), any(), nullable(AigRouteHint.class))).thenReturn(decision);

        NamedStubInvoker a = new NamedStubInvoker("InvokerA",
            ModelInvokeResult.failure(null, 401, "unauthorized", 5L));
        NamedStubInvoker b = new NamedStubInvoker("InvokerB", ModelInvokeResult.success("{}", 5L));

        service(List.of(a, b)).invoke(bo(AigDataLevelEnum.INTERNAL.getCode()));

        assertEquals(1, inserted.size());
        AigPolicyDecisionLog row = inserted.get(0);
        assertEquals("MODEL", row.getDecision());
        assertEquals(102L, row.getModelId(), "必须记实际跑的那个候选（modelB）");
        assertEquals("modelB", row.getModelKey());
        assertEquals("EXTERNAL_API", row.getDeploymentType());
        assertEquals("Y", row.getExternalCall(),
            "主候选本地、备选外部：这次真的外发了，账本若停在主候选就会把「外发过」记成「没外发」");
    }

    @Test
    @DisplayName("★ dryRun 不写账本：预检不是决策执行，写了就无法回答「实际发生过什么」")
    void dryRunDoesNotWriteTheLedger() {
        AigRouteDecision decision = baseDecision(AigRouteDecisionEnum.MODEL.getCode(), "命中模型 modelA");
        decision.setModelId(101L);
        decision.setModelKey("modelA");
        when(routeService.decide(any(), any(), nullable(AigRouteHint.class))).thenReturn(decision);

        service(List.of()).dryRun(bo(AigDataLevelEnum.PUBLIC.getCode()));

        assertEquals(0, inserted.size(), "dryRun 只做决策预览，不得写决策账本");
        verify(ledger, never()).insert(org.mockito.ArgumentMatchers.<AigPolicyDecisionLog>any());
    }

    @Test
    @DisplayName("成功调用落一行 MODEL，model_id/model_key 与选中模型一致（防「配 A 跑 B」）")
    void successfulCallRecordsTheSelectedModel() {
        AigRouteDecision decision = baseDecision(AigRouteDecisionEnum.MODEL.getCode(), "命中模型 modelA");
        decision.setModelId(101L);
        decision.setModelKey("modelA");
        decision.setDeploymentType("LOCAL");
        decision.setCandidates(List.of(candidate(1, 101L, "modelA", "LOCAL", "InvokerA")));
        when(routeService.decide(any(), any(), nullable(AigRouteHint.class))).thenReturn(decision);

        service(List.of(new NamedStubInvoker("InvokerA", ModelInvokeResult.success("{}", 5L))))
            .invoke(bo(AigDataLevelEnum.PUBLIC.getCode()));

        assertEquals(1, inserted.size());
        AigPolicyDecisionLog row = inserted.get(0);
        assertEquals(101L, row.getModelId());
        assertEquals("modelA", row.getModelKey());
        assertEquals("N", row.getExternalCall(), "LOCAL 部署不外发");
        assertNotNull(row.getCapabilityCode());
        assertEquals(CAPABILITY, row.getCapabilityCode());
        assertNotNull(row.getTraceId(), "trace_id 是账本与审计对上的唯一钥匙");
        // 同一 traceId 也要落到审计里，否则「为什么」接不上「发生了什么」
        assertEquals(row.getTraceId(), captureAudit().getTraceId());
    }

    @Test
    @DisplayName("账本写失败绝不能让正常调用失败（旁路举证，异常只打日志）")
    void ledgerFailureDoesNotBreakTheCall() {
        when(ledger.insert(any(AigPolicyDecisionLog.class)))
            .thenThrow(new IllegalStateException("模拟账本写失败"));
        AigRouteDecision decision = baseDecision(AigRouteDecisionEnum.MODEL.getCode(), "命中模型 modelA");
        decision.setModelId(101L);
        decision.setModelKey("modelA");
        decision.setDeploymentType("LOCAL");
        decision.setCandidates(List.of(candidate(1, 101L, "modelA", "LOCAL", "InvokerA")));
        when(routeService.decide(any(), any(), nullable(AigRouteHint.class))).thenReturn(decision);

        var vo = service(List.of(new NamedStubInvoker("InvokerA",
            ModelInvokeResult.success("{\"ok\":1}", 5L)))).invoke(bo(AigDataLevelEnum.PUBLIC.getCode()));

        assertEquals("{\"ok\":1}", vo.getOutput(), "账本写失败不该影响调用结果");
        // 写失败时没有可回填的行，也不该去 updateById
        verify(ledger, never()).updateById(org.mockito.ArgumentMatchers.<AigPolicyDecisionLog>any());
    }

    // ------------------------------------------------------------------
    // 测试脚手架
    // ------------------------------------------------------------------

    private AigInvokeServiceImpl service(List<ModelInvoker> invokers) {
        return new AigInvokeServiceImpl(routeService, auditRecorder, invokers, modelViewMapper,
            retryProperties, mock(IAigUserQuotaService.class), mock(IAigCallApprovalService.class), ledger);
    }

    private static AigInvokeBo bo(String dataLevel) {
        AigInvokeBo bo = new AigInvokeBo();
        bo.setCapabilityCode(CAPABILITY);
        bo.setDataLevel(dataLevel);
        bo.setPrompt("生成一张图");
        return bo;
    }

    private static AigRouteDecision baseDecision(String decision, String reason) {
        AigRouteDecision d = new AigRouteDecision();
        d.setDecision(decision);
        d.setReason(reason);
        d.setCapabilityCode(CAPABILITY);
        return d;
    }

    private static AigRouteCandidate candidate(int order, Long modelId, String modelKey,
                                               String deployment, String invoker) {
        AigRouteCandidate c = new AigRouteCandidate();
        c.setOrder(order);
        c.setModelId(modelId);
        c.setModelKey(modelKey);
        c.setDeploymentType(deployment);
        c.setInvoker(invoker);
        c.setUsageType("FALLBACK");
        c.setPriority(500);
        return c;
    }

    private AigAuditContext captureAudit() {
        ArgumentCaptor<AigAuditContext> captor = ArgumentCaptor.forClass(AigAuditContext.class);
        verify(auditRecorder).record(captor.capture());
        return captor.getValue();
    }

    /**
     * 账本写入被捕获的实体。
     *
     * <p>{@code insert} 成功时按 MyBatis-Plus 的行为<b>回填主键</b>——只有回填了主键，
     * {@code finalizePolicyDecision} 才会走回填分支，而那正是我们要测的路径。</p>
     *
     * <p>断言读的是<b>同一个实体对象</b>的最终字段值，而不是 {@code updateById} 的入参捕获：
     * 前者能同时覆盖"它被回填了"与"回填的值来自实际执行的候选"。</p>
     */
    private AigPolicyDecisionLog insertedRow() {
        return inserted.get(0);
    }

    /**
     * 具名 stub 调用器：记录被调用次数，用于断言"确实/确实没有"发起调用。
     */
    private static final class NamedStubInvoker implements ModelInvoker {

        private final String name;
        private final ModelInvokeResult result;
        private int calls;

        private NamedStubInvoker(String name, ModelInvokeResult result) {
            this.name = name;
            this.result = result;
        }

        private int calls() {
            return calls;
        }

        @Override
        public String invokerName() {
            return name;
        }

        @Override
        public boolean supports(org.dromara.aigov.enums.AigDeploymentTypeEnum deploymentType) {
            // 测试里部署类型不作为筛选依据：候选的 invoker 名是显式指定的
            return true;
        }

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public ModelInvokeResult invoke(ModelInvokeRequest request) {
            calls++;
            return result;
        }
    }
}
