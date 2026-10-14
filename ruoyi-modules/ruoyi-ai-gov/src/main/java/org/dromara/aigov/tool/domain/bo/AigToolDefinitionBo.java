package org.dromara.aigov.tool.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 工具定义入参（设计 §8.2）。
 *
 * @author ai-gov
 */
@Data
public class AigToolDefinitionBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 工具编码（唯一） */
    @NotBlank(message = "工具编码不能为空")
    @Size(max = 64, message = "工具编码长度不能超过 64")
    private String toolCode;

    /** 工具名称 */
    @NotBlank(message = "工具名称不能为空")
    @Size(max = 160, message = "工具名称长度不能超过 160")
    private String toolName;

    /** 用途说明 */
    @Size(max = 500, message = "用途说明长度不能超过 500")
    private String description;

    /** 数据策略说明（不含密钥） */
    @Size(max = 500, message = "数据策略长度不能超过 500")
    private String dataPolicy;

    /** 备注 */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;

}
