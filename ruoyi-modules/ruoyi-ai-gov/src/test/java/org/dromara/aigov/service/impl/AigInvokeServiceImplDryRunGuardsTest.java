package org.dromara.aigov.service.impl;

import org.dromara.aigov.config.AigRetryProperties;
import org.dromara.aigov.domain.bo.AigInvokeBo;
import org.dromara.aigov.domain.vo.AigInvokeVo;
import org.dromara.aigov.domain.vo.AigQuotaStatusVo;
import org.dromara.aigov.domain.vo.AigRouteDecision;
import org.dromara.aigov.domain.vo.AigRouteHint;
import org.dromara.aigov.domain.vo.AigUserQuotaUsageVo;
import org.dromara.aigov.enums.AigDataLevelEnum;
import org.dromara.aigov.enums.AigRouteDecisionEnum;
import org.dromara.aigov.helper.AigAuditRecorder;
import org.dromara.aigov.mapper.AigModelViewMapper;
import org.dromara.aigov.mapper.AigPolicyDecisionLogMapper;
import org.dromara.aigov.service.IAigCallApprovalService;
import org.dromara.aigov.service.IAigRouteService;
import org.dromara.aigov.service.IAigUserQuotaService;
import org.dromara.aigov.service.invoker.ModelInvoker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 预演（{@code dryRun}）的"两道闸门"只读预判（Q3）。
 *
 * <p><b>守的是"预演不产生后果、但要说真话"</b>：</p>
 * <ol>
 *     <li>说真话：真实调用会被审批/配额拦的，预演就要能看见（否则"路由选中了模型"会被当成"可以调了"）；</li>
 *     <li>不产生后果：不写审计、不写决策账本、<b>不调用</b> {@code assertWithinQuota}（预演耗掉一次额度是把
 *         预览变成了一次真实动作）；</li>
 *     <li>没有调用人时如实留空，不能把"没有额度概念"说成"额度没问题"。</li>
 * </ol>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigInvokeServiceImplDryRunGuardsTest {

    private static final String CAPABILITY = "image_generation";

    private IAigRouteService routeService;
    private AigAuditRecorder auditRecorder;
    private AigModelViewMapper modelViewMapper;
    private IAigCallApprovalService approvalService;
    private IAigUserQuotaService quotaService;
    private AigPolicyDecisionLogMapper policyDecisionLogMapper;

    @BeforeEach
    void setUp() {
        routeService = mock(IAigRouteService.class);
        auditRecorder = mock(AigAuditRecorder.class);
        modelViewMapper = mock(AigModelViewMapper.class);
        approvalService = mock(IAigCallApprovalService.class);
        quotaService = mock(IAigUserQuotaService.class);
        policyDecisionLogMapper = mock(AigPolicyDecisionLogMapper.class);
    }

    /**
     * 打桩一次路由决策。
     *
     * @param approvalRequired 该能力×数据等级是否要求调用审批
     */
    private void stubDecision(boolean approvalRequired) {
        AigRouteDecision decision = new AigRouteDecision();
        decision.setCapabilityCode(CAPABILITY);
        decision.setDecision(AigRouteDecisionEnum.MODEL.getCode());
        decision.setApprovalRequired(approvalRequired);
        when(routeService.decide(any(), any(), nullable(AigRouteHint.class))).thenReturn(decision);
    }

    private AigInvokeServiceImpl service() {
        AigRetryProperties retry = new AigRetryProperties();
        retry.setMaxAttempts(1);
        return new AigInvokeServiceImpl(routeService, auditRecorder, List.<ModelInvoker>of(),
            modelViewMapper, retry, quotaService, approvalService, policyDecisionLogMapper);
    }

    private static AigInvokeBo bo() {
        AigInvokeBo bo = new AigInvokeBo();
        bo.setCapabilityCode(CAPABILITY);
        bo.setDataLevel(AigDataLevelEnum.INTERNAL.getCode());
        bo.setPrompt("生成一张图");
        return bo;
    }

    @Test
    @DisplayName("★ 策略要求审批 → 预演直接回答「真实调用会被审批拦」（否则会被当成可调）")
    void reportsApprovalRequirement() {
        stubDecision(true);
        AigInvokeVo vo = service().dryRun(bo());
        assertEquals(Boolean.TRUE, vo.getWouldRequireApproval());
    }

    @Test
    @DisplayName("策略不要求审批 → 明确回答 false（不是留空让人猜）")
    void reportsNoApprovalRequirement() {
        stubDecision(false);
        AigInvokeVo vo = service().dryRun(bo());
        assertEquals(Boolean.FALSE, vo.getWouldRequireApproval());
    }

    @Test
    @DisplayName("★ 预演不产生任何后果：不写审计、不写决策账本、不动配额计数")
    void dryRunHasNoSideEffects() {
        stubDecision(true);
        service().dryRun(bo());
        verify(auditRecorder, never()).record(any());
        verify(policyDecisionLogMapper, never()).insert(any(org.dromara.aigov.domain.AigPolicyDecisionLog.class));
        // 预演绝不能消耗额度：那会把"预览一次"变成"用掉一次"
        verify(quotaService, never()).assertWithinQuota(any());
        // 也不该去查调用授权（那是"真实调用能不能放行"的判定，且 dryRun 没有 traceId 可落账）
        verify(approvalService, never()).hasValidGrant(any(), any(), any());
    }

    @Test
    @DisplayName("没有调用人（调度/系统发起）→ 配额状态留空，且不去查任何人的额度")
    void noCallerMeansNoQuotaStatus() {
        stubDecision(false);
        // 单测里没有 Sa-Token 上下文 → resolveCallerId 返回 null
        AigInvokeVo vo = service().dryRun(bo());
        assertNull(vo.getQuotaStatus(), "没有调用人时不能回「额度没问题」——真实调用此时是 fail-closed");
        verify(quotaService, never()).usage(any());
    }

    @Test
    @DisplayName("★ 配额快照：remaining 取日/月里最紧的那个；不限时为 null")
    void quotaStatusComputesTightestRemaining() {
        AigQuotaStatusVo both = AigQuotaStatusVo.of(new AigUserQuotaUsageVo(7L, "steven",
            10, 8, LocalDateTime.now(), 100, 95, LocalDateTime.now(), true, false));
        assertTrue(both.limited());
        assertFalse(both.exceeded());
        // 日剩 2 / 月剩 5 → 取 2（否则界面会说"还有 5 次"而实际再调两次就被拦）
        assertEquals(2, both.remaining());
        assertEquals(10, both.dailyLimit());
        assertEquals(100, both.monthlyLimit());

        AigQuotaStatusVo unlimited = AigQuotaStatusVo.of(new AigUserQuotaUsageVo(7L, "steven",
            null, 3, LocalDateTime.now(), null, 9, LocalDateTime.now(), false, false));
        assertNull(unlimited.remaining());

        // 已超限时 remaining 不小于 0（不能给一个负数让人以为"倒欠"）
        AigQuotaStatusVo exceeded = AigQuotaStatusVo.of(new AigUserQuotaUsageVo(7L, "steven",
            5, 5, LocalDateTime.now(), null, 0, LocalDateTime.now(), true, true));
        assertTrue(exceeded.exceeded());
        assertEquals(0, exceeded.remaining());

        assertNull(AigQuotaStatusVo.of(null), "取不到用量时必须显式为空，不能编一个 0");
    }
}
