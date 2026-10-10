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
