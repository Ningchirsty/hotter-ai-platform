package org.dromara.creative.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 交付产物（V0.2 R30，文档 §26 Renderer Hub）。
 *
 * @author creative
 */
@Data
public class DeliveryArtifactVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 清单ID */
    private Long id;

    /** 项目ID */
    private Long taskId;

    /** 交付类型 */
    private String deliveryType;

    /** 渲染器编码 */
    private String renderer;

    /** 渲染器中文名（页面直接显示，不用前端再维护一份映射） */
    private String rendererName;

    /** 版本 */
    private Integer version;

    /** 产物张数 */
    private Integer imageCount;

    /** 产物字节合计 */
    private Long totalBytes;

    /** 清单校验和 */
    private String checksum;

    /** 备注（缺图屏等） */
    private String remark;

    /** 产物明细（从清单解析出来，页面直接渲染表格） */
    private List<Map<String, Object>> products;

    /** 交付包文件名（下载时用） */
    private String downloadName;

    /** 生成时间 */
    private java.time.LocalDateTime createTime;
}
