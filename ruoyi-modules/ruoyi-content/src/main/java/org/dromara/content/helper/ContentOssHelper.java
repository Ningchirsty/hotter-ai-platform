package org.dromara.content.helper;

import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.oss.client.OssClient;
import org.dromara.common.oss.factory.OssFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * 内容生产对象存储访问助手。
 *
 * <p>与人才库同口径的安全约定：</p>
 * <ul>
 *     <li><b>不缓存 {@link OssClient}</b>：OSS 配置可能在运行期被修改，每次经
 *     {@link OssFactory} 获取（工厂内部已有校验与缓存）。</li>
 *     <li>资料写入 {@code content-private/} 私有前缀对象，<b>不登记 {@code sys_oss}</b>，
 *     避免持有 {@code system:oss:download} 的账号绕过内容模块的授权直接取到产品资料。</li>
 *     <li>日志禁止出现对象键内容部分与任何 URL。</li>
 * </ul>
 *
 * @author content
 */
@Slf4j
@Component
public class ContentOssHelper {

    /**
     * 私有对象键根前缀
     */
    private static final String KEY_ROOT = "content-private";

    /**
     * 单次读取到内存的对象上限（字节）。
     * <p>解析需要把文件整份读入内存，故设上限避免大文件把堆打满——
     * 超限时给出可读提示，而不是 OOM。</p>
     */
    private static final long MAX_READ_BYTES = 50L * 1024 * 1024;

    /**
     * 获取 OSS 客户端。
     *
     * @return OSS 客户端
     */
    private OssClient client() {
        return OssFactory.instance();
    }

    /**
     * 上传字节数组。
     *
     * @param key  对象键
     * @param data 字节数组
     * @throws ServiceException 上传失败
     */
    public void put(String key, byte[] data) {
        if (data == null) {
            throw new ServiceException("文件存储失败");
        }
        try {
            client().upload(key, data);
        } catch (Exception e) {
            log.error("内容资料对象写入失败, exception={}", e.getClass().getSimpleName());
            throw new ServiceException("文件存储失败");
        }
    }

    /**
     * 读取对象全部字节。
     *
     * @param key 对象键
     * @return 字节数组
     * @throws ServiceException 读取失败或超过上限
     */
    public byte[] getBytes(String key) {
        try {
            return client().download(key, (meta, in) -> readAll(key, in));
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("内容资料对象读取失败, exception={}", e.getClass().getSimpleName());
            throw new ServiceException("文件读取失败");
        }
    }

    /**
     * 读取输入流到字节数组，超限即中止。
     *
     * @param key 对象键（仅用于日志排查，不写内容）
     * @param in  输入流
     * @return 字节数组
     */
    private byte[] readAll(String key, InputStream in) {
        try (InputStream input = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            long total = 0;
            int n;
            while ((n = input.read(buf)) != -1) {
                total += n;
                if (total > MAX_READ_BYTES) {
                    throw new ServiceException("文件过大，暂不支持在线解析（上限 50MB）");
                }
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("内容资料读取异常, exception={}", e.getClass().getSimpleName());
            throw new ServiceException("文件读取失败");
        }
    }

    /**
     * 删除对象；失败只记日志，不影响业务。
     *
     * @param key 对象键
     */
    public void delete(String key) {
        try {
            client().delete(key);
        } catch (Exception e) {
            log.warn("内容资料对象删除失败, exception={}", e.getClass().getSimpleName());
        }
    }

    /**
     * 附件对象键：{@code content-private/{taskId}/{fileId}/original.{ext}}。
     *
     * @param taskId 任务ID
     * @param fileId 附件ID
     * @param ext    扩展名（小写，无点）
     * @return 对象键
     */
    public String buildFileKey(Long taskId, Long fileId, String ext) {
        String suffix = StringUtils.isBlank(ext) ? "bin" : ext.toLowerCase();
        return KEY_ROOT + "/" + taskId + "/" + fileId + "/original." + suffix;
    }

}
