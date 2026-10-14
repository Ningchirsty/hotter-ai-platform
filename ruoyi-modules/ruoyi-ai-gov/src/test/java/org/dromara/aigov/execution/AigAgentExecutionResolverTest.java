package org.dromara.aigov.execution;

import org.dromara.aigov.agent.domain.AigAgentVersion;
import org.dromara.aigov.agent.mapper.AigAgentVersionMapper;
import org.dromara.aigov.agent.service.IAigAgentRegistryService;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 执行计划解析器测试（设计 D01）。
 *
 * <p>要钉死的三条：</p>
 * <ol>
 *     <li><b>非 v2 配置必须返回 null</b>——本能力上线不能改变任何存量版本的行为；</li>
 *     <li><b>系统 Prompt 取自冻结版本</b>，且相同版本两次解析给出相同 configHash（可复核）；</li>
 *     <li><b>外发许可是取与</b>：版本 {@code allow_external} 与 v2 {@code dataPolicy.allowExternal}
 *         任一为否即为否；执行前必须复查版本授权（D03）。</li>
 * </ol>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigAgentExecutionResolverTest {

    private static final long VERSION_ID = 8801L;

    private AigAgentVersionMapper agentVersionMapper;
    private IAigAgentRegistryService registryService;
    private AigAgentExecutionResolver resolver;

    @BeforeEach
    void setUp() {
        agentVersionMapper = mock(AigAgentVersionMapper.class);
        registryService = mock(IAigAgentRegistryService.class);
        resolver = new AigAgentExecutionResolver(agentVersionMapper, registryService,
            JsonMapper.builder().build());
    }

    private static AigAgentVersion version(String configJson, String allowExternal, String promptTemplate) {
        AigAgentVersion version = new AigAgentVersion();
        version.setAgentVersionId(VERSION_ID);
        version.setVersion("1.2.0");
        version.setConfigJson(configJson);
        version.setAllowExternal(allowExternal);
        version.setPromptTemplate(promptTemplate);
        version.setProviderCapability("legacy_capability");
        return version;
    }

    private AigExecutionPlan resolve() {
        return resolver.resolve(1L, VERSION_ID, "INTERNAL", "{\"k\":1}", "业务输入",
            null, AigExecutionPurposeEnum.PRODUCTION);
    }

    @Test
    @DisplayName("无目标版本 → 直接返回 null（裸能力调用不受 Agent 计划约束）")
    void noVersionReturnsNull() {
        assertNull(resolver.resolve(1L, null, "INTERNAL", null, null, null,
            AigExecutionPurposeEnum.PRODUCTION));
        verify(agentVersionMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("★ 旧格式配置（无 schemaVersion）→ 返回 null，按旧适配器运行，且不做授权检查")
    void legacyConfigFallsBack() {
        when(agentVersionMapper.selectById(VERSION_ID))
            .thenReturn(version("{\"agentName\":\"x\",\"allowExternal\":\"Y\"}", "Y", "SYS"));

        assertNull(resolve());
        verify(registryService, never()).assertExecutionAuthorized(any());
    }

    @Test
    @DisplayName("schemaVersion 低于 2 / 非 JSON → 返回 null")
    void lowerSchemaVersionOrBrokenJsonFallsBack() {
        when(agentVersionMapper.selectById(VERSION_ID))
            .thenReturn(version("{\"schemaVersion\":1}", "Y", "SYS"));
        assertNull(resolve(), "schemaVersion=1 不是 v2");

        when(agentVersionMapper.selectById(VERSION_ID)).thenReturn(version("not-json", "Y", "SYS"));
        assertNull(resolve(), "非 JSON 不能抛异常，应退回旧路径");
    }

    @Test
    @DisplayName("★ v2 配置 → 系统 Prompt 取自冻结版本，能力取 v2，运行用途与哈希齐备")
    void v2ConfigProducesFrozenPlan() {
        String config = "{\"schemaVersion\":2,\"providerCapability\":\"text_generation\","
            + "\"dataPolicy\":{\"allowExternal\":true,\"minimumLevel\":\"INTERNAL\"},"
            + "\"knowledgeRefs\":[{\"collectionCode\":\"product_docs\"}],"
            + "\"toolRefs\":[{\"toolCode\":\"product_fact_query\"}],"
            + "\"limits\":{\"maxToolCalls\":3,\"maxDepth\":1}}";
        when(agentVersionMapper.selectById(VERSION_ID)).thenReturn(version(config, "Y", "FROZEN-SYSTEM"));

        AigExecutionPlan plan = resolve();

        assertNotNull(plan);
        assertEquals(VERSION_ID, plan.getAgentVersionId());
        assertEquals(AigExecutionPurposeEnum.PRODUCTION, plan.getPurpose());
        assertEquals("FROZEN-SYSTEM", plan.getSystemPrompt());
        assertEquals("text_generation", plan.getCapabilityCode());
        assertEquals(Boolean.TRUE, plan.getAllowExternal());
        assertEquals(3, plan.getMaxToolCalls());
        assertEquals(1, plan.getMaxDepth());
        assertEquals(java.util.List.of("product_docs"), plan.getKnowledgeRefs());
        assertEquals(java.util.List.of("product_fact_query"), plan.getToolRefs());
        verify(registryService).assertExecutionAuthorized(VERSION_ID);
        assertTrue(plan.composePrompt("业务输入").startsWith("FROZEN-SYSTEM"));
        assertTrue(plan.composePrompt("业务输入").contains("## 业务输入"));
    }

    @Test
    @DisplayName("相同版本两次解析 configHash 一致（哈希是「依据哪份配置」的锚点）")
    void configHashIsDeterministic() {
        String config = "{\"schemaVersion\":2,\"providerCapability\":\"text_generation\"}";
        when(agentVersionMapper.selectById(VERSION_ID)).thenReturn(version(config, "Y", "SYS"));

        assertEquals(resolve().getConfigHash(), resolve().getConfigHash());
    }

    @Test
    @DisplayName("★ 外发许可取与：版本 N 或 v2 dataPolicy=false 任一为否即为否")
    void allowExternalIsIntersection() {
        String allowTrue = "{\"schemaVersion\":2,\"dataPolicy\":{\"allowExternal\":true}}";
        String allowFalse = "{\"schemaVersion\":2,\"dataPolicy\":{\"allowExternal\":false}}";

        when(agentVersionMapper.selectById(VERSION_ID)).thenReturn(version(allowTrue, "N", "SYS"));
        assertEquals(Boolean.FALSE, resolve().getAllowExternal(), "版本 allow_external=N 必须压过 v2=true");

        when(agentVersionMapper.selectById(VERSION_ID)).thenReturn(version(allowFalse, "Y", "SYS"));
        assertEquals(Boolean.FALSE, resolve().getAllowExternal(), "v2 dataPolicy=false 必须禁止外发");

        when(agentVersionMapper.selectById(VERSION_ID)).thenReturn(version(allowTrue, "Y", "SYS"));
        assertEquals(Boolean.TRUE, resolve().getAllowExternal(), "两者都允许才不禁止外发");
    }

    @Test
    @DisplayName("版本 allow_external 空白 → 按禁止处理（配置缺失不能变成一次外发）")
    void blankAllowExternalMeansDenied() {
        when(agentVersionMapper.selectById(VERSION_ID))
            .thenReturn(version("{\"schemaVersion\":2}", null, "SYS"));

        assertEquals(Boolean.FALSE, resolve().getAllowExternal());
    }

    @Test
    @DisplayName("授权检查失败 → 向上抛出，不产出计划")
    void authorizationFailurePropagates() {
        when(agentVersionMapper.selectById(VERSION_ID))
            .thenReturn(version("{\"schemaVersion\":2}", "Y", "SYS"));
        org.mockito.Mockito.doThrow(new ServiceException("当前范围未授权"))
            .when(registryService).assertExecutionAuthorized(VERSION_ID);

        assertThrows(ServiceException.class, this::resolve);
    }

    @Test
    @DisplayName("v2 配置但无冻结 Prompt → 退回旧路径（不执行空系统提示）")
    void blankPromptTemplateFallsBack() {
        when(agentVersionMapper.selectById(VERSION_ID))
            .thenReturn(version("{\"schemaVersion\":2}", "Y", "  "));

        assertNull(resolve());
        verify(registryService, never()).assertExecutionAuthorized(any());
    }

    @Test
    @DisplayName("版本不存在 → 明确报错")
    void missingVersionThrows() {
        when(agentVersionMapper.selectById(eq(VERSION_ID))).thenReturn(null);

        assertThrows(ServiceException.class, this::resolve);
    }

    @Test
    @DisplayName("AigExecutionPlan 的集合不可被外部修改（不可变）")
    void planCollectionsAreImmutable() {
        AigExecutionPlan plan = AigExecutionPlan.builder()
            .knowledgeRefs(new java.util.ArrayList<>(java.util.List.of("a")))
            .build();
        assertThrows(UnsupportedOperationException.class, () -> plan.getKnowledgeRefs().add("b"));
        assertFalse(plan.getKnowledgeRefs().isEmpty());
    }

}
