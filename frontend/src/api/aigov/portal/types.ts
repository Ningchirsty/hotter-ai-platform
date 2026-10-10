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
