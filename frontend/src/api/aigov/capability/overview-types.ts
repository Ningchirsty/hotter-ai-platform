/**
 * 能力「配置完整度」聚合视图（对齐后端 AigCapabilityOverviewVo）。
 *
 * <p>能力清单卡片与看板的唯一数据源：候选数、策略覆盖、近 24h 调用、问题码都由服务端算好，
 * 前端不再自己拼（否则筛选只能筛当前页，翻页后结论就错了）。</p>
 */
export interface AigCapabilityOverviewVO {
  capabilityId?: string | number;
  capabilityCode?: string;
  capabilityName?: string;
  /** 需要的模型能力标签，逗号分隔 */
  requiredTags?: string;
  /** 状态（0正常 1停用） */
  status?: string;
  updateTime?: string;
  /** 启用的模型候选数 */
  candidateCount?: number;
  /** 首选候选的模型键 */
  primaryModelKey?: string;
  /** 候选健康汇总：NONE / HEALTHY / UNHEALTHY / UNKNOWN */
  candidateHealthAgg?: string;
  /** 已配置策略的数据等级数（0~4） */
  policyCoveredLevels?: number;
  /** 需要配置的策略总数（固定 4） */
  policyTotalLevels?: number;
  /** 场景指定条数 */
  sceneCount?: number;
  /** 近 24h 调用次数 */
  calls24h?: number;
  /** 近 24h 失败次数 */
  fails24h?: number;
  /**
   * 这张卡「为什么红」的原因码：
   * NO_MODEL_CANDIDATE / CANDIDATE_UNHEALTHY / NO_ROUTE_POLICY / POLICY_INCOMPLETE
   */
  issueCodes?: string[];
}

/** 概览查询条件（筛选必须下发到服务端） */
export interface AigCapabilityOverviewQuery {
  capabilityCode?: string;
  capabilityName?: string;
  status?: string;
  requiredTags?: string;
  /** 只看带某类问题的能力 */
  issueCode?: string;
  /** 候选状态：HAS / NO */
  candidateState?: string;
}

/** 候选（能力×模型绑定）行，对齐后端 AigCapabilityModelVo */
export interface AigCapabilityCandidateVO {
  bindId?: string | number;
  capabilityCode?: string;
  modelId?: string | number;
  /** 模型标识（联表展示） */
  modelKey?: string;
  modelName?: string;
  modelType?: string;
  modelEnabled?: boolean;
  /** 部署类型（联表展示） */
  deploymentType?: string;
  deploymentTypeLabel?: string;
  /** 用途：PRIMARY / FALLBACK / GRAY */
  usageType?: string;
  usageTypeLabel?: string;
  /** 优先级，越小越优先 */
  priority?: number;
  status?: string;
  remark?: string;
}

/** 候选新增/编辑入参（编辑时只提交 usageType / priority / status） */
export interface AigCapabilityCandidateForm {
  bindId?: string | number;
  capabilityCode?: string;
  modelId?: string | number;
  usageType?: string;
  priority?: number;
  status?: string;
  remark?: string;
}
