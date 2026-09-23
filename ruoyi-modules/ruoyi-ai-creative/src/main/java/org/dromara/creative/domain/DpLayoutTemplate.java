package org.dromara.creative.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;

/**
 * 视觉模板 dp_layout_template
 *
 * <p>模板本体（HTML/CSS）随渲染服务镜像分发，库里只登记「哪个编码版本可用、校验和是多少、发布状态如何」。
 * 这样模板的「有没有被换过」是可判定的：库里的校验和与渲染服务实时给出的不一致，就拒绝使用该模板。</p>
 *
 * @author creative
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dp_layout_template")
public class DpLayoutTemplate extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private Long id;

    /**
     * 模板编码（如 HERO-H02）
     */
    private String templateCode;

    /**
     * 模板名称
     */
    private String templateName;

    /**
     * 模板类型（COVER/SELLING_POINT/…/PAGE）
     */
    private String templateType;

    /**
     * 模板版本
     */
    private String version;

    /**
     * 可填字段约束 + 校验和（结构化）
     */
    private String schemaJson;

    /**
     * 模板定位（渲染服务内的路径，如 HERO-H02/1.0.0）
     */
    private String htmlTemplateKey;

    /**
     * CSS 存储键（模板内联 CSS 时为空）
     */
    private String cssKey;

    /**
     * 预览图附件ID
     */
    private Long previewFileId;

    /**
     * 状态（DRAFT/PUBLISHED/RETIRED）
     */
    private String status;

    /**
     * 是否启用（0启用 1停用）
     */
    private String enabled;

    /**
     * 备注
     */
    private String remark;

    @TableLogic
    private String delFlag;

}
