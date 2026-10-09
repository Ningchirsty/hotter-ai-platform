package org.dromara.aigov.token.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.token.config.AigServiceTokenProperties;
import org.dromara.aigov.token.domain.AigServiceIdentity;
import org.dromara.aigov.token.holder.AigServiceIdentityHolder;
import org.dromara.aigov.token.service.IAigServiceLoginAdapter;
import org.dromara.aigov.token.service.IAigServiceTokenService;
import org.dromara.common.core.utils.ServletUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * 服务令牌认证过滤器：把 {@code X-Service-Token} 变成一个可被 {@code @SaCheckPermission} 识别的身份。
 *
 * <p><b>为什么用 Filter 而不是拦截器</b>：Sa-Token 的校验在 Spring MVC 拦截器里（过滤之后），
 * 所以认证必须在过滤器阶段完成；反过来若做成拦截器，就得与 Sa-Token 抢执行顺序，
 * 而"顺序错了"这类缺陷往往表现为"偶尔放行"，极难发现。</p>
 *
 * <p><b>四条行为约定（都有对应测试）</b>：</p>
 * <ol>
 *     <li><b>没有该请求头 → 完全不管</b>（继续走原有会话认证，行为与启用前一致）；</li>
 *     <li><b>带了但无效/停用/过期 → 401 且不继续</b>：刻意**不**回退到会话认证——
 *         否则一个过期的机器令牌会悄悄用某个浏览器会话的权限跑起来，是权限混淆；</li>
 *     <li><b>通过 → 以服务身份登录 + 绑定 holder</b>，随后由既有权限注解按 scope 判定；</li>
 *     <li><b>holder 在 finally 清理</b>：线程复用不清理会造成"上一个请求的身份串到下一个请求"。</li>
 * </ol>
 *
 * @author ai-gov
 */
@Slf4j
@RequiredArgsConstructor
public class AigServiceTokenFilter extends OncePerRequestFilter {

    /**
     * User-Agent 请求头名（平台登录时会解析它，且没有判空）。
     */
    private static final String HEADER_USER_AGENT = "User-Agent";

    /**
     * 合成 User-Agent 的前缀（机器调用方没带 UA 时用）。
     */
    private static final String HEADER_USER_AGENT_VALUE_PREFIX = "service-token/";

    private final IAigServiceTokenService tokenService;
    private final IAigServiceLoginAdapter loginAdapter;
    private final AigServiceTokenProperties properties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String raw = request.getHeader(properties.getHeaderName());
        if (StringUtils.isBlank(raw)) {
            chain.doFilter(request, response);
            return;
        }

        // clientid 是平台登录态的既有约定：token 里存一份，之后每个请求都要与请求头一致
        // （SecurityConfig 的拦截器是硬取 StpUtil.getExtra("clientid")，缺了就是 NPE→500）。
        // 这里提前挡住并给出可读原因：否则调用方看到的是一句"客户端ID与Token不匹配"甚至 500，
        // 而真正的原因只是漏了一个请求头。
        String clientId = StringUtils.trimToEmpty(request.getHeader(LoginHelper.CLIENT_KEY));
        if (clientId.isEmpty()) {
            log.warn("服务令牌请求缺少 clientid 头: path={}, ip={}",
                request.getRequestURI(), ServletUtils.getClientIP(request));
            reject(response, "缺少 clientid 请求头：平台要求每次请求携带 clientid，"
                + "且与令牌认证时一致（机器调用同样遵守该约定）");
            return;
        }

        Optional<AigServiceIdentity> authenticated =
            tokenService.authenticate(raw, ServletUtils.getClientIP(request));
        if (authenticated.isEmpty()) {
            // 刻意不告诉调用方"是没找到、被停用、还是过期了"
            log.warn("服务令牌认证失败: path={}, ip={}, prefix={}",
                request.getRequestURI(), ServletUtils.getClientIP(request), safePrefix(raw));
            reject(response, "服务令牌无效、已停用或已过期");
            return;
        }

        AigServiceIdentity identity = authenticated.get();
        AigServiceIdentityHolder.set(identity);
        try {
            // 机器调用方常常不带 User-Agent，而平台在登录时直接 UserAgentUtil.parse(...).getBrowser()
            // （LoginHelper#fillRequestContext 与 UserLoginSuccessListener 都没有判空）→ NPE → 500。
            // 缺失时补一个可读的合成值：既不让平台炸，也让登录态/在线列表里一眼看出是机器身份。
            HttpServletRequest effective = withUserAgentIfAbsent(request, identity.name());
            login(identity, clientId, effective, response);
            if (log.isDebugEnabled()) {
                log.debug("服务身份认证通过: principal={}, uri={}", identity.principal(), request.getRequestURI());
            }
            chain.doFilter(effective, response);
        } finally {
            AigServiceIdentityHolder.clear();
        }
    }

    /**
     * User-Agent 缺失时包一层请求，补上 {@code service-token/<服务名>}。
     *
     * @param request     原请求
     * @param serviceName 服务名
     * @return 原请求，或在缺失 UA 时的包装
     */
    private HttpServletRequest withUserAgentIfAbsent(HttpServletRequest request, String serviceName) {
        if (StringUtils.isNotBlank(request.getHeader(HEADER_USER_AGENT))) {
            return request;
        }
        String synthesized = HEADER_USER_AGENT_VALUE_PREFIX + serviceName;
        return new HttpServletRequestWrapper(request) {
            @Override
            public String getHeader(String name) {
                return HEADER_USER_AGENT.equalsIgnoreCase(name) ? synthesized : super.getHeader(name);
            }
        };
    }

    /**
     * 以服务身份登录，并<b>临时</b>把本次请求绑定到 Spring 的 {@code RequestContextHolder}。
     *
     * <p><b>为什么必须绑定（这一步是真机跑出来的，不是设计时就想到的）</b>：
     * {@code LoginHelper.login} 会触发平台的「登录成功」监听器
     * {@code UserLoginSuccessListener}，它要读 {@code User-Agent} 与客户端 IP——
     * 而该监听器直接写的是 {@code ServletUtils.getRequest().getHeader("User-Agent")}，
     * 没有判空。本过滤器跑在 {@code DispatcherServlet} <b>之前</b>，此时 Spring MVC 还没绑定请求，
     * 于是 {@code getRequest()} 返回 null → NPE → 整个请求 500。</p>
     *
     * <p>普通登录不会踩到：它发生在 Controller 里（MVC 已绑定）。所以这个缺陷
     * <b>只有真的发一次带令牌的请求才会暴露</b>——单测里用的是 mock 上下文，没有这些监听器。</p>
     *
     * <p>登录后立刻复位：后续 MVC 处理会自行绑定，把线程局部变量留给下一个请求是泄漏。</p>
     *
     * @param identity 服务身份
     * @param clientId 本次请求的 clientid（会写进 token 扩展，供平台拦截器比对）
     * @param request  请求
     * @param response 响应
     */
    private void login(AigServiceIdentity identity, String clientId,
                       HttpServletRequest request, HttpServletResponse response) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));
        try {
            loginAdapter.login(identity, clientId);
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    /**
     * 输出 401（JSON，与平台其它错误响应同形）。
     *
     * @param response 响应
     * @param message  给调用方看的原因
     * @throws IOException 写响应失败
     */
    private void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"msg\":\"" + message + "\",\"data\":null}");
    }

    /**
     * 日志里只出现令牌前缀（够指认是哪一把，不足以复用）。
     *
     * @param raw 原始令牌
     * @return 前缀
     */
    private String safePrefix(String raw) {
        return raw.length() <= 12 ? "***" : raw.substring(0, 12) + "…";
    }

}
