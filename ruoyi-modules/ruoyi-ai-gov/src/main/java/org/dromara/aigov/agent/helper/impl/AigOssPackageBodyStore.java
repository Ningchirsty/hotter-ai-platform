package org.dromara.aigov.agent.helper.impl;

import lombok.extern.slf4j.Slf4j;
import org.dromara.aigov.agent.helper.IAigPackageBodyStore;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.oss.client.OssClient;
import org.dromara.common.oss.factory.OssFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * 基于平台对象存储（{@code ruoyi-common-oss}）的包体留存实现。
 *
 * <p><b>不缓存 {@link OssClient}</b>：OSS 配置可能在运行期被修改，
 * 每次经 {@link OssFactory} 获取（工厂内部已有校验与缓存）——与内容域的
 * {@code ContentOssHelper} 同一写法。</p>
 *
 * <p><b>失败必须响亮</b>：这里的异常一律带可读原因并向上抛。若写成「失败就记个日志、继续登记」，
 * 结果就是「控制台上说包体已留存、对象存储里其实什么都没有」——这类「不生效也不报错」
 * 正是本模块一路在防的东西。调用方（{@code AigPackageServiceImpl}）据此让整笔上传失败。</p>
 *
 * @author ai-gov
 */
@Slf4j
@Component
public class AigOssPackageBodyStore implements IAigPackageBodyStore {

    /**
     * 私有对象键根前缀。
     *
     * <p>与内容域（{@code content-private/}）并列而不是共用：两者的可见范围与授权口径不同，
     * 混在一个前缀下，按前缀做的生命周期/权限策略就没法分开施加。</p>
     */
    private static final String KEY_ROOT = "aig-private";

    /**
     * 包体读取上限（字节）：包体上限本来就是 64MB，留一倍余量避免把堆打满。
     */
    private static final long MAX_READ_BYTES = 128L * 1024 * 1024;

    @Override
    public String buildKey(String packageCode, String version, String bodySha256) {
        String code = StringUtils.isBlank(packageCode) ? "unknown" : packageCode.trim();
        String ver = StringUtils.isBlank(version) ? "unknown" : version.trim();
        String digest = StringUtils.isBlank(bodySha256)
            ? "nohash" : bodySha256.trim().toLowerCase();
        String shortHash = StringUtils.substring(digest, 0, 16);
        return KEY_ROOT + "/package/" + code + "/" + ver + "/body-" + shortHash + ".bin";
    }

    @Override
    public void put(String key, byte[] body) {
        if (body == null || body.length == 0) {
            throw new ServiceException("包体为空，未写入对象存储");
        }
        try {
            client().upload(key, body);
        } catch (Exception e) {
            // 不回显对象键内容部分与任何 URL；只报类型与一句可执行的处置
            log.error("包体对象写入失败, exception={}", e.getClass().getSimpleName());
            throw new ServiceException("包体留存失败（对象存储不可用或未配置）："
                + e.getClass().getSimpleName()
                + "。请确认 sys_oss_config 有启用中的配置；若本环境不需要留存包体，"
                + "把 aigov.package.store-body 置为 false");
        }
    }

    @Override
    public byte[] get(String key) {
        try {
            return client().download(key, (meta, in) -> readAll(in));
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("包体对象读取失败, exception={}", e.getClass().getSimpleName());
            throw new ServiceException("包体读取失败（对象存储不可用或对象不存在）："
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
                    throw new ServiceException("包体超过读取上限（128MB），拒绝把整份读入内存");
                }
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("包体对象读取异常, exception={}", e.getClass().getSimpleName());
            throw new ServiceException("包体读取失败：" + e.getClass().getSimpleName());
        }
    }

}
