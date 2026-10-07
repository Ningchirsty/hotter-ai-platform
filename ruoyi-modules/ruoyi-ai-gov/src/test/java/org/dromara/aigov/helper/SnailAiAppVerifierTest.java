package org.dromara.aigov.helper;

import org.dromara.aigov.config.AigGovProperties;
import org.dromara.aigov.domain.vo.AigSnailAppVo;
import org.dromara.aigov.mapper.AigSnailAppMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * snail-ai 客户端身份核对（C2）。
 *
 * <p>钉住的是「配置与 {@code sai_app} 对不上时不放行、且报出到底哪一处不对」。
 * 这几类漂移（应用重建换了 app-id、换了 token、被停用）在真实环境里都以
 * 「超时 / 鉴权失败」的面目出现，不专门核对就只能靠猜。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class SnailAiAppVerifierTest {

    private AigGovProperties properties;
    private AigSnailAppMapper snailAppMapper;
    private MockEnvironment environment;

    @BeforeEach
    void setUp() {
        properties = new AigGovProperties();
        snailAppMapper = mock(AigSnailAppMapper.class);
        environment = new MockEnvironment();
    }

    /**
     * 造校验器。
     *
     * @return 校验器
     */
    private SnailAiAppVerifier verifier() {
        return new SnailAiAppVerifier(properties, snailAppMapper, environment);
    }

    /**
     * 造一行 sai_app。
     *
     * @param status       状态
     * @param tokenMatched 令牌是否一致
     * @return 行
     */
    private static AigSnailAppVo app(int status, boolean tokenMatched) {
        AigSnailAppVo vo = new AigSnailAppVo();
        vo.setId(1L);
        vo.setAppId("1");
        vo.setAppName("测试应用");
        vo.setStatus(status);
        vo.setTokenMatched(tokenMatched);
        return vo;
    }

    @Test
    @DisplayName("配置与 sai_app 一致：无问题（空清单 = 通过）")
    void passesWhenConsistent() {
        environment.setProperty("snail-ai.app-id", "1");
        environment.setProperty("snail-ai.token", "SAI_xxx");
        when(snailAppMapper.selectByAppId(eq("1"), any())).thenReturn(app(1, true));

        List<String> problems = verifier().verify();

        assertTrue(problems.isEmpty(), String.valueOf(problems));
    }

    @Test
    @DisplayName("应用标识优先取治理层显式配置，否则回落到平台客户端配置")
    void resolvesEffectiveAppId() {
        environment.setProperty("snail-ai.app-id", "platform-app");
        assertEquals("platform-app", verifier().effectiveAppId(), "未显式配置时应回落到平台客户端");

        properties.setAppId("aigov-app");
        assertEquals("aigov-app", verifier().effectiveAppId(), "显式配置优先");
    }

    @Test
    @DisplayName("没配 app-id：明确报出来（无法确定身份），不查库")
    void reportsMissingAppId() {
        List<String> problems = verifier().verify();

        assertFalse(problems.isEmpty());
        assertTrue(problems.get(0).contains("未配置 snail-ai.app-id"), problems.get(0));
    }

    @Test
    @DisplayName("sai_app 里没有这个应用：报「调用会被服务端拒绝」，给出两条处置")
    void reportsUnknownApp() {
        environment.setProperty("snail-ai.app-id", "9");
        environment.setProperty("snail-ai.token", "SAI_xxx");
        when(snailAppMapper.selectByAppId(eq("9"), any())).thenReturn(null);

        List<String> problems = verifier().verify();

        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("没有应用 app_id=9"), problems.get(0));
        assertTrue(problems.get(0).contains("snail-ai 的应用管理"), problems.get(0));
    }

    @Test
    @DisplayName("应用被停用：报出来（启用用不了）")
    void reportsDisabledApp() {
        environment.setProperty("snail-ai.app-id", "1");
        environment.setProperty("snail-ai.token", "SAI_xxx");
        when(snailAppMapper.selectByAppId(eq("1"), any())).thenReturn(app(0, true));

        List<String> problems = verifier().verify();

        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("不是启用状态"), problems.get(0));
    }

    @Test
    @DisplayName("令牌不一致：报出来并提示「应用重建会换令牌」——这是最容易漂移的一处")
    void reportsTokenMismatch() {
        environment.setProperty("snail-ai.app-id", "1");
        environment.setProperty("snail-ai.token", "SAI_old");
        when(snailAppMapper.selectByAppId(eq("1"), any())).thenReturn(app(1, false));

        List<String> problems = verifier().verify();

        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("令牌不一致"), problems.get(0));
        assertTrue(problems.get(0).contains("漂移"), problems.get(0));
    }

    @Test
    @DisplayName("没配令牌：报出来；同时仍会核对应用是否存在")
    void reportsMissingToken() {
        environment.setProperty("snail-ai.app-id", "1");
        when(snailAppMapper.selectByAppId(eq("1"), any())).thenReturn(app(1, false));

        List<String> problems = verifier().verify();

        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("未配置 snail-ai.token"), problems.get(0));
        // 没配令牌时不应因为「tokenMatched=false」再报一条令牌不一致（那会把原因说岔）
        assertFalse(SnailAiAppVerifier.describe(problems).contains("不一致"), problems.get(0));
    }

    @Test
    @DisplayName("sai_app 读不到（表未导入）：如实报告，不假装通过，也不抛异常")
    void reportsUnreadableTable() {
        environment.setProperty("snail-ai.app-id", "1");
        environment.setProperty("snail-ai.token", "SAI_xxx");
        when(snailAppMapper.selectByAppId(eq("1"), any()))
            .thenThrow(new RuntimeException("table doesn't exist"));

        List<String> problems = verifier().verify();

        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("无法核对 sai_app"), problems.get(0));
        assertTrue(problems.get(0).contains("ry_ai.sql"), problems.get(0));
    }

    @Test
    @DisplayName("describe：空清单给空串，多条用分号连起来（可读的失败信息）")
    void describeJoinsProblems() {
        assertEquals("", SnailAiAppVerifier.describe(List.of()));
        assertEquals("", SnailAiAppVerifier.describe(null));
        assertEquals("a；b", SnailAiAppVerifier.describe(List.of("a", "b")));
    }

}
