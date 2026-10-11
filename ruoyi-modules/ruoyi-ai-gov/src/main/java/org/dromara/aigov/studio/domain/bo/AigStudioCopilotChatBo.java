package org.dromara.aigov.studio.domain.bo;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 对话式 Copilot 的一轮对话入参（附件 07 增量 6「对话式 Copilot」）。
 *
 * @author ai-gov
 */
@Data
public class AigStudioCopilotChatBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户消息（必填）
     */
    @NotBlank(message = "对话内容不能为空")
    @Size(max = 2000, message = "对话内容长度不能超过 2000")
    private String message;

    /**
     * 本次数据等级（必填；它决定能不能外发——测错等级等于没测，见 {@code AigInvokeBo} 的说明）
     */
    @NotBlank(message = "数据等级不能为空")
    @Pattern(regexp = "^(PUBLIC|INTERNAL|RESTRICTED|STRICT)$",
        message = "数据等级只能为 PUBLIC/INTERNAL/RESTRICTED/STRICT")
    private String dataLevel;

    /**
     * 本次调用可接受的最高成本（单次，可选；单位＝美元 USD）
     */
    @DecimalMin(value = "0", message = "本次预算不能为负数")
    @Digits(integer = 10, fraction = 8, message = "本次预算整数位最多 10 位、小数位最多 8 位")
    private BigDecimal maxCost;

}
