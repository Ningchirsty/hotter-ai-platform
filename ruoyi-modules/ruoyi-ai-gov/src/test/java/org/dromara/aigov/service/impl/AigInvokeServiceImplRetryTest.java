package org.dromara.aigov.service.impl;

import org.dromara.aigov.config.AigRetryProperties;
import org.dromara.aigov.domain.bo.AigInvokeBo;
import org.dromara.aigov.domain.vo.AigInvokeVo;
import org.dromara.aigov.domain.vo.AigRouteDecision;
import org.dromara.aigov.domain.vo.AigRouteHint;
import org.dromara.aigov.enums.AigDataLevelEnum;
import org.dromara.aigov.enums.AigDeploymentTypeEnum;
import org.dromara.aigov.enums.AigErrorClassEnum;
import org.dromara.aigov.enums.AigManualDecisionEnum;
import org.dromara.aigov.enums.AigRouteDecisionEnum;
import org.dromara.aigov.helper.AigAuditContext;
import org.dromara.aigov.helper.AigAuditRecorder;
import org.dromara.aigov.mapper.AigModelViewMapper;
import org.dromara.aigov.mapper.AigPolicyDecisionLogMapper;
import org.dromara.aigov.service.IAigRouteService;
import org.dromara.aigov.service.invoker.ModelInvokeRequest;
import org.dromara.aigov.service.invoker.ModelInvokeResult;
import org.dromara.aigov.service.invoker.ModelInvoker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.dromara.aigov.service.IAigCallApprovalService;
import org.dromara.aigov.service.IAigUserQuotaService;

/**
 * 调用编排「失败重试」行为锁定测试。
 *
 * <p><b>这里要钉住的是「重试的边界」，不是「重试会发生」</b>。重试本身很容易实现，
 * 难的是不被顺手扩大：参数错误重试一百次也是同样的错、鉴权失败继续重试只会把账号
 * 打到风控、关掉重试的开关必须真的关得掉。因此每个用例都同时断言
 * <b>调用次数</b>——只断言最终结果的话，「重试了但没人发现」也能通过。</p>
 *
 * <p>用真实的 {@link ModelInvoker} 实现（{@link StubInvoker}）而不是 Mockito mock：
 * mock 不会执行默认方法，而 {@code classifyError} 正是默认方法，
 * 用 mock 会把「分类是否正确驱动了重试」这条真链路绕过去，测试就只剩空壳。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigInvokeServiceImplRetryTest {

    private static final String CAPABILITY = "deliverable_consistency";
    private static final long MODEL_ID = 100L;

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
        // 退避压到 1~2ms：测的是重试逻辑，不是真实等待时长
        retryProperties.setBaseBackoffMs(1L);
        retryProperties.setMaxBackoffMs(2L);
        retryProperties.setMaxAttempts(3);
    }

    @Test
    @DisplayName("可重试错误：限流两次后成功，共调用 3 次，审计 retryCount=2")
    void retriesRetryableUntilSuccess() {
        StubInvoker invoker = new StubInvoker(
            ModelInvokeResult.failure(null, 429, "too many requests", 5L),
            ModelInvokeResult.failure(null, 429, "too many requests", 5L),
            ModelInvokeResult.success("{}", 5L));
        AigInvokeVo vo = invokeWith(invoker);

        assertEquals(3, invoker.calls(), "限流应被重试到成功为止（maxAttempts=3）");
        assertEquals("{}", vo.getOutput(), "最终应返回成功输出");
        AigAuditContext audit = captureAudit();
        assertEquals(2, audit.getRetryCount(), "重试次数要如实入审计（原先恒为 0，等于没有重试证据）");
        assertEquals("0", audit.getResult());
    }

    @Test
    @DisplayName("不可重试错误（参数/Schema）：只调用 1 次，且转人工")
    void doesNotRetryNonRetryableAndEscalatesToHuman() {
        StubInvoker invoker = new StubInvoker(
            ModelInvokeResult.failure(null, 400, "bad request", 5L));
        AigInvokeVo vo = invokeWith(invoker);

        assertEquals(1, invoker.calls(), "参数类错误重试多少次都是同样的结果，不许重试");
        assertEquals(AigRouteDecisionEnum.MODEL.getCode(), vo.getDecision());
        AigAuditContext audit = captureAudit();
        assertEquals(0, audit.getRetryCount());
        assertEquals("1", audit.getResult());
        assertEquals(AigManualDecisionEnum.PENDING.getCode(), audit.getManualDecision(),
            "不可重试的失败必须转人工，不能被当成偶发失败悄悄吞掉");
    }

    @Test
    @DisplayName("重试耗尽：一直超时则调用满 3 次后收敛为失败，recount 不超上限")
    void stopsAfterAttemptsExhausted() {
        StubInvoker invoker = new StubInvoker(ModelInvokeResult.failure(null, 504, "gateway timeout", 5L));
        AigInvokeVo vo = invokeWith(invoker);

        assertEquals(3, invoker.calls(), "总尝试次数应为 maxAttempts=3（含首次）");
        assertEquals("1", captureAudit().getResult());
        assertEquals(AigRouteDecisionEnum.MODEL.getCode(), vo.getDecision(), "仍应如实回报命中的模型，便于排障");
    }

    @Test
    @DisplayName("开关关闭时不得重试：即使错误可重试也只调用 1 次")
    void retryDisabledMeansSingleCall() {
        retryProperties.setEnabled(false);
        StubInvoker invoker = new StubInvoker(ModelInvokeResult.failure(null, 429, "too many requests", 5L));
        invokeWith(invoker);

        assertEquals(1, invoker.calls(), "关掉重试的出问题时必须能一键停掉外呼，开关不能形同虚设");
    }

    @Test
    @DisplayName("鉴权失败：不重试、不静默，且写明熔断建议")
    void authFailureIsNotRetriedAndFlagsCircuitBreak() {
        StubInvoker invoker = new StubInvoker(
            ModelInvokeResult.failure(null, 401, "unauthorized", 5L));
        invokeWith(invoker);

        assertEquals(1, invoker.calls(), "密钥错了继续调用只会把账号打到风控");
        AigAuditContext audit = captureAudit();
        assertNotEquals(AigManualDecisionEnum.PENDING.getCode(), audit.getManualDecision(),
            "鉴权失败属运维处置，不是「等人补输入」，不该占用人工确认待办");
        assertTrue(audit.getPolicyHits().stream().anyMatch(hit -> hit.contains("熔断建议")),
            "必须在审计里留下可检索的熔断线索：" + audit.getPolicyHits());
    }

    @Test
    @DisplayName("调用器 classifyError 返回 null（覆写有缺陷）：不得 NPE，退化为不重试")
    void nullClassificationDegradesToUnknownWithoutNpe() {
        ModelInvoker broken = new ModelInvoker() {
            @Override
            public boolean supports(AigDeploymentTypeEnum deploymentType) {
                return true;
            }

            @Override
            public boolean available() {
                return true;
            }

            @Override
            public ModelInvokeResult invoke(ModelInvokeRequest request) {
                return ModelInvokeResult.failure(null, 429, "too many requests", 5L);
            }

            @Override
            public AigErrorClassEnum classifyError(ModelInvokeResult result) {
                return null;
            }
        };
        AigInvokeVo vo = invokeWith(broken);

        assertEquals(AigRouteDecisionEnum.MODEL.getCode(), vo.getDecision());
        assertEquals("1", captureAudit().getResult(), "分类失败应退化为失败收敛，而不是抛异常");
    }

    /**
     * 以固定脚本驱动一次调用。
     *
     * @param invoker 调用器
     * @return 调用返回视图
     */
    private AigInvokeVo invokeWith(ModelInvoker invoker) {
        AigRouteDecision decision = routeDecision();
        // 带 AigRouteHint 的重载才是真实入口（String 那个是 default 方法，Mockito 不会代跑到它）。
        // 用 nullable 而不是 any(Class)：本用例没有场景也没有预算，提示就是 null，
        // 而 any(Class) 走 instanceof 语义、**不匹配 null**，会静默打不中桩。
        when(routeService.decide(any(), any(), nullable(AigRouteHint.class))).thenReturn(decision);
        AigInvokeServiceImpl service = new AigInvokeServiceImpl(routeService, auditRecorder,
            List.of(invoker), modelViewMapper, retryProperties, mock(IAigUserQuotaService.class),
            mock(IAigCallApprovalService.class), mock(AigPolicyDecisionLogMapper.class));
        AigInvokeBo bo = new AigInvokeBo();
        bo.setCapabilityCode(CAPABILITY);
        bo.setDataLevel(AigDataLevelEnum.INTERNAL.getCode());
        bo.setPrompt("请比较两张图");
        return service.invoke(bo);
    }

    /**
     * 构造一个「已命中模型」的路由决策。
     *
     * @return 决策
     */
    private AigRouteDecision routeDecision() {
        AigRouteDecision decision = new AigRouteDecision();
        decision.setCapabilityCode(CAPABILITY);
        decision.setDecision(AigRouteDecisionEnum.MODEL.getCode());
        decision.setModelId(MODEL_ID);
        decision.setModelKey("vendor/cloud-model");
        decision.setDeploymentType(AigDeploymentTypeEnum.EXTERNAL_API.getCode());
        decision.setAuditLevel("SUMMARY");
        decision.setReason("命中模型");
        return decision;
    }

    /**
     * 取审计记录器收到的上下文。
     *
     * @return 审计上下文
     */
    private AigAuditContext captureAudit() {
        ArgumentCaptor<AigAuditContext> captor = ArgumentCaptor.forClass(AigAuditContext.class);
        verify(auditRecorder).record(captor.capture());
        return captor.getValue();
    }

    /**
     * 按脚本返回结果的调用器；脚本用尽后重复最后一条。
     */
    private static final class StubInvoker implements ModelInvoker {

        private final List<ModelInvokeResult> scripted;
        private int calls;

        StubInvoker(ModelInvokeResult... scripted) {
            this.scripted = List.of(scripted);
        }

        @Override
        public boolean supports(AigDeploymentTypeEnum deploymentType) {
            return true;
        }

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public String invokerName() {
            return "StubInvoker";
        }

        @Override
        public ModelInvokeResult invoke(ModelInvokeRequest request) {
            int index = Math.min(calls, scripted.size() - 1);
            calls++;
            return scripted.get(index);
        }

        int calls() {
            return calls;
        }
    }

}

