package org.dromara.aigov.service.impl;

import org.dromara.aigov.config.AigRetryProperties;
import org.dromara.aigov.domain.bo.AigInvokeBo;
import org.dromara.aigov.domain.vo.AigInvokeVo;
import org.dromara.aigov.domain.vo.AigModelVo;
import org.dromara.aigov.domain.vo.AigRouteCandidate;
import org.dromara.aigov.domain.vo.AigRouteDecision;
import org.dromara.aigov.enums.AigDataLevelEnum;
import org.dromara.aigov.enums.AigDeploymentTypeEnum;
import org.dromara.aigov.enums.AigErrorClassEnum;
import org.dromara.aigov.enums.AigRouteDecisionEnum;
import org.dromara.aigov.helper.AigAuditContext;
import org.dromara.aigov.helper.AigAuditRecorder;
import org.dromara.aigov.mapper.AigModelViewMapper;
import org.dromara.aigov.service.IAigRouteService;
import org.dromara.aigov.service.invoker.ModelInvokeRequest;
import org.dromara.aigov.service.invoker.ModelInvokeResult;
import org.dromara.aigov.service.invoker.ModelInvoker;
import cn.hutool.extra.spring.SpringUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.support.GenericApplicationContext;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 调用编排「有序 fallback」行为锁定测试。
 *
 * <p><b>它守的是什么</b>：候选列表来自路由（PRIMARY → GRAY → FALLBACK），此前只取第一个，
 * 主选失败就直接把失败抛给业务——<b>「有备选但从不启用」等于没有备选</b>
 * （bluocto 那条绑定就是 {@code usage_type=FALLBACK}）。</p>
 *
 * <p>两件事必须同时钉住，缺一半都是错的：</p>
 * <ul>
 *     <li><b>该换的要换</b>：鉴权失败、限流耗尽、输出不合格都要顺延到下一个候选；</li>
 *     <li><b>不该换的别换</b>：入参类错误换谁都一样被拒，顺延只是把同一个失败乘以候选数。</li>
 * </ul>
 *
 * <p>每个用例都把 {@code maxAttempts} 压到 1：测的是<b>候选之间的顺延</b>，
 * 不是候选内的重试（那条另有测试）。不压的话次数断言会被重试搅浑，看不出到底换没换。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigInvokeServiceImplFallbackTest {

    private static final String CAPABILITY = "image_generation";

    private IAigRouteService routeService;
    private AigAuditRecorder auditRecorder;
    private AigModelViewMapper modelViewMapper;
    private AigRetryProperties retryProperties;

    /**
     * 让 {@code JsonUtils} 能初始化，好在「输出不符合 Schema」这条分支上用**真实**校验器。
     *
     * <p>{@code JsonUtils} 的 mapper 是 {@code SpringUtils.getBean(JsonMapper.class)} 静态字段，
     * 类一加载就要 Spring 环境；纯单测里它会抛 {@code ExceptionInInitializerError}。
     * 注入一个只含 {@code JsonMapper} 的最小上下文即可。hutool 的
     * {@code SpringUtil.setApplicationContext} 虽是实例方法，但写的是静态字段，故可这样调用。</p>
     *
     * <p><b>注意</b>：{@code ExceptionInInitializerError} 是"粘"的——同一 JVM 内一旦有代码在注入前
     * 碰过 {@code JsonUtils}，该类就永久损坏、后续无法恢复。因此别在其它测试里不注入就调用
     * 依赖 {@code JsonUtils} 的方法（{@code AigOutputSchemaValidator.matches} 在 schema 为空白时
     * 会提前返回，不触碰 JsonUtils）。</p>
     */
    @BeforeAll
    static void bootJsonMapperForSchemaValidation() {
        GenericApplicationContext context = new GenericApplicationContext();
        context.getBeanFactory().registerSingleton("jsonMapper", JsonMapper.builder().build());
        context.refresh();
        new SpringUtil().setApplicationContext(context);
    }

    @BeforeEach
    void setUp() {
        routeService = mock(IAigRouteService.class);
        auditRecorder = mock(AigAuditRecorder.class);
        modelViewMapper = mock(AigModelViewMapper.class);
        retryProperties = new AigRetryProperties();
        retryProperties.setMaxAttempts(1);
        retryProperties.setBaseBackoffMs(1L);
        retryProperties.setMaxBackoffMs(2L);
        AigModelVo model = new AigModelVo();
        model.setModelType("CHAT");
        model.setModelKey("stub");
        when(modelViewMapper.selectModelById(any())).thenReturn(model);
    }

    @Test
    @DisplayName("鉴权失败：不重试同一家，但换下一候选并成功——审计必须记实际执行的那个候选")
    void fallsBackOnAuthFailureAndAuditsTheCandidateActuallyRun() {
        NamedStubInvoker a = new NamedStubInvoker("InvokerA",
            ModelInvokeResult.failure(null, 401, "unauthorized", 5L));
        NamedStubInvoker b = new NamedStubInvoker("InvokerB",
            ModelInvokeResult.success("{\"ok\":1}", 5L));

        AigInvokeVo vo = invoke(List.of(candidate(1, 101L, "modelA", "InvokerA"),
            candidate(2, 102L, "modelB", "InvokerB")), List.of(a, b), null);

        assertEquals(1, a.calls(), "鉴权失败不该向同一家重试（只会把账号打到风控）");
        assertEquals(1, b.calls(), "必须顺延到下一候选");
        assertEquals("{\"ok\":1}", vo.getOutput());
        AigAuditContext audit = captureAudit();
        assertEquals("0", audit.getResult());
        assertEquals(102L, audit.getModelId(), "审计要记实际执行的候选，否则就是一笔假账");
        assertEquals("modelB", audit.getModelKey());
        assertTrue(audit.getPolicyHits().stream().anyMatch(hit -> hit.contains("顺延到候选 #2")),
            "顺延过程要留痕，否则事后看不出发生过 fallback：" + audit.getPolicyHits());
    }

    @Test
    @DisplayName("入参类错误（INVALID_REQUEST）：不得顺延——换谁都一样被拒，顺延只是把失败乘以候选数")
    void doesNotFallBackOnInvalidRequest() {
        NamedStubInvoker a = new NamedStubInvoker("InvokerA",
            ModelInvokeResult.failure(AigErrorClassEnum.INVALID_REQUEST.getCode(), null, "提示词缺失", 5L));
        NamedStubInvoker b = new NamedStubInvoker("InvokerB", ModelInvokeResult.success("{\"ok\":1}", 5L));

        AigInvokeVo vo = invoke(List.of(candidate(1, 101L, "modelA", "InvokerA"),
            candidate(2, 102L, "modelB", "InvokerB")), List.of(a, b), null);

        assertEquals(1, a.calls());
        assertEquals(0, b.calls(), "入参错误不该顺延：同一个请求换谁都一样");
        assertEquals("1", captureAudit().getResult(), "结果必须是失败，不能因为还有候选就变成成功");
        assertNotNull(vo.getReason());
    }

    @Test
    @DisplayName("候选没有可用调用器：跳过它继续试下一个，而不是整体失败")
    void skipsCandidateWithoutInvokerAndTriesNext() {
        NamedStubInvoker b = new NamedStubInvoker("InvokerB", ModelInvokeResult.success("{\"ok\":1}", 5L));
        // 候选 1 是 SELF 部署，而两个桩调用器都只认 EXTERNAL_API → 该候选派不出调用器
        AigRouteCandidate self = candidate(1, 101L, "selfModel", "InvokerA");
        self.setDeploymentType(AigDeploymentTypeEnum.SELF.getCode());
        AigRouteCandidate external = candidate(2, 102L, "modelB", "InvokerB");

        AigInvokeVo vo = invoke(List.of(self, external), List.of(b), null);

        assertEquals("{\"ok\":1}", vo.getOutput(), "跳过候选 1 后应命中候选 2");
        assertEquals(1, b.calls());
        assertTrue(captureAudit().getPolicyHits().stream()
                .anyMatch(hit -> hit.contains("无可用调用器，顺延下一候选")),
            "跳过原因要留痕");
    }

    @Test
    @DisplayName("全部候选都失败：如实报最后一次的错误，且审计记最后执行的候选")
    void allCandidatesFailReportsLastCandidate() {
        NamedStubInvoker a = new NamedStubInvoker("InvokerA",
            ModelInvokeResult.failure(null, 503, "service unavailable", 5L));
        NamedStubInvoker b = new NamedStubInvoker("InvokerB",
            ModelInvokeResult.failure(null, 503, "service unavailable", 5L));

        invoke(List.of(candidate(1, 101L, "modelA", "InvokerA"),
            candidate(2, 102L, "modelB", "InvokerB")), List.of(a, b), null);

        assertEquals(1, a.calls());
        assertEquals(1, b.calls());
        AigAuditContext audit = captureAudit();
        assertEquals("1", audit.getResult());
        assertEquals(102L, audit.getModelId(), "最后执行的是候选 2，审计就该记候选 2");
    }

    @Test
    @DisplayName("输出不符合 Schema：不重试同一模型，但换下一个候选——换个模型可能就按格式回了")
    void schemaMismatchFallsBackToNextCandidate() {
        NamedStubInvoker a = new NamedStubInvoker("InvokerA",
            ModelInvokeResult.success("{}", 5L));
        NamedStubInvoker b = new NamedStubInvoker("InvokerB",
            ModelInvokeResult.success("{\"x\":42}", 5L));
        String schema = "{\"fields\":[{\"name\":\"x\"}]}";

        AigInvokeVo vo = invoke(List.of(candidate(1, 101L, "modelA", "InvokerA"),
            candidate(2, 102L, "modelB", "InvokerB")), List.of(a, b), schema);

        assertEquals(1, a.calls(), "输出不合格不重试同一模型（同样输入只会得到同样输出）");
        assertEquals(1, b.calls(), "但值得换一个模型");
        assertEquals("{\"x\":42}", vo.getOutput());
        assertEquals("0", captureAudit().getResult());
    }

    @Test
    @DisplayName("外发标记按实际执行的候选记：本地主候选失败、外部备选成功时必须记 external_call=Y")
    void externalCallFlagFollowsTheExecutedCandidate() {
        NamedStubInvoker local = new NamedStubInvoker("LocalInvoker",
            ModelInvokeResult.failure(null, 503, "local down", 5L));
        NamedStubInvoker external = new NamedStubInvoker("ExternalInvoker",
            ModelInvokeResult.success("{\"ok\":1}", 5L));

        AigRouteCandidate localCandidate = candidate(1, 101L, "localModel", "LocalInvoker");
        localCandidate.setDeploymentType(AigDeploymentTypeEnum.LOCAL.getCode());
        AigRouteCandidate externalCandidate = candidate(2, 102L, "remoteModel", "ExternalInvoker");
        externalCandidate.setDeploymentType(AigDeploymentTypeEnum.EXTERNAL_API.getCode());

        invoke(List.of(localCandidate, externalCandidate), List.of(external), null);

        AigAuditContext audit = captureAudit();
        assertTrue(audit.isExternalCall(),
            "真正跑的是外部备选，审计必须记 external_call=Y；"
                + "记成 N 会把「数据外发过」瞒下来，比没有审计更坏");
    }

    /**
     * 驱动一次调用。
     *
     * @param candidates 有序候选
     * @param invokers   可用调用器
     * @param outputSchema 能力输出模板（可空）
     * @return 调用返回视图
     */
    private AigInvokeVo invoke(List<AigRouteCandidate> candidates, List<ModelInvoker> invokers,
                               String outputSchema) {
        AigRouteDecision decision = new AigRouteDecision();
        decision.setCapabilityCode(CAPABILITY);
        decision.setDecision(AigRouteDecisionEnum.MODEL.getCode());
        decision.setAuditLevel("SUMMARY");
        decision.setOutputSchema(outputSchema);
        decision.getCandidates().addAll(candidates);
        AigRouteCandidate first = candidates.get(0);
        decision.setModelId(first.getModelId());
        decision.setModelKey(first.getModelKey());
        decision.setDeploymentType(first.getDeploymentType());
        decision.setInvoker(first.getInvoker());
        // 三参重载才是真实入口（两参是 default 方法，Mockito 不会代跑到它）
        when(routeService.decide(any(), any(), any())).thenReturn(decision);

        AigInvokeServiceImpl service = new AigInvokeServiceImpl(routeService, auditRecorder,
            invokers, modelViewMapper, retryProperties);
        AigInvokeBo bo = new AigInvokeBo();
        bo.setCapabilityCode(CAPABILITY);
        bo.setDataLevel(AigDataLevelEnum.INTERNAL.getCode());
        bo.setPrompt("生成一张图");
        return service.invoke(bo);
    }

    /**
     * 造一个候选。
     *
     * @param order          序号
     * @param modelId        模型ID
     * @param modelKey       模型键
     * @param invokerName    决策时解析出的调用器名
     * @return 候选
     */
    private AigRouteCandidate candidate(int order, long modelId, String modelKey, String invokerName) {
        AigRouteCandidate candidate = new AigRouteCandidate();
        candidate.setOrder(order);
        candidate.setModelId(modelId);
        candidate.setModelKey(modelKey);
        candidate.setModelType("CHAT");
        candidate.setDeploymentType(AigDeploymentTypeEnum.EXTERNAL_API.getCode());
        candidate.setInvoker(invokerName);
        candidate.setUsageType(order == 1 ? "PRIMARY" : "FALLBACK");
        candidate.setPriority(order);
        return candidate;
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
     * 具名桩调用器：只认 EXTERNAL_API，按脚本返回结果（用尽后重复最后一条）。
     */
    private static final class NamedStubInvoker implements ModelInvoker {

        private final String name;
        private final List<ModelInvokeResult> scripted;
        private int calls;

        NamedStubInvoker(String name, ModelInvokeResult... scripted) {
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

        int calls() {
            return calls;
        }
    }

}
