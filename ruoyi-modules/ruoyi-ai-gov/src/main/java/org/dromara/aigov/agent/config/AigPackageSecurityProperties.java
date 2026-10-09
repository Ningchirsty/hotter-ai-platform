package org.dromara.aigov.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 包体安全检查（{@code aigov.package.security.*}）。
 *
 * <p><b>为什么这类阈值必须是配置而不是写死</b>：它们是"多大的包算异常"的<b>策略</b>——
 * 业务侧开始收真实设计包时体量会变，该改配置而不是改代码再发一版平台。
 * 而这里每一项都只会让判定<b>更严或更松</b>，不会改变"检查什么"。</p>
 *
 * <p><b>默认值刻意保守但可用</b>：声明式 Package 是 Manifest + 资源描述，
 * 2000 个条目 / 256MB 解压总量已经远超真实需求；上限的作用是拦住
 * "把整份数据集或一个 zip bomb 当包传上来"。</p>
 *
 * @author ai-gov
 */
@Data
@Component
@ConfigurationProperties(prefix = "aigov.package.security")
public class AigPackageSecurityProperties {

    /**
     * 是否启用包体安全检查。
     *
     * <p>默认开。<b>关掉它等于回到"包体从头到尾没人看"</b>——那是 F-02 之前的状态，
     * 而这个开关存在的意义是"万一某个客户端的包被误拦，运维能先开门再定位"，
     * 不是"平时可以关着"：因此关闭时服务端会<b>打出 warn</b>（见扫描器）。</p>
     */
    private boolean enabled = true;

    /**
     * 单个包体允许的最大条目数（超过按 zip bomb 形态拒绝）
     */
    private int maxEntries = 2000;

    /**
     * 解压后总字节上限
     */
    private long maxTotalBytes = 256L * 1024 * 1024;

    /**
     * 单个条目解压后字节上限
     */
    private long maxEntryBytes = 64L * 1024 * 1024;

    /**
     * 单条目最大压缩比（解压后 / 压缩后）。超过按 zip bomb 形态拒绝。
     *
     * <p>文本类资源正常压缩比在 10:1 上下，200:1 已经远超正常范围，
     * 而经典 zip bomb 的比值是 1000:1 以上。</p>
     */
    private int maxCompressionRatio = 200;

    /**
     * 每个条目读进来做特征嗅探的字节数上限。
     *
     * <p>只嗅前若干字节：判可执行文件靠的是魔数（前 4 字节就够），
     * 而"全文扫描私钥/凭据"会把注册变成一次全量 IO——那是另一件事，
     * 这里只对明显特征负责，并在说明里写清边界。</p>
     */
    private int sniffBytes = 4096;

}
