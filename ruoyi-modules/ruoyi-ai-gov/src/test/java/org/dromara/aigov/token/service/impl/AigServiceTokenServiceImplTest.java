package org.dromara.aigov.token.service.impl;

import org.dromara.aigov.token.domain.AigServiceIdentity;
import org.dromara.aigov.token.domain.AigServiceToken;
import org.dromara.aigov.token.mapper.AigServiceTokenMapper;
import org.dromara.aigov.token.service.IAigServiceTokenService;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 服务身份（机器令牌）行为锁定测试。
 *
 * <p><b>它守的是什么</b>：这不是普通业务逻辑，而是<b>身份认证</b>。所以这里钉住的都是
 * "出一次就是安全事故"的不变量，而不是覆盖率：</p>
 * <ol>
 *     <li><b>库里绝不出现明文</b>——只存 SHA-256，且明文只在签发时返回一次；</li>
 *     <li><b>默认拒绝</b>——scopes 为空 = 什么都不能做，而不是"默认全给"；</li>
 *     <li><b>停用/过期一律拒绝，且不向调用方区分原因</b>；</li>
 *     <li><b>认证失败是正常结果（empty），不是异常</b>；</li>
 *     <li><b>审计写入（last_used）失败不能把人挡在门外</b>，且要节流，避免每请求一次写。</li>
 * </ol>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigServiceTokenServiceImplTest {

    private AigServiceTokenMapper tokenMapper;
    private AigServiceTokenServiceImpl service;

    @BeforeEach
    void setUp() {
        tokenMapper = mock(AigServiceTokenMapper.class);
        service = new AigServiceTokenServiceImpl(tokenMapper);
    }

    @Test
    @DisplayName("★ 只存哈希：库里不得出现明文令牌")
    void issueStoresOnlyHashNeverPlaintext() throws Exception {
        when(tokenMapper.selectCount(any())).thenReturn(0L);
        when(tokenMapper.insert(any(AigServiceToken.class))).thenAnswer(inv -> {
            inv.<AigServiceToken>getArgument(0).setTokenId(9001L);
            return 1;
        });

        IAigServiceTokenService.IssuedToken issued = service.issue("vibeposter-worker", "aig:capability:query", null, "试点");

        ArgumentCaptor<AigServiceToken> captor = ArgumentCaptor.forClass(AigServiceToken.class);
        verify(tokenMapper).insert(captor.capture());
        AigServiceToken stored = captor.getValue();

        assertEquals(sha256(issued.plaintextToken()), stored.getTokenHash(), "存的必须是明文的 SHA-256");
        assertNotEquals(issued.plaintextToken(), stored.getTokenHash(), "存的绝不能是明文");
        assertTrue(issued.plaintextToken().startsWith("hsvc_"), "明文应带可识别前缀");
        assertTrue(issued.plaintextToken().length() > 40, "明文应有足够熵");
        assertTrue(issued.plaintextToken().startsWith(stored.getTokenPrefix()), "前缀应是明文的前缀（仅用于指认）");
        assertEquals("0", stored.getStatus(), "新签发的令牌应为启用状态");
        assertEquals(9001L, issued.tokenId());
    }

    @Test
    @DisplayName("★ 同名令牌（未删除的）不允许重复签发")
    void issueRejectsDuplicateName() {
        when(tokenMapper.selectCount(any())).thenReturn(1L);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.issue("dup-name", "aig:capability:query", null, null));
        assertTrue(ex.getMessage().contains("dup-name"), "报错要点名是哪个服务名冲突了");
        verify(tokenMapper, never()).insert(any(AigServiceToken.class));
    }

    @Test
    @DisplayName("空服务名必须被拒（否则审计里会出现无法指认的 principal）")
    void issueRejectsBlankName() {
        assertThrows(ServiceException.class, () -> service.issue("   ", null, null, null));
        verify(tokenMapper, never()).insert(any(AigServiceToken.class));
    }

    @Test
    @DisplayName("★ scopes 归一化：去空白、去空项、去重，且保持书写顺序")
    void scopesAreNormalizedDeduplicatedAndOrdered() {
        when(tokenMapper.selectCount(any())).thenReturn(0L);
        when(tokenMapper.insert(any(AigServiceToken.class))).thenAnswer(inv -> {
            inv.<AigServiceToken>getArgument(0).setTokenId(1L);
            return 1;
        });

        service.issue("svc", " aig:model:edit , ,aig:task:operate,aig:model:edit ", null, null);

        ArgumentCaptor<AigServiceToken> captor = ArgumentCaptor.forClass(AigServiceToken.class);
        verify(tokenMapper).insert(captor.capture());
        assertEquals("aig:model:edit,aig:task:operate", captor.getValue().getScopes());
    }

    @Test
    @DisplayName("★ 拒绝签发含通配 scope 的令牌：单独 * 与内嵌 *（aig:*）都不行")
    void issueRejectsWildcardScope() {
        ServiceException lone = assertThrows(ServiceException.class,
            () -> service.issue("wild-svc", "aig:task:list,*", null, null));
        assertTrue(lone.getMessage().contains("*"), "报错要指出是通配符被拒");

        // 内嵌通配符同样拒：平台判定是"先精确、再对已授权限做通配匹配"，
        // 所以库里存 aig:* 会匹配上 aig:model:edit 这类远宽于字面的权限
        ServiceException embedded = assertThrows(ServiceException.class,
            () -> service.issue("wild-svc-2", "aig:*", null, null));
        assertTrue(embedded.getMessage().contains("aig:*"), "报错要点名是哪一条 scope 越界了");

        verify(tokenMapper, never()).insert(any(AigServiceToken.class));
    }

    @Test
    @DisplayName("★ 拒绝把令牌管理权限签发给机器身份（防自我提权）：这个组合必须建不出来")
    void issueRejectsTokenManagementScopes() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.issue("escalator", "aig:capability:query,aig:service-token:issue", null, null));

        assertTrue(ex.getMessage().contains("aig:service-token:issue"), "报错要点名是哪个 scope 越界了");
        verify(tokenMapper, never()).insert(any(AigServiceToken.class));
    }

    @Test
    @DisplayName("★ scopes 为空 = 默认拒绝（不是默认全给）")
    void emptyScopesMeanNoPermission() {
        AigServiceToken row = liveToken(7L, "reader", null);
        when(tokenMapper.selectOne(any())).thenReturn(row);

        Optional<AigServiceIdentity> id = service.authenticate("hsvc_whatever", "10.0.0.1");

        assertTrue(id.isPresent(), "令牌本身有效");
        assertFalse(id.get().hasScope("aig:model:edit"), "没写 scope 就不能做任何事");
        assertFalse(id.get().hasScope(null) || id.get().hasScope(""), "空权限码不算授权");
    }

    @Test
    @DisplayName("★ 权限是精确匹配，不做通配展开（权限必须能一眼看清）")
    void scopesMatchExactly() {
        AigServiceToken row = liveToken(8L, "invoker", "aig:capability:query");
        when(tokenMapper.selectOne(any())).thenReturn(row);

        AigServiceIdentity id = service.authenticate("hsvc_x", null).orElseThrow();

        assertTrue(id.hasScope("aig:capability:query"));
        assertFalse(id.hasScope("aig:capability"), "前缀不算授权");
        assertFalse(id.hasScope("aig:capability:query2"), "更长的不算授权");
        assertEquals("service:invoker", id.principal(), "principal 由服务端推导，格式为 service:<name>");
    }

    @Test
    @DisplayName("★ 已停用的令牌必须拒绝")
    void authenticateRejectsDisabledToken() {
        AigServiceToken row = liveToken(9L, "disabled-svc", "aig:capability:query");
        row.setStatus("1");
        when(tokenMapper.selectOne(any())).thenReturn(row);

        assertTrue(service.authenticate("hsvc_x", null).isEmpty());
    }

    @Test
    @DisplayName("★ 已过期的令牌必须拒绝；未到期的正常放行")
    void authenticateRejectsExpiredToken() {
        AigServiceToken expired = liveToken(10L, "expired-svc", "aig:capability:query");
        expired.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(tokenMapper.selectOne(any())).thenReturn(expired);
        assertTrue(service.authenticate("hsvc_x", null).isEmpty(), "过期必须拒绝");

        AigServiceToken valid = liveToken(11L, "valid-svc", "aig:capability:query");
        valid.setExpiresAt(LocalDateTime.now().plusDays(1));
        when(tokenMapper.selectOne(any())).thenReturn(valid);
        assertTrue(service.authenticate("hsvc_x", null).isPresent(), "未到期应放行");
    }

    @Test
    @DisplayName("未知令牌 / 空令牌：返回 empty（不抛异常），且空令牌连库都不查")
    void unknownOrBlankTokenReturnsEmpty() {
        when(tokenMapper.selectOne(any())).thenReturn(null);
        assertTrue(service.authenticate("hsvc_not-exists", null).isEmpty());

        assertTrue(service.authenticate(null, null).isEmpty());
        assertTrue(service.authenticate("   ", null).isEmpty());
        // 上面三次调用里，只有"未知令牌"那次该查库；两次空令牌必须在查库前就返回。
        // （我第一版这里写的是 never()，与前面那次查询自相矛盾——是测试写错了，不是实现。）
        verify(tokenMapper, org.mockito.Mockito.times(1)).selectOne(any());
    }

    @Test
    @DisplayName("★ last_used 节流：刚更新过就不写库，超过间隔才写")
    void lastUsedUpdateIsThrottled() {
        AigServiceToken fresh = liveToken(12L, "busy-svc", "aig:capability:query");
        fresh.setLastUsedAt(LocalDateTime.now().minusSeconds(5));
        when(tokenMapper.selectOne(any())).thenReturn(fresh);
        assertTrue(service.authenticate("hsvc_x", "10.0.0.9").isPresent());
        verify(tokenMapper, never()).updateById(any(AigServiceToken.class));

        AigServiceToken stale = liveToken(13L, "busy-svc2", "aig:capability:query");
        stale.setLastUsedAt(LocalDateTime.now().minusMinutes(5));
        when(tokenMapper.selectOne(any())).thenReturn(stale);
        assertTrue(service.authenticate("hsvc_x", "10.0.0.9").isPresent());
        ArgumentCaptor<AigServiceToken> captor = ArgumentCaptor.forClass(AigServiceToken.class);
        verify(tokenMapper).updateById(captor.capture());
        assertEquals(13L, captor.getValue().getTokenId());
        assertEquals("10.0.0.9", captor.getValue().getLastUsedIp());
    }

    @Test
    @DisplayName("★ 审计写入失败不能把人挡在门外（认证仍应成功）")
    void touchFailureDoesNotBreakAuthentication() {
        AigServiceToken row = liveToken(14L, "svc", "aig:capability:query");
        row.setLastUsedAt(LocalDateTime.now().minusHours(1));
        when(tokenMapper.selectOne(any())).thenReturn(row);
        when(tokenMapper.updateById(any(AigServiceToken.class)))
            .thenThrow(new RuntimeException("库抖动"));

        assertTrue(service.authenticate("hsvc_x", null).isPresent(),
            "last_used 写失败是次要问题，不该让调用方认证失败");
    }

    @Test
    @DisplayName("★ 吊销 = 置停用（不删行），且只吊销当前仍在启用的那一行")
    void revokeDisablesWithoutDeleting() {
        when(tokenMapper.update(any(AigServiceToken.class), any())).thenReturn(1);
        assertTrue(service.revoke(21L));

        ArgumentCaptor<AigServiceToken> captor = ArgumentCaptor.forClass(AigServiceToken.class);
        verify(tokenMapper).update(captor.capture(), any());
        assertEquals("1", captor.getValue().getStatus(), "吊销应置 status=1");
        verify(tokenMapper, never()).deleteById(any(Long.class));

        when(tokenMapper.update(any(AigServiceToken.class), any())).thenReturn(0);
        assertFalse(service.revoke(22L), "已经停用/不存在时应返回 false");
        assertFalse(service.revoke(null));
    }

    /**
     * 造一行"活着"的令牌。
     *
     * @param id     令牌ID
     * @param name   服务名
     * @param scopes 授权范围
     * @return 令牌行
     */
    private AigServiceToken liveToken(Long id, String name, String scopes) {
        AigServiceToken row = new AigServiceToken();
        row.setTokenId(id);
        row.setName(name);
        row.setScopes(scopes);
        row.setStatus("0");
        row.setDelFlag("0");
        return row;
    }

    /**
     * 测试侧独立实现的 SHA-256（刻意不复用被测类的私有方法，否则等于自己证明自己）。
     *
     * @param raw 原文
     * @return 十六进制
     * @throws Exception 算法不可用
     */
    private String sha256(String raw) throws Exception {
        byte[] out = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : out) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

}
