package org.dromara.aigov.studio.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Map;

/**
 * 训练台测试调用入参（专题 C §C3.1 Playground / §C4 沙箱）。
 *
 * <p><b>为什么 {@code dataLevel} 必填</b>：数据等级决定路由能不能外发（严格级禁止外发）。
 * 若默认成 INTERNAL，就会出现"按 INTERNAL 测通了、按 RESTRICTED 上线"——
 * 那样的测试恰好证明了**错误的那件事**。所以要求调用方明确声明这次按什么等级测。</p>
 *
 * @author ai-gov
 */
@Data
public class AigStudioTestRunBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 数据等级（{@code AigDataLevelEnum} 的 code）
     */
    @NotBlank(message = "数据等级不能为空（它决定能不能外发，测错等级等于没测）")
    @Size(max = 16, message = "数据等级长度不能超过 16")
    private String dataLevel;

    /**
     * 测试输入（会拼在 Prompt 之后作为这一次的输入）
     */
    @NotBlank(message = "测试输入不能为空")
    private String input;

    /**
     * 结构化入参（可选；与 input 一起交给网关）
     */
    private Map<String, Object> payload;

    /**
     * 本次测试的成本上限（可选；单位=美元 USD）
     */
    private BigDecimal maxCost;

}
