package org.dromara.aigov.tool.exec;

import org.dromara.aigov.tool.enums.AigToolCallStatusEnum;

/**
 * 一次工具调用的结果（设计 §7.5）。
 *
 * @param status         结论
 * @param outputJson     结构化输出（可空；大结果走 {@code outputRef}）
 * @param outputRef      输出存储引用（可空）
 * @param receiptRef     操作凭证引用（写操作必有；用于"结果未知"时核查）
 * @param errorCode      失败分类（可空）
 * @param errorMessage   失败原因（可空）
 * @param replayed       是否命中幂等键的历史结论
 */
public record AigToolCallResult(AigToolCallStatusEnum status, String outputJson, String outputRef,
                                String receiptRef, String errorCode, String errorMessage,
                                boolean replayed) {

    /**
     * 成功。
     *
     * @param outputJson 输出
     * @param receiptRef 凭证（可空）
     * @return 结果
     */
    public static AigToolCallResult ok(String outputJson, String receiptRef) {
        return new AigToolCallResult(AigToolCallStatusEnum.SUCCEEDED, outputJson, null, receiptRef,
            null, null, false);
    }

    /**
     * 需要人工确认（尚未执行，不是失败）。
     *
     * @param reason 原因
     * @return 结果
     */
    public static AigToolCallResult needsConfirmation(String reason) {
        return new AigToolCallResult(AigToolCallStatusEnum.NEEDS_CONFIRMATION, null, null, null,
            "TOOL_APPROVAL_REQUIRED", reason, false);
    }

    /**
     * 失败。
     *
     * @param errorCode 分类
     * @param reason    原因
     * @return 结果
     */
    public static AigToolCallResult failed(String errorCode, String reason) {
        return new AigToolCallResult(AigToolCallStatusEnum.FAILED, null, null, null,
            errorCode, reason, false);
    }

    /**
     * 结果未知（写操作超时/断连，必须先查凭证，不立即重试）。
     *
     * @param reason 原因
     * @return 结果
     */
    public static AigToolCallResult outcomeUnknown(String reason) {
        return new AigToolCallResult(AigToolCallStatusEnum.OUTCOME_UNKNOWN, null, null, null,
            "EXECUTION_OUTCOME_UNKNOWN", reason, false);
    }

}
