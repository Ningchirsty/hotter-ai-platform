package org.dromara.aigov.token.holder;

import org.dromara.aigov.token.domain.AigServiceIdentity;

/**
 * 当前请求的服务身份（线程内可见）。
 *
 * <p><b>为什么还要一个 holder</b>：Sa-Token 会话能回答"有没有权限"，但拿不到"是哪个服务"的
 * 结构化信息（它只知道一个 {@code loginId} 字符串）。审计、账本、日志需要 {@code service:<name>}
 * 这种可读的身份，因此认证通过后把身份放在请求线程内，供下游记录。</p>
 *
 * <p><b>必须在 finally 里 clear</b>：线程池复用线程，不清理会把上一个请求的身份带给下一个请求
 * ——那是"串号"，属于最严重的一类认证缺陷。过滤器已用 try/finally 保证。</p>
 *
 * @author ai-gov
 */
public final class AigServiceIdentityHolder {

    private static final ThreadLocal<AigServiceIdentity> CURRENT = new ThreadLocal<>();

    private AigServiceIdentityHolder() {
    }

    /**
     * 绑定当前请求的服务身份。
     *
     * @param identity 身份
     */
    public static void set(AigServiceIdentity identity) {
        CURRENT.set(identity);
    }

    /**
     * 取当前请求的服务身份。
     *
     * @return 身份；人工会话/未认证请求返回 null
     */
    public static AigServiceIdentity current() {
        return CURRENT.get();
    }

    /**
     * 取当前请求的身份 principal（{@code service:<name>}）。
     *
     * @return principal；非服务身份返回 null
     */
    public static String currentPrincipal() {
        AigServiceIdentity identity = CURRENT.get();
        return identity == null ? null : identity.principal();
    }

    /**
     * 清理（务必在 finally 中调用）。
     */
    public static void clear() {
        CURRENT.remove();
    }

}
