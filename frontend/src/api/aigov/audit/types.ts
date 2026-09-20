/**
 * AI 调用审计类型定义（对齐后端 AigInvocationAuditVo / AigInvocationAuditQueryBo）
 *
 * aig_invocation_audit 为追加型审计表：只读、不删除、不修改。
 * input_summary 仅在审计等级允许且不含受限内容时由后端写入；
 * 前端不做还原，也不展示任何原始输入内容。
 */

/** 逐次调用审计行 */
export interface AigInvocationAuditVO {
  auditId?: string | number;
  /** 调用链ID（一次业务动作一个） */
  traceId?: string;
  capabilityCode?: string;
  capabilityName?: string;
  callerId?: string | number;
  /** 调用人账号（冗余，便于离线审计） */
  callerName?: string;
  /** 本次数据等级 PUBLIC/INTERNAL/RESTRICTED */
  dataLevel?: string;
  modelId?: string | number;
  modelKey?: string;
  modelVersion?: string;
  /** 部署类型 LOCAL/GROUP/EXTERNAL_ENTERPRISE/EXTERNAL_API */
  deploymentType?: string;
  /** 是否外发（Y是 N否） */
  externalCall?: string;
  /** 命中的路由策略摘要 */
  policyHit?: string;
  inputHash?: string;
  inputSummary?: string;
  outputRef?: string;
  /** 结果（0成功 1失败） */
  result?: string;
  errorSummary?: string;
  latencyMs?: number;
  cost?: number;
  retryCount?: number;
  /** 人工结论 PENDING/ACCEPTED/REJECTED/NOT_REQUIRED */
  manualDecision?: string;
  operateTime?: string;
}

/** 查询条件（能力、数据等级、是否外发、时间范围） */
export interface AigInvocationAuditQuery extends PageQuery {
  traceId?: string;
  capabilityCode?: string;
  dataLevel?: string;
  externalCall?: string;
  modelKey?: string;
  result?: string;
  callerName?: string;
  params?: Record<string, any>;
}
