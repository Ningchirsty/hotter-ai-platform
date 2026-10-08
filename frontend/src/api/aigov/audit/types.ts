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
  /** 本次数据等级 PUBLIC/INTERNAL/RESTRICTED/STRICT */
  dataLevel?: string;
  modelId?: string | number;
  modelKey?: string;
  modelVersion?: string;
  /**
   * 本次调用所属的 Agent 版本ID（治理层发布的版本）。
   * 与 modelVersion 不是一回事：那是「模型」版本，这里是「Agent」版本。
   * 为空表示本次未绑定某个 Agent 版本（如直接调能力、不经任务），不是「不知道」。
   */
  agentVersionId?: string | number;
  /** 实际使用的供应商ID（当时那一次的归属；模型改归属后它不跟着变） */
  providerId?: string | number;
  /** 供应商名称（后端回填） */
  providerName?: string;
  /** 模型用量回执（JSON，如 {"tokensUsed":123}）；为空表示该次未拿到用量 */
  usageJson?: string;
  /** 不可变输入快照引用（只存引用，不存副本） */
  inputSnapshotRef?: string;
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
  /**
   * 错误分类编码（AigErrorClassEnum：POLICY_DENIED/AUTH_FAILED/TIMEOUT…），成功时为空。
   * 与 errorSummary 的分工：那是给人看的文本，这是机器可读的分类；
   * 灰度的「无严重错误」判据按它统计，明细里也据此一眼看出严不严重。
   */
  errorClass?: string;
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
  /** 按供应商对账：查这家供应商实际执行过的调用 */
  providerId?: string | number;
  result?: string;
  callerName?: string;
  params?: Record<string, any>;
}
