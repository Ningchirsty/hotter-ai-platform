package org.dromara.aigov.token.service;

import org.dromara.aigov.token.domain.AigServiceIdentity;

/**
 * 把"已认证的服务身份"接入 Sa-Token 会话的适配器。
 *
 * <p><b>为什么要单独抽一层</b>：这是唯一一处依赖 Sa-Token 内部机制（{@code LoginHelper.login}
 * + 平台既有的 {@code SaPermissionImpl} 从会话 {@code LoginUser.menuPermission} 取权限）的代码。
 * 把它抽成接口后，过滤器的决策逻辑可以用 mock 精确测试，
 * 而这层与 Sa-Token 的真实交互则用 {@code SaTokenContextMockUtil} 单独验证——
 * 两类风险分开测，不混成"要么全绿要么全红"。</p>
 *
 * <p><b>机制说明（已在单测中验证）</b>：{@code LoginHelper.login(loginUser, param)} 会把
 * {@code loginUser} 存进 token session；平台的 {@code SaPermissionImpl} 在会话里取到它后
 * 直接返回 {@code menuPermission}。因此只要把 {@code menuPermission} 设为令牌的 scopes，
 * 既有的 {@code @SaCheckPermission} 就<b>原样生效</b>，不需要改动任何共享代码。</p>
 *
 * @author ai-gov
 */
public interface IAigServiceLoginAdapter {

    /**
     * 让当前请求以该服务身份"登录"（使后续 Sa-Token 权限校验可见）。
     *
     * @param identity 已认证的服务身份
     * @param clientId 本次请求的客户端标识（{@code clientid} 请求头）。<b>必须是调用方每次请求都会带的值</b>：
     *                 平台的登录态把 clientid 存进 token 扩展，之后每个请求都要与请求头里的 clientid 一致
     *                 （见 {@code SecurityConfig} 的拦截器），不一致会被判为"客户端ID与Token不匹配"。
     */
    void login(AigServiceIdentity identity, String clientId);

}
