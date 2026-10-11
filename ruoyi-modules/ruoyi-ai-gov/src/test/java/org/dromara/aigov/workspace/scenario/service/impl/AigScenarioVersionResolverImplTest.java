package org.dromara.aigov.workspace.scenario.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.aigov.workspace.domain.AigScenario;
import org.dromara.aigov.workspace.domain.AigScenarioVersion;
import org.dromara.aigov.workspace.mapper.AigScenarioMapper;
import org.dromara.aigov.workspace.mapper.AigScenarioVersionMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 场景版本解析测试（增量 13）。
 *
 * <p>钉住三件事：①**只认 STABLE**（DRAFT/CANDIDATE 不可执行）；②多个 STABLE 并存时取**版本号最大**
 * 的那个，且按数字段比较（{@code 1.10.0} 必须赢过 {@code 1.9.0}——字符串比较会判反）；
 * ③场景不存在/编码为空返回 null（调用方据此 fail-closed）。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigScenarioVersionResolverImplTest {

    private AigScenarioMapper scenarioMapper;
    private AigScenarioVersionMapper versionMapper;
    private AigScenarioVersionResolverImpl resolver;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, AigScenario.class);
        TableInfoHelper.initTableInfo(assistant, AigScenarioVersion.class);
    }

    @BeforeEach
    void setUp() {
        scenarioMapper = mock(AigScenarioMapper.class);
        versionMapper = mock(AigScenarioVersionMapper.class);
        resolver = new AigScenarioVersionResolverImpl(scenarioMapper, versionMapper);
    }

    @Test
    @DisplayName("★多个 STABLE 并存：取版本号最大的（1.10.0 赢过 1.9.0，数字段比较）")
    void picksLatestStableByNumericVersion() {
        stubScenario("COMMERCE");
        when(versionMapper.selectList(any())).thenReturn(List.of(
            versionRow("1.9.0", "STABLE", "A"),
            versionRow("1.10.0", "STABLE", "B"),
            versionRow("2.0.0", "DRAFT", "C")));

        assertEquals("1.10.0", resolver.stableVersion("COMMERCE").getVersion());
    }

    @Test
    @DisplayName("没有 STABLE 版本：返回 null（调用方据此拒绝执行）")
    void noStableVersionReturnsNull() {
        stubScenario("COMMERCE");
        when(versionMapper.selectList(any())).thenReturn(List.of(
            versionRow("1.0.0", "DRAFT", "A"),
            versionRow("1.1.0", "CANDIDATE", "B")));

        assertNull(resolver.stableVersion("COMMERCE"));
    }

    @Test
    @DisplayName("场景不存在 / 编码为空：返回 null（不猜）")
    void missingScenarioReturnsNull() {
        when(scenarioMapper.selectOne(any())).thenReturn(null);
        assertNull(resolver.stableVersion("NOPE"));
        assertNull(resolver.stableVersion(null));
        assertNull(resolver.stableVersion("   "));
    }

    private void stubScenario(String code) {
        AigScenario scenario = new AigScenario();
        scenario.setScenarioId(5L);
        scenario.setScenarioCode(code);
        when(scenarioMapper.selectOne(any())).thenReturn(scenario);
    }

    private static AigScenarioVersion versionRow(String version, String status, String adapter) {
        AigScenarioVersion row = new AigScenarioVersion();
        row.setVersion(version);
        row.setReleaseStatus(status);
        row.setWorkflowAdapter(adapter);
        return row;
    }

}
