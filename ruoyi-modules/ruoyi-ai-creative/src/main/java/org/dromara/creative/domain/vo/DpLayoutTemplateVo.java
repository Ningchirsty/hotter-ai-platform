package org.dromara.creative.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 视觉模板展示对象（含与渲染服务的实时对账结果）。
 *
 * @author creative
 */
@Data
public class DpLayoutTemplateVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String templateCode;
    private String templateName;
    private String templateType;
    private String version;
    private String htmlTemplateKey;
    private String status;
    private String statusDesc;
    private String enabled;
    private String remark;

    /**
     * 库里记录的校验和（来自 schema_json）
     */
    private String registeredChecksum;

    /**
     * 渲染服务当前的校验和（对账用；渲染服务不可达时为 null）
     */
    private String rendererChecksum;

    /**
     * 校验和是否一致（渲染服务不可达时 null）
     */
    private Boolean checksumMatches;

    /**
     * 渲染服务是否可达
     */
    private Boolean rendererAvailable;

    /**
     * 模板文件字节数（渲染服务给出）
     */
    private Long templateBytes;

}
