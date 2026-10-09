package org.dromara.aigov.token.filter;

import cn.dev33.satoken.context.mock.SaTokenContextMockUtil;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.stp.StpUtil;
import org.dromara.aigov.token.config.AigServiceTokenProperties;
import org.dromara.aigov.token.domain.AigServiceIdentity;
import org.dromara.aigov.token.holder.AigServiceIdentityHolder;
import org.dromara.aigov.token.service.IAigServiceLoginAdapter;
import org.dromara.aigov.token.service.IAigServiceTokenService;
import org.dromara.aigov.token.service.impl.AigServiceLoginAdapterImpl;
import org.dromara.common.core.utils.ServletUtils;
import org.dromara.common.satoken.core.service.SaPermissionImpl;
import org.dromara.common.satoken.utils.LoginHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 服务令牌过滤器的行为锁定测试。
 *
 * <p><b>它守的是什么</b>：这是身份认证的接入点，且它要"借用"Sa-Token 的会话机制才能让既有的
 * {@code @SaCheckPermission} 生效。所以本测试刻意<b>不 mock Sa-Token</b>：
 * 用 {@code SaTokenContextMockUtil} 起一个真实的 Sa-Token 上下文、装上平台真实的
 * {@code SaPermissionImpl}，然后验证"认证通过后，权限校验确实按令牌的 scope 判定"。
 * 只断言"我调用了某个方法"是不够的——真正会出错的地方是"机制没生效，但代码看起来对"。</p>
 *
 * @author ai-gov
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AigServiceTokenFilterTest {

    /**
     * 平台既有的客户端标识（本机内测实例的 sys_client 里那个）。
     */
    private static final String CLIENT_ID = "e5cd7e4891bf95d1d19206ce24a7b32e";

    private IAigServiceTokenService tokenService;
    private AigServiceTokenFilter filter;
    private AigServiceTokenProperties properties;

    @BeforeEach
    void setUp() {
        SaTokenContextMockUtil.setMockContext();
        // Spring 启动时由 SaTokenConfig 设置；单测里手工装上平台真实实现
        cn.dev33.satoken.SaManager.setStpInterface(new SaPermissionImpl());
        // extra（clientid）只在配置了 jwt-secret-key 时可用，否则 StpUtil.getExtra 抛 ApiDisabled。
        // 生产 application.yml 里配了，所以这里也补上，否则测不到真实会用到的路径。
        cn.dev33.satoken.SaManager.getConfig().setJwtSecretKey(
            "r78-unit-test-secret-key-0123456789abcdef0123456789abcdef");
        tokenService = mock(IAigServiceTokenService.class);
        properties = new AigServiceTokenProperties();
        filter = new AigServiceTokenFilter(tokenService, new AigServiceLoginAdapterImpl(properties), properties);
    }

    @AfterEach
    void tearDown() {
        AigServiceIdentityHolder.clear();
        SaTokenContextMockUtil.clearContext();
    }

    @Test
    @DisplayName("不带服务令牌请求头 → 完全不管（行为与启用前一致）")
    void requestsWithoutHeaderPassThroughUntouched() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/aigov/model/page");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> {
            chainCalled.set(true);
            assertFalse(StpUtil.isLogin(), "没有令牌就不该有登录态");
            assertNull(AigServiceIdentityHolder.current(), "没有令牌就不该有服务身份");
        });

        assertTrue(chainCalled.get(), "没有令牌的请求必须继续往下走（交给原有会话认证）");
        assertEquals(200, response.getStatus());
    }

    @Test
    @DisplayName("★ 令牌无效/停用/过期 → 401 且不继续；不回退到会话认证")
    void invalidTokenIsRejectedAndDoesNotFallBackToSession() throws Exception {
        when(tokenService.authenticate(any(), any())).thenReturn(Optional.empty());
        MockHttpServletRequest request = serviceRequest("/aigov/model/page", "hsvc_deadbeef");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> chainCalled.set(true));

        assertFalse(chainCalled.get(), "认证失败绝不能继续（否则会回退到某个会话的权限）");
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"code\":401"), "响应体应是平台的错误 JSON 形状");
    }

    @Test
    @DisplayName("★ 带令牌但缺 clientid → 401 且点明原因；且不打库（快速失败）")
    void missingClientIdIsRejectedWithReadableReason() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/aigov/model/page");
        request.addHeader("X-Service-Token", "hsvc_valid");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> chainCalled.set(true));

        assertFalse(chainCalled.get());
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("clientid"), "必须点明缺的是 clientid");
        // 缺 clientid 的请求一定走不通，没必要先去查一次库
        verify(tokenService, never()).authenticate(any(), any());
    }

    @Test
    @DisplayName("★ 令牌有效 → 身份对、scope 变成权限、越权被拒（默认拒绝）、holder 用后即清")
    void validTokenBecomesIdentityAndScopesBecomePermissions() throws Exception {
        AigServiceIdentity identity = new AigServiceIdentity(4242L, "vibeposter-worker",
            Set.of("aig:capability:query"));
        when(tokenService.authenticate(any(), any())).thenReturn(Optional.of(identity));

        MockHttpServletRequest request = serviceRequest("/aigov/invoke/brief_precheck", "hsvc_valid");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> loginIdInChain = new AtomicReference<>();
        AtomicReference<String> principalInChain = new AtomicReference<>();
        AtomicReference<Boolean> scopedPermissionOk = new AtomicReference<>();
        AtomicReference<Boolean> foreignPermissionDenied = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> {
            loginIdInChain.set(StpUtil.getLoginIdAsString());
            principalInChain.set(AigServiceIdentityHolder.currentPrincipal());
            // 机制验证：会话里的权限 = 令牌 scope ⇒ 既有注解会按 scope 判定
            scopedPermissionOk.set(canCheck("aig:capability:query"));
            foreignPermissionDenied.set(!canCheck("aig:model:edit"));
        });

        assertEquals(200, response.getStatus(), "认证通过后请求应正常继续");
        assertEquals("service:4242", loginIdInChain.get(), "会话登录标识应为 service:<tokenId>");
        assertEquals("service:vibeposter-worker", principalInChain.get(), "holder 应提供可读 principal");
        // 注：clientid 是否真的写进了 token 扩展，这里测不了——StpUtil.getExtra 需要 sa-token-jwt
        // （只在 ruoyi-admin 的依赖树里），本模块单测 JVM 没有它，调用会抛 ApiDisabled。
        // 这一条由本地端到端验证覆盖：带上服务令牌但故意用不匹配的 clientid，
        // 平台拦截器会以"客户端ID与Token不匹配"拒绝——能拒绝就说明扩展里确实存了值。
        assertTrue(scopedPermissionOk.get(), "令牌授权范围内的权限校验必须通过");
        assertTrue(foreignPermissionDenied.get(), "★ 未授权的权限必须被拒（默认拒绝）");
        assertNull(AigServiceIdentityHolder.current(), "★ 请求结束后必须清理 holder（线程复用会串号）");
    }

    @Test
    @DisplayName("★ scope 为空 → 拿到身份但什么权限都没有（默认拒绝，不是默认全给）")
    void emptyScopesGrantNoPermission() throws Exception {
        AigServiceIdentity identity = new AigServiceIdentity(5001L, "no-scope", Set.of());
        when(tokenService.authenticate(any(), any())).thenReturn(Optional.of(identity));

        MockHttpServletRequest request = serviceRequest("/aigov/model/page", "hsvc_noscope");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Boolean> anyPermissionDenied = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> anyPermissionDenied.set(!canCheck("aig:model:edit")));

        assertEquals(200, response.getStatus(), "身份有效，只是没有权限");
        assertTrue(anyPermissionDenied.get(), "空 scopes 不得授予任何权限");
    }

    @Test
    @DisplayName("★ 同一身份的并发请求互不踢下线（isConcurrent=true），两次都按 scope 授权")
    void concurrentRequestsForTheSameIdentityDoNotInvalidateEachOther() throws Exception {
        AigServiceIdentity identity = new AigServiceIdentity(6001L, "shared-svc", Set.of("aig:capability:query"));
        when(tokenService.authenticate(any(), any())).thenReturn(Optional.of(identity));

        AtomicBoolean firstHadPermission = new AtomicBoolean(false);
        String first = tokenValueAfterFilterPass("hsvc_shared", firstHadPermission);
        String second = tokenValueAfterFilterPass("hsvc_shared", null);

        assertNotNull(first, "第一次请求应拿到登录态");
        assertNotNull(second, "第二次请求应拿到登录态");
        assertTrue(firstHadPermission.get(), "第一次请求应按 scope 授权");
        // 机制验证：第二次登录不得让第一次的会话失效，否则并发中正在执行的那个请求会莫名 401
        assertTrue(StpUtil.getStpLogic().isValidToken(first), "★ 第二次请求之后，第一次的 token 仍必须有效");
        assertEquals("service:6001", String.valueOf(StpUtil.getStpLogic().getLoginIdByToken(first)),
            "第一次的 token 仍应属于同一个服务身份");
        // 实测事实（不是待修的 bug，别再"优化"它）：两次拿到的是不同的 token 值，即每次认证都会新建会话，
        // 堆积量由 properties.sessionTimeoutSeconds（默认 300 秒）限幅。本用例只锁定"互不踢下线"，
        // 因为那才是会变成线上间歇 401 的性质。
    }

    @Test
    @DisplayName("★ 登录期间必须绑定请求上下文，且用后复位（否则平台登录监听器 NPE → 线上 500）")
    void requestContextIsBoundDuringLoginAndResetAfterwards() throws Exception {
        AigServiceIdentity identity = new AigServiceIdentity(7001L, "ctx-svc", Set.of("aig:capability:query"));
        when(tokenService.authenticate(any(), any())).thenReturn(Optional.of(identity));
        AtomicReference<jakarta.servlet.http.HttpServletRequest> seenDuringLogin = new AtomicReference<>();
        AtomicReference<String> clientIdSeen = new AtomicReference<>();
        // 用假适配器替换真实登录：真实登录需要 Spring 容器（它触发平台的登录成功监听器）
        IAigServiceLoginAdapter fakeAdapter = (id, cid) -> {
            seenDuringLogin.set(ServletUtils.getRequest());
            clientIdSeen.set(cid);
        };
        AigServiceTokenFilter filterWithFakeLogin =
            new AigServiceTokenFilter(tokenService, fakeAdapter, properties);

        MockHttpServletRequest request = serviceRequest("/aigov/model/page", "hsvc_ctx");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filterWithFakeLogin.doFilter(request, response, (req, res) -> { });

        // 这条断言对应真机上真实发生过的 NPE：
        // UserLoginSuccessListener 直接写 ServletUtils.getRequest().getHeader("User-Agent")，
        // 过滤器阶段若没绑定 RequestContextHolder，getRequest() 就是 null
        assertSame(request, seenDuringLogin.get(),
            "★ 登录期间 ServletUtils.getRequest() 必须正是本次请求（否则监听器 NPE → 500）");
        assertNull(ServletUtils.getRequest(), "登录后必须复位：线程复用不能把绑定留给下一个请求");
        assertEquals(CLIENT_ID, clientIdSeen.get(), "登录时必须把本次请求的 clientid 交给适配器");
    }

    @Test
    @DisplayName("★ 机器调用方不带 User-Agent → 补合成值，登录态里 browser 仍有效（平台 UA 解析没判空）")
    void missingUserAgentIsSynthesized() throws Exception {
        AigServiceIdentity identity = new AigServiceIdentity(8001L, "cron-svc", Set.of("aig:capability:query"));
        when(tokenService.authenticate(any(), any())).thenReturn(Optional.of(identity));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/aigov/model/page");
        request.addHeader("X-Service-Token", "hsvc_noua");
        request.addHeader(LoginHelper.CLIENT_KEY, CLIENT_ID);
        // 刻意不加 User-Agent：模拟"机器调用方只用 curl/最小客户端"的情形
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> userAgentInChain = new AtomicReference<>();
        AtomicReference<String> browserInLoginUser = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> {
            userAgentInChain.set(((jakarta.servlet.http.HttpServletRequest) req).getHeader("User-Agent"));
            // 平台在登录时就是拿 UA 去填这两个字段的；它们有值 = 合成 UA 真的可被解析
            browserInLoginUser.set(LoginHelper.getLoginUser().getBrowser());
        });

        assertEquals(200, response.getStatus(), "缺 UA 不该让请求失败");
        assertEquals("service-token/cron-svc", userAgentInChain.get(), "合成值要能指认是哪个服务");
        assertNotNull(browserInLoginUser.get(), "登录态里的 browser 必须有值（平台 UA 解析没有判空）");
    }

    /**
     * 构造一个带服务令牌与 clientid 的请求（clientid 是平台登录态的既有约定，两者缺一不可）。
     *
     * @param uri      请求地址
     * @param rawToken 原始服务令牌
     * @return 请求
     */
    private MockHttpServletRequest serviceRequest(String uri, String rawToken) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.addHeader("X-Service-Token", rawToken);
        request.addHeader(LoginHelper.CLIENT_KEY, CLIENT_ID);
        // 真实 HTTP 客户端都会带 UA；不带的情况由 missingUserAgentIsSynthesized 单独覆盖
        request.addHeader("User-Agent", "r78-test-client/1.0");
        return request;
    }

    /**
     * 走一遍过滤器并返回本次请求拿到的 Sa-Token 令牌值。
     *
     * @param raw            原始令牌
     * @param permissionSeen 非空时，在过滤链内记录"scope 对应的权限是否通过"
     * @return 令牌值
     * @throws Exception 过滤器异常
     */
    private String tokenValueAfterFilterPass(String raw, AtomicBoolean permissionSeen) throws Exception {
        MockHttpServletRequest request = serviceRequest("/aigov/model/page", raw);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> tokenValue = new AtomicReference<>();
        filter.doFilter(request, response, (req, res) -> {
            tokenValue.set(StpUtil.getTokenValue());
            if (permissionSeen != null) {
                permissionSeen.set(canCheck("aig:capability:query"));
            }
        });
        assertEquals(200, response.getStatus(), "认证通过的请求应正常继续");
        return tokenValue.get();
    }

    /**
     * 用平台的权限实现判断当前会话是否有某权限（与 {@code @SaCheckPermission} 同源）。
     *
     * @param permission 权限码
     * @return true = 有
     */
    private boolean canCheck(String permission) {
        try {
            StpUtil.checkPermission(permission);
            return true;
        } catch (NotPermissionException e) {
            return false;
        }
    }

}
