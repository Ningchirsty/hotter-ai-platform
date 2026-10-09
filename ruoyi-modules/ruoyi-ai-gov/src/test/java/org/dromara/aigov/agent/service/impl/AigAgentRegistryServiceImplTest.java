package org.dromara.aigov.agent.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.aigov.agent.domain.AigAgentBinding;
import org.dromara.aigov.agent.domain.AigAgentVersion;
import org.dromara.aigov.agent.domain.AigPackage;
import org.dromara.aigov.agent.domain.AigPackageVersion;
import org.dromara.aigov.agent.domain.AigReleaseEvent;
import org.dromara.aigov.agent.domain.AigSandboxRun;
import org.dromara.aigov.agent.domain.bo.AigAgentBindingBo;
import org.dromara.aigov.agent.domain.bo.AigReleaseAdvanceBo;
import org.dromara.aigov.agent.enums.AigPackageRejectRuleEnum;
import org.dromara.aigov.agent.enums.AigReleaseGateEnum;
import org.dromara.aigov.agent.enums.AigReleaseStatusEnum;
import org.dromara.aigov.agent.enums.AigReleaseTargetTypeEnum;
import org.dromara.aigov.agent.evaluation.AigCanaryEvidence;
import org.dromara.aigov.agent.evaluation.AigGoldenCaseEvidence;
import org.dromara.aigov.agent.evaluation.AigSandboxRunEvidence;
import org.dromara.aigov.agent.manifest.AigManifestScanResult;
import org.dromara.aigov.agent.manifest.AigPackageManifestValidator;
import org.dromara.aigov.agent.mapper.AigAgentBindingMapper;
import org.dromara.aigov.agent.mapper.AigAgentVersionMapper;
import org.dromara.aigov.agent.mapper.AigPackageMapper;
import org.dromara.aigov.agent.mapper.AigPackageVersionMapper;
import org.dromara.aigov.agent.mapper.AigReleaseEventMapper;
import org.dromara.aigov.agent.mapper.AigSkillVersionMapper;
import org.dromara.aigov.agent.service.IAigCanaryEvidenceService;
import org.dromara.aigov.agent.service.IAigEvaluationService;
import org.dromara.aigov.agent.service.IAigSandboxRunService;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 发布推进的守门测试（设计 §5.4、§6.3）。
 *
 * <p>这里要钉住的四类违约，都是「不生效也不会报错」的类型：</p>
 * <ol>
 *     <li><b>缺门槛就前进</b>：没有沙箱/人工批准却进了 CANDIDATE；</li>
 *     <li><b>跳步</b>：DRAFT 直接到 STABLE；</li>
 *     <li><b>绕道发布</b>：从未 STABLE 过的版本靠「先停用再启用」变成 STABLE；</li>
 *     <li><b>发布了但谁都看不见</b>：受限通道却没有启用中的绑定。</li>
 * </ol>
 *
 * <p>纯 Mockito：不加载 Spring 上下文，也不碰数据库。由于实现里用了
 * {@code LambdaUpdateWrapper}，需要先在 {@code @BeforeAll} 注册实体→列的 lambda 缓存，
 * 否则构造 wrapper 会抛 {@code can not find lambda cache}（看起来像业务逻辑坏了）。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigAgentRegistryServiceImplTest {

    private static final long VERSION_ID = 7401L;

    private static final long PACKAGE_VERSION_ID = 7601L;

    private static final long PACKAGE_ID = 7701L;

    private static final String PACKAGE_CHECKSUM = "a".repeat(64);

    /**
     * 一份合规的 Package Manifest（§6.1 最小字段集全齐，且与下面桩的包记录一致）
     */
    private static final String MANIFEST = """
        {"package_code":"vision-planning-skill","name":"视觉规划 Skill","publisher":"design-center",
         "version":"1.0.0","license":"Apache-2.0","checksum":"%s","package_type":"SKILL",
         "capabilities":["CREATIVE_PLANNING"],"scenario_codes":["CREATIVE_DRAFT"],
         "input_schema":{"type":"object"},"output_schema":{"type":"object"},
         "min_platform_version":"6.0.0","dependencies":[],"required_tools":[],
         "forbidden_tools":["shell","ssh","db-direct","docker-socket"],"roles":["aig_viewer"],
         "data_level":"INTERNAL","network_access":"NONE","golden_cases":["case-1"],
         "version_notes":"v1","upgrade_policy":"IN_PLACE","rollback_policy":"PREVIOUS_STABLE"}
        """.formatted(PACKAGE_CHECKSUM);

    private AigAgentVersionMapper agentVersionMapper;
    private AigSkillVersionMapper skillVersionMapper;
    private AigPackageVersionMapper packageVersionMapper;
    private AigReleaseEventMapper releaseEventMapper;
    private AigAgentBindingMapper bindingMapper;
    private AigPackageMapper packageMapper;
    private IAigEvaluationService evaluationService;
    private IAigCanaryEvidenceService canaryEvidenceService;
    private IAigSandboxRunService sandboxRunService;
    private AigAgentRegistryServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, AigAgentVersion.class);
        TableInfoHelper.initTableInfo(assistant, AigReleaseEvent.class);
        TableInfoHelper.initTableInfo(assistant, AigAgentBinding.class);
        TableInfoHelper.initTableInfo(assistant, AigPackageVersion.class);
        TableInfoHelper.initTableInfo(assistant, AigPackage.class);
    }

    @BeforeEach
    void setUp() {
        agentVersionMapper = mock(AigAgentVersionMapper.class);
        skillVersionMapper = mock(AigSkillVersionMapper.class);
        packageVersionMapper = mock(AigPackageVersionMapper.class);
        releaseEventMapper = mock(AigReleaseEventMapper.class);
        bindingMapper = mock(AigAgentBindingMapper.class);
        packageMapper = mock(AigPackageMapper.class);
        evaluationService = mock(IAigEvaluationService.class);
        canaryEvidenceService = mock(IAigCanaryEvidenceService.class);
        sandboxRunService = mock(IAigSandboxRunService.class);
        service = new AigAgentRegistryServiceImpl(agentVersionMapper, skillVersionMapper,
            packageVersionMapper, releaseEventMapper, bindingMapper, packageMapper,
            new AigPackageManifestValidator(JsonMapper.builder().build()), evaluationService,
            canaryEvidenceService, sandboxRunService);
        // 默认：黄金用例证据「已满足」（需要它的用例各自再覆盖）
        when(evaluationService.goldenCaseEvidence(any(), any()))
            .thenReturn(AigGoldenCaseEvidence.satisfied(List.of(), Map.of()));
        // 默认：灰度证据「已达标」。注意这个默认值是**真的走了一遍判定**得来的
        // （100 次调用、0 失败、0 严重错误），而不是凭空造一个 satisfied=true——
        // 否则这条桩会把判定逻辑的缺陷一起掩盖掉
        when(canaryEvidenceService.canaryEvidence(any(), any())).thenReturn(satisfiedCanary());
        // 默认：沙箱证据「已跑通」（同样是走真实判定得来的，见 satisfiedSandbox）
        when(sandboxRunService.sandboxRunEvidence(any(), any())).thenReturn(satisfiedSandbox());
        // 默认：条件更新命中 1 行、事件写入成功
        when(agentVersionMapper.update(isNull(), any())).thenReturn(1);
        when(packageVersionMapper.update(isNull(), any())).thenReturn(1);
        when(releaseEventMapper.insert(any(AigReleaseEvent.class))).thenReturn(1);
    }

    /**
     * 造一个「灰度达标」的证据：100 次调用、0 失败、0 严重错误。
     *
     * <p>刻意走真实的 {@link AigCanaryEvidence#evaluate}，而不是直接造一个
     * {@code satisfied=true}：桩与生产共用同一段判定，判定写错时测试不会替它遮掩。</p>
     *
     * @return 达标证据
     */
    private static AigCanaryEvidence satisfiedCanary() {
        LocalDateTime to = LocalDateTime.now();
        return AigCanaryEvidence.evaluate(to.minusHours(1), to, 100L, 0L, Map.of(),
            new AigCanaryEvidence.Thresholds(50, 0.05, 0));
    }

    /**
     * 造一个「沙箱跑通」的证据：退出码 0、未超时、无网。
     *
     * <p>与 {@link #satisfiedCanary()} 同因：走真实的 {@code evaluate}，
     * 不直接造 {@code satisfied=true}——否则判据写错时这条桩会替它遮掩。</p>
     *
     * @return 证据结论
     */
    private static AigSandboxRunEvidence satisfiedSandbox() {
        AigSandboxRun row = new AigSandboxRun();
        row.setSandboxRunId(1L);
        row.setJobId("job-1");
        row.setImageRef("nginx@sha256:" + "a".repeat(64));
        row.setExitCode(0);
        row.setTimedOut(false);
        row.setNetwork("none");
        row.setDurationMs(250L);
        row.setArtifactCount(2);
        row.setCreateTime(LocalDateTime.now());
        return AigSandboxRunEvidence.evaluate(row);
    }

    /**
     * 造一个处于指定状态/通道的 Agent 版本桩。
     *
     * @param status  发布状态
     * @param channel 发布通道
     */
    private void stubVersion(String status, String channel) {
        AigAgentVersion version = new AigAgentVersion();
        version.setAgentVersionId(VERSION_ID);
        version.setReleaseStatus(status);
        version.setReleaseChannel(channel);
        when(agentVersionMapper.selectById(VERSION_ID)).thenReturn(version);
    }

    /**
     * 造一个推进入参。
     *
     * @param from 期望的当前状态
     * @param to   目标状态
     * @param gates 已通过的门槛（可空）
     * @return 入参
     */
    private static AigReleaseAdvanceBo bo(String from, String to, List<String> gates) {
        AigReleaseAdvanceBo bo = new AigReleaseAdvanceBo();
        bo.setTargetType(AigReleaseTargetTypeEnum.AGENT_VERSION.getCode());
        bo.setTargetVersionId(VERSION_ID);
        bo.setExpectedStatus(from);
        bo.setToStatus(to);
        bo.setPassedGates(gates);
        bo.setOperatorId(7L);
        return bo;
    }

    @Test
    @DisplayName("主干：过 Manifest 校验即 DRAFT→VALIDATED，并写入校验时间与发布事件")
    void advancesOnGatePass() {
        stubVersion("DRAFT", "TESTING");

        AigReleaseStatusEnum result = service.advanceRelease(
            bo("DRAFT", "VALIDATED", List.of(AigReleaseGateEnum.MANIFEST_VALIDATION.getCode())));

        assertEquals(AigReleaseStatusEnum.VALIDATED, result);
        verify(agentVersionMapper).update(isNull(), any());
        ArgumentCaptor<AigReleaseEvent> captor = ArgumentCaptor.forClass(AigReleaseEvent.class);
        verify(releaseEventMapper).insert(captor.capture());
        AigReleaseEvent event = captor.getValue();
        assertEquals("DRAFT", event.getFromStatus());
        assertEquals("VALIDATED", event.getToStatus());
        assertEquals("MANIFEST_VALIDATION", event.getPassedGates());
        assertEquals(7L, event.getOperatorId());
        assertNotNull(event.getOperateTime());
    }

    @Test
    @DisplayName("缺门槛不放行：一个门槛都没过时报「还差…」，且绝不写库")
    void refusesWhenGateMissing() {
        stubVersion("SANDBOX_TESTED", "TESTING");

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.advanceRelease(bo("SANDBOX_TESTED", "CANDIDATE",
                List.of(AigReleaseGateEnum.GOLDEN_CASE.getCode()))));

        assertTrue(error.getMessage().contains("还差"), error.getMessage());
        assertTrue(error.getMessage().contains("HUMAN_APPROVAL"),
            "要指明还差哪一把，而不是笼统「不满足条件」：" + error.getMessage());
        verify(agentVersionMapper, never()).update(isNull(), any());
        verify(releaseEventMapper, never()).insert(any(AigReleaseEvent.class));
    }

    @Test
    @DisplayName("沙箱之后必须两把都过：黄金用例 + 三方人工批准，缺一不可")
    void requiresBothGatesBeforeCandidate() {
        stubVersion("SANDBOX_TESTED", "GENERAL");

        // 只过一把 → 拒
        assertThrows(ServiceException.class, () -> service.advanceRelease(bo("SANDBOX_TESTED",
            "CANDIDATE", List.of(AigReleaseGateEnum.GOLDEN_CASE.getCode()))));
        // 两把都过 → 放行
        assertEquals(AigReleaseStatusEnum.CANDIDATE, service.advanceRelease(bo("SANDBOX_TESTED",
            "CANDIDATE", List.of(AigReleaseGateEnum.GOLDEN_CASE.getCode(),
                AigReleaseGateEnum.HUMAN_APPROVAL.getCode()))));
    }

    @Test
    @DisplayName("不许跳步：DRAFT 直接到 STABLE 被拒，且提示「下一步只能是 VALIDATED」")
    void refusesSkippingSteps() {
        stubVersion("DRAFT", "TESTING");

        ServiceException error = assertThrows(ServiceException.class, () -> service.advanceRelease(
            bo("DRAFT", "STABLE", List.of(AigReleaseGateEnum.MANIFEST_VALIDATION.getCode(),
                AigReleaseGateEnum.SANDBOX_RUN.getCode(), AigReleaseGateEnum.GOLDEN_CASE.getCode(),
                AigReleaseGateEnum.HUMAN_APPROVAL.getCode(), AigReleaseGateEnum.CANARY.getCode()))));

        assertTrue(error.getMessage().contains("门槛顺序不对"), error.getMessage());
        assertTrue(error.getMessage().contains("VALIDATED"), "要指出真正的下一步：" + error.getMessage());
        verify(agentVersionMapper, never()).update(isNull(), any());
    }

    @Test
    @DisplayName("视图过期：expectedStatus 与库中不一致时报可读原因，而不是「影响 0 行」")
    void refusesWhenCallerViewIsStale() {
        stubVersion("CANDIDATE", "GENERAL");

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.advanceRelease(bo("SANDBOX_TESTED", "CANDIDATE",
                List.of(AigReleaseGateEnum.HUMAN_APPROVAL.getCode()))));

        assertTrue(error.getMessage().contains("视图已过期"), error.getMessage());
        verify(agentVersionMapper, never()).update(isNull(), any());
    }

    @Test
    @DisplayName("并发冲突：条件更新影响 0 行时报错且不追加事件（宁可失败，不可覆盖）")
    void refusesOnConcurrentUpdate() {
        stubVersion("CANDIDATE", "GENERAL");
        when(agentVersionMapper.update(isNull(), any())).thenReturn(0);

        ServiceException error = assertThrows(ServiceException.class, () -> service.advanceRelease(
            bo("CANDIDATE", "STABLE", List.of(AigReleaseGateEnum.CANARY.getCode()))));

        assertTrue(error.getMessage().contains("并发"), error.getMessage());
        verify(releaseEventMapper, never()).insert(any(AigReleaseEvent.class));
    }

    @Test
    @DisplayName("受限通道发布必须有启用中的绑定，否则「已发布」与「谁都看不见」同时成立")
    void limitedChannelNeedsBinding() {
        stubVersion("SANDBOX_TESTED", "BRAND");
        when(bindingMapper.selectCount(any())).thenReturn(0L);

        ServiceException error = assertThrows(ServiceException.class, () -> service.advanceRelease(
            bo("SANDBOX_TESTED", "CANDIDATE", List.of(AigReleaseGateEnum.GOLDEN_CASE.getCode(),
                AigReleaseGateEnum.HUMAN_APPROVAL.getCode()))));

        assertTrue(error.getMessage().contains("绑定"), error.getMessage());
        assertTrue(error.getMessage().contains("看不见"), "要说清后果，而不是「校验不通过」：" + error.getMessage());
        verify(agentVersionMapper, never()).update(isNull(), any());
    }

    @Test
    @DisplayName("受限通道有绑定则放行；通用通道（GENERAL）不需要绑定")
    void bindingSatisfiesLimitedChannelAndGeneralNeedsNone() {
        stubVersion("SANDBOX_TESTED", "BRAND");
        when(bindingMapper.selectCount(any())).thenReturn(1L);
        assertEquals(AigReleaseStatusEnum.CANDIDATE, service.advanceRelease(
            bo("SANDBOX_TESTED", "CANDIDATE", List.of(AigReleaseGateEnum.GOLDEN_CASE.getCode(),
                AigReleaseGateEnum.HUMAN_APPROVAL.getCode()))));

        stubVersion("CANDIDATE", "GENERAL");
        assertEquals(AigReleaseStatusEnum.STABLE, service.advanceRelease(
            bo("CANDIDATE", "STABLE", List.of(AigReleaseGateEnum.CANARY.getCode()))));
    }

    @Test
    @DisplayName("★ 声明「灰度已达标」而库里没有证据 → 拒绝推进，且一行都不改")
    void refusesCanaryWithoutEvidence() {
        stubVersion("CANDIDATE", "GENERAL");
        LocalDateTime to = LocalDateTime.now();
        // 三项判据同时不满足：样本不够、失败率超标、还有 1 个严重错误
        when(canaryEvidenceService.canaryEvidence(any(), any())).thenReturn(AigCanaryEvidence.evaluate(
            to.minusHours(1), to, 40L, 5L, Map.of("POLICY_DENIED", 1L),
            new AigCanaryEvidence.Thresholds(50, 0.05, 0)));

        ServiceException error = assertThrows(ServiceException.class, () -> service.advanceRelease(
            bo("CANDIDATE", "STABLE", List.of(AigReleaseGateEnum.CANARY.getCode()))));

        assertTrue(error.getMessage().contains("灰度"), error.getMessage());
        assertTrue(error.getMessage().contains("调用次数不足"), "要说清差在哪：" + error.getMessage());
        assertTrue(error.getMessage().contains("失败率超标"), "三项都要报出来：" + error.getMessage());
        assertTrue(error.getMessage().contains("出现严重错误"), "严重错误也要报出来：" + error.getMessage());
        assertTrue(error.getMessage().contains("POLICY_DENIED"),
            "严重错误要给出分类明细，否则运维不知道是策略拒绝还是鉴权失败：" + error.getMessage());
        verify(agentVersionMapper, never()).update(isNull(), any());
        verify(releaseEventMapper, never()).insert(any(AigReleaseEvent.class));
    }

    @Test
    @DisplayName("不声明 CANARY 门槛时不去查灰度证据（只有声明了才要求拿得出证据）")
    void canaryEvidenceNotQueriedWhenGateNotClaimed() {
        stubVersion("DRAFT", "GENERAL");

        service.advanceRelease(bo("DRAFT", "VALIDATED",
            List.of(AigReleaseGateEnum.MANIFEST_VALIDATION.getCode())));

        verify(canaryEvidenceService, never()).canaryEvidence(any(), any());
    }

    @Test
    @DisplayName("不声明 SANDBOX_RUN 门槛时不去查沙箱证据（只有声明了才要求拿得出证据）")
    void sandboxEvidenceNotQueriedWhenGateNotClaimed() {
        stubVersion("DRAFT", "GENERAL");

        service.advanceRelease(bo("DRAFT", "VALIDATED",
            List.of(AigReleaseGateEnum.MANIFEST_VALIDATION.getCode())));

        verify(sandboxRunService, never()).sandboxRunEvidence(any(), any());
    }

    @Test
    @DisplayName("声明「沙箱已跑通」而库里没有证据 → 拒绝推进（这道门槛原先只看声明）")
    void sandboxRunGateNeedsEvidence() {
        stubVersion("VALIDATED", "GENERAL");
        when(sandboxRunService.sandboxRunEvidence(any(), any()))
            .thenReturn(AigSandboxRunEvidence.unavailable("该版本没有任何沙箱运行记录"));

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.advanceRelease(bo("VALIDATED", "SANDBOX_TESTED",
                List.of(AigReleaseGateEnum.SANDBOX_RUN.getCode()))));

        assertTrue(error.getMessage().contains("沙箱"), error.getMessage());
        assertTrue(error.getMessage().contains("没有任何沙箱运行记录"), error.getMessage());
        verify(agentVersionMapper, never()).update(isNull(), any());
    }

    @Test
    @DisplayName("最近一次沙箱运行没跑通（超时/有网/非零退出）→ 拒绝推进，并说明实测数字")
    void sandboxRunGateRejectsFailedLatestRun() {
        stubVersion("VALIDATED", "GENERAL");
        // 走真实判定：超时被杀 + 允许了出网，两项都不满足
        AigSandboxRun failed = new AigSandboxRun();
        failed.setSandboxRunId(9L);
        failed.setJobId("job-9");
        failed.setImageRef("nginx@sha256:" + "b".repeat(64));
        failed.setExitCode(137);
        failed.setTimedOut(true);
        failed.setNetwork("bridge");
        failed.setDurationMs(60_000L);
        failed.setArtifactCount(0);
        failed.setCreateTime(LocalDateTime.now());
        when(sandboxRunService.sandboxRunEvidence(any(), any()))
            .thenReturn(AigSandboxRunEvidence.evaluate(failed));

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.advanceRelease(bo("VALIDATED", "SANDBOX_TESTED",
                List.of(AigReleaseGateEnum.SANDBOX_RUN.getCode()))));

        assertTrue(error.getMessage().contains("超时"), error.getMessage());
        assertTrue(error.getMessage().contains("bridge"), error.getMessage());
        assertTrue(error.getMessage().contains("137"), error.getMessage());
        verify(agentVersionMapper, never()).update(isNull(), any());
    }

    @Test
    @DisplayName("绕道发布被堵死：从未 STABLE 过的版本不能靠「先停用再启用」变成 STABLE")
    void reEnableNeedsStableHistory() {
        stubVersion("DISABLED", "GENERAL");
        when(releaseEventMapper.selectCount(any())).thenReturn(0L);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.advanceRelease(bo("DISABLED", "STABLE", List.of())));

        assertTrue(error.getMessage().contains("从未发布过"), error.getMessage());
        verify(agentVersionMapper, never()).update(isNull(), any());

        // 有历史（账本里存在 to_status=STABLE 的事件）→ 放行
        when(releaseEventMapper.selectCount(any())).thenReturn(1L);
        assertEquals(AigReleaseStatusEnum.STABLE, service.advanceRelease(bo("DISABLED", "STABLE", List.of())));
    }

    @Test
    @DisplayName("运维动作（停用/归档）不需要门槛，但必须走状态机允许的边")
    void operationalTransitionsNeedNoGates() {
        stubVersion("DRAFT", "TESTING");
        assertEquals(AigReleaseStatusEnum.DISABLED, service.advanceRelease(bo("DRAFT", "DISABLED", List.of())));
        assertEquals(AigReleaseStatusEnum.ARCHIVED, service.advanceRelease(bo("DRAFT", "ARCHIVED", List.of())));

        // ARCHIVED 是终态：不能从它再迁出去
        stubVersion("ARCHIVED", "TESTING");
        ServiceException error = assertThrows(ServiceException.class,
            () -> service.advanceRelease(bo("ARCHIVED", "STABLE", List.of())));
        assertTrue(error.getMessage().contains("终态"), error.getMessage());
    }

    @Test
    @DisplayName("未知门槛编码报错而不是静默忽略：拼错的名字不该被解读成「没通过」")
    void unknownGateCodeIsRejected() {
        stubVersion("DRAFT", "TESTING");

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.advanceRelease(bo("DRAFT", "VALIDATED", List.of("GOLDEN_CAS"))));

        assertTrue(error.getMessage().contains("未知的门槛编码"), error.getMessage());
    }

    @Test
    @DisplayName("missingGates 给出差额，供页面显示「还差哪几步」")
    void missingGatesReportsGap() {
        stubVersion("SANDBOX_TESTED", "TESTING");

        Set<AigReleaseGateEnum> missing = service.missingGates(
            AigReleaseTargetTypeEnum.AGENT_VERSION.getCode(), VERSION_ID,
            Set.of(AigReleaseGateEnum.GOLDEN_CASE));

        assertEquals(Set.of(AigReleaseGateEnum.HUMAN_APPROVAL), missing);
        assertTrue(service.missingGates(AigReleaseTargetTypeEnum.AGENT_VERSION.getCode(), VERSION_ID,
            Set.of(AigReleaseGateEnum.values())).isEmpty());
    }

    @Test
    @DisplayName("新增绑定：留空取版本通道；填了必须与版本一致（通道说 A、绑定说 B 会自相矛盾）")
    void addBindingKeepsChannelConsistent() {
        stubVersion("DRAFT", "BRAND");
        when(bindingMapper.insert(any(AigAgentBinding.class))).thenAnswer(invocation -> {
            invocation.<AigAgentBinding>getArgument(0).setBindingId(7501L);
            return 1;
        });

        AigAgentBindingBo ok = new AigAgentBindingBo();
        ok.setAgentVersionId(VERSION_ID);
        ok.setBrandId(88L);
        // 留空 → 取版本通道
        assertEquals(7501L, service.addBinding(ok));

        AigAgentBindingBo mismatch = new AigAgentBindingBo();
        mismatch.setAgentVersionId(VERSION_ID);
        mismatch.setReleaseChannel("GENERAL");
        ServiceException error = assertThrows(ServiceException.class, () -> service.addBinding(mismatch));
        assertTrue(error.getMessage().contains("不一致"), error.getMessage());

        AigAgentBindingBo notFound = new AigAgentBindingBo();
        notFound.setAgentVersionId(99999L);
        assertThrows(ServiceException.class, () -> service.addBinding(notFound));
    }

    @Test
    @DisplayName("对象类型/状态/版本ID非法时给出可读错误，且列出可选值")
    void rejectsInvalidInput() {
        AigReleaseAdvanceBo badType = bo("DRAFT", "VALIDATED", List.of());
        badType.setTargetType("NOPE");
        assertTrue(assertThrows(ServiceException.class, () -> service.advanceRelease(badType))
            .getMessage().contains("可选"));

        AigReleaseAdvanceBo badStatus = bo("DRAFT", "NOPE", List.of());
        assertTrue(assertThrows(ServiceException.class, () -> service.advanceRelease(badStatus))
            .getMessage().contains("未知的目标状态"));

        AigReleaseAdvanceBo noId = bo("DRAFT", "VALIDATED", List.of());
        noId.setTargetVersionId(null);
        assertThrows(ServiceException.class, () -> service.advanceRelease(noId));

        // 版本不存在
        when(agentVersionMapper.selectById(99999L)).thenReturn(null);
        AigReleaseAdvanceBo missing = bo("DRAFT", "VALIDATED", List.of());
        missing.setTargetVersionId(99999L);
        assertTrue(assertThrows(ServiceException.class, () -> service.advanceRelease(missing))
            .getMessage().contains("不存在"));
    }

    @Test
    @DisplayName("wasEverStable 以发布事件账本为准；空入参返回 false（不抛异常）")
    void wasEverStableReadsLedger() {
        when(releaseEventMapper.selectCount(any())).thenReturn(1L);
        assertTrue(service.wasEverStable(AigReleaseTargetTypeEnum.AGENT_VERSION.getCode(), VERSION_ID));
        when(releaseEventMapper.selectCount(any())).thenReturn(0L);
        assertFalse(service.wasEverStable(AigReleaseTargetTypeEnum.AGENT_VERSION.getCode(), VERSION_ID));
        assertFalse(service.wasEverStable(null, null));
        verify(releaseEventMapper, times(2)).selectCount(any());
    }

    /**
     * 造一个 Package 版本桩，以及与之一致的包记录桩。
     *
     * @param status     发布状态
     * @param scanResult 扫描结论（模拟历史扫描；null = 未扫描）
     * @param publisher  包记录的发布方（换个值就能造出「Manifest 与包记录不一致」）
     */
    private void stubPackageVersion(String status, String scanResult, String publisher) {
        AigPackageVersion version = new AigPackageVersion();
        version.setPackageVersionId(PACKAGE_VERSION_ID);
        version.setPackageId(PACKAGE_ID);
        version.setVersion("1.0.0");
        version.setManifestJson(MANIFEST);
        version.setManifestHash(AigPackageManifestValidator.manifestHash(MANIFEST));
        version.setReleaseStatus(status);
        version.setReleaseChannel("TESTING");
        version.setScanResult(scanResult);
        when(packageVersionMapper.selectById(PACKAGE_VERSION_ID)).thenReturn(version);

        AigPackage pkg = new AigPackage();
        pkg.setPackageId(PACKAGE_ID);
        pkg.setPackageCode("vision-planning-skill");
        pkg.setPublisher(publisher);
        pkg.setLicenseCode("Apache-2.0");
        pkg.setChecksum(PACKAGE_CHECKSUM);
        pkg.setPackageType("SKILL");
        when(packageMapper.selectById(PACKAGE_ID)).thenReturn(pkg);
    }

    /**
     * 造一个 Package 版本的推进入参。
     *
     * @param from  期望的当前状态
     * @param to    目标状态
     * @param gates 已通过的门槛（可空）
     * @return 入参
     */
    private static AigReleaseAdvanceBo packageBo(String from, String to, List<String> gates) {
        AigReleaseAdvanceBo bo = new AigReleaseAdvanceBo();
        bo.setTargetType(AigReleaseTargetTypeEnum.PACKAGE_VERSION.getCode());
        bo.setTargetVersionId(PACKAGE_VERSION_ID);
        bo.setExpectedStatus(from);
        bo.setToStatus(to);
        bo.setPassedGates(gates);
        bo.setOperatorId(7L);
        return bo;
    }

    @Test
    @DisplayName("Manifest 扫描：DRAFT 版本落库 scan_result/scan_detail，且刻意不写 manifest_hash")
    void scanStoredManifestRecordsVerdict() {
        stubPackageVersion("DRAFT", null, "design-center");
        AtomicReference<Wrapper<AigPackageVersion>> written = new AtomicReference<>();
        when(packageVersionMapper.update(isNull(), any())).thenAnswer(invocation -> {
            written.set(invocation.getArgument(1));
            return 1;
        });

        AigManifestScanResult result = service.scanStoredManifest(PACKAGE_VERSION_ID);

        assertTrue(result.isPass(), result.getDetail());
        assertEquals(AigManifestScanResult.PASS, result.scanResult());
        assertEquals(AigPackageManifestValidator.manifestHash(MANIFEST), result.getManifestHash());
        assertNotNull(written.get());
        LambdaUpdateWrapper<AigPackageVersion> wrapper =
            (LambdaUpdateWrapper<AigPackageVersion>) written.get();
        assertTrue(wrapper.getSqlSet().contains("scan_result"), wrapper.getSqlSet());
        assertTrue(wrapper.getSqlSet().contains("scan_detail"), wrapper.getSqlSet());
        assertTrue(wrapper.getParamNameValuePairs().containsValue("PASS"));
        // 条件更新必须带「当前状态是 DRAFT」：否则会覆盖别人已经推进过的结论
        assertTrue(wrapper.getSqlSegment().contains("release_status"), wrapper.getSqlSegment());
        // 刻意不写 manifest_hash：原文对不上时结论是拒绝，顺手改哈希会抹掉「原文被动过」这件事
        assertFalse(wrapper.getSqlSet().contains("manifest_hash"), wrapper.getSqlSet());
    }

    @Test
    @DisplayName("Manifest 扫描只对 DRAFT：已推进的版本拒绝重算（历史结论不许被改写）")
    void scanStoredManifestRejectsNonDraft() {
        stubPackageVersion("VALIDATED", "PASS", "design-center");

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.scanStoredManifest(PACKAGE_VERSION_ID));

        assertTrue(error.getMessage().contains("只对 DRAFT"), error.getMessage());
        verify(packageVersionMapper, never()).update(isNull(), any());
    }

    @Test
    @DisplayName("Manifest 扫描落库带条件：影响 0 行时报并发冲突，不覆盖他人结论")
    void scanStoredManifestReportsConflict() {
        stubPackageVersion("DRAFT", null, "design-center");
        when(packageVersionMapper.update(isNull(), any())).thenReturn(0);

        ServiceException error = assertThrows(ServiceException.class,
            () -> service.scanStoredManifest(PACKAGE_VERSION_ID));

        assertTrue(error.getMessage().contains("并发"), error.getMessage());
    }

    @Test
    @DisplayName("扫描会用包记录交叉核对：包记录说别人发布的，Manifest 就不可信（§6.2-4）")
    void scanStoredManifestCrossChecksPackageRecord() {
        stubPackageVersion("DRAFT", null, "someone-else");

        AigManifestScanResult result = service.scanStoredManifest(PACKAGE_VERSION_ID);

        assertFalse(result.isPass());
        assertTrue(result.getHitRules().contains(AigPackageRejectRuleEnum.UNCLEAR_PROVENANCE),
            result.getDetail());
        assertTrue(result.getDetail().contains("someone-else"), result.getDetail());
    }

    @Test
    @DisplayName("Manifest 扫描入参：空 ID / 版本不存在 / 包记录不存在都给可读错误")
    void scanStoredManifestValidatesInput() {
        assertTrue(assertThrows(ServiceException.class, () -> service.scanStoredManifest(null))
            .getMessage().contains("不能为空"));

        when(packageVersionMapper.selectById(99999L)).thenReturn(null);
        assertTrue(assertThrows(ServiceException.class, () -> service.scanStoredManifest(99999L))
            .getMessage().contains("不存在"));

        stubPackageVersion("DRAFT", null, "design-center");
        when(packageMapper.selectById(PACKAGE_ID)).thenReturn(null);
        assertTrue(assertThrows(ServiceException.class,
            () -> service.scanStoredManifest(PACKAGE_VERSION_ID)).getMessage().contains("主记录不存在"));
    }

    @Test
    @DisplayName("「Manifest 校验」这道门槛吃库里的证据：scan_result 不是 PASS 就不许声明已通过")
    void manifestGateNeedsDatabaseEvidence() {
        stubPackageVersion("DRAFT", null, "design-center");
        ServiceException notScanned = assertThrows(ServiceException.class, () -> service.advanceRelease(
            packageBo("DRAFT", "VALIDATED",
                List.of(AigReleaseGateEnum.MANIFEST_VALIDATION.getCode()))));
        assertTrue(notScanned.getMessage().contains("没有证据"), notScanned.getMessage());
        assertTrue(notScanned.getMessage().contains("未扫描"), notScanned.getMessage());

        stubPackageVersion("DRAFT", AigManifestScanResult.REJECT, "design-center");
        ServiceException rejected = assertThrows(ServiceException.class, () -> service.advanceRelease(
            packageBo("DRAFT", "VALIDATED",
                List.of(AigReleaseGateEnum.MANIFEST_VALIDATION.getCode()))));
        assertTrue(rejected.getMessage().contains("REJECT"), rejected.getMessage());
        verify(packageVersionMapper, never()).update(isNull(), any());

        // 有证据则放行，并照常追加发布事件
        stubPackageVersion("DRAFT", AigManifestScanResult.PASS, "design-center");
        assertEquals(AigReleaseStatusEnum.VALIDATED, service.advanceRelease(
            packageBo("DRAFT", "VALIDATED",
                List.of(AigReleaseGateEnum.MANIFEST_VALIDATION.getCode()))));
        verify(packageVersionMapper).update(isNull(), any());
        verify(releaseEventMapper).insert(any(AigReleaseEvent.class));
    }

    @Test
    @DisplayName("「黄金用例」这道门槛也吃评测账本：没有通过的评测就不许声明已通过")
    void goldenCaseGateNeedsEvaluationEvidence() {
        stubVersion("SANDBOX_TESTED", "GENERAL");

        // 没声明这把门槛 → 报「还差」，且压根不去查评测（被检查的是「声明了的门槛」）
        ServiceException missing = assertThrows(ServiceException.class, () -> service.advanceRelease(
            bo("SANDBOX_TESTED", "CANDIDATE", List.of(AigReleaseGateEnum.HUMAN_APPROVAL.getCode()))));
        assertTrue(missing.getMessage().contains("还差"), missing.getMessage());
        verify(evaluationService, never()).goldenCaseEvidence(any(), any());

        // 声明了 → 必须拿得出证据
        when(evaluationService.goldenCaseEvidence(any(), any())).thenReturn(
            AigGoldenCaseEvidence.blocked(List.of("case-a"), Map.of("case-a", "NO_RUN"),
                "用例 case-a 还没有任何评测运行"));
        ServiceException error = assertThrows(ServiceException.class, () -> service.advanceRelease(
            bo("SANDBOX_TESTED", "CANDIDATE", List.of(AigReleaseGateEnum.GOLDEN_CASE.getCode(),
                AigReleaseGateEnum.HUMAN_APPROVAL.getCode()))));
        assertTrue(error.getMessage().contains("没有证据"), error.getMessage());
        assertTrue(error.getMessage().contains("case-a"), "要说清是哪条用例挡住了：" + error.getMessage());
        verify(agentVersionMapper, never()).update(isNull(), any());
    }

}
