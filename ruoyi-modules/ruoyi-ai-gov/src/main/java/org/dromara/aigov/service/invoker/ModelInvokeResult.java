package org.dromara.aigov.service.invoker;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 模型调用结果（SPI 出参）。
 * <p>调用器<b>不得抛出异常</b>，一律把失败转成本对象；{@code output} 为结构化 JSON 字符串，
 * 是否合格由调用编排按能力 {@code output_schema} 判定。</p>
 *
 * @author ai-gov
 */
@Data
public class ModelInvokeResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 结构化输出（JSON 字符串）
     */
    private String output;

    /**
     * 错误摘要（失败时必填，禁止写入敏感内容）
     */
    private String errorSummary;

    /**
     * 消耗 Token 数（调用方无法获知时为 null，不得编造）
     */
    private Long tokensUsed;

    /**
     * 本次成本（调用方无法获知时为 null）
     */
    private BigDecimal cost;

    /**
     * 耗时（毫秒）
     */
    private long latencyMs;

    /**
     * 模型版本（调用方无法获知时为 null）
     */
    private String modelVersion;

    /**
     * 构造成功结果。
     *
     * @param output    结构化输出
     * @param latencyMs 耗时（毫秒）
     * @return 成功结果
     */
    public static ModelInvokeResult success(String output, long latencyMs) {
        ModelInvokeResult result = new ModelInvokeResult();
        result.setSuccess(true);
        result.setOutput(output);
        result.setLatencyMs(latencyMs);
        return result;
    }

    /**
     * 构造失败结果。
     *
     * @param errorSummary 错误摘要
     * @param latencyMs    耗时（毫秒）
     * @return 失败结果
     */
    public static ModelInvokeResult failure(String errorSummary, long latencyMs) {
        ModelInvokeResult result = new ModelInvokeResult();
        result.setSuccess(false);
        result.setErrorSummary(errorSummary);
        result.setLatencyMs(latencyMs);
        return result;
    }

}
