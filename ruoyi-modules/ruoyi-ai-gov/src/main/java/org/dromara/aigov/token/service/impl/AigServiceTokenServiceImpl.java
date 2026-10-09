package org.dromara.aigov.token.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.token.domain.AigServiceIdentity;
import org.dromara.aigov.token.domain.AigServiceToken;
import org.dromara.aigov.token.domain.vo.AigServiceTokenVo;
import org.dromara.aigov.token.mapper.AigServiceTokenMapper;
import org.dromara.aigov.token.service.IAigServiceTokenService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 服务身份（机器令牌）实现。
 *
 * <p><b>三处刻意的实现选择</b>：</p>
 * <ol>
 *     <li><b>只存 SHA-256</b>，不存明文、不用可逆加密：令牌是高熵随机串，
 *         哈希足够，且"库被读走也拿不到可用凭据"；</li>
 *     <li><b>认证失败返回 empty 而不是抛异常</b>：令牌错/停用/过期都是正常结果，
 *         且失败原因不对调用方区分（避免"这个令牌存在但过期了"这类信息泄露）；</li>
 *     <li><b>last_used_at 做节流更新</b>：每次认证都写库会让"每个请求一次写放大"，
 *         因此只在超过 {@link #LAST_USED_UPDATE_INTERVAL_SECONDS} 秒未更新时才写，
 *         且写失败不影响认证结果（审计是次要目标，不能因为它失败就把服务拒之门外）。</li>
 * </ol>
 *
 * @author ai-gov
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AigServiceTokenServiceImpl implements IAigServiceTokenService {

    /**
     * 令牌明文前缀。加前缀是为了"一眼能看出这是本平台的服务令牌"
     * （便于在日志/聊天里指认，也便于日后做泄露扫描）。
     */
    private static final String TOKEN_PREFIX = "hsvc_";

    /**
     * 随机部分字节数：32 字节 = 256 位熵。用 Base64URL（无填充）编码，约 43 字符。
     */
    private static final int TOKEN_RANDOM_BYTES = 32;

    /**
     * 人工指认用的前缀长度（含 {@code hsvc_}）：只用于展示，**不参与认证**。
     */
    private static final int DISPLAY_PREFIX_LENGTH = 14;

    /**
     * last_used_at 的最小更新间隔（秒）。避免每个请求都写一次库。
     */
    private static final long LAST_USED_UPDATE_INTERVAL_SECONDS = 60L;

    private static final String STATUS_NORMAL = "0";
    private static final String STATUS_DISABLED = "1";
    private static final String DEL_FLAG_NORMAL = "0";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AigServiceTokenMapper tokenMapper;

    @Override
    public IssuedToken issue(String name, String scopes, LocalDateTime expiresAt, String remark) {
        String trimmed = StringUtils.trimToEmpty(name);
        if (trimmed.isEmpty()) {
            throw new ServiceException("服务名不能为空");
        }
        if (trimmed.length() > 64) {
            throw new ServiceException("服务名过长（最长 64）");
        }
        Long exists = tokenMapper.selectCount(new LambdaQueryWrapper<AigServiceToken>()
            .eq(AigServiceToken::getName, trimmed));
        if (exists != null && exists > 0) {
            throw new ServiceException("服务名已存在（未删除的令牌里同名只允许一把）：" + trimmed);
        }

        String plaintext = generateToken();
        AigServiceToken entity = new AigServiceToken();
        entity.setName(trimmed);
        entity.setTokenHash(sha256Hex(plaintext));
        entity.setTokenPrefix(plaintext.substring(0, DISPLAY_PREFIX_LENGTH));
        entity.setScopes(normalizeScopes(scopes));
        entity.setExpiresAt(expiresAt);
        entity.setStatus(STATUS_NORMAL);
        entity.setDelFlag(DEL_FLAG_NORMAL);
        entity.setRemark(remark);
        tokenMapper.insert(entity);
        log.info("签发服务令牌: tokenId={}, name={}, scopes=[{}], expiresAt={}",
            entity.getTokenId(), trimmed, entity.getScopes(), expiresAt);
        return new IssuedToken(entity.getTokenId(), trimmed, plaintext, entity.getTokenPrefix());
    }

    @Override
    public Optional<AigServiceIdentity> authenticate(String rawToken, String clientIp) {
        if (StringUtils.isBlank(rawToken)) {
            return Optional.empty();
        }
        AigServiceToken token = tokenMapper.selectOne(new LambdaQueryWrapper<AigServiceToken>()
            .eq(AigServiceToken::getTokenHash, sha256Hex(rawToken.trim())));
        if (token == null) {
            return Optional.empty();
        }
        // 停用 / 过期：都返回 empty，且**不向调用方区分原因**
        if (!STATUS_NORMAL.equals(token.getStatus())) {
            return Optional.empty();
        }
        LocalDateTime expiresAt = token.getExpiresAt();
        if (expiresAt != null && !expiresAt.isAfter(LocalDateTime.now())) {
            return Optional.empty();
        }
        touch(token, clientIp);
        return Optional.of(new AigServiceIdentity(token.getTokenId(), token.getName(),
            parseScopes(token.getScopes())));
    }

    @Override
    public boolean revoke(Long tokenId) {
        if (tokenId == null) {
            return false;
        }
        AigServiceToken update = new AigServiceToken();
        update.setStatus(STATUS_DISABLED);
        int rows = tokenMapper.update(update, new LambdaQueryWrapper<AigServiceToken>()
            .eq(AigServiceToken::getTokenId, tokenId)
            .eq(AigServiceToken::getStatus, STATUS_NORMAL));
        if (rows > 0) {
            log.info("吊销服务令牌: tokenId={}", tokenId);
        }
        return rows > 0;
    }

    @Override
    public List<AigServiceTokenVo> list() {
        return tokenMapper.selectVoList(new LambdaQueryWrapper<AigServiceToken>()
            .orderByDesc(AigServiceToken::getCreateTime));
    }

    /**
     * 记录"最近一次成功认证"（节流；失败只记日志，不影响认证结果）。
     *
     * @param token    认证通过的令牌行
     * @param clientIp 来源 IP
     */
    private void touch(AigServiceToken token, String clientIp) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime last = token.getLastUsedAt();
        if (last != null && last.plusSeconds(LAST_USED_UPDATE_INTERVAL_SECONDS).isAfter(now)) {
            return;
        }
        try {
            AigServiceToken update = new AigServiceToken();
            update.setTokenId(token.getTokenId());
            update.setLastUsedAt(now);
            update.setLastUsedIp(clientIp);
            tokenMapper.updateById(update);
        } catch (Exception e) {
            log.warn("更新服务令牌 last_used 失败（不影响本次认证）tokenId={}：{}",
                token.getTokenId(), e.getMessage());
        }
    }

    /**
     * 生成明文令牌：{@code hsvc_} + 32 字节随机（Base64URL 无填充）。
     *
     * @return 明文令牌
     */
    private String generateToken() {
        byte[] bytes = new byte[TOKEN_RANDOM_BYTES];
        RANDOM.nextBytes(bytes);
        return TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 归一化 scopes：去空白、去空项、去重，并保持书写顺序。
     *
     * @param raw 原始逗号分隔串
     * @return 归一化后的串（可能为空串 = 不授予任何操作）
     */
    private String normalizeScopes(String raw) {
        return String.join(",", parseScopes(raw));
    }

    /**
     * 解析 scopes 为不可变集合。
     *
     * @param raw 原始串
     * @return 权限码集合
     */
    private Set<String> parseScopes(String raw) {
        Set<String> result = new LinkedHashSet<>();
        if (StringUtils.isBlank(raw)) {
            return result;
        }
        for (String part : raw.split(",")) {
            String s = part.trim();
            if (!s.isEmpty()) {
                result.add(s);
            }
        }
        return result;
    }

    /**
     * SHA-256 十六进制（小写）。
     *
     * @param raw 原文
     * @return 64 字符十六进制
     */
    private String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] out = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(out.length * 2);
            for (byte b : out) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // JDK 必然带 SHA-256；走到这里说明运行环境异常，直接失败比"静默降级"安全
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

}
