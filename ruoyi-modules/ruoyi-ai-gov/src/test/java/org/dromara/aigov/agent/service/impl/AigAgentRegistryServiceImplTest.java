package org.dromara.aigov.agent.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.aigov.agent.domain.AigAgentBinding;
import org.dromara.aigov.agent.domain.AigAgentVersion;
import org.dromara.aigov.agent.domain.AigReleaseEvent;
import org.dromara.aigov.agent.domain.bo.AigAgentBindingBo;
import org.dromara.aigov.agent.domain.bo.AigReleaseAdvanceBo;
import org.dromara.aigov.agent.enums.AigReleaseGateEnum;
import org.dromara.aigov.agent.enums.AigReleaseStatusEnum;
import org.dromara.aigov.agent.enums.AigReleaseTargetTypeEnum;
import org.dromara.aigov.agent.mapper.AigAgentBindingMapper;
import org.dromara.aigov.agent.mapper.AigAgentVersionMapper;
import org.dromara.aigov.agent.mapper.AigPackageVersionMapper;
import org.dromara.aigov.agent.mapper.AigReleaseEventMapper;
import org.dromara.aigov.agent.mapper.AigSkillVersionMapper;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;

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

    private AigAgentVersionMapper agentVersionMapper;
    private AigSkillVersionMapper skillVersionMapper;
    private AigPackageVersionMapper packageVersionMapper;
    private AigReleaseEventMapper releaseEventMapper;
    private AigAgentBindingMapper bindingMapper;
    private AigAgentRegistryServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, AigAgentVersion.class);
        TableInfoHelper.initTableInfo(assistant, AigReleaseEvent.class);
        TableInfoHelper.initTableInfo(assistant, AigAgentBinding.class);
    }

    @BeforeEach
    void setUp() {
        agentVersionMapper = mock(AigAgentVersionMapper.class);
        skillVersionMapper = mock(AigSkillVersionMapper.class);
        packageVersionMapper = mock(AigPackageVersionMapper.class);
        releaseEventMapper = mock(AigReleaseEventMapper.class);
        bindingMapper = mock(AigAgentBindingMapper.class);
        service = new AigAgentRegistryServiceImpl(agentVersionMapper, skillVersionMapper,
            packageVersionMapper, releaseEventMapper, bindingMapper);
        // 默认：条件更新命中 1 行、事件写入成功
        when(agentVersionMapper.update(isNull(), any())).thenReturn(1);
        when(releaseEventMapper.insert(any(AigReleaseEvent.class))).thenReturn(1);
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

}
