package org.dromara.aigov.agent.helper.impl;

import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.agent.helper.IAigSandboxArtifactStore;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.oss.client.OssClient;
import org.dromara.common.oss.factory.OssFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * 基于平台对象存储（{@code ruoyi-common-oss}）的沙箱产物存取（ADR-016）。
 *
 * <p><b>独立前缀</b>（{@code aig-private/sandbox/}）：产物是不可信内容，与包体（{@code package/}）
 * 并列但分开——两者的可见范围与生命周期策略不同，混在一个前缀下，按前缀施加的
 * 生命周期/权限策略就没法分开。</p>
 *
 * <p><b>不缓存 {@link OssClient}</b>：OSS 配置可能在运行期被修改，每次经 {@link OssFactory} 获取
 * （与内容域 {@code ContentOssHelper}、本模块 {@code AigOssPackageBodyStore} 同一写法）。</p>
 *
 * <p><b>不生成任何公开 URL</b>：下载一律经平台接口鉴权后由平台转发（ADR-016 §存储隔离）。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Component
public class AigOssSandboxArtifactStore implements IAigSandboxArtifactStore {

    /**
     * 私有对象键根前缀
     */
    private static final String KEY_ROOT = "aig-private/sandbox";

    /**
     * 读取上限（字节）：入库上限由 {@code aigov.sandbox.artifact.max-file-bytes} 把关（默认 64MB），
     * 这里留一倍余量，同时避免"配置被改大后把堆打满"变成无上限。
     */
    private static final long MAX_READ_BYTES = 256L * 1024 * 1024;

    @Override
    public String keyOf(Long sandboxRunId, String sha256) {
        long runId = sandboxRunId == null ? 0L : sandboxRunId;
        String digest = StringUtils.isBlank(sha256) ? "nohash" : sha256.trim().toLowerCase();
        return KEY_ROOT + "/" + runId + "/" + digest;
    }

    @Override
    public void put(String key, byte[] body) {
        if (body == null) {
            throw new ServiceException("产物字节为空（null），未写入对象存储");
        }
        try {
            client().upload(key, body);
        } catch (Exception e) {
            // 不回显对象键内容部分与任何 URL；只报类型与一句可执行的处置
            log.error("沙箱产物对象写入失败, exception={}", e.getClass().getSimpleName());
            throw new ServiceException("产物字节留存失败（对象存储不可用或未配置）："
                + e.getClass().getSimpleName() + "。请确认 sys_oss_config 有启用中的配置");
        }
    }

    @Override
    public byte[] get(String key) {
        try {
            return client().download(key, (meta, in) -> readAll(in));
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("沙箱产物对象读取失败, exception={}", e.getClass().getSimpleName());
            throw new ServiceException("产物字节读取失败（对象存储不可用或对象不存在）："
                + e.getClass().getSimpleName());
        }
    }

    /**
     * 取 OSS 客户端（每次经工厂获取，不做本地缓存）。
     *
     * @return 客户端
     */
    private OssClient client() {
        return OssFactory.instance();
    }

    /**
     * 读取输入流到字节数组，超限即中止。
     *
     * @param in 输入流
     * @return 字节数组
     */
    private byte[] readAll(InputStream in) {
        try (InputStream input = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            long total = 0;
            int n;
            while ((n = input.read(buf)) != -1) {
                total += n;
                if (total > MAX_READ_BYTES) {
                    throw new ServiceException("产物超过读取上限（256MB），拒绝把整份读入内存");
                }
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("沙箱产物对象读取异常, exception={}", e.getClass().getSimpleName());
            throw new ServiceException("产物字节读取失败：" + e.getClass().getSimpleName());
        }
    }

}
