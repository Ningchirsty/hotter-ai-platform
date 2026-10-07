package org.dromara.aigov.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.aigov.domain.AigCapability;
import org.dromara.aigov.domain.AigRouteScenarioBinding;
import org.dromara.aigov.domain.bo.AigRouteScenarioBindingBo;
import org.dromara.aigov.domain.vo.AigModelProviderVo;
import org.dromara.aigov.domain.vo.AigRouteScenarioBindingVo;
import org.dromara.aigov.mapper.AigCapabilityMapper;
import org.dromara.aigov.mapper.AigModelConfigMapper;
import org.dromara.aigov.mapper.AigRouteScenarioBindingMapper;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 场景强制绑定服务的守卫行为测试。
 *
 * <p><b>为什么这些守卫值得单独测</b>：绑定表的语义是「钉死供应商」。配错时它不会报错，
 * 只会让路由<b>少选几个候选</b>——所以三类错误都必须在这里就拦下：</p>
 * <ul>
 *     <li>能力编码写错：绑定永不生效，而配置的人以为生效了，路由继续自由选；</li>
 *     <li>供应商不存在：同上，且查不出是哪一步没用上；</li>
 *     <li>重复绑定：表里有两条一样的行，删掉一条以为放开了，其实还钉着。</li>
 * </ul>
 *
 * <p>纯 Mockito：不加载 Spring 上下文，也不碰真实数据库。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigRouteScenarioBindingServiceImplTest {

    private static final String CAPABILITY = "image_generation";
    private static final long PROVIDER_ID = 1001L;

    private AigRouteScenarioBindingMapper bindingMapper;
    private AigCapabilityMapper capabilityMapper;
    private AigModelConfigMapper modelConfigMapper;
    private AigRouteScenarioBindingServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        // 服务内用 LambdaQueryWrapper（依赖实体→列的 lambda 缓存）；纯单测没有 Spring，
        // 缓存为空会抛「can not find lambda cache」，必须显式初始化。
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, AigRouteScenarioBinding.class);
        TableInfoHelper.initTableInfo(assistant, AigCapability.class);
    }

    @BeforeEach
    void setUp() {
        bindingMapper = mock(AigRouteScenarioBindingMapper.class);
        capabilityMapper = mock(AigCapabilityMapper.class);
        modelConfigMapper = mock(AigModelConfigMapper.class);
        service = new AigRouteScenarioBindingServiceImpl(bindingMapper, capabilityMapper, modelConfigMapper);
    }

    private static AigRouteScenarioBindingBo bo(String scenarioCode, String capabilityCode, Long providerId) {
        AigRouteScenarioBindingBo bo = new AigRouteScenarioBindingBo();
        bo.setScenarioCode(scenarioCode);
        bo.setCapabilityCode(capabilityCode);
        bo.setProviderId(providerId);
        return bo;
    }

    /**
     * 放行能力与供应商的存在性校验。
     */
    private void stubReferencesExist() {
        when(capabilityMapper.selectCount(any())).thenReturn(1L);
        when(modelConfigMapper.countProvider(PROVIDER_ID)).thenReturn(1);
    }

    @Test
    @DisplayName("新增：场景编码规范化成大写，status/priority 取默认值")
    void createNormalizesScenarioAndFillsDefaults() {
        stubReferencesExist();
        when(bindingMapper.selectCount(any())).thenReturn(0L);
        // 模拟自增主键回填
        when(bindingMapper.insert(any(AigRouteScenarioBinding.class))).thenAnswer(invocation -> {
            invocation.<AigRouteScenarioBinding>getArgument(0).setBindId(77L);
            return 1;
        });

        Long bindId = service.create(bo("long_page", CAPABILITY, PROVIDER_ID));

        assertEquals(77L, bindId, "应返回自增主键");
        ArgumentCaptor<AigRouteScenarioBinding> captor = ArgumentCaptor.forClass(AigRouteScenarioBinding.class);
        verify(bindingMapper).insert(captor.capture());
        AigRouteScenarioBinding saved = captor.getValue();
        assertEquals("LONG_PAGE", saved.getScenarioCode(),
            "统一大写，避免「配了 LONG_PAGE 但请求写 long_page」这种看起来配了却没生效的情况");
        assertEquals(CAPABILITY, saved.getCapabilityCode());
        assertEquals(PROVIDER_ID, saved.getProviderId());
        assertEquals("0", saved.getStatus(), "默认启用");
        assertEquals(0, saved.getPriority(), "默认优先序 0");
    }

    @Test
    @DisplayName("新增：场景编码里的空白被去掉（不靠调用方自己保证）")
    void createTrimsScenario() {
        stubReferencesExist();
        when(bindingMapper.selectCount(any())).thenReturn(0L);

        service.create(bo("  POSTER  ", CAPABILITY, PROVIDER_ID));

        ArgumentCaptor<AigRouteScenarioBinding> captor = ArgumentCaptor.forClass(AigRouteScenarioBinding.class);
        verify(bindingMapper).insert(captor.capture());
        assertEquals("POSTER", captor.getValue().getScenarioCode(),
            "带空白存进去会让「请求场景 == 配置场景」永远为假，绑定静默失效");
    }

    @Test
    @DisplayName("新增：能力不存在 → 拒绝且不插入（拼错的能力编码会让绑定永不生效）")
    void createRejectsMissingCapability() {
        when(capabilityMapper.selectCount(any())).thenReturn(0L);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.create(bo("LONG_PAGE", "no_such_capability", PROVIDER_ID)));

        assertTrue(ex.getMessage().contains("no_such_capability"),
            "报错要带上那个不存在的编码，便于直接改正；实际=" + ex.getMessage());
        verify(bindingMapper, never()).insert(any(AigRouteScenarioBinding.class));
    }

    @Test
    @DisplayName("新增：供应商不存在 → 拒绝且不插入")
    void createRejectsMissingProvider() {
        when(capabilityMapper.selectCount(any())).thenReturn(1L);
        when(modelConfigMapper.countProvider(PROVIDER_ID)).thenReturn(0);

        assertThrows(ServiceException.class, () -> service.create(bo("LONG_PAGE", CAPABILITY, PROVIDER_ID)));

        verify(bindingMapper, never()).insert(any(AigRouteScenarioBinding.class));
    }

    @Test
    @DisplayName("新增：同一「场景 × 能力 × 供应商」重复 → 拒绝（否则删一条以为放开了，其实还钉着）")
    void createRejectsDuplicate() {
        stubReferencesExist();
        when(bindingMapper.selectCount(any())).thenReturn(1L);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.create(bo("LONG_PAGE", CAPABILITY, PROVIDER_ID)));

        assertTrue(ex.getMessage().contains("已绑定"),
            "重复绑定必须明确拒绝；实际=" + ex.getMessage());
        verify(bindingMapper, never()).insert(any(AigRouteScenarioBinding.class));
    }

    @Test
    @DisplayName("新增：场景编码为空 → 拒绝且不触库")
    void createRejectsBlankScenario() {
        assertThrows(ServiceException.class, () -> service.create(bo("   ", CAPABILITY, PROVIDER_ID)));

        verifyNoInteractions(bindingMapper);
    }

    @Test
    @DisplayName("修改：绑定不存在 → 拒绝且不更新")
    void updateRejectsMissingBinding() {
        AigRouteScenarioBindingBo bo = bo("LONG_PAGE", CAPABILITY, PROVIDER_ID);
        bo.setBindId(404L);
        when(bindingMapper.selectById(404L)).thenReturn(null);

        assertThrows(ServiceException.class, () -> service.update(bo));

        verify(bindingMapper, never()).updateById(any(AigRouteScenarioBinding.class));
    }

    @Test
    @DisplayName("删除：绑定不存在 → 拒绝（避免「删了但没删」被当成已放开）")
    void removeRejectsMissingBinding() {
        when(bindingMapper.selectById(404L)).thenReturn(null);

        assertThrows(ServiceException.class, () -> service.remove(404L));

        verify(bindingMapper, never()).deleteById(any(Long.class));
    }

    @Test
    @DisplayName("查询：回填能力名称与供应商名称，界面不必再各自查一遍")
    void queryPageFillsNames() {
        AigRouteScenarioBindingVo row = new AigRouteScenarioBindingVo();
        row.setBindId(1L);
        row.setScenarioCode("LONG_PAGE");
        row.setCapabilityCode(CAPABILITY);
        row.setProviderId(PROVIDER_ID);
        Page<AigRouteScenarioBindingVo> page = new Page<>(1, 10);
        page.setRecords(List.of(row));
        page.setTotal(1);
        when(bindingMapper.selectVoPage(any(), any())).thenReturn(page);

        AigCapability capability = new AigCapability();
        capability.setCapabilityCode(CAPABILITY);
        capability.setCapabilityName("图像生成");
        when(capabilityMapper.selectList(any())).thenReturn(List.of(capability));

        AigModelProviderVo provider = new AigModelProviderVo();
        provider.setProviderId(PROVIDER_ID);
        provider.setProviderName("bluocto");
        when(modelConfigMapper.selectAllProviders()).thenReturn(List.of(provider));

        PageQuery pageQuery = new PageQuery();
        pageQuery.setPageNum(1);
        pageQuery.setPageSize(10);

        PageResult<AigRouteScenarioBindingVo> result =
            service.queryPage(bo(null, null, null), pageQuery);

        assertEquals(1, result.getTotal());
        assertNotNull(result.getRows());
        AigRouteScenarioBindingVo filled = result.getRows().iterator().next();
        assertEquals("图像生成", filled.getCapabilityName(), "能力名应回填，否则界面只有编码");
        assertEquals("bluocto", filled.getProviderName(), "供应商名应回填，否则界面只有 ID");
    }

}
