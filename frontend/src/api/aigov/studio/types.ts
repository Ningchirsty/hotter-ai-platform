/** 训练台草稿列表项 */
export interface AigStudioDraftVO {
  draftId: string | number;
  agentId?: string | number | null;
  agentCode: string;
  orgId?: string | number | null;
  ownerId?: string | number | null;
  latestRevision?: number;
  contentJson?: string;
  contentHash?: string;
  lastPublishedHash?: string | null;
  agentVersionId?: string | number | null;
  status?: string;
  /** 状态中文名（服务端填） */
  statusLabel?: string;
  /** 是否有未提交改动（服务端按哈希判定） */
  unpublishedChanges?: boolean;
  remark?: string;
  createTime?: string;
  updateTime?: string;
}

/** 草稿详情（比列表多一个"本次是否产生新修订"） */
export interface AigStudioDraftDetailVO extends AigStudioDraftVO {
  revisionCreated?: boolean;
}

/** 草稿查询条件 */
export interface AigStudioDraftQuery {
  pageNum?: number;
  pageSize?: number;
  agentCode?: string;
  status?: string;
}

/** 新建草稿 */
export interface AigStudioDraftCreateForm {
  agentCode: string;
  agentId?: string | number | null;
  orgId?: string | number | null;
  contentJson?: string;
  summary?: string;
  remark?: string;
}

/** 保存草稿（必须带期望修订号——没有它就是允许静默覆盖） */
export interface AigStudioDraftSaveForm {
  expectedRevision: number;
  contentJson: string;
  summary?: string;
}

/** 回滚 */
export interface AigStudioDraftRollbackForm {
  targetRevisionNo: number;
  expectedRevision: number;
}

/** 提交（版本号可空：空则服务端按 0.1.<修订号> 生成） */
export interface AigStudioDraftSubmitForm {
  version?: string;
  remark?: string;
}

/** 修订记录 */
export interface AigStudioRevisionVO {
  revisionId: string | number;
  draftId: string | number;
  revisionNo: number;
  contentSnapshotJson?: string;
  contentHash?: string;
  authorId?: string | number;
  source?: string;
  summary?: string;
  createTime?: string;
}

/** 预检结论 */
export interface AigStudioValidateVO {
  draftId: string | number;
  revision?: number;
  contentHash?: string;
  passed: boolean;
  problems: string[];
}

/** 提交结果 */
export interface AigStudioDraftSubmitVO {
  draftId: string | number;
  revision?: number;
  contentHash?: string;
  agentId?: string | number;
  agentCode?: string;
  agentCreated?: boolean;
  agentVersionId?: string | number;
  version?: string;
  releaseStatus?: string;
}

/** 草稿内容（页面可编辑的部分；其余字段原样带回，避免保存时丢字段） */
export interface AigStudioDraftContentForm {
  agentName?: string;
  agentCategory?: string;
  roleDescription?: string;
  objective?: string;
  prohibitions?: string;
  providerCapability?: string;
  allowExternal?: string;
  inputSchema?: string;
  outputSchema?: string;
  promptSections: Record<string, string>;
  /** 未在页面上编辑的其余字段，保存时原样保留 */
  [key: string]: unknown;
}

/** 变更提案里的一处改动预览（服务端由 previewJson 解析而来，页面不自己解析 JSON） */
export interface AigStudioProposalDiffItem {
  /** JSON Pointer 路径，如 /objective、/promptSections/role */
  path: string;
  /** 改前值（展示文本；空为 null） */
  before?: string | null;
  /** 改后值（展示文本；空为 null） */
  after?: string | null;
}

/**
 * 变更提案（培训助手只提案，人工采纳才产生新修订）。
 *
 * `baseRevision` 是"提案基于哪一版"——采纳时必须仍是草稿的当前修订，否则已失效。
 */
export interface AigStudioChangeProposalVO {
  proposalId: string | number;
  draftId: string | number;
  baseRevision?: number;
  /** 来源（COPILOT=培训助手 / MANUAL=人工草拟） */
  source?: string;
  /** 改动预览（结构化；服务端解析） */
  preview?: AigStudioProposalDiffItem[];
  contentHashBefore?: string;
  contentHashAfter?: string;
  /** PENDING=待处理 / APPLIED=已采纳 / REJECTED=已拒绝 */
  status?: string;
  decidedBy?: string | number | null;
  decidedAt?: string | null;
  decidedReason?: string | null;
  summary?: string | null;
  createTime?: string;
}

/** 处理提案（采纳/拒绝共用） */
export interface AigStudioProposalDecideForm {
  /** 采纳必填：期望的草稿修订号（写草稿必须带 CAS，否则会覆盖别人的修改） */
  expectedRevision?: number;
  /** 拒绝原因（可空） */
  reason?: string;
}

/**
 * 对话式 Copilot 的一轮对话（培训助手在某一版草稿上提了几次案）。
 *
 * `status` 区分三件事，页面不该混着显示：`SUCCEEDED` 产出了提案、`REJECTED` 模型越界被平台拒绝、
 * `FAILED` 这次没调通/输出不是要求的格式。
 */
export interface AigStudioCopilotTurnVO {
  turnId: string | number;
  draftId: string | number;
  /** 对话基于的修订号 */
  revision?: number;
  contentHash?: string;
  userMessage?: string;
  dataLevel?: string;
  /** 模型原文（截断） */
  modelOutput?: string | null;
  /** SUCCEEDED=已产出提案 / REJECTED=提案被拒绝 / FAILED=调用失败 / RUNNING=进行中 */
  status?: string;
  failureReason?: string | null;
  /** 产出的提案ID（仅 SUCCEEDED） */
  proposalId?: string | number | null;
  traceId?: string;
  modelKey?: string;
  deploymentType?: string;
  /** 是否发生外发（Y/N） */
  externalCall?: string | null;
  latencyMs?: number;
  createTime?: string;
}

/** 与培训助手对话入参（数据等级必填：它决定能不能外发） */
export interface AigStudioCopilotChatForm {
  message: string;
  dataLevel: string;
  /** 本次调用可接受的最高成本（美元 USD；可空） */
  maxCost?: number;
}

/** 测试调用入参（数据等级必填：它决定能不能外发，测错等级等于没测） */
export interface AigStudioTestRunForm {
  dataLevel: string;
  input: string;
  maxCost?: number;
}

/** 测试调用结果 */
export interface AigStudioTestRunVO {
  linkId: string | number;
  draftId: string | number;
  revision?: number;
  contentHash?: string;
  testStatus?: string;
  output?: string;
  outputTruncated?: boolean;
  resultDigest?: string;
  traceId?: string;
  modelKey?: string;
  deploymentType?: string;
  externalCall?: boolean;
  latencyMs?: number;
  errorCode?: string;
  reason?: string;
  policyHits?: string;
}
