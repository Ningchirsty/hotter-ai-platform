package org.dromara.aigov.service.impl;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.dromara.aigov.config.AigRetryProperties;
import org.dromara.aigov.domain.bo.AigInvokeBo;
import org.dromara.aigov.domain.vo.AigRouteDecision;
import org.dromara.aigov.enums.AigDataLevelEnum;
import org.dromara.aigov.enums.AigRouteDecisionEnum;
import org.dromara.aigov.helper.AigAuditContext;
import org.dromara.aigov.helper.AigAuditRecorder;
import org.dromara.aigov.mapper.AigModelViewMapper;
import org.dromara.aigov.service.IAigRouteService;
import org.dromara.aigov.service.invoker.ModelInvoker;
import org.dromara.common.core.utils.StringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 场景编码从「调用入参」到「路由决策」与「审计留痕」的贯通测试。
 *
 * <p><b>为什么必须单独测这一段</b>：路由引擎那边的场景收窄已经有测试，但它证明不了
 * 「调用方传进来的场景真的被送到了引擎」。而这条链上最典型的失败是
 * <b>默认方法陷阱</b>：{@code decide(capability, dataLevel)} 被保留为 default 重载后，
 * 只要有一处调用仍写两参，场景就被静默丢弃——功能「上线了」，却永远不生效，
 * 且从任何一次调用的返回上都看不出来。因此这里显式断言三参重载被调用、且参数正确。</p>
 *
 * <p>同时锁定审计：场景是「为什么这次没走默认首选」的唯一线索，
 * 只存在于请求里、不落审计的话，事后无法复盘。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigInvokeServiceImplScenarioTest {

    private static final String CAPABILITY = "deliverable_consistency";
    private static final String SCENARIO = "LONG_PAGE";

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    private final IAigRouteService routeService = mock(IAigRouteService.class);
    private final AigAuditRecorder auditRecorder = mock(AigAuditRecorder.class);
    private final AigModelViewMapper modelViewMapper = mock(AigModelViewMapper.class);

    @BeforeAll
    static void initValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        if (validatorFactory != null) {
            validatorFactory.close();
        }
    }

    private AigInvokeServiceImpl service() {
        return new AigInvokeServiceImpl(routeService, auditRecorder, List.<ModelInvoker>of(),
            modelViewMapper, new AigRetryProperties());
    }

    private static AigInvokeBo bo(String scenarioCode) {
        AigInvokeBo bo = new AigInvokeBo();
        bo.setCapabilityCode(CAPABILITY);
        bo.setDataLevel(AigDataLevelEnum.INTERNAL.getCode());
        bo.setScenarioCode(scenarioCode);
        bo.setPrompt("生成一张长图");
        return bo;
    }

    private static AigRouteDecision deniedDecision() {
        AigRouteDecision decision = new AigRouteDecision();
        decision.setCapabilityCode(CAPABILITY);
        decision.setDecision(AigRouteDecisionEnum.DENIED.getCode());
        decision.setReason("无可用模型且未允许转人工");
        decision.setAuditLevel("SUMMARY");
        return decision;
    }

    @Test
    @DisplayName("dryRun：场景编码必须原样送到路由引擎（而不是走两参重载被丢弃）")
    void dryRunPassesScenarioToRouter() {
        when(routeService.decide(eq(CAPABILITY), any(), eq(SCENARIO))).thenReturn(deniedDecision());

        service().dryRun(bo(SCENARIO));

        verify(routeService).decide(eq(CAPABILITY), any(), eq(SCENARIO));
    }

    @Test
    @DisplayName("invoke：场景编码必须原样送到路由引擎")
    void invokePassesScenarioToRouter() {
        when(routeService.decide(eq(CAPABILITY), any(), eq(SCENARIO))).thenReturn(deniedDecision());

        service().invoke(bo(SCENARIO));

        verify(routeService).decide(eq(CAPABILITY), any(), eq(SCENARIO));
    }

    @Test
    @DisplayName("审计留痕：场景编码必须落进审计上下文，否则事后无法解释「为什么不是默认首选」")
    void auditCarriesScenario() {
        when(routeService.decide(eq(CAPABILITY), any(), eq(SCENARIO))).thenReturn(deniedDecision());

        service().invoke(bo(SCENARIO));

        ArgumentCaptor<AigAuditContext> captor = ArgumentCaptor.forClass(AigAuditContext.class);
        verify(auditRecorder).record(captor.capture());
        assertEquals(SCENARIO, captor.getValue().getScenarioCode(),
            "审计必须记下场景：它是「候选为什么被收窄」的唯一线索");
    }

    @Test
    @DisplayName("不带场景：不得凭空写入任何场景值")
    void auditHasNoScenarioWhenRequestHasNone() {
        when(routeService.decide(eq(CAPABILITY), any(), isNull())).thenReturn(deniedDecision());

        service().invoke(bo(null));

        ArgumentCaptor<AigAuditContext> captor = ArgumentCaptor.forClass(AigAuditContext.class);
        verify(auditRecorder).record(captor.capture());
        assertTrue(StringUtils.isBlank(captor.getValue().getScenarioCode()),
            "未带场景时审计应为空，而不是写入一个默认场景名——那会让「没走场景收窄」看起来像走过了");
    }

    @Test
    @DisplayName("入参校验：STRICT 数据等级必须被接受")
    void strictDataLevelIsAccepted() {
        AigInvokeBo bo = bo(null);
        bo.setDataLevel(AigDataLevelEnum.STRICT.getCode());

        Set<ConstraintViolation<AigInvokeBo>> violations = validator.validate(bo);

        assertTrue(violations.isEmpty(),
            "严格级是「任何策略都不允许外发」的数据等级。若入口把它判为非法，"
                + "持有严格级数据的业务域只能放弃调用、或把等级标低再调——而后者正是 STRICT 要防的事。"
                + "实际违规=" + violations);
    }

    @Test
    @DisplayName("入参校验：场景编码有长度上限，且可以为空")
    void scenarioCodeLengthIsBounded() {
        AigInvokeBo tooLong = bo("X".repeat(65));
        assertTrue(validator.validate(tooLong).stream()
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("scenarioCode")),
            "超长场景编码应被拦在入口，避免带着无效场景去查库");

        AigInvokeBo blank = bo(null);
        assertTrue(validator.validate(blank).isEmpty(), "场景可为空：没有场景概念的调用占绝大多数");
        assertNull(blank.getScenarioCode());
    }

}
