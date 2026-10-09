package org.dromara.aigov.token.service;

import org.dromara.aigov.token.domain.AigServiceIdentity;
import org.dromara.aigov.token.domain.vo.AigServiceTokenVo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 服务身份（机器令牌）服务：V2 阶段 1 的前置能力（ADR-002 / 契约 §7.3）。
 *
 * <p>一句话边界：<b>本服务只负责"证明你是谁"与"你能做什么"，不负责"这次调用能不能做"</b>。
 * 后者仍由既有的权限校验（{@code @SaCheckPermission}）与路由/策略层决定——
 * 把两者混在一起，就会出现"令牌里写了 scope 就绕过了策略"的漏洞。</p>
 *
 * @author ai-gov
 */
public interface IAigServiceTokenService {

    /**
     * 签发一把令牌。<b>明文只在此刻返回一次</b>，之后任何接口都取不回来。
     *
     * @param name      服务名（须在未删除的令牌里唯一）
     * @param scopes    逗号分隔的权限码；空 = 不授予任何操作
     * @param expiresAt 到期时间（null = 不过期）
     * @param remark    备注：给谁用、为什么需要这些 scope
     * @return 签发结果（含明文）
     */
    IssuedToken issue(String name, String scopes, LocalDateTime expiresAt, String remark);

    /**
     * 认证一把令牌。
     *
     * <p><b>不抛异常</b>：认证失败是正常结果（令牌错、已停用、已过期），不是系统错误。
     * 抛异常会让调用方用 try/catch 表达"没通过"，且容易把失败原因泄露给外部。</p>
     *
     * @param rawToken 客户端提交的明文令牌
     * @param clientIp 来源 IP（仅用于审计）
     * @return 身份；未通过则 empty
     */
    Optional<AigServiceIdentity> authenticate(String rawToken, String clientIp);

    /**
     * 吊销（停用）一把令牌：<b>不删行</b>，保留审计痕迹。
     *
     * @param tokenId 令牌ID
     * @return true = 本次确实停用了；false = 不存在或已停用
     */
    boolean revoke(Long tokenId);

    /**
     * 列出全部未删除的令牌（不含哈希）。
     *
     * @return 列表
     */
    List<AigServiceTokenVo> list();

    /**
     * 签发结果。明文只在这个 record 里出现一次。
     *
     * <p>{@code scopes} 返回的是<b>归一化后真正落库</b>的串（而不是调用方提交的原文），
     * 否则"界面上看到的授权范围"与"库里生效的范围"会在空格/重复项上悄悄不一致。</p>
     *
     * @param tokenId        令牌ID
     * @param name           服务名
     * @param plaintextToken 明文令牌（**唯一一次**可见）
     * @param tokenPrefix    前缀，用于日后人工指认
     * @param scopes         归一化后落库的权限码串
     * @param expiresAt      到期时间（null = 不过期）
     */
    record IssuedToken(Long tokenId, String name, String plaintextToken, String tokenPrefix,
                       String scopes, LocalDateTime expiresAt) {
    }

}
