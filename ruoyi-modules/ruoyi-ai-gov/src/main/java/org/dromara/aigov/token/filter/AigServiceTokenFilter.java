package org.dromara.aigov.token.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
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

        Optional<AigServiceIdentity> authenticated =
            tokenService.authenticate(raw, ServletUtils.getClientIP(request));
        if (authenticated.isEmpty()) {
            // 刻意不告诉调用方"是没找到、被停用、还是过期了"
            log.warn("服务令牌认证失败: path={}, ip={}, prefix={}",
                request.getRequestURI(), ServletUtils.getClientIP(request), safePrefix(raw));
            rejectUnauthorized(response);
            return;
        }

        AigServiceIdentity identity = authenticated.get();
        AigServiceIdentityHolder.set(identity);
        try {
            loginAdapter.login(identity);
            if (log.isDebugEnabled()) {
                log.debug("服务身份认证通过: principal={}, uri={}", identity.principal(), request.getRequestURI());
            }
            chain.doFilter(request, response);
        } finally {
            AigServiceIdentityHolder.clear();
        }
    }

    /**
     * 输出 401（JSON，与平台其它错误响应同形）。
     *
     * @param response 响应
     * @throws IOException 写响应失败
     */
    private void rejectUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"msg\":\"服务令牌无效、已停用或已过期\",\"data\":null}");
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
