package org.dromara.aigov.tool.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 工具版本入参（设计 §7.5、§8.2）。
 *
 * <p>{@code executorRef} 可空：空表示"已登记、待接入"，此时工具不可执行（设计 §4.1）。</p>
 *
 * @author ai-gov
 */
@Data
public class AigToolDefinitionVersionBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 工具编码 */
    @NotBlank(message = "工具编码不能为空")
    @Size(max = 64, message = "工具编码长度不能超过 64")
    private String toolCode;

    /** 版本号 */
    @NotBlank(message = "版本号不能为空")
    @Size(max = 32, message = "版本号长度不能超过 32")
    private String version;

    /** 执行器引用（可空=待接入） */
    @Size(max = 128, message = "执行器引用长度不能超过 128")
    private String executorRef;

    /** 操作类型 */
    @NotBlank(message = "操作类型不能为空")
    @Size(max = 32, message = "操作类型长度不能超过 32")
    private String operationType;

    /** 输入 Schema */
    @Size(max = 2000, message = "输入 Schema 长度不能超过 2000")
    private String inputSchema;

    /** 输出 Schema */
    @Size(max = 2000, message = "输出 Schema 长度不能超过 2000")
    private String outputSchema;

    /** 超时（毫秒） */
    private Integer timeoutMs;

    /** 最大尝试次数 */
    private Integer maxAttempts;

    /** 调用所需权限点 */
    @Size(max = 128, message = "权限点长度不能超过 128")
    private String permissionPoint;

    /** 数据等级上限 */
    @Size(max = 32, message = "数据等级长度不能超过 32")
    private String dataLevelMax;

    /** 备注 */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;

}
