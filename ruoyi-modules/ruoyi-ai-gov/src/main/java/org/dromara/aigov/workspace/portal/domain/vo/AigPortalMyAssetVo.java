package org.dromara.aigov.workspace.portal.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 门户里的「我的资产」一条（跨域聚合；增量 8）。
 *
 * <p>刻意只带展示字段：**没有**存储键、没有下载直链。下载要走各域自己的入口——
 * 那里的权限仍然生效，从门户给一条直链等于绕过它（与产物台账同一条理由）。</p>
 *
 * @author ai-gov
 */
@Data
public class AigPortalMyAssetVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 资产ID（各域自己的主键）
     */
    private Long assetId;

    /**
     * 资产类型（如 IMAGE/VIDEO/PDF…，各域口径）
     */
    private String assetType;

    /**
     * 来源（UPLOAD/OUTPUT/REFERENCE…，可空）
     */
    private String sourceKind;

    /**
     * 展示名
     */
    private String name;

    /**
     * MIME（内容域不存，留空）
     */
    private String mimeType;

    /**
     * 字节数
     */
    private Long sizeBytes;

    /**
     * 产出/所属任务ID（可空）
     */
    private Long taskId;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

}
