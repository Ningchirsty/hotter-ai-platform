package org.dromara.aigov.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 沙箱产物入库限额（{@code aigov.sandbox.artifact.*}，ADR-016）。
 *
 * <h3>为什么单文件上限默认是 64MB、而不是设计草案里提的 512MB</h3>
 * <p>草案按"与 scratch 1G 上限相称"建议 512MB。但**下载是平台把字节读进内存再写给浏览器**的
 * （`ResponseEntity&lt;byte[]&gt;`），512MB 的默认值意味着一次下载就能把堆打满——那是把配额事故
 * 写进了默认配置。所以这里取一个**能在内存里安全服务**的上限，并把理由写在默认值旁边：
 * 要放开到更大，先把服务端改成流式（`StreamingResponseBody` + 对象存储直传），再调这个值。</p>
 *
 * <p>三个限额都是**拒绝式**的（超限即拒），与"保留策略=超限删最旧"不同：后者是对象存储侧的
 * 生命周期策略，管的是"旧的产物字节什么时候清掉"，不改变"能不能入库"。</p>
 *
 * @author ai-gov
 */
@Data
@Component
@ConfigurationProperties(prefix = "aigov.sandbox.artifact")
public class AigSandboxArtifactProperties {

    /**
     * 单文件上限（字节），默认 64MB。
     *
     * <p>见类注释：这个值同时是"入库限额"与"下载时一次性读入内存的上限"，两者必须一致，
     * 否则会出现"能入库、下载必炸堆"的配置。</p>
     */
    private long maxFileBytes = 64L * 1024 * 1024;

    /**
     * 每个沙箱作业最多入库的文件数（默认 200）
     */
    private int maxFilesPerRun = 200;

    /**
     * 每个沙箱作业入库字节总量上限（默认 512MB）
     */
    private long maxTotalBytesPerRun = 512L * 1024 * 1024;

}
