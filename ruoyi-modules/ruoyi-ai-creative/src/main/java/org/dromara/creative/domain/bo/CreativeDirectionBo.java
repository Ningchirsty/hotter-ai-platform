package org.dromara.creative.domain.bo;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 视觉方向编辑表单。
 *
 * @author creative
 */
@Data
public class CreativeDirectionBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 方向ID
     */
    private Long id;

    /**
     * 方向名称
     */
    @Size(max = 64, message = "方向名称不能超过 64")
    private String directionName;

    /**
     * 一句话概念
     */
    @Size(max = 500, message = "概念不能超过 500")
    private String concept;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注不能超过 500")
    private String remark;

}
