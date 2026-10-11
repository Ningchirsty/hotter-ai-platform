package org.dromara.aigov.agent.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 沙箱产物清单项（<b>账本声明</b> + 是否已入库）。
 *
 * <h3>为什么列表以"声明的清单"为准，而不是以"已入库的行"为准</h3>
 * <p>用户要回答的问题是"这次作业产出的东西都收齐了吗"。只列已入库的行，答不出来——
 * 漏传的那个恰恰不会出现。所以列表由 {@code result.json} 的 {@code artifacts} 驱动，
 * 每一项带 {@link #uploaded}：<b>声明了几个、收了几个、还差哪个</b>，一眼可见。</p>
 *
 * @author ai-gov
 */
@Data
public class AigSandboxArtifactManifestVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 产物相对路径（归一化后；下载与上传都用它作参数）
     */
    private String path;

    /**
     * 账本声明的字节数
     */
    private Long bytes;

    /**
     * 账本声明的 SHA-256
     */
    private String sha256;

    /**
     * 是否已经入库（true=字节已在对象存储且账本有行）
     */
    private Boolean uploaded;

    /**
     * 入库的产物记录ID（未入库时为空）
     */
    private Long artifactId;

    /**
     * 入库时上传方声明的类型（仅记录，未入库时为空）
     */
    private String contentType;

    /**
     * 入库时间（未入库时为空）
     */
    private LocalDateTime uploadedAt;

}
