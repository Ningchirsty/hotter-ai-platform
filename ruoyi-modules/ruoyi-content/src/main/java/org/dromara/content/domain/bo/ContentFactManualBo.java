package org.dromara.content.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 人工录入事实业务对象。
 *
 * @author content
 */
@Data
public class ContentFactManualBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 任务ID
     */
    @NotNull(message = "任务ID不能为空")
    private Long taskId;

    /**
     * 事实字段编码（需与 cp_gate_rule.field_code 对齐）
     */
    @NotBlank(message = "事实字段编码不能为空")
    @Size(max = 64, message = "字段编码长度不能超过 64")
    private String fieldCode;

    /**
     * 事实值
     */
    @NotBlank(message = "事实值不能为空")
    @Size(max = 500, message = "事实值长度不能超过 500")
    private String value;

    /**
     * 说明（录入依据）
     */
    @Size(max = 500, message = "说明长度不能超过 500")
    private String remark;

}
