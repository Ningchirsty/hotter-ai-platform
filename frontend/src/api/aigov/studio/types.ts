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
