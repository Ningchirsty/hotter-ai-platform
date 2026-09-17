package org.dromara.talent.helper;

import cn.hutool.crypto.digest.DigestUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.ServletUtils;
import org.dromara.common.core.utils.SpringUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.talent.domain.TlSensitiveAudit;
import org.dromara.talent.enums.AuditActionEnum;
import org.dromara.talent.enums.AuditTargetTypeEnum;
import org.dromara.talent.mapper.TlSensitiveAuditMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 人才库敏感操作审计记录器。
 * <p>
 * 关键设计（验收点）：
 * <ul>
 *     <li><b>独立 Spring Bean</b>：不能写成某个 Service 的私有方法，否则 {@code @Transactional} 失效。</li>
 *     <li>{@code REQUIRES_NEW}：独立于调用方事务，业务回滚不会丢审计，审计失败也不会回滚业务。</li>
 *     <li>异常只记 {@code log.error} 不外抛，审计绝不阻断业务。</li>
 *     <li>只落 IP / UA 的 SHA-256 前 32 位摘要；{@code reason} 入库前做手机号兜底打码，
 *     严禁写入完整手机号、Token、简历正文、对象存储 URL、下载内容。</li>
 * </ul>
 *
 * @author talent
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TalentAuditRecorder {

    /**
     * 摘要长度（SHA-256 前 32 位）。
     */
    private static final int DIGEST_LENGTH = 32;

    /**
     * reason 字段长度上限（tl_sensitive_audit.reason varchar(255)）。
     */
    private static final int REASON_MAX_LENGTH = 255;

    /**
     * 审计 Mapper。
     */
    private final TlSensitiveAuditMapper auditMapper;

    /**
     * 记录敏感操作。
     * <p>
     * {@code REQUIRES_NEW} 会挂起调用方事务并开启独立事务；即使业务随后回滚，审计记录也已落库。
     * 若审计写库本身失败，只记录错误日志（不抛异常），保证业务不被审计故障中断。
     *
     * @param action     动作
     * @param targetType 对象类型
     * @param targetId   对象ID
     * @param talentId   关联人才ID
     * @param success    是否成功
     * @param reason     原因 / 失败摘要（会被截断并做手机号打码）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void record(AuditActionEnum action, AuditTargetTypeEnum targetType, Long targetId, Long talentId,
                       boolean success, String reason) {
        try {
            TlSensitiveAudit audit = new TlSensitiveAudit();
            audit.setOperatorId(LoginHelper.getUserId());
            audit.setOperatorName(LoginHelper.getUsername());
            audit.setAction(action == null ? null : action.getCode());
            audit.setTargetType(targetType == null ? null : targetType.getCode());
            audit.setTargetId(targetId);
            audit.setTalentId(talentId);
            audit.setResult(success ? "0" : "1");
            audit.setReason(sanitizeReason(reason));
            audit.setIpDigest(digest(clientIp()));
            audit.setUserAgentDigest(digest(userAgent()));
            audit.setOperateTime(LocalDateTime.now());
            auditMapper.insert(audit);
        } catch (Exception e) {
            // 审计失败绝不中断业务，但必须可见：记录动作与对象，不记录 reason 原文之外的请求内容
            log.error("人才库敏感审计写入失败, action={}, targetType={}, targetId={}, talentId={}",
                action, targetType, targetId, talentId, e);
        }
    }

    /**
     * 附件下载审计快捷方法。
     * <p>
     * 通过 {@link SpringUtils#getAopProxy(Object)} 调用自身，避免自调用导致
     * {@link Transactional} 与 {@code REQUIRES_NEW} 失效（与基线
     * {@code SysOssServiceImpl#listByIds} 的写法一致）。
     *
     * @param attachmentId 附件ID
     * @param talentId     人才ID
     * @param success      是否成功
     * @param reason       原因 / 失败摘要
     */
    public void recordDownload(Long attachmentId, Long talentId, boolean success, String reason) {
        SpringUtils.getAopProxy(this)
            .record(AuditActionEnum.DOWNLOAD, AuditTargetTypeEnum.ATTACHMENT, attachmentId, talentId, success, reason);
    }

    /**
     * 导出审计快捷方法。
     *
     * @param exportId 导出任务ID
     * @param success  是否成功
     * @param reason   原因 / 失败摘要
     */
    public void recordExport(Long exportId, boolean success, String reason) {
        SpringUtils.getAopProxy(this)
            .record(AuditActionEnum.EXPORT, AuditTargetTypeEnum.EXPORT, exportId, null, success, reason);
    }

    /**
     * 取客户端 IP 原始值（仅供摘要使用，绝不落库 / 落日志）。
     *
     * @return 客户端 IP，取不到返回 null
     */
    private String clientIp() {
        try {
            String ip = ServletUtils.getClientIP();
            if (StringUtils.isNotBlank(ip)) {
                return ip;
            }
            // 兜底：直接取原生 request（代理链头部已由 ServletUtils 处理）
            HttpServletRequest request = ServletUtils.getRequest();
            return request == null ? null : request.getRemoteAddr();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 取 User-Agent 原始值（仅供摘要使用）。
     *
     * @return User-Agent，取不到返回 null
     */
    private String userAgent() {
        try {
            HttpServletRequest request = ServletUtils.getRequest();
            return request == null ? null : request.getHeader("User-Agent");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 摘要：SHA-256 后取前 32 位。
     *
     * @param raw 原始值
     * @return 摘要，入参为空返回 null
     */
    private String digest(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        String hex = DigestUtil.sha256Hex(raw);
        return hex.length() > DIGEST_LENGTH ? hex.substring(0, DIGEST_LENGTH) : hex;
    }

    /**
     * reason 兜底清洗：截断 + 打码，防止调用方误传完整手机号。
     *
     * @param reason 原始原因
     * @return 可安全入库的原因
     */
    private String sanitizeReason(String reason) {
        if (StringUtils.isBlank(reason)) {
            return null;
        }
        // 兜底：即便调用方误把手机号拼进 reason，也按 1[3-9]xxxxxxxxx 打码
        String safe = reason.replaceAll("1[3-9]\\d{9}", "1**********");
        return StringUtils.substring(safe, 0, REASON_MAX_LENGTH);
    }

}
