package org.dromara.aigov.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.aigov.domain.AigInvocationAudit;
import org.dromara.aigov.domain.bo.AigAuditQueryBo;
import org.dromara.aigov.domain.vo.AigInvocationAuditVo;
import org.dromara.aigov.domain.vo.AigModelProviderVo;
import org.dromara.aigov.mapper.AigCapabilityMapper;
import org.dromara.aigov.mapper.AigInvocationAuditMapper;
import org.dromara.aigov.mapper.AigModelConfigMapper;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 审计查询「按供应商过滤 + 回填供应商名称」的行为测试。
 *
 * <p><b>为什么值得测</b>：供应商列是补出来的对账口径。如果只有「写进去」而没有
 * 「查得出来」，这一列就只是个躺在库里的值——而这恰恰是补列类需求最常见的半成品：
 * 写入路径做了、查询路径忘了，界面上看不到，于是下一个人仍然去 join 模型表现查，
 * 查到的还是今天的归属，问题原样存在。</p>
 *
 * <p>过滤条件的断言直接读 {@code LambdaQueryWrapper} 生成的 SQL 片段：只有这样才能
 * 证明「provider_id 真的进了 where」，而不是「我们传了个参数进去、然后被忽略了」。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigAuditServiceImplProviderTest {

    private static final long PROVIDER_ID = 1001L;

    private AigInvocationAuditMapper auditMapper;
    private AigModelConfigMapper modelConfigMapper;
    private AigAuditServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        // LambdaQueryWrapper 需要实体→列的 lambda 缓存；纯单测没有 Spring，必须显式初始化
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, AigInvocationAudit.class);
    }

    @BeforeEach
    void setUp() {
        auditMapper = mock(AigInvocationAuditMapper.class);
        modelConfigMapper = mock(AigModelConfigMapper.class);
        service = new AigAuditServiceImpl(auditMapper, mock(AigCapabilityMapper.class), modelConfigMapper);
    }

    private static PageQuery pageQuery() {
        PageQuery pageQuery = new PageQuery();
        pageQuery.setPageNum(1);
        pageQuery.setPageSize(10);
        return pageQuery;
    }

    /**
     * 让 mapper 返回给定行（总数与行数一致）。
     *
     * @param rows 审计行
     */
    private void stubRows(List<AigInvocationAuditVo> rows) {
        Page<AigInvocationAuditVo> page = new Page<>(1, 10);
        page.setRecords(rows);
        page.setTotal(rows.size());
        when(auditMapper.selectVoPage(any(), any())).thenReturn(page);
    }

    @Test
    @DisplayName("按供应商过滤：provider_id 必须真的进 where 子句")
    void providerFilterReachesTheSql() {
        stubRows(new ArrayList<>());
        AigAuditQueryBo query = new AigAuditQueryBo();
        query.setProviderId(PROVIDER_ID);

        service.queryPage(query, pageQuery());

        ArgumentCaptor<LambdaQueryWrapper<AigInvocationAudit>> captor =
            ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(auditMapper).selectVoPage(any(), captor.capture());
        String sql = captor.getValue().getCustomSqlSegment();
        assertTrue(sql.contains("provider_id"),
            "过滤条件必须进 where；否则界面上「按供应商查」会安静地返回全部记录，"
                + "而结果看起来完全正常。实际 SQL 片段=" + sql);
    }

    @Test
    @DisplayName("未传供应商时不加该条件（避免把「不筛」写成「筛 null」）")
    void noProviderFilterWhenAbsent() {
        stubRows(new ArrayList<>());

        service.queryPage(new AigAuditQueryBo(), pageQuery());

        ArgumentCaptor<LambdaQueryWrapper<AigInvocationAudit>> captor =
            ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(auditMapper).selectVoPage(any(), captor.capture());
        String sql = captor.getValue().getCustomSqlSegment();
        assertTrue(!sql.contains("provider_id"),
            "不传条件就不该出现该列，否则会恒查 provider_id is null，把有供应商的记录全滤掉。实际=" + sql);
    }

    @Test
    @DisplayName("回填供应商名称，且某行没有供应商时不影响其它行")
    void fillsProviderNameAndToleratesMissingProvider() {
        AigInvocationAuditVo withProvider = new AigInvocationAuditVo();
        withProvider.setAuditId(1L);
        withProvider.setProviderId(PROVIDER_ID);
        AigInvocationAuditVo withoutProvider = new AigInvocationAuditVo();
        withoutProvider.setAuditId(2L);
        // 补列之前的历史行：provider_id 为 NULL
        stubRows(new ArrayList<>(List.of(withProvider, withoutProvider)));

        AigModelProviderVo provider = new AigModelProviderVo();
        provider.setProviderId(PROVIDER_ID);
        provider.setProviderName("bluocto");
        when(modelConfigMapper.selectAllProviders()).thenReturn(List.of(provider));

        PageResult<AigInvocationAuditVo> result = service.queryPage(new AigAuditQueryBo(), pageQuery());

        assertEquals(2, result.getTotal());
        List<AigInvocationAuditVo> rows = new ArrayList<>(result.getRows());
        assertEquals("bluocto", rows.get(0).getProviderName(), "供应商名要回填，否则界面只有 ID");
        assertEquals(null, rows.get(1).getProviderName(),
            "历史行的供应商为空是正常状态（当时没采集），不能因此报错或编一个名字");
    }

}
