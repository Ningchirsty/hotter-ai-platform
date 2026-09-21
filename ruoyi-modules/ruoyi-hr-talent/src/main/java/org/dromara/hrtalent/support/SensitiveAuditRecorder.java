package org.dromara.hrtalent.support;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.ServletUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.hrtalent.domain.entity.RecruitSensitiveAudit;
import org.dromara.hrtalent.mapper.RecruitSensitiveAuditMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 敏感操作审计记录器（设计文档 §15.2、§21.9）。
 * <p>电话明文查看、背调明细查看、附件预览/下载、导出与管理员例外跳转等动作，
 * <b>统一</b>通过本记录器写入 {@code hr_recruit_sensitive_audit}，不要在业务代码里各自拼装实体。</p>
 *
 * <p><b>本记录器永不抛出异常</b>：审计写入失败只记错误日志，不能因此让业务操作失败或回滚。</p>
 *
 * <p><b>安全约束</b>：{@code detailJson} 只允许传脱敏后的结构化明细，
 * 禁止传入电话明文、简历正文、背调明细或对象存储长期地址。</p>
 *
 * @author hr-talent
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SensitiveAuditRecorder {

    /**
     * 事件类型：查看电话明文。
     */
    public static final String EVENT_PHONE_VIEW = "phone_view";

    /**
     * 事件类型：查看背调明细。
     */
    public static final String EVENT_BACKGROUND_VIEW = "background_view";

    /**
     * 事件类型：附件预览。
     */
    public static final String EVENT_ATTACHMENT_PREVIEW = "attachment_preview";

    /**
     * 事件类型：附件下载。
     */
    public static final String EVENT_ATTACHMENT_DOWNLOAD = "attachment_download";

    /**
     * 事件类型：附件删除（设计文档 §15.2 要求留痕）。
     */
    public static final String EVENT_ATTACHMENT_DELETE = "attachment_delete";

    /**
     * 事件类型：数据导出。
     */
    public static final String EVENT_EXPORT = "export";

    /**
     * 事件类型：管理员例外阶段跳转。
     */
    public static final String EVENT_STAGE_OVERRIDE = "stage_override";

    /**
     * 操作结果：成功。
     */
    public static final String RESULT_SUCCESS = "success";

    /**
     * 操作结果：被拒绝。
     */
    public static final String RESULT_DENIED = "denied";

    /**
     * 操作结果：失败。
     */
    public static final String RESULT_FAILED = "failed";

    /**
     * 业务类型：人才主档。
     */
    public static final String BIZ_TALENT = "talent";

    /**
     * 业务类型：应聘记录。
     */
    public static final String BIZ_APPLICATION = "application";

    /**
     * 业务类型：面试。
     */
    public static final String BIZ_INTERVIEW = "interview";

    /**
     * 业务类型：背调。
     */
    public static final String BIZ_BACKGROUND = "background";

    /**
     * 业务类型：附件。
     */
    public static final String BIZ_ATTACHMENT = "attachment";

    /**
     * 业务类型：审计记录本身（导出审计日志时留痕）。
     */
    public static final String BIZ_AUDIT = "audit";

    /**
     * 业务类型：录用邀约。
     */
    public static final String BIZ_OFFER = "offer";

    private final RecruitSensitiveAuditMapper sensitiveAuditMapper;

    /**
     * 记录一次敏感操作。
     *
     * @param eventType 事件类型，取本类 {@code EVENT_*} 常量
     * @param bizType   业务类型，取本类 {@code BIZ_*} 常量
     * @param bizId     业务对象ID，可为空
     * @param purpose   操作事由/用途
     * @param result    操作结果，取本类 {@code RESULT_*} 常量
     */
    public void record(String eventType, String bizType, Long bizId, String purpose, String result) {
        record(eventType, bizType, bizId, purpose, result, null);
    }

    /**
     * 记录一次敏感操作（带脱敏明细）。
     *
     * @param eventType  事件类型
     * @param bizType    业务类型
     * @param bizId      业务对象ID
     * @param purpose    操作事由/用途
     * @param result     操作结果
     * @param detailJson 脱敏后的结构化明细 JSON，禁止包含敏感明文
     */
    public void record(String eventType, String bizType, Long bizId, String purpose, String result, String detailJson) {
        try {
            RecruitSensitiveAudit audit = new RecruitSensitiveAudit();
            audit.setEventType(eventType);
            audit.setBizType(bizType);
            audit.setBizId(bizId);
            audit.setPurpose(truncate(purpose, 255));
            audit.setResult(StringUtils.isBlank(result) ? RESULT_SUCCESS : result);
            audit.setDetailJson(detailJson);
            audit.setEventTime(LocalDateTime.now());
            audit.setOperatorId(currentUserId());
            audit.setOperatorName(truncate(currentUserName(), 64));
            audit.setIp(truncate(currentIp(), 64));
            audit.setUserAgent(truncate(currentUserAgent(), 255));
            sensitiveAuditMapper.insert(audit);
        } catch (Exception e) {
            // 审计失败不得影响业务操作；只记录错误类型与事件类型，避免把敏感上下文写进日志
            log.error("敏感操作审计写入失败, eventType={}, bizType={}, bizId={}", eventType, bizType, bizId, e);
        }
    }

    /**
     * 取当前登录用户ID；无登录态（如定时任务）时返回 null。
     *
     * @return 用户ID或 null
     */
    private Long currentUserId() {
        try {
            return LoginHelper.getUserId();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 取当前登录账号；无登录态时返回 null。
     *
     * @return 账号或 null
     */
    private String currentUserName() {
        try {
            return LoginHelper.getUsername();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 取客户端IP；非 Web 上下文时返回 null。
     *
     * @return IP 或 null
     */
    private String currentIp() {
        try {
            return ServletUtils.getClientIP();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 取客户端 User-Agent；非 Web 上下文时返回 null。
     *
     * @return User-Agent 或 null
     */
    private String currentUserAgent() {
        try {
            HttpServletRequest request = ServletUtils.getRequest();
            return request == null ? null : request.getHeader("User-Agent");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 按列长度截断，避免超长导致插入失败。
     *
     * @param value  原值
     * @param maxLen 最大长度
     * @return 截断后的值
     */
    private String truncate(String value, int maxLen) {
        if (value == null || value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen);
    }

}
