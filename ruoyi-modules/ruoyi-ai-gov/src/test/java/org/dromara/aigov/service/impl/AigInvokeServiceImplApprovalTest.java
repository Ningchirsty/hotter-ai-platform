package org.dromara.aigov.service.impl;

import org.dromara.aigov.config.AigRetryProperties;
import org.dromara.aigov.domain.bo.AigInvokeBo;
import org.dromara.aigov.domain.vo.AigInvokeVo;
import org.dromara.aigov.domain.vo.AigModelVo;
import org.dromara.aigov.domain.vo.AigRouteCandidate;
import org.dromara.aigov.domain.vo.AigRouteDecision;
import org.dromara.aigov.domain.vo.AigRouteHint;
import org.dromara.aigov.enums.AigDataLevelEnum;
import org.dromara.aigov.enums.AigDeploymentTypeEnum;
import org.dromara.aigov.enums.AigErrorClassEnum;
import org.dromara.aigov.enums.AigInvokeResultEnum;
import org.dromara.aigov.enums.AigManualDecisionEnum;
import org.dromara.aigov.enums.AigRouteDecisionEnum;
import org.dromara.aigov.helper.AigAuditContext;
import org.dromara.aigov.helper.AigAuditRecorder;
import org.dromara.aigov.mapper.AigModelViewMapper;
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

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 调用授权审批在<b>统一调用入口</b>上的接线测试（C3）。
 *
 * <p><b>为什么单独测这一段</b>：审批的判定散成两半——路由侧只"读出"要求
 * （{@code AigRouteDecision.approvalRequired}），授权判定在调用入口（因为只有那里
 * 拿得到调用人）。这段接线断了不会报错：策略写着"需要审批"，调用照样跑出去。
 * 因此这里逐条钉住「没有授权就一次都不发」「有授权才发」「先看授权再看配额」
 * 「策略不要求审批时压根不去查授权」。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigInvokeServiceImplApprovalTest {

    private static final String CAPABILITY = "image_generation";
    private static final long MODEL_ID = 101L;
    private static final String SNAPSHOT_REF = "oss://ai-input/snap/a.json";

    private IAigRouteService routeService;
    private AigAuditRecorder auditRecorder;
    private AigModelViewMapper modelViewMapper;
    private AigRetryProperties retryProperties;
    private IAigCallApprovalService approvalService;
    private IAigUserQuotaService quotaService;

    @BeforeEach
    void setUp() {
        routeService = mock(IAigRouteService.class);
        auditRecorder = mock(AigAuditRecorder.class);
        modelViewMapper = mock(AigModelViewMapper.class);
        approvalService = mock(IAigCallApprovalService.class);
        quotaService = mock(IAigUserQuotaService.class);
        retryProperties = new AigRetryProperties();
        retryProperties.setMaxAttempts(1);
        retryProperties.setBaseBackoffMs(1L);
        retryProperties.setMaxBackoffMs(2L);

        AigModelVo model = new AigModelVo();
        model.setModelType("IMAGE");
        model.setModelKey("modelA");
        model.setProviderId(1001L);
        when(modelViewMapper.selectModelById(MODEL_ID)).thenReturn(model);
    }

    /**
     * 打桩一次「命中模型」的路由决策。
     *
     * @param approvalRequired 该策略是否要求调用审批
     */
    private void stubModelDecision(boolean approvalRequired) {
        AigRouteDecision decision = new AigRouteDecision();
        decision.setCapabilityCode(CAPABILITY);
        decision.setDecision(AigRouteDecisionEnum.MODEL.getCode());
        decision.setAuditLevel("SUMMARY");
        decision.setApprovalRequired(approvalRequired);
        AigRouteCandidate candidate = new AigRouteCandidate();
        candidate.setOrder(1);
        candidate.setModelId(MODEL_ID);
        candidate.setModelKey("modelA");
        candidate.setModelType("IMAGE");
        candidate.setDeploymentType(AigDeploymentTypeEnum.EXTERNAL_API.getCode());
        candidate.setInvoker("InvokerA");
        candidate.setUsageType("PRIMARY");
        decision.getCandidates().add(candidate);
        decision.setModelId(MODEL_ID);
        decision.setModelKey("modelA");
        decision.setDeploymentType(AigDeploymentTypeEnum.EXTERNAL_API.getCode());
        when(routeService.decide(any(), any(), nullable(AigRouteHint.class))).thenReturn(decision);
    }

    private AigInvokeServiceImpl service(StubInvoker invoker) {
        return new AigInvokeServiceImpl(routeService, auditRecorder, List.of(invoker),
            modelViewMapper, retryProperties, quotaService, approvalService);
    }

    private static AigInvokeBo bo() {
        AigInvokeBo bo = new AigInvokeBo();
        bo.setCapabilityCode(CAPABILITY);
        bo.setDataLevel(AigDataLevelEnum.INTERNAL.getCode());
        bo.setPrompt("生成一张图");
        bo.setInputSnapshotRef(SNAPSHOT_REF);
        return bo;
    }

    private AigAuditContext captureAudit() {
        ArgumentCaptor<AigAuditContext> captor = ArgumentCaptor.forClass(AigAuditContext.class);
        verify(auditRecorder).record(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("★ 策略要求审批但没有有效授权 → 一次都不发给 Provider，且落 APPROVAL_REQUIRED")
    void blocksBeforeCallingProviderWhenNoGrant() {
        stubModelDecision(true);
        // 单测里没有 Sa-Token 上下文 → resolveCallerId 返回 null；any() 不匹配 null，
        // 必须用 nullable(...)，否则桩静默打不中、测试会因为「恰好也返回 false」而假绿
        when(approvalService.hasValidGrant(nullable(Long.class), any(), any())).thenReturn(false);
        StubInvoker invoker = new StubInvoker("InvokerA", ModelInvokeResult.success("{\"image\":\"x\"}", 5L));

        AigInvokeVo vo = service(invoker).invoke(bo());

        assertEquals(AigErrorClassEnum.APPROVAL_REQUIRED.getCode(), vo.getErrorCode());
        assertEquals(0, invoker.calls.get(), "没有授权就绝不能把请求发出去——审批的意义全在这一条上");
        AigAuditContext audit = captureAudit();
        assertEquals(AigInvokeResultEnum.FAILED.getCode(), audit.getResult());
        assertEquals(AigErrorClassEnum.APPROVAL_REQUIRED.getCode(), audit.getErrorClass(),
            "分类要落库：否则「审批缺失」会被当成未归类错误");
        assertEquals(AigManualDecisionEnum.PENDING.getCode(), audit.getManualDecision(),
            "这是对人的待办（去申请/去批准），不是偶发失败");
        assertTrue(audit.getErrorSummary().contains("需要调用审批"), audit.getErrorSummary());
        assertTrue(audit.getErrorSummary().contains("治理台"), "要给出路：" + audit.getErrorSummary());
        // 没有 Sa-Token 上下文时按「无调用人」查（fail-closed）
        verify(approvalService).hasValidGrant(isNull(), eq(CAPABILITY), eq("INTERNAL"));
        // 被审批拦下时不消耗额度
        verify(quotaService, never()).assertWithinQuota(any());
    }

    @Test
    @DisplayName("★ 有有效授权 → 正常调用，并在 policyHits 里留下「按审批放行」")
    void allowsCallWhenGrantExists() {
        stubModelDecision(true);
        when(approvalService.hasValidGrant(nullable(Long.class), any(), any())).thenReturn(true);
        StubInvoker invoker = new StubInvoker("InvokerA", ModelInvokeResult.success("{\"image\":\"x\"}", 5L));

        AigInvokeVo vo = service(invoker).invoke(bo());

        assertEquals("{\"image\":\"x\"}", vo.getOutput());
        assertEquals(1, invoker.calls.get());
        AigAuditContext audit = captureAudit();
        assertEquals(AigInvokeResultEnum.SUCCESS.getCode(), audit.getResult());
        assertTrue(audit.getPolicyHits().stream().anyMatch(h -> h.contains("命中有效调用授权")),
            "账本要留下「这次凭什么放行」：" + audit.getPolicyHits());
        verify(quotaService).assertWithinQuota(any());
    }

    @Test
    @DisplayName("★ 先看授权再看配额：缺授权时报的是审批，不是「配额超了」")
    void approvalIsCheckedBeforeQuota() {
        stubModelDecision(true);
        when(approvalService.hasValidGrant(nullable(Long.class), any(), any())).thenReturn(false);

        AigInvokeVo vo = service(new StubInvoker("InvokerA",
            ModelInvokeResult.success("{}", 5L))).invoke(bo());

        assertEquals(AigErrorClassEnum.APPROVAL_REQUIRED.getCode(), vo.getErrorCode(),
            "没有授权时真实且可操作的结论是「去申请审批」；报配额超了会把人引到完全错误的方向");
        verify(quotaService, never()).assertWithinQuota(any());
    }

    @Test
    @DisplayName("策略不要求审批 → 压根不去查授权（默认行为与引入审批之前逐字不变）")
    void doesNotConsultApprovalWhenNotRequired() {
        stubModelDecision(false);
        StubInvoker invoker = new StubInvoker("InvokerA", ModelInvokeResult.success("{\"image\":\"x\"}", 5L));

        AigInvokeVo vo = service(invoker).invoke(bo());

        assertEquals("{\"image\":\"x\"}", vo.getOutput());
        verify(approvalService, never()).hasValidGrant(any(), any(), any());
    }

    /**
     * 具名桩调用器：只认 EXTERNAL_API，并记下被调了次数。
     */
    private static final class StubInvoker implements ModelInvoker {

        private final String name;
        private final ModelInvokeResult result;
        private final AtomicInteger calls = new AtomicInteger();

        StubInvoker(String name, ModelInvokeResult result) {
            this.name = name;
            this.result = result;
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
            calls.incrementAndGet();
            return result;
        }
    }

}
