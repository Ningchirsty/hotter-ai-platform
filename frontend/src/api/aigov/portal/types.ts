/**
 * 员工 AI 工作台（门户）类型定义（主文档线增量 2）。
 *
 * 与后端 `org.dromara.aigov.workspace.portal.domain.vo` 一一对应。
 *
 * 注意这里**没有**计费金额、供应商、traceId、策略原因、错误详情等字段：
 * 门户是对全体员工的接口，那些是运维视角（后端有专门用例守着这条边界）。
 */

/** 岗位分类 */
export interface AigPortalCategoryVO {
  code: string;
  name?: string;
  /** 该分类下的卡片数（0 = 这一栏暂时是空的，而不是这栏不存在） */
  actionCount?: number;
}

/** 能力卡片（门户视角） */
export interface AigPortalActionVO {
  actionCode: string;
  categoryCode?: string;
  title?: string;
  description?: string;
  launchMode?: string;
  targetType?: string;
  targetRef?: string;
  studioRouteKey?: string;
  requiredContext?: string;
  sortOrder?: number;
}

/** 我的岗位 */
export interface AigPortalRoleVO {
  roleCode: string;
  roleName?: string;
  description?: string;
  version?: string;
  categories?: AigPortalCategoryVO[];
  actionCount?: number;
}

/** 岗位首页（含服务端过滤后的卡片） */
export interface AigPortalRoleHomeVO extends AigPortalRoleVO {
  actions?: AigPortalActionVO[];
}

/** 我的任务（门户视角） */
export interface AigPortalTaskVO {
  taskId: string | number;
  taskNo?: string;
  taskType?: string;
  capabilityCode?: string;
  scenarioCode?: string;
  projectType?: string;
  projectId?: string | number;
  status?: string;
  statusLabel?: string;
  progress?: number;
  dataLevel?: string;
  startedAt?: string;
  finishedAt?: string;
}

/** 我的任务查询条件（提交人由服务端固定为当前用户） */
export interface AigPortalTaskQuery {
  status?: string;
  taskType?: string;
  scenarioCode?: string;
  pageNum?: number;
  pageSize?: number;
}

/** 启动问题（码 + 可直接展示的文案；文案由后端给，前端不再另写映射表） */
export interface AigLaunchProblemVO {
  code: string;
  message?: string;
}

/** 启动请求（prepare 与 commit 共用） */
export interface AigLaunchRequestForm {
  roleCode: string;
  actionCode: string;
  /** 幂等键：客户端生成，重试必须复用（服务端按它判"这是同一次启动"） */
  idempotencyKey: string;
  /** commit 必填（prepare 返回的票据） */
  ticket?: string;
  taskType?: string;
  projectType?: string;
  projectId?: string | number;
  dataLevel?: string;
  snapshotJson?: string;
  context?: Record<string, string>;
  remark?: string;
}

/** prepare 结果 */
export interface AigLaunchPrepareVO {
  ticketId?: string;
  expiresAt?: string;
  roleCode?: string;
  roleVersionId?: string | number;
  actionCode?: string;
  title?: string;
  launchMode?: string;
  targetType?: string;
  targetRef?: string;
  studioRouteKey?: string;
  /** 场景类卡片的流程适配器（交给哪条既有链路跑；非场景卡片为空） */
  workflowAdapter?: string;
  /** 场景类卡片的结果页跳转键（启动成功后据此跳转，仍走 routeKey 白名单） */
  scenarioRouteKey?: string;
  willCreateTask?: boolean;
  taskType?: string;
  projectType?: string;
  projectId?: string | number;
  missingContextKeys?: string[];
  problems?: AigLaunchProblemVO[];
  passed?: boolean;
  /** 同一次启动已经存在时给出（幂等重放：不需要再提交） */
  existingLaunchId?: string | number;
  existingTaskId?: string | number;
  existingTaskNo?: string;
}

/** commit 结果 */
export interface AigLaunchCommitVO {
  launchId?: string | number;
  taskId?: string | number;
  taskNo?: string;
  launchMode?: string;
  targetType?: string;
  targetRef?: string;
  /** 场景类卡片的流程适配器（非场景卡片为空） */
  workflowAdapter?: string;
  /** 场景类卡片的结果页跳转键（启动成功后界面据此跳转） */
  scenarioRouteKey?: string;
  launchStatus?: string;
  replayed?: boolean;
  committedAt?: string;
  problems?: AigLaunchProblemVO[];
  passed?: boolean;
}

/** 启动记录（按任务查；专业台回跳入口用） */
export interface AigLaunchRecordVO {
  launchId?: string | number;
  taskId?: string | number;
  taskNo?: string;
  roleCode?: string;
  roleName?: string;
  actionCode?: string;
  launchMode?: string;
  targetType?: string;
  targetRef?: string;
  committedAt?: string;
}

/** 工作台偏好（收藏 + 默认岗位） */
export interface AigPortalPrefVO {
  favorites?: string[];
  defaultRoleCode?: string;
}

/** 我的产物（平台产物台账；刻意不带 storageRef/哈希/校验明细，也不给下载直链） */
export interface AigPortalArtifactVO {
  artifactId: string | number;
  taskId?: string | number;
  taskNo?: string;
  artifactType?: string;
  mimeType?: string;
  sizeBytes?: number;
  createTime?: string;
}

/** 我的产物查询条件（范围由服务端固定为当前用户） */
export interface AigPortalArtifactQuery {
  /** 只看某个任务的产物 */
  taskId?: string | number;
  pageNum?: number;
  pageSize?: number;
}

/**
 * 一条推荐结果（增量 7）。
 *
 * `action` 是服务端可见清单里的卡片本体（不是模型编造的），`roleCode`/`roleName` 说明它属于哪个岗位。
 * 刻意没有 traceId / 模型 / 策略原因：那些是运维视角，审计由网关写在 `aig_invocation_audit`。
 */
export interface AigRecommendSuggestionVO {
  roleCode: string;
  roleName?: string;
  action: AigPortalActionVO;
}

/** 自然语言推荐结果（已按可见卡片求交过滤） */
export interface AigRecommendResultVO {
  suggestions?: AigRecommendSuggestionVO[];
  /** 调用成功但确实没有可推荐内容时的说明（调用失败会直接报错，不会伪装成空结果） */
  reason?: string;
}

/**
 * 我的资产一条（跨域只读；增量 8）。
 *
 * 只给展示字段：**没有**存储键、没有下载直链——下载走各域自己的入口，那里的权限仍然生效。
 */
export interface AigPortalMyAssetVO {
  assetId?: string | number;
  assetType?: string;
  sourceKind?: string;
  name?: string;
  mimeType?: string;
  sizeBytes?: number;
  taskId?: string | number;
  createTime?: string;
}

/**
 * 我的资产分组（按域分栏）。
 *
 * 某域没接入时该组根本不出现；接入但你没有数据时该组出现且 `items` 为空——两者含义不同。
 * 不承诺跨域排序/分页（各域"我的"口径不同，全局分页会漏或重）。
 */
export interface AigPortalAssetGroupVO {
  domain: string;
  items?: AigPortalMyAssetVO[];
}
