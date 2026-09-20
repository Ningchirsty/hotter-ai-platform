package org.dromara.aigov.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * AI 能力调用入参业务对象
 * <p>唯一对外调用入口 {@code IAigInvokeService} 的入参；<b>不得</b>包含任何密钥字段。</p>
 *
 * @author ai-gov
 */
@Data
public class AigInvokeBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 能力编码
     */
    @NotBlank(message = "能力编码不能为空")
    @Size(max = 64, message = "能力编码长度不能超过 64")
    private String capabilityCode;

    /**
     * 本次数据等级（PUBLIC/INTERNAL/RESTRICTED）
     */
    @NotBlank(message = "数据等级不能为空")
    @Pattern(regexp = "^(PUBLIC|INTERNAL|RESTRICTED)$", message = "数据等级只能为 PUBLIC/INTERNAL/RESTRICTED")
    private String dataLevel;

    /**
     * 提示词（业务输入）
     */
    private String prompt;

    /**
     * 结构化业务载荷（如 talent_match 的 skillTags / candidates）
     */
    private Map<String, Object> payload;

}
