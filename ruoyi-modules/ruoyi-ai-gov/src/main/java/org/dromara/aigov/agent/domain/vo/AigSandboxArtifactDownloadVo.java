package org.dromara.aigov.agent.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.aigov.agent.domain.AigSandboxArtifactDownload;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 沙箱产物下载记录视图（ADR-016 安全硬要求 ⑤）。
 *
 * @author ai-gov
 */
@Data
@AutoMapper(target = AigSandboxArtifactDownload.class)
public class AigSandboxArtifactDownloadVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 下载记录ID
     */
    private Long downloadId;

    /**
     * 所属沙箱运行记录ID
     */
    private Long sandboxRunId;

    /**
     * 产物记录ID
     */
    private Long artifactId;

    /**
     * 产物相对路径
     */
    private String path;

    /**
     * 本次下发的 SHA-256
     */
    private String sha256;

    /**
     * 本次下发的字节数
     */
    private Long bytes;

    /**
     * 下载人ID
     */
    private Long downloadedBy;

    /**
     * 来源 IP
     */
    private String clientIp;

    /**
     * 下载时间
     */
    private LocalDateTime createTime;

}
