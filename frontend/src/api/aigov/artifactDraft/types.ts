/**
 * 草案生成器类型定义（对齐后端 AigStudioArtifact*Vo；附件 07 增量 7）。
 *
 * <p>三类草案**各自落表**，字段确实不同；这里也各自一套类型，不用一个"大而全"的接口硬套。</p>
 */

/** 草案类型（与后端 AigStudioArtifactKindEnum 一致） */
export type AigStudioArtifactKind = 'SKILL' | 'CHILD_AGENT' | 'ACTION';

/** 清单查询（三类共用：编码/名称/启用/组织） */
export interface AigStudioArtifactDraftQuery extends PageQuery {
  draftCode?: string;
  draftName?: string;
  enabled?: string;
  orgId?: string | number;
  params?: Record<string, any>;
}

/** 技能草案 */
export interface AigStudioSkillDraftVO extends BaseEntity {
  skillDraftId?: string | number;
  draftCode?: string;
  draftName?: string;
  skillCode?: string;
  skillName?: string;
  capabilities?: string;
  description?: string;
  usageNotes?: string;
  agentCategory?: string;
  providerCapability?: string;
  allowedTools?: string;
  ownerId?: string | number;
  orgId?: string | number;
  enabled?: string;
  remark?: string;
}

/** 子 Agent 草案 */
export interface AigStudioChildAgentDraftVO extends BaseEntity {
  childAgentDraftId?: string | number;
  draftCode?: string;
  draftName?: string;
  agentCode?: string;
  agentName?: string;
  agentCategory?: string;
  parentAgentCode?: string;
  roleDescription?: string;
  objective?: string;
  prohibitions?: string;
  outputSummary?: string;
  promptSectionsJson?: string;
  providerCapability?: string;
  allowExternal?: string;
  allowedTools?: string;
  knowledgeScope?: string;
  ownerId?: string | number;
  orgId?: string | number;
  enabled?: string;
  remark?: string;
}

/** 快捷指令草案 */
export interface AigStudioActionDraftVO extends BaseEntity {
  actionDraftId?: string | number;
  draftCode?: string;
  draftName?: string;
  actionCode?: string;
  actionName?: string;
  launchMode?: string;
  targetType?: string;
  targetRef?: string;
  agentCategory?: string;
  objective?: string;
  outputContract?: string;
  providerCapability?: string;
  allowedTools?: string;
  ownerId?: string | number;
  orgId?: string | number;
  enabled?: string;
  remark?: string;
}

/** 新增技能草案 */
export interface AigStudioSkillDraftForm {
  draftCode?: string;
  draftName?: string;
  skillCode?: string;
  skillName?: string;
  capabilities?: string;
  description?: string;
  usageNotes?: string;
  agentCategory?: string;
  providerCapability?: string;
  allowedTools?: string;
  orgId?: string | number;
  enabled?: string;
  remark?: string;
}

/** 新增子 Agent 草案 */
export interface AigStudioChildAgentDraftForm {
  draftCode?: string;
  draftName?: string;
  agentCode?: string;
  agentName?: string;
  agentCategory?: string;
  parentAgentCode?: string;
  roleDescription?: string;
  objective?: string;
  prohibitions?: string;
  outputSummary?: string;
  promptSectionsJson?: string;
  providerCapability?: string;
  allowExternal?: string;
  allowedTools?: string;
  knowledgeScope?: string;
  orgId?: string | number;
  enabled?: string;
  remark?: string;
}

/** 新增快捷指令草案 */
export interface AigStudioActionDraftForm {
  draftCode?: string;
  draftName?: string;
  actionCode?: string;
  actionName?: string;
  launchMode?: string;
  targetType?: string;
  targetRef?: string;
  agentCategory?: string;
  objective?: string;
  outputContract?: string;
  providerCapability?: string;
  allowedTools?: string;
  orgId?: string | number;
  enabled?: string;
  remark?: string;
}

/** 生成物（预览与生成共用同一个视图：页面先看清、再决定落到哪个草稿） */
export interface AigStudioArtifactGenerateVO {
  kind?: string;
  sourceDraftId?: string | number;
  sourceDraftCode?: string;
  /** 生成的可提交内容（八分节 + 声明字段） */
  content?: Record<string, any>;
  /** 没写进内容、以及留空待补的说明 */
  notes?: string[];
  /** 仅 generate 时有值 */
  targetDraftId?: string | number;
  revision?: number;
  contentHash?: string;
  revisionCreated?: boolean;
}

/** 生成入参（写草稿，必须带 CAS） */
export interface AigStudioArtifactGenerateForm {
  targetDraftId?: string | number;
  expectedRevision?: number;
}
