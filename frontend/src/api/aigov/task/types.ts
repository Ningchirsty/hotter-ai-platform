/**
 * AI 统一任务类型定义（对齐后端 org.dromara.aigov.task 的 VO/BO）。
 *
 * 状态与任务类型的「码 → 中文」映射刻意由后端下发（statusLabel/taskTypeLabel），
 * 前端不再各维护一份：同一个状态在任务列表、任务详情、审计页三处都出现，
 * 三份映射迟早不一致，而「已取消」显示成「已完成」是会被当真的。
 */

/** 任务列表行 */
export interface AigTaskVO {
  taskId?: string | number;
  taskNo?: string;
  taskType?: string;
  taskTypeLabel?: string;
  capabilityCode?: string;
  scenarioCode?: string;
  projectType?: string;
  projectId?: string | number;
  agentVersionId?: string | number;
  dataLevel?: string;
  allowExternal?: string;
  status?: string;
  statusLabel?: string;
  attemptNo?: number;
  maxAttempt?: number;
  inputSnapshotId?: string | number;
  policyResult?: string;
  policyReason?: string;
  providerCode?: string;
  providerJobId?: string;
  progress?: number;
  resultType?: string;
  externalCall?: string;
  costAmount?: number;
  latencyMs?: number;
  traceId?: string;
  errorCode?: string;
  errorMessage?: string;
  reviewStatus?: string;
  reviewedBy?: string | number;
  reviewedAt?: string;
  reviewComment?: string;
  startedAt?: string;
  finishedAt?: string;
  /** 乐观锁版本：取消/重排必须带上它 */
  version?: number;
  createTime?: string;
  updateTime?: string;
  remark?: string;
}

/** 任务事件（排障主视图：从哪到哪、什么时候、谁改的） */
export interface AigTaskEventVO {
  eventId?: string | number;
  taskId?: string | number;
  sequence?: number;
  eventType?: string;
  eventTypeLabel?: string;
  fromStatus?: string;
  toStatus?: string;
  attemptNo?: number;
  detail?: string;
  actorId?: string | number;
  actorName?: string;
  traceId?: string;
  operateTime?: string;
}

/** 输入快照（只读；改内容必须新增版本） */
export interface AigTaskSnapshotVO {
  snapshotId?: string | number;
  taskId?: string | number;
  snapshotVersion?: number;
  snapshotJson?: string;
  snapshotHash?: string;
  dataLevel?: string;
  allowExternal?: string;
  budgetAmount?: number;
  negativeConstraints?: string;
  frozenAt?: string;
  createBy?: string | number;
  createTime?: string;
}

/** 候选结果 */
export interface AigTaskResultVO {
  resultId?: string | number;
  taskId?: string | number;
  attemptNo?: number;
  resultType?: string;
  assetId?: string | number;
  structuredOutputJson?: string;
  validationResult?: string;
  validationDetail?: string;
  candidateStatus?: string;
  candidateStatusLabel?: string;
  qaVerdict?: string;
  qaDetail?: string;
  selectedBy?: string | number;
  selectedAt?: string;
  createTime?: string;
  remark?: string;
}

/** 任务详情：任务 + 快照 + 事件流 + 候选结果（一次给全） */
export interface AigTaskDetailVO {
  task?: AigTaskVO;
  snapshot?: AigTaskSnapshotVO;
  events?: AigTaskEventVO[];
  results?: AigTaskResultVO[];
}

/** 查询条件 */
export interface AigTaskQuery extends PageQuery {
  taskNo?: string;
  taskType?: string;
  projectType?: string;
  projectId?: string | number;
  status?: string;
  capabilityCode?: string;
  scenarioCode?: string;
  dataLevel?: string;
  externalCall?: string;
  providerCode?: string;
  traceId?: string;
  reviewStatus?: string;
  params?: Record<string, any>;
}

/** 执行任务结果 */
export interface AigTaskExecuteVO {
  taskId?: string | number;
  status?: string;
  success?: boolean;
  /** 调用链ID：一条 traceId 串起任务与逐次审计 */
  traceId?: string;
  modelKey?: string;
  deploymentType?: string;
  invoker?: string;
  externalCall?: boolean;
  /** 模型输出（原样返回，未落库——落资产是业务域的事） */
  output?: string;
  reason?: string;
  errorCode?: string;
  latencyMs?: number;
}

/** 调度扫描结果 */
export interface AigTaskSweepVO {
  retryCandidate?: number;
  retried?: number;
  timeoutCandidate?: number;
  timedOut?: number;
  skipped?: number;
  failures?: string[];
}
