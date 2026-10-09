package org.dromara.aigov.token.domain;

import java.util.Set;

/**
 * 已认证的服务身份（机器身份）。
 *
 * <p><b>为什么需要一个独立的类型，而不是直接复用 {@code LoginUser}</b>：
 * 「机器身份」与「人的会话」在契约里是两种 principal 来源，混成一个类型后，
 * 任何一处"顺手"把它当登录用户用（例如填 user_id、走人的数据权限）都会悄悄扩大权限面。
 * 用一个只读 record 明确它"只有名字和 scope"，就没有那个口子。</p>
 *
 * <p><b>principal 为什么是 {@code service:<name>}</b>：契约红线 3 规定 principal
 * 不进客户端契约——即<b>由服务端从认证结果推导</b>。加前缀是为了让审计里一眼能区分
 * "某个人的会话"与"某个服务"。审计与账本记录的都应是这个字符串，而不是客户端传来的任何字段。</p>
 *
 * @param tokenId 令牌ID（审计用）
 * @param name    服务名
 * @param scopes  授权范围（不可变集合；空集 = 不授予任何操作）
 * @author ai-gov
 */
public record AigServiceIdentity(Long tokenId, String name, Set<String> scopes) {

    /**
     * 审计/账本里记录的 principal 字符串。
     *
     * @return {@code service:<name>}
     */
    public String principal() {
        return "service:" + name;
    }

    /**
     * 是否被授予某个权限码。
     *
     * <p>刻意只做<b>精确匹配</b>：不做前缀/通配展开——通配会让"这个令牌到底能干什么"
     * 无法通过读一行数据回答，而最小权限的前提恰恰是权限可被一眼看清。</p>
     *
     * @param scope 权限码（与本仓既有的权限字符串一致，如 {@code aig:model:edit}）
     * @return true = 已授权
     */
    public boolean hasScope(String scope) {
        return scope != null && !scope.isBlank() && scopes.contains(scope);
    }

}
