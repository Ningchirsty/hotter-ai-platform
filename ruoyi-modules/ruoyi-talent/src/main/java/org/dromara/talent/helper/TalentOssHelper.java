package org.dromara.talent.helper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.oss.client.OssClient;
import org.dromara.common.oss.factory.OssFactory;
import org.dromara.talent.config.TalentProperties;
import org.dromara.talent.constant.TalentConstants;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;

/**
 * 人才库对象存储访问助手。
 * <p>
 * 安全与稳定性约定：
 * <ul>
 *     <li><b>不缓存 {@link OssClient}</b>：OSS 配置可能在运行期被后台修改，每次通过
 *     {@link OssFactory#instance()} / {@link OssFactory#instance(String)} 获取（工厂内部已有配置校验与缓存）。</li>
 *     <li>附件与导出文件写入 {@code talent-private/} 私有前缀对象，<b>不登记</b> {@code sys_oss}，
 *     避免任何持有 {@code system:oss:download} 的账号绕过人才库授权下载简历。</li>
 *     <li>日志中禁止出现对象存储 URL / 预签名 URL / 对象键内容部分。</li>
 * </ul>
 *
 * @author talent
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TalentOssHelper {

    /**
     * 默认预签名有效期（秒）。
     */
    private static final Duration DEFAULT_PRESIGN_TTL = Duration.ofSeconds(120);

    /**
     * 人才库配置。
     */
    private final TalentProperties talentProperties;

    /**
     * 获取 OssClient：配置了 {@code talent.oss-config-key} 用指定配置，否则用默认配置。
     * <p>
     * 刻意不缓存实例，保证配置变更后立即生效。
     *
     * @return OSS 客户端
     */
    private OssClient client() {
        String configKey = talentProperties.getOssConfigKey();
        return StringUtils.isBlank(configKey) ? OssFactory.instance() : OssFactory.instance(configKey);
    }

    /**
     * 上传字节流到指定对象键。
     *
     * @param key  对象键
     * @param in   输入流
     * @param size 字节数
     * @throws ServiceException 上传失败
     */
    public void put(String key, InputStream in, long size) {
        try {
            client().upload(key, in, size);
        } catch (Exception e) {
            // 只记录异常类型，异常消息可能包含 endpoint / 对象 URL
            log.error("人才库对象写入失败, exception={}", e.getClass().getSimpleName());
            throw new ServiceException("文件存储失败");
        }
    }

    /**
     * 上传字节数组到指定对象键。
     *
     * @param key  对象键
     * @param data 字节数组
     * @throws ServiceException 上传失败
     */
    public void put(String key, byte[] data) {
        if (data == null) {
            throw new ServiceException("文件存储失败");
        }
        put(key, new ByteArrayInputStream(data), data.length);
    }

    /**
     * 受控流式下载到输出流。
     *
     * @param key 对象键
     * @param out 输出流
     * @throws ServiceException 读取失败
     */
    public void get(String key, OutputStream out) {
        try {
            client().download(key, out);
        } catch (Exception e) {
            log.error("人才库对象读取失败, exception={}", e.getClass().getSimpleName());
            throw new ServiceException("文件读取失败");
        }
    }

    /**
     * 生成短时预签名 URL（默认 120 秒）。
     * <p>
     * 失败时返回 null，调用方降级为流式下载；返回值<b>不得</b>写入日志。
     *
     * @param key 对象键
     * @param ttl 有效期，为空用默认值
     * @return 预签名 URL，失败返回 null
     */
    public String presign(String key, Duration ttl) {
        try {
            return client().presignGetUrl(key, ttl == null ? DEFAULT_PRESIGN_TTL : ttl);
        } catch (Exception e) {
            log.warn("人才库对象预签名失败, exception={}", e.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * 删除对象；失败只记日志，不影响业务（对象键为内部标识，不含 URL）。
     *
     * @param key 对象键
     */
    public void delete(String key) {
        try {
            client().delete(key);
        } catch (Exception e) {
            log.warn("人才库对象删除失败, exception={}", e.getClass().getSimpleName());
        }
    }

    /**
     * 附件对象键：{@code talent-private/{talentId}/{attachmentId}/v{version}/original.{ext}}。
     *
     * @param talentId     人才ID
     * @param attachmentId 附件ID
     * @param version      版本号
     * @param ext          扩展名（小写，无点）
     * @return 对象键
     */
    public String buildAttachmentKey(Long talentId, Long attachmentId, int version, String ext) {
        String suffix = StringUtils.isBlank(ext) ? "bin" : ext.toLowerCase();
        return TalentConstants.OBJECT_KEY_ROOT + "/" + talentId + "/" + attachmentId
            + "/v" + version + "/original." + suffix;
    }

    /**
     * 导出文件对象键：{@code talent-private/exports/{exportId}/talent-ledger.xlsx}。
     *
     * @param exportId 导出任务ID
     * @return 对象键
     */
    public String buildExportKey(Long exportId) {
        return TalentConstants.OBJECT_KEY_ROOT + "/" + TalentConstants.KEY_SEG_EXPORTS
            + "/" + exportId + "/talent-ledger.xlsx";
    }

}
