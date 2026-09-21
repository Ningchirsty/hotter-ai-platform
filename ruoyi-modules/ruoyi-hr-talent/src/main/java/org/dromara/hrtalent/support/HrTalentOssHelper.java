package org.dromara.hrtalent.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.oss.client.OssClient;
import org.dromara.common.oss.factory.OssFactory;
import org.dromara.hrtalent.config.HrTalentProperties;
import org.dromara.hrtalent.constant.HrTalentConstants;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;

/**
 * 招聘与人才管理附件对象存储访问封装。
 * <p>基于 {@code ruoyi-common-oss} 的 {@link OssClient} / {@link OssFactory}，
 * 供 {@code hr_recruit_attachment} 与 {@code hr_talent_resume} 复用。</p>
 *
 * <p>安全与稳定性约定（设计文档 §9.4 / §15.3）：</p>
 * <ul>
 *     <li>数据库只保存 OSS 对象 ID（对象键），<b>不保存长期公网 URL</b>；
 *     需要访问时按需生成短时预签名地址。</li>
 *     <li>附件写入 {@code hr-talent-private/} 私有前缀对象，<b>不登记</b> {@code sys_oss}，
 *     避免持有通用 OSS 下载权限的账号绕过人才库授权。</li>
 *     <li>日志中禁止出现对象键内容部分、预签名 URL 与对象存储地址，仅记录异常类型。</li>
 *     <li>不缓存 {@link OssClient}：每次通过工厂获取，保证后台修改 OSS 配置后立即生效。</li>
 * </ul>
 *
 * @author hr-talent
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HrTalentOssHelper {

    /**
     * 默认预签名有效期（秒级短时地址）。
     */
    private static final Duration DEFAULT_PRESIGN_TTL = Duration.ofSeconds(120);

    /**
     * 招聘与人才管理业务配置。
     */
    private final HrTalentProperties hrTalentProperties;

    /**
     * 获取 OSS 客户端：配置了 {@code hrtalent.oss-config-key} 时使用指定配置，否则使用默认配置。
     *
     * @return OSS 客户端
     */
    private OssClient client() {
        String configKey = hrTalentProperties.getOssConfigKey();
        return StringUtils.isBlank(configKey) ? OssFactory.instance() : OssFactory.instance(configKey);
    }

    /* ------------------------------------------------------------------ 对象读写 ------------------------------------------------------------------ */

    /**
     * 将输入流写入指定对象键。
     *
     * @param key  对象键（数据库保存的对象 ID）
     * @param in   输入流
     * @param size 字节数
     * @throws ServiceException 写入失败
     */
    public void put(String key, InputStream in, long size) {
        try {
            client().upload(key, in, size);
        } catch (Exception e) {
            // 仅记录异常类型：异常消息可能包含 endpoint 或对象地址
            log.error("招聘附件对象写入失败, exception={}", e.getClass().getSimpleName());
            throw new ServiceException("文件存储失败");
        }
    }

    /**
     * 将字节数组写入指定对象键。
     *
     * @param key  对象键
     * @param data 字节数组
     * @throws ServiceException 写入失败
     */
    public void put(String key, byte[] data) {
        if (data == null || data.length == 0) {
            throw new ServiceException("文件存储失败");
        }
        try {
            client().upload(key, data);
        } catch (Exception e) {
            log.error("招聘附件对象写入失败, exception={}", e.getClass().getSimpleName());
            throw new ServiceException("文件存储失败");
        }
    }

    /**
     * 受控流式下载到输出流；调用方必须先完成资源级鉴权。
     *
     * @param key 对象键
     * @param out 输出流
     * @throws ServiceException 读取失败
     */
    public void get(String key, OutputStream out) {
        try {
            client().download(key, out);
        } catch (Exception e) {
            log.error("招聘附件对象读取失败, exception={}", e.getClass().getSimpleName());
            throw new ServiceException("文件读取失败");
        }
    }

    /**
     * 生成短时预签名下载地址（使用配置的 {@code hrtalent.presign-ttl}）。
     * <p>返回值<b>不得</b>写入日志；失败返回 {@code null}，调用方应降级为流式下载。</p>
     *
     * @param key 对象键
     * @return 预签名地址；失败返回 null
     */
    public String presign(String key) {
        return presign(key, hrTalentProperties.getPresignTtl());
    }

    /**
     * 生成短时预签名下载地址。
     * <p>有效期为空时使用 120 秒的默认短时值；即便传入超长有效期，
     * 也会被收敛到 {@code hrtalent.presign-ttl} 配置值。</p>
     *
     * @param key 对象键
     * @param ttl 期望有效期
     * @return 预签名地址；失败返回 null
     */
    public String presign(String key, Duration ttl) {
        try {
            return client().presignGetUrl(key, normalizeTtl(ttl));
        } catch (Exception e) {
            log.warn("招聘附件对象预签名失败, exception={}", e.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * 删除对象；失败只记日志，不影响业务调用结果。
     *
     * @param key 对象键
     */
    public void delete(String key) {
        try {
            client().delete(key);
        } catch (Exception e) {
            log.warn("招聘附件对象删除失败, exception={}", e.getClass().getSimpleName());
        }
    }

    /* ------------------------------------------------------------------ 对象键构造 ------------------------------------------------------------------ */

    /**
     * 构造通用附件对象键：{@code hr-talent-private/attachment/{bizType}/{bizId}/{attachmentId}/v{version}/original.{ext}}。
     *
     * @param bizType      业务类型编码（如 recruitment / talent）
     * @param bizId        业务主键
     * @param attachmentId 附件ID
     * @param version      版本号
     * @param ext          扩展名（可带点，可为空）
     * @return 对象键
     */
    public String buildAttachmentKey(String bizType, Long bizId, Long attachmentId, int version, String ext) {
        String type = StringUtils.isBlank(bizType) ? "common" : sanitizeSegment(bizType);
        return HrTalentConstants.OBJECT_KEY_ROOT + "/" + HrTalentConstants.KEY_SEG_ATTACHMENT
            + "/" + type + "/" + bizId + "/" + attachmentId
            + "/v" + Math.max(version, 1) + "/original." + normalizeExt(ext);
    }

    /**
     * 构造简历对象键：{@code hr-talent-private/resume/{talentId}/{resumeId}/v{version}/original.{ext}}。
     * <p>新文件新增版本，不覆盖旧文件（设计文档 §9.4）。</p>
     *
     * @param talentId 人才ID
     * @param resumeId 简历ID
     * @param version  版本号
     * @param ext      扩展名（可带点，可为空）
     * @return 对象键
     */
    public String buildResumeKey(Long talentId, Long resumeId, int version, String ext) {
        return HrTalentConstants.OBJECT_KEY_ROOT + "/" + HrTalentConstants.KEY_SEG_RESUME
            + "/" + talentId + "/" + resumeId
            + "/v" + Math.max(version, 1) + "/original." + normalizeExt(ext);
    }

    /**
     * 构造导出文件对象键：{@code hr-talent-private/exports/{exportId}/{HrTalentConstants.EXPORT_FILE_NAME}}。
     *
     * @param exportId 导出任务ID
     * @return 对象键
     */
    public String buildExportKey(Long exportId) {
        return HrTalentConstants.OBJECT_KEY_ROOT + "/" + HrTalentConstants.KEY_SEG_EXPORTS
            + "/" + exportId + "/" + HrTalentConstants.EXPORT_FILE_NAME;
    }

    /* ------------------------------------------------------------------ 内部方法 ------------------------------------------------------------------ */

    /**
     * 收敛预签名有效期：为空或非正数用默认值，且不得超过配置的 {@code presign-ttl}。
     *
     * @param ttl 期望有效期
     * @return 实际使用的有效期
     */
    private Duration normalizeTtl(Duration ttl) {
        Duration configured = hrTalentProperties.getPresignTtl();
        Duration upperBound = (configured == null || configured.isZero() || configured.isNegative())
            ? DEFAULT_PRESIGN_TTL : configured;
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return upperBound;
        }
        return ttl.compareTo(upperBound) > 0 ? upperBound : ttl;
    }

    /**
     * 归一化扩展名：去点、去空白、转小写，为空时降级为 {@code bin}。
     *
     * @param ext 原始扩展名
     * @return 归一化后的扩展名
     */
    private String normalizeExt(String ext) {
        if (StringUtils.isBlank(ext)) {
            return "bin";
        }
        String normalized = ext.trim().toLowerCase().replace(".", "").replace("/", "").replace("\\", "");
        return normalized.isEmpty() ? "bin" : normalized;
    }

    /**
     * 归一化对象键路径分段，剔除路径穿越字符。
     *
     * @param segment 原始分段
     * @return 安全分段
     */
    private String sanitizeSegment(String segment) {
        String normalized = segment.trim().toLowerCase().replace("..", "").replace("/", "").replace("\\", "");
        return normalized.isEmpty() ? "common" : normalized;
    }

}
