package org.dromara.aigov.service.impl;

import org.dromara.aigov.config.AigModelHealthProbeProperties;
import org.dromara.aigov.domain.AigCapabilityModel;
import org.dromara.aigov.domain.AigModelGovernance;
import org.dromara.aigov.domain.vo.AigModelHealthProbeVo;
import org.dromara.aigov.domain.vo.AigModelTestVo;
import org.dromara.aigov.mapper.AigCapabilityModelMapper;
import org.dromara.aigov.mapper.AigModelGovernanceMapper;
import org.dromara.aigov.service.IAigModelGovernanceService;
import org.dromara.aigov.service.IAigModelHealthProbeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 模型健康探测（M-003）行为锁定测试。
 *
 * <p><b>它守的是什么</b>：探测本身不难，难的是<b>别乱花钱</b>。探测会对外发起真实调用，
 * 所以两条不变量必须钉住，否则"加了自动探测"会变成"账单变多"：</p>
 * <ol>
 *     <li><b>只为参与路由的模型花</b>：没有绑定的模型即使不健康也不影响任何一次路由；</li>
 *     <li><b>跳过刚测过的</b>：否则把周期调短就等于等比例放大外呼量。</li>
 * </ol>
 * <p>另外两条是"别把机制做成摆设"：关闭时**一次都不许调用**；
 * 以及"本轮截断"要让最陈旧的先测。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigModelHealthProbeServiceTest {

    private static final long BOUND_MODEL = 3L;
    private static final long UNBOUND_MODEL = 7L;

    private AigModelGovernanceMapper governanceMapper;
    private AigCapabilityModelMapper capabilityModelMapper;
    private IAigModelGovernanceService governanceService;
    private AigModelHealthProbeProperties properties;
    private AigModelHealthProbeServiceImpl service;

    @BeforeEach
    void setUp() {
        governanceMapper = mock(AigModelGovernanceMapper.class);
        capabilityModelMapper = mock(AigCapabilityModelMapper.class);
        governanceService = mock(IAigModelGovernanceService.class);
        properties = new AigModelHealthProbeProperties();
        properties.setEnabled(true);
        service = new AigModelHealthProbeServiceImpl(
            governanceMapper, capabilityModelMapper, governanceService, properties);
    }

    @Test
    @DisplayName("★ 关闭时一次都不许调用（默认 enabled=false 的意义就在这里）")
    void disabledProbeCallsNothing() {
        properties.setEnabled(false);

        AigModelHealthProbeVo result = service.probeOnce();

        assertFalse(result.isExecuted(), "未启用不应执行");
        assertEquals("aigov.model.health-probe.enabled=false", result.getSkippedReason());
        verify(governanceService, never()).testConnection(any());
        verify(governanceMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("★ 只为参与路由的模型花钱：未绑定的模型一次都不测")
    void onlyProbesModelsThatAreActuallyBound() {
        properties.setOnlyBound(true);
        stubGovernance(governance(BOUND_MODEL, null), governance(UNBOUND_MODEL, null));
        stubBindings(BOUND_MODEL);

        AigModelHealthProbeVo result = service.probeOnce();

        assertEquals(1, result.getProbed(), "只有绑定中的那一个该被测");
        ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(Long.class);
        verify(governanceService).testConnection(captor.capture());
        assertEquals(BOUND_MODEL, captor.getValue(), "被测的必须是绑定中的模型");
    }

    @Test
    @DisplayName("★ 跳过刚测过的：staleHours 内不再测，避免调短周期就等于多花钱")
    void skipsRecentlyProbedModels() {
        properties.setStaleHours(6);
        stubGovernance(
            governance(1L, LocalDateTime.now().minusMinutes(30)),   // 刚测过 -> 跳过
            governance(2L, LocalDateTime.now().minusHours(12)));    // 陈旧 -> 要测
        stubBindings(1L, 2L);

        AigModelHealthProbeVo result = service.probeOnce();

        assertEquals(1, result.getProbed());
        ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(Long.class);
        verify(governanceService).testConnection(captor.capture());
        assertEquals(2L, captor.getValue(), "只该测陈旧的那个");
    }

    @Test
    @DisplayName("★ 从未测过（health_time 为空）必须测——那正是要暴露的状态")
    void neverTestedModelsAreAlwaysProbed() {
        properties.setStaleHours(6);
        stubGovernance(governance(5L, null));
        stubBindings(5L);

        AigModelHealthProbeVo result = service.probeOnce();

        assertEquals(1, result.getProbed(), "health_time 为空 = 从未测过，必须测");
    }

    @Test
    @DisplayName("★ 本轮上限：最陈旧的先测，其余计入 deferred（而不是丢弃）")
    void truncatesOldestFirstAndReportsDeferred() {
        properties.setStaleHours(0);
        properties.setMaxPerRound(2);
        stubGovernance(
            governance(1L, LocalDateTime.now().minusDays(30)),
            governance(2L, LocalDateTime.now().minusDays(10)),
            governance(3L, LocalDateTime.now().minusDays(5)));
        stubBindings(1L, 2L, 3L);

        AigModelHealthProbeVo result = service.probeOnce();

        assertEquals(2, result.getProbed());
        assertEquals(1, result.getDeferred(), "未处理的要报出来，否则会被当成已覆盖");
        List<Long> probed = new ArrayList<>();
        ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(Long.class);
        verify(governanceService, org.mockito.Mockito.times(2)).testConnection(captor.capture());
        probed.addAll(captor.getAllValues());
        assertTrue(probed.contains(1L) && probed.contains(2L),
            "应先测最陈旧的 1、2；实际=" + probed);
    }

    @Test
    @DisplayName("单个模型探测抛异常：不中断整轮，且不计入 unhealthy（两种失败不能混）")
    void oneFailureDoesNotAbortTheRoundAndIsNotCountedAsUnhealthy() {
        properties.setStaleHours(0);
        properties.setMaxPerRound(0);
        stubGovernance(governance(1L, null), governance(2L, null));
        stubBindings(1L, 2L);
        when(governanceService.testConnection(1L)).thenThrow(new RuntimeException("供应商超时"));
        when(governanceService.testConnection(2L)).thenReturn(ok(true));

        AigModelHealthProbeVo result = service.probeOnce();

        assertEquals(1, result.getSkipped(), "探测本身失败应计入 skipped");
        assertEquals(0, result.getUnhealthy(), "「探测失败」不等于「探到了不健康」，不能混");
        assertEquals(1, result.getProbed());
        assertEquals(1, result.getHealthy(), "第二个模型仍应被正常测到");
    }

    @Test
    @DisplayName("探到不健康要能被点名（运维不必自己翻日志找人）")
    void unhealthyModelsAreNamed() {
        properties.setStaleHours(0);
        stubGovernance(governance(9L, null));
        stubBindings(9L);
        when(governanceService.testConnection(9L)).thenReturn(ok(false));

        AigModelHealthProbeVo result = service.probeOnce();

        assertEquals(1, result.getUnhealthy());
        assertTrue(result.getUnhealthyModels().contains("9"), "不健康的模型ID要带出来");
    }

    // ------------------------------------------------------------------
    // HTTP 入口的"提交即返回"契约（2026-10-08 实测 133 秒超时事故）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("★ 开关关闭时提交必须被拒（且一次外呼都不发），而不是受理后什么都不做")
    void triggerAsyncRefusesWhenDisabled() {
        properties.setEnabled(false);

        IAigModelHealthProbeService.SubmitOutcome outcome = service.triggerAsync();

        assertEquals(IAigModelHealthProbeService.SubmitOutcome.DISABLED, outcome);
        verify(governanceService, never()).testConnection(any());
    }

    @Test
    @DisplayName("★ 提交立即返回：探测耗时 133 秒，入口不能同步等它（网关约 100 秒就断）")
    void triggerAsyncReturnsImmediatelyWhileProbeKeepsRunning() throws Exception {
        properties.setStaleHours(0);
        stubGovernance(governance(1L, null));
        stubBindings(1L);
        CountDownLatch probeFinished = new CountDownLatch(1);
        // 模拟真实外呼：单次探测耗时明显长于"提交"应耗的时间
        when(governanceService.testConnection(1L)).thenAnswer(inv -> {
            try {
                Thread.sleep(1500L);
                return ok(true);
            } finally {
                probeFinished.countDown();
            }
        });

        long start = System.nanoTime();
        IAigModelHealthProbeService.SubmitOutcome outcome = service.triggerAsync();
        long submitMs = (System.nanoTime() - start) / 1_000_000L;

        assertEquals(IAigModelHealthProbeService.SubmitOutcome.ACCEPTED, outcome);
        assertTrue(submitMs < 1000L,
            "提交必须在 1 秒内返回（实测探测本身要 133 秒）；实际=" + submitMs + "ms");
        assertTrue(probeFinished.await(10, TimeUnit.SECONDS),
            "返回之后后台仍必须真的把这一轮跑完——否则『立即返回』就变成了『什么都没做』");
    }

    @Test
    @DisplayName("★ 去重：一轮没跑完时的第二次提交必须被拒（单线程执行器只会排队，挡不住重复外呼）")
    void triggerAsyncIsDeduplicatedWhileARoundIsStillRunning() throws Exception {
        properties.setStaleHours(0);
        stubGovernance(governance(1L, null));
        stubBindings(1L);
        CountDownLatch probeEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstRound = new CountDownLatch(1);
        when(governanceService.testConnection(1L)).thenAnswer(inv -> {
            probeEntered.countDown();
            releaseFirstRound.await(10, TimeUnit.SECONDS);
            return ok(true);
        });

        assertEquals(IAigModelHealthProbeService.SubmitOutcome.ACCEPTED, service.triggerAsync());
        assertTrue(probeEntered.await(10, TimeUnit.SECONDS), "第一轮应已进入探测");

        assertEquals(IAigModelHealthProbeService.SubmitOutcome.ALREADY_RUNNING, service.triggerAsync(),
            "第一轮还在跑，第二次提交必须是 ALREADY_RUNNING");
        assertEquals(IAigModelHealthProbeService.SubmitOutcome.ALREADY_RUNNING, service.triggerAsync(),
            "连续重试也不该被放行——重试正是网关超时后最可能发生的事");

        releaseFirstRound.countDown();

        // 状态位必须在 finally 里释放，否则这个开关会永久卡死、从此再也探测不了
        IAigModelHealthProbeService.SubmitOutcome afterRelease = null;
        long deadline = System.currentTimeMillis() + 10_000L;
        while (System.currentTimeMillis() < deadline) {
            afterRelease = service.triggerAsync();
            if (afterRelease == IAigModelHealthProbeService.SubmitOutcome.ACCEPTED) {
                break;
            }
            Thread.sleep(50L);
        }
        assertEquals(IAigModelHealthProbeService.SubmitOutcome.ACCEPTED, afterRelease,
            "第一轮结束后必须能再次提交（probeInFlight 要在 finally 里释放）");

        verify(governanceService, org.mockito.Mockito.timeout(10_000).atLeast(2)).testConnection(1L);
    }

    @Test
    @DisplayName("提交失败必须把状态位放回去，否则一次执行器故障会永久禁用探测")
    void rejectedSubmissionDoesNotLatchTheInFlightFlag() {
        properties.setStaleHours(0);
        stubGovernance(governance(1L, null));
        stubBindings(1L);

        // 用替身执行器模拟"执行器不可用"（真实执行器无法安全地模拟这一状态）
        ExecutorService rejecting = mock(ExecutorService.class);
        doThrow(new RejectedExecutionException("shutdown")).when(rejecting).execute(any());
        setProbeExecutor(rejecting);

        assertEquals(IAigModelHealthProbeService.SubmitOutcome.REJECTED, service.triggerAsync(),
            "执行器拒绝时应如实回 REJECTED，而不是含糊的 false");

        // 执行器恢复后必须能再次提交——若 try 里忘了在 catch 放回状态位，这里会得到 ALREADY_RUNNING
        ExecutorService accepting = mock(ExecutorService.class);
        setProbeExecutor(accepting);
        assertEquals(IAigModelHealthProbeService.SubmitOutcome.ACCEPTED, service.triggerAsync(),
            "一次拒绝不能把探测永久锁死");
    }

    /**
     * 用反射替换私有执行器，以便在不 shutdown 真实线程池的前提下模拟"执行器不可用"。
     *
     * @param executor 替身执行器
     */
    private void setProbeExecutor(ExecutorService executor) {
        try {
            java.lang.reflect.Field f =
                AigModelHealthProbeServiceImpl.class.getDeclaredField("probeExecutor");
            f.setAccessible(true);
            f.set(service, executor);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法替换 probeExecutor（字段名被改动了？）", e);
        }
    }

    // ------------------------------------------------------------------
    // 脚手架
    // ------------------------------------------------------------------

    private void stubGovernance(AigModelGovernance... rows) {
        when(governanceMapper.selectList(any())).thenReturn(List.of(rows));
    }

    private void stubBindings(Long... modelIds) {
        List<AigCapabilityModel> binds = new ArrayList<>();
        for (Long id : modelIds) {
            AigCapabilityModel b = new AigCapabilityModel();
            b.setModelId(id);
            binds.add(b);
        }
        when(capabilityModelMapper.selectList(any())).thenReturn(binds);
    }

    private static AigModelGovernance governance(Long modelId, LocalDateTime healthTime) {
        AigModelGovernance g = new AigModelGovernance();
        g.setModelId(modelId);
        g.setHealthTime(healthTime);
        g.setStatus("0");
        g.setDelFlag("0");
        return g;
    }

    private static AigModelTestVo ok(boolean healthy) {
        AigModelTestVo vo = new AigModelTestVo();
        vo.setOk(healthy);
        vo.setHealthStatus(healthy ? "HEALTHY" : "UNHEALTHY");
        return vo;
    }
}
