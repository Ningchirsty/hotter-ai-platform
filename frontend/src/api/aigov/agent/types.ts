/**
 * Agent 注册中心类型定义（对齐后端 AigAgentVo / AigAgentVersionVo / AigReleaseAdvanceBo）
 * 字段名与表 aig_agent / aig_agent_version 列名驼峰化保持一致。
 */

/** Agent 清单行 */
export interface AigAgentVO extends BaseEntity {
  agentId?: string | number;
  /** Agent 编码（跨版本稳定） */
  agentCode?: string;
  agentName?: string;
  /** 类别：PLANNING / VISUAL_DNA / GENERATION / QA */
  category?: string;
  ownerId?: string | number;
  /** 是否平台内置（Y/N）：第三方 Package 带入的是 N */
  builtin?: string;
  description?: string;
  /** 记录状态（0正常 1停用） */
  status?: string;
  remark?: string;
}

/** Agent 清单查询 */
export interface AigAgentQuery extends PageQuery {
  agentCode?: string;
  agentName?: string;
  category?: string;
  builtin?: string;
  status?: string;
  params?: Record<string, any>;
}

/** Agent 版本行 */
export interface AigAgentVersionVO extends BaseEntity {
  agentVersionId?: string | number;
  agentId?: string | number;
  version?: string;
  /** 发布状态机：DRAFT→VALIDATED→SANDBOX_TESTED→CANDIDATE→STABLE（DISABLED/ARCHIVED） */
  releaseStatus?: string;
  /** 发布通道/可见范围：TESTING / BRAND / DEPT / GENERAL */
  releaseChannel?: string;
  scenarioCode?: string;
  providerCapability?: string;
  /** 是否允许外部调用（Y/N） */
  allowExternal?: string;
  /** 来源 Package 版本（第三方带入时不为空） */
  packageVersionId?: string | number;
  /** 最近一次评测运行ID（GOLDEN_CASE 门槛的证据指针） */
  evaluationRunId?: string | number;
  validatedAt?: string;
  sandboxTestedAt?: string;
  approvedBy?: string | number;
  approvedAt?: string;
  status?: string;
  remark?: string;
}

/** Agent 版本清单查询 */
export interface AigAgentVersionQuery extends PageQuery {
  agentId?: string | number;
  releaseStatus?: string;
  releaseChannel?: string;
  providerCapability?: string;
  scenarioCode?: string;
  version?: string;
  params?: Record<string, any>;
}

/**
 * 发布推进入参。
 *
 * `expectedStatus` 必填：调用方要声明「我以为它现在是什么状态」，
 * 与服务层比对不一致会报「你的视图已过期」——比「更新影响 0 行」可读得多。
 */
export interface AigReleaseAdvanceForm {
  targetType: string;
  targetVersionId: string | number;
  expectedStatus: string;
  toStatus: string;
  /** 本次认为已通过的门槛编码（服务层会核对库里的证据） */
  passedGates?: string[];
  operatorId?: string | number;
  detail?: string;
}
