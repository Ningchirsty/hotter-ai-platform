package org.dromara.aigov.service.impl;

import org.dromara.aigov.agent.domain.AigAgent;
import org.dromara.aigov.agent.domain.AigAgentVersion;
import org.dromara.aigov.agent.mapper.AigAgentMapper;
import org.dromara.aigov.agent.mapper.AigAgentVersionMapper;
import org.dromara.aigov.domain.vo.AigCapabilityReferenceVo;
import org.dromara.aigov.studio.mapper.AigStudioDraftMapper;
import org.dromara.aigov.task.mapper.AigTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 「谁在引用这个能力」测试（Q6 影响面）。
 *
 * <p>钉住：三种来源都出现、同一版本两处命中只算一次、Agent 主数据缺失时也给出可读标签、
 * 空编码返回空列表。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigCapabilityReferenceServiceImplTest {

    private AigAgentVersionMapper agentVersionMapper;
    private AigAgentMapper agentMapper;
    private AigTaskMapper taskMapper;
    private AigStudioDraftMapper studioDraftMapper;
    private AigCapabilityReferenceServiceImpl service;

    @BeforeEach
    void setUp() {
        agentVersionMapper = mock(AigAgentVersionMapper.class);
        agentMapper = mock(AigAgentMapper.class);
        taskMapper = mock(AigTaskMapper.class);
        studioDraftMapper = mock(AigStudioDraftMapper.class);
        service = new AigCapabilityReferenceServiceImpl(agentVersionMapper, agentMapper, taskMapper,
            studioDraftMapper);
        when(agentVersionMapper.selectList(any())).thenReturn(List.of());
        when(agentMapper.selectByIds(any())).thenReturn(List.of());
        when(taskMapper.selectCount(any())).thenReturn(0L);
        when(studioDraftMapper.selectCount(any())).thenReturn(0L);
    }

    private static AigAgentVersion version(long id, long agentId, String releaseStatus) {
        AigAgentVersion version = new AigAgentVersion();
        version.setAgentVersionId(id);
        version.setAgentId(agentId);
        version.setVersion("1.0.0");
        version.setReleaseStatus(releaseStatus);
        version.setProviderCapability("image_generation");
        return version;
    }

    private static AigAgent agent(long id, String code, String name) {
        AigAgent agent = new AigAgent();
        agent.setAgentId(id);
        agent.setAgentCode(code);
        agent.setAgentName(name);
        return agent;
    }

    @Test
    @DisplayName("★ 三种来源一次返回：Agent 版本 / 历史任务 / 训练台草稿")
    void returnsThreeKindsOfReferences() {
        when(agentVersionMapper.selectList(any())).thenReturn(List.of(version(900L, 77L, "RELEASED")));
        when(agentMapper.selectByIds(any())).thenReturn(List.of(agent(77L, "IMAGE_AGENT", "图片助手")));
        when(taskMapper.selectCount(any())).thenReturn(12L);
        when(studioDraftMapper.selectCount(any())).thenReturn(2L);

        List<AigCapabilityReferenceVo> rows = service.listReferences("image_generation");

        assertEquals(3, rows.size());
        AigCapabilityReferenceVo agentRef = rows.get(0);
        assertEquals("AGENT_VERSION", agentRef.getRefType());
        assertEquals("IMAGE_AGENT", agentRef.getAgentCode());
        assertTrue(agentRef.getRefLabel().contains("图片助手"), agentRef.getRefLabel());
        assertTrue(agentRef.getRefLabel().contains("已发布"), "在跑的版本必须显式写出来");
        assertEquals("显式声明 provider_capability", agentRef.getDetail());

        assertEquals("TASK", rows.get(1).getRefType());
        assertEquals(12, rows.get(1).getCount());
        assertEquals("DRAFT", rows.get(2).getRefType());
        assertEquals(2, rows.get(2).getCount());
    }

    @Test
    @DisplayName("同一版本同时命中「独立列」与「config_json」：只算一次，并如实标注声明来源")
    void dedupesSameVersionAndMarksDeclarationSource() {
        // 两处命中同一个版本：模拟查询返回两行（真实 SQL 会因 or 条件重复命中）
        AigAgentVersion inColumn = version(901L, 78L, "RELEASED");
        AigAgentVersion inJson = version(901L, 78L, "RELEASED");
        inJson.setProviderCapability(null);
        when(agentVersionMapper.selectList(any())).thenReturn(List.of(inColumn, inJson));
        when(agentMapper.selectByIds(any())).thenReturn(List.of(agent(78L, "A2", "助手二")));

        List<AigCapabilityReferenceVo> rows = service.listReferences("image_generation");

        assertEquals(1, rows.size(), "同一 agentVersionId 只算一次，否则确认框会重复列同一条");
        assertEquals("显式声明 provider_capability", rows.get(0).getDetail());
    }

    @Test
    @DisplayName("Agent 主数据缺失时不报错：用 Agent#id 兜底，仍然可读")
    void missingAgentFallsBackToIdLabel() {
        when(agentVersionMapper.selectList(any())).thenReturn(List.of(version(902L, 79L, null)));
        when(agentMapper.selectByIds(any())).thenReturn(List.of());

        List<AigCapabilityReferenceVo> rows = service.listReferences("image_generation");

        assertEquals(1, rows.size());
        assertTrue(rows.get(0).getRefLabel().contains("Agent#79"), rows.get(0).getRefLabel());
        assertTrue(rows.get(0).getRefLabel().contains("状态未知"), "状态为空也要说清，不留空白");
    }

    @Test
    @DisplayName("没有任何引用 / 编码为空：返回空列表（而不是 null，也不抛错）")
    void emptyCasesReturnEmptyList() {
        assertTrue(service.listReferences("image_generation").isEmpty());
        assertTrue(service.listReferences("  ").isEmpty());
        assertTrue(service.listReferences(null).isEmpty());
    }

}
