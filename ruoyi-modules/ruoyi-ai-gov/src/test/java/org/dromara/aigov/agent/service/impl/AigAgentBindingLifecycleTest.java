package org.dromara.aigov.agent.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.aigov.agent.domain.AigAgentBinding;
import org.dromara.aigov.agent.domain.AigAgentVersion;
import org.dromara.aigov.agent.enums.AigReleaseChannelEnum;
import org.dromara.aigov.agent.manifest.AigPackageManifestValidator;
import org.dromara.aigov.agent.mapper.AigAgentBindingMapper;
import org.dromara.aigov.agent.mapper.AigAgentResourceBindingMapper;
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
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Agent 版本绑定生命周期测试（设计 D03）。
 *
 * <p>要钉死的三条：</p>
 * <ol>
 *     <li><b>撤销是逻辑删除</b>，且对"不存在/已撤销"给出可读拒绝；</li>
 *     <li><b>执行前复查授权</b>：受限通道必须有启用中且在有效期内的绑定（有效期口径由
 *         {@code countActiveBindings} 统一承担），非受限通道不看绑定；</li>
 *     <li><b>裸能力调用</b>（无版本）不受版本授权约束。</li>
 * </ol>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigAgentBindingLifecycleTest {

    private static final long VERSION_ID = 9901L;

    private AigAgentVersionMapper agentVersionMapper;
    private AigAgentBindingMapper bindingMapper;
    private AigAgentRegistryServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, AigAgentVersion.class);
        TableInfoHelper.initTableInfo(assistant, AigAgentBinding.class);
    }

    @BeforeEach
    void setUp() {
        agentVersionMapper = mock(AigAgentVersionMapper.class);
        bindingMapper = mock(AigAgentBindingMapper.class);
        service = new AigAgentRegistryServiceImpl(agentVersionMapper,
            mock(AigSkillVersionMapper.class), mock(AigPackageVersionMapper.class),
            mock(AigReleaseEventMapper.class), bindingMapper, mock(AigAgentResourceBindingMapper.class),
            mock(AigPackageMapper.class), new AigPackageManifestValidator(JsonMapper.builder().build()),
            mock(IAigEvaluationService.class), mock(IAigCanaryEvidenceService.class),
            mock(IAigSandboxRunService.class));
    }

    @Test
    @DisplayName("★ D03：撤销绑定走逻辑删除；对不存在/已撤销的绑定给出可读拒绝")
    void revokeBindingIsLogicalAndGuardsMissing() {
        AigAgentBinding binding = new AigAgentBinding();
        binding.setBindingId(11L);
        binding.setAgentVersionId(VERSION_ID);
        when(bindingMapper.selectById(11L)).thenReturn(binding);

        service.revokeBinding(11L, "灰度结束");

        verify(bindingMapper).deleteById(11L);

        when(bindingMapper.selectById(12L)).thenReturn(null);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.revokeBinding(12L, null));
        assertTrue(ex.getMessage().contains("不存在或已撤销"), ex.getMessage());
        verify(bindingMapper, never()).deleteById(12L);
    }

    @Test
    @DisplayName("★ D03：受限通道执行前复查绑定数，无有效绑定即拒绝")
    void limitedChannelRequiresActiveBinding() {
        when(agentVersionMapper.selectById(VERSION_ID))
            .thenReturn(version(AigReleaseChannelEnum.BRAND.getCode()));
        when(bindingMapper.selectCount(any())).thenReturn(0L);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.assertExecutionAuthorized(VERSION_ID));
        assertTrue(ex.getMessage().contains("当前范围未授权"), ex.getMessage());
    }

    @Test
    @DisplayName("受限通道有有效绑定时放行；非受限通道不查绑定")
    void activeBindingPassesAndGeneralChannelSkipsCheck() {
        when(agentVersionMapper.selectById(VERSION_ID))
            .thenReturn(version(AigReleaseChannelEnum.BRAND.getCode()));
        when(bindingMapper.selectCount(any())).thenReturn(1L);

        assertDoesNotThrow(() -> service.assertExecutionAuthorized(VERSION_ID));

        when(agentVersionMapper.selectById(VERSION_ID))
            .thenReturn(version(AigReleaseChannelEnum.GENERAL.getCode()));
        assertDoesNotThrow(() -> service.assertExecutionAuthorized(VERSION_ID));

        // 只在受限通道那一次查了绑定，GENERAL 不再查
        verify(bindingMapper, times(1)).selectCount(any());
    }

    @Test
    @DisplayName("裸能力调用（无版本）不受版本授权约束")
    void bareCapabilityCallSkipsVersionAuthorization() {
        assertDoesNotThrow(() -> service.assertExecutionAuthorized(null));
        verify(agentVersionMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("版本不存在 → 明确报错")
    void missingVersionThrows() {
        when(agentVersionMapper.selectById(VERSION_ID)).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.assertExecutionAuthorized(VERSION_ID));
        assertTrue(ex.getMessage().contains("不存在"), ex.getMessage());
    }

    private static AigAgentVersion version(String channel) {
        AigAgentVersion version = new AigAgentVersion();
        version.setAgentVersionId(VERSION_ID);
        version.setReleaseChannel(channel);
        return version;
    }

}
