package org.dromara.aigov.tool.exec;

import java.time.LocalDateTime;

/**
 * 一次工具调用请求（设计 §7.5）。
 *
 * @param taskId         归属任务（可空）
 * @param toolCode       工具编码
 * @param toolVersion    工具版本
 * @param idempotencyKey 幂等键（重试必须复用）
 * @param inputJson      输入（JSON 文本；人工确认绑定它的摘要）
 * @param approvalRef    批准引用（人工确认/业务审批凭证ID，可空）
 * @param approvalDigest 批准时绑定的输入摘要（可空）
 * @param approvalExpiresAt 批准有效期（可空）
 */
public record AigToolCallRequest(Long taskId, String toolCode, String toolVersion, String idempotencyKey,
                                 String inputJson, String approvalRef, String approvalDigest,
                                 LocalDateTime approvalExpiresAt) {
}
