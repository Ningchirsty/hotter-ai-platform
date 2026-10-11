package org.dromara.aigov.agent.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.aigov.agent.domain.AigSandboxArtifact;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 沙箱产物记录视图（<b>已入库</b>的那一份）。
 *
 * <p>注意 {@link #contentType} 是"上传方声明的类型"，它只作记录；
 * 下载响应里一律 {@code application/octet-stream}（ADR-016 已裁定"永不非附件下载"）。</p>
 *
 * @author ai-gov
 */
@Data
@AutoMapper(target = AigSandboxArtifact.class)
public class AigSandboxArtifactVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 产物记录ID
     */
    private Long artifactId;

    /**
     * 所属沙箱运行记录ID
     */
    private Long sandboxRunId;

    /**
     * 产物相对路径
     */
    private String path;

    /**
     * 字节数（服务端实算）
     */
    private Long bytes;

    /**
     * SHA-256（服务端实算）
     */
    private String sha256;

    /**
     * 对象存储键（服务端生成）
     */
    private String objectKey;

    /**
     * 上传方声明的类型（仅记录）
     */
    private String contentType;

    /**
     * 登记人ID
     */
    private Long uploadedBy;

    /**
     * 登记时间
     */
    private LocalDateTime createTime;

}
