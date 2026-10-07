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
     * 结构化错误码（失败时建议填）。
     *
     * <p>可以是本层 {@code AigErrorClassEnum} 的编码（调用器最清楚自己遇到了什么时直接给），
     * 也可以是供应商自有码（如 {@code context_length_exceeded}）；两者都填不出来时留 null，
     * 由 {@code AigErrorClassEnum.classify} 按 HTTP 状态与文本兜底。</p>
     */
    private String errorCode;

    /**
     * 上游 HTTP 状态码（拿不到时为 null，<b>不得编造</b>）。
     *
     * <p>它是错误分类里最可靠的一路输入：429/401/5xx 的含义不依赖任何文案措辞。</p>
     */
    private Integer httpStatus;

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

    /**
     * 构造失败结果（带结构化错误码与上游状态码）。
     *
     * <p>能填就填：错误分类靠它们才能把「限流可重试」与「鉴权失败要熔断」分开，
     * 只给一段中文文案时分类只能靠措辞猜，容易把不可重试的错误当成可重试的。</p>
     *
     * @param errorCode    结构化错误码（可为 null）
     * @param httpStatus   上游 HTTP 状态码（可为 null）
     * @param errorSummary 错误摘要
     * @param latencyMs    耗时（毫秒）
     * @return 失败结果
     */
    public static ModelInvokeResult failure(String errorCode, Integer httpStatus, String errorSummary, long latencyMs) {
        ModelInvokeResult result = failure(errorSummary, latencyMs);
        result.setErrorCode(errorCode);
        result.setHttpStatus(httpStatus);
        return result;
    }

}
