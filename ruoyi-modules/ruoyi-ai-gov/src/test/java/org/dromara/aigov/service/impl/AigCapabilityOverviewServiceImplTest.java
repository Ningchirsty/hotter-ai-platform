package org.dromara.aigov.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.aigov.domain.AigCapability;
import org.dromara.aigov.domain.AigCapabilityModel;
import org.dromara.aigov.domain.AigInvocationAudit;
import org.dromara.aigov.domain.AigRoutePolicy;
import org.dromara.aigov.domain.AigRouteScenarioBinding;
import org.dromara.aigov.domain.vo.AigCapabilityOverviewVo;
import org.dromara.aigov.domain.vo.AigModelVo;
import org.dromara.aigov.mapper.AigCapabilityMapper;
import org.dromara.aigov.mapper.AigCapabilityModelMapper;
import org.dromara.aigov.mapper.AigInvocationAuditMapper;
import org.dromara.aigov.mapper.AigModelViewMapper;
import org.dromara.aigov.mapper.AigRoutePolicyMapper;
import org.dromara.aigov.mapper.AigRouteScenarioBindingMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 能力概览聚合测试（P0 能力清单卡片的判定来源）。
 *
 * <p>钉住四件事：①问题码的判定（无候选 / 无策略 / 策略不全）②排序（有问题的在前）
 * ③健康口径与路由一致（只有明确 DOWN/UNHEALTHY 才算坏，从未探测过是"未知"而不是"健康"）
 * ④24h 调用与失败计数按审计结果列统计。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigCapabilityOverviewServiceImplTest {

    private AigCapabilityMapper capabilityMapper;
    private AigCapabilityModelMapper capabilityModelMapper;
    private AigRoutePolicyMapper routePolicyMapper;
    private AigRouteScenarioBindingMapper scenarioBindingMapper;
    private AigInvocationAuditMapper auditMapper;
    private AigModelViewMapper modelViewMapper;
    private AigCapabilityOverviewServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, AigCapability.class);
        TableInfoHelper.initTableInfo(assistant, AigCapabilityModel.class);
        TableInfoHelper.initTableInfo(assistant, AigRoutePolicy.class);
        TableInfoHelper.initTableInfo(assistant, AigRouteScenarioBinding.class);
        TableInfoHelper.initTableInfo(assistant, AigInvocationAudit.class);
    }

    @BeforeEach
    void setUp() {
        capabilityMapper = mock(AigCapabilityMapper.class);
        capabilityModelMapper = mock(AigCapabilityModelMapper.class);
        routePolicyMapper = mock(AigRoutePolicyMapper.class);
        scenarioBindingMapper = mock(AigRouteScenarioBindingMapper.class);
        auditMapper = mock(AigInvocationAuditMapper.class);
        modelViewMapper = mock(AigModelViewMapper.class);
        service = new AigCapabilityOverviewServiceImpl(capabilityMapper, capabilityModelMapper, routePolicyMapper,
            scenarioBindingMapper, auditMapper, modelViewMapper);
        // 默认：没有候选 / 没有策略 / 没有场景 / 没有调用
        when(capabilityModelMapper.selectList(any())).thenReturn(List.of());
        when(routePolicyMapper.selectList(any())).thenReturn(List.of());
        when(scenarioBindingMapper.selectList(any())).thenReturn(List.of());
        when(auditMapper.selectList(any())).thenReturn(List.of());
        when(modelViewMapper.selectModelListByIds(anyList())).thenReturn(List.of());
    }

    private static AigCapability capability(String code, String name) {
        AigCapability capability = new AigCapability();
        capability.setCapabilityId(1L);
        capability.setCapabilityCode(code);
        capability.setCapabilityName(name);
        capability.setRequiredTags("TEXT");
        capability.setStatus("0");
        capability.setUpdateTime(LocalDateTime.of(2026, 10, 14, 10, 0));
        return capability;
    }

    private static AigCapabilityModel candidate(String code, long modelId, String usage, int priority) {
        AigCapabilityModel row = new AigCapabilityModel();
        row.setBindId(modelId * 10);
        row.setCapabilityCode(code);
        row.setModelId(modelId);
        row.setUsageType(usage);
        row.setPriority(priority);
        row.setStatus("0");
        return row;
    }

    private static AigRoutePolicy policy(String code, String level) {
        AigRoutePolicy row = new AigRoutePolicy();
        row.setCapabilityCode(code);
        row.setDataLevel(level);
        row.setStatus("0");
        return row;
    }

    private static AigInvocationAudit audit(String code, String result) {
        AigInvocationAudit row = new AigInvocationAudit();
        row.setCapabilityCode(code);
        row.setResult(result);
        return row;
    }

    private static AigModelVo model(long modelId, String modelKey, String healthStatus) {
        AigModelVo model = new AigModelVo();
        model.setModelId(modelId);
        model.setModelKey(modelKey);
        model.setHealthStatus(healthStatus);
        return model;
    }

    @Test
    @DisplayName("★ 无候选 + 无策略的能力排在最前，问题码逐条给出")
    void flagsAndSortsProblemsFirst() {
        when(capabilityMapper.selectList(any()))
            .thenReturn(List.of(capability("image_generation", "图片生成"), capability("document_parse", "文档解析")));
        when(capabilityModelMapper.selectList(any()))
            .thenReturn(List.of(candidate("image_generation", 10L, "PRIMARY", 10)));
        when(routePolicyMapper.selectList(any())).thenReturn(List.of(
            policy("image_generation", "PUBLIC"), policy("image_generation", "INTERNAL"),
            policy("image_generation", "RESTRICTED"), policy("image_generation", "STRICT")));
        when(modelViewMapper.selectModelListByIds(anyList()))
            .thenReturn(List.of(model(10L, "bluocto-image-v2", "UP")));

        List<AigCapabilityOverviewVo> rows = service.overview(null, null, null, null);

        assertEquals(2, rows.size());
        AigCapabilityOverviewVo first = rows.get(0);
        assertEquals("document_parse", first.getCapabilityCode());
        assertEquals(List.of("NO_MODEL_CANDIDATE", "NO_ROUTE_POLICY"), first.getIssueCodes());
        assertEquals(0, first.getCandidateCount());
        assertEquals(0, first.getPolicyCoveredLevels());
        assertEquals(4, first.getPolicyTotalLevels());

        AigCapabilityOverviewVo second = rows.get(1);
        assertEquals("image_generation", second.getCapabilityCode());
        assertTrue(second.getIssueCodes().isEmpty(), "候选与策略都齐的能力不应有问题码");
        assertEquals(1, second.getCandidateCount());
        assertEquals("bluocto-image-v2", second.getPrimaryModelKey());
        assertEquals("HEALTHY", second.getCandidateHealthAgg());
    }

    @Test
    @DisplayName("★ 健康口径与路由一致：只有明确 DOWN/UNHEALTHY 才算坏；从未探测过是「未知」")
    void healthAggregationMatchesRoutingRule() {
        when(capabilityMapper.selectList(any())).thenReturn(List.of(capability("a", "A")));
        when(capabilityModelMapper.selectList(any())).thenReturn(List.of(candidate("a", 1L, "PRIMARY", 10)));

        when(modelViewMapper.selectModelListByIds(anyList())).thenReturn(List.of(model(1L, "m", null)));
        assertEquals("UNKNOWN", service.overview(null, null, null, null).get(0).getCandidateHealthAgg(),
            "从未探测过不能被当成健康，否则界面会给一个假绿灯");

        when(modelViewMapper.selectModelListByIds(anyList())).thenReturn(List.of(model(1L, "m", "unhealthy")));
        assertEquals("UNHEALTHY", service.overview(null, null, null, null).get(0).getCandidateHealthAgg(),
            "大小写不敏感");

        when(modelViewMapper.selectModelListByIds(anyList())).thenReturn(List.of(model(1L, "m", "UP")));
        assertEquals("HEALTHY", service.overview(null, null, null, null).get(0).getCandidateHealthAgg());
    }

    @Test
    @DisplayName("策略只配了一部分 → POLICY_INCOMPLETE（不是 NO_ROUTE_POLICY，也不是完整）")
    void partialPolicyIsIncomplete() {
        when(capabilityMapper.selectList(any())).thenReturn(List.of(capability("a", "A")));
        when(routePolicyMapper.selectList(any())).thenReturn(List.of(
            policy("a", "internal"), policy("a", "INTERNAL"), policy("a", "STRICT")));

        AigCapabilityOverviewVo row = service.overview(null, null, null, null).get(0);

        assertEquals(List.of("NO_MODEL_CANDIDATE", "POLICY_INCOMPLETE"), row.getIssueCodes());
        assertEquals(2, row.getPolicyCoveredLevels(), "同等级大小写不同只算一次");
    }

    @Test
    @DisplayName("近 24h 调用与失败按审计结果列统计（0 成功 / 1 失败）")
    void countsCallsAndFailures24h() {
        when(capabilityMapper.selectList(any())).thenReturn(List.of(capability("a", "A")));
        when(auditMapper.selectList(any())).thenReturn(List.of(
            audit("a", "0"), audit("a", "1"), audit("a", "0")));

        AigCapabilityOverviewVo row = service.overview(null, null, null, null).get(0);

        assertEquals(3, row.getCalls24h());
        assertEquals(1, row.getFails24h());
    }

    @Test
    @DisplayName("筛选：issueCode 只看某类问题；candidateState=NO 只看无候选；认不出的取值忽略而不是报错")
    void filtersByIssueAndCandidateState() {
        when(capabilityMapper.selectList(any())).thenReturn(List.of(
            capability("a", "A"), capability("b", "B")));
        when(capabilityModelMapper.selectList(any()))
            .thenReturn(List.of(candidate("b", 2L, "PRIMARY", 10)));

        List<AigCapabilityOverviewVo> noCandidate = service.overview(null, null, "NO_MODEL_CANDIDATE", null);
        assertEquals(1, noCandidate.size());
        assertEquals("a", noCandidate.get(0).getCapabilityCode());

        assertEquals(1, service.overview(null, null, null, "NO").size());
        assertEquals(1, service.overview(null, null, null, "HAS").size());
        assertEquals(2, service.overview(null, null, null, "whatever").size(),
            "认不出的筛选值按不过滤处理：一个参数写错不该让整页打不开");
    }

    @Test
    @DisplayName("没有能力时返回空列表（不抛错，也不返回 null）")
    void emptyCapabilitiesReturnsEmptyList() {
        when(capabilityMapper.selectList(any())).thenReturn(List.of());
        assertTrue(service.overview(null, null, null, null).isEmpty());
    }

}
