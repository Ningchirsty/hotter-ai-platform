package org.dromara.aigov.agent.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.aigov.agent.domain.AigAgentResourceBinding;
import org.dromara.aigov.agent.domain.AigAgentVersion;
import org.dromara.aigov.agent.domain.bo.AigAgentResourceBindingQueryBo;
import org.dromara.aigov.agent.domain.vo.AigAgentDeclaredResourceVo;
import org.dromara.aigov.agent.domain.vo.AigAgentResourceBindingVo;
import org.dromara.aigov.agent.mapper.AigAgentBindingMapper;
import org.dromara.aigov.agent.mapper.AigAgentMapper;
import org.dromara.aigov.agent.mapper.AigAgentResourceBindingMapper;
import org.dromara.aigov.agent.mapper.AigAgentVersionMapper;
import org.dromara.aigov.agent.mapper.AigPackageMapper;
import org.dromara.aigov.agent.mapper.AigPackageVersionMapper;
import org.dromara.aigov.agent.mapper.AigSkillMapper;
import org.dromara.aigov.agent.mapper.AigSkillVersionMapper;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 工具/知识绑定只读查询测试（附件 07 §5）。
 *
 * <p><b>为什么这条要单独测</b>：页面上的动作是"先看版本声明了什么，再从声明里挑一个去绑定"。
 * 声明解析算错时页面照样渲染——只是让人挑一个服务端随后会拒绝的项，或者把已经绑过的项
 * 又标成"未绑"、劝人重复绑定。两种都不报错。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigAgentRegistryQueryServiceImplTest {

    private static final long VERSION_ID = 7401L;

    private AigAgentMapper agentMapper;
    private AigAgentVersionMapper agentVersionMapper;
    private AigAgentResourceBindingMapper resourceBindingMapper;
    private AigAgentRegistryQueryServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        // LambdaQueryWrapper 需要实体→列的 lambda 缓存（纯单测没有 Spring）
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, AigAgentResourceBinding.class);
    }

    /**
     * 装配服务（未用到的依赖给替身，避免命中 null）。
     */
    private void setUp(AigAgentVersion version, List<AigAgentResourceBinding> boundRows) {
        agentMapper = mock(AigAgentMapper.class);
        agentVersionMapper = mock(AigAgentVersionMapper.class);
        resourceBindingMapper = mock(AigAgentResourceBindingMapper.class);
        service = new AigAgentRegistryQueryServiceImpl(agentMapper, agentVersionMapper,
            mock(AigSkillMapper.class), mock(AigSkillVersionMapper.class), mock(AigPackageMapper.class),
            mock(AigPackageVersionMapper.class), mock(AigAgentBindingMapper.class),
            resourceBindingMapper);
        when(agentVersionMapper.selectById(VERSION_ID)).thenReturn(version);
        when(resourceBindingMapper.selectList(any())).thenReturn(boundRows);
    }

    private static AigAgentVersion version(String allowed, String forbidden, String knowledge) {
        AigAgentVersion version = new AigAgentVersion();
        version.setAgentVersionId(VERSION_ID);
        version.setVersion("0.1.3");
        version.setAllowedTools(allowed);
        version.setForbiddenTools(forbidden);
        version.setKnowledgeScopeJson(knowledge);
        return version;
    }

    private static AigAgentResourceBinding bound(String type, String code) {
        AigAgentResourceBinding binding = new AigAgentResourceBinding();
        binding.setAgentVersionId(VERSION_ID);
        binding.setResourceType(type);
        binding.setResourceCode(code);
        binding.setEnabled("Y");
        return binding;
    }

    @Test
    @DisplayName("★ 声明视图：允许工具/知识范围逐项列出，并标出哪些已有启用中的绑定")
    void declaredResourcesMarksBoundItems() {
        setUp(version(" brand_brief , image_search ", "shell", " brand-kb , product-facts "),
            List.of(bound("TOOL", "brand_brief"), bound("KNOWLEDGE", "product-facts")));

        AigAgentDeclaredResourceVo vo = service.declaredResources(VERSION_ID);

        assertEquals(VERSION_ID, vo.getAgentVersionId());
        assertEquals("0.1.3", vo.getVersion());
        assertEquals(List.of("brand_brief", "image_search"),
            vo.getAllowedTools().stream().map(AigAgentDeclaredResourceVo.Item::code).toList(),
            "编码要去空白后逐项列出");
        assertEquals(List.of(true, false),
            vo.getAllowedTools().stream().map(AigAgentDeclaredResourceVo.Item::bound).toList(),
            "已绑的要标出来，否则页面会劝人重复绑一条");
        assertEquals(List.of("shell"), vo.getForbiddenTools());
        assertEquals(List.of("brand-kb", "product-facts"),
            vo.getKnowledgeScopes().stream().map(AigAgentDeclaredResourceVo.Item::code).toList());
        assertEquals(List.of(false, true),
            vo.getKnowledgeScopes().stream().map(AigAgentDeclaredResourceVo.Item::bound).toList());
    }

    @Test
    @DisplayName("声明视图：什么都没声明时给空列表（不是 null，页面不必到处判空）")
    void declaredResourcesReturnsEmptyListsWhenNothingDeclared() {
        setUp(version(null, "  ", null), List.of());

        AigAgentDeclaredResourceVo vo = service.declaredResources(VERSION_ID);

        assertTrue(vo.getAllowedTools().isEmpty());
        assertTrue(vo.getForbiddenTools().isEmpty());
        assertTrue(vo.getKnowledgeScopes().isEmpty());
    }

    @Test
    @DisplayName("★ 声明视图：只统计启用中的绑定（停用的不该把声明项标成已绑）")
    void declaredResourcesOnlyCountsEnabledBindings() {
        setUp(version("brand_brief", null, null), List.of(bound("TOOL", "brand_brief")));

        service.declaredResources(VERSION_ID);

        // TOOL 与 KNOWLEDGE 各查一次（分开统计，不能合成一次性把所有绑定都算上）
        ArgumentCaptor<Wrapper<AigAgentResourceBinding>> captor =
            ArgumentCaptor.forClass(Wrapper.class);
        verify(resourceBindingMapper, times(2)).selectList(captor.capture());
        List<String> segments = captor.getAllValues().stream().map(Wrapper::getSqlSegment).toList();
        assertTrue(segments.stream().allMatch(sql -> sql.contains("enabled")),
            "必须只取启用中的绑定：" + segments);
        assertTrue(segments.stream().allMatch(sql -> sql.contains("resource_type")),
            "必须按资源类型分开统计：" + segments);
    }

    @Test
    @DisplayName("声明视图：版本不存在 / 版本ID为空都要报错（不能返回一个空壳让人以为「没声明」）")
    void declaredResourcesRejectsUnknownVersion() {
        setUp(null, List.of());

        assertThrows(ServiceException.class, () -> service.declaredResources(VERSION_ID));
        assertThrows(ServiceException.class, () -> service.declaredResources(null));
    }

    @Test
    @DisplayName("绑定清单：按条件分页，且最新在前（create_time DESC）")
    void queryResourceBindingPageOrdersByCreateTime() {
        setUp(null, List.of());
        AigAgentResourceBindingVo row = new AigAgentResourceBindingVo();
        row.setBindingId(7901L);
        row.setResourceCode("brand_brief");
        when(resourceBindingMapper.selectVoPage(any(), any()))
            .thenReturn(new Page<AigAgentResourceBindingVo>(1, 10)
                .setRecords(List.of(row)).setTotal(1));

        AigAgentResourceBindingQueryBo query = new AigAgentResourceBindingQueryBo();
        query.setAgentVersionId(VERSION_ID);
        query.setResourceType("TOOL");
        PageResult<AigAgentResourceBindingVo> result =
            service.queryResourceBindingPage(query, new PageQuery());

        List<AigAgentResourceBindingVo> rows = new ArrayList<>(result.getRows());
        assertEquals(1, rows.size());
        assertEquals("brand_brief", rows.get(0).getResourceCode());
        assertEquals(1L, result.getTotal());

        ArgumentCaptor<Wrapper<AigAgentResourceBinding>> captor =
            ArgumentCaptor.forClass(Wrapper.class);
        verify(resourceBindingMapper).selectVoPage(any(), captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertTrue(sql.contains("create_time DESC"), "最新在前：" + sql);
    }
}
