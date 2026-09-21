/**
 * 公司月度计划域类型定义
 * 对齐 script/sql/hr_recruit.sql 的 hr_recruit_plan / hr_recruit_plan_item。
 * 接口契约见 docs/hr-talent/SPEC-P2-招聘主线.md §3.2。
 */

/** 计划表头状态（hr_recruit_plan.status，字典 recruit_plan_status） */
export type PlanStatus = 'draft' | 'executing' | 'closed';

/** 计划表头动作（confirm / close） */
export type PlanAction = 'confirm' | 'close';

/** 任务来源类型（字典 recruit_plan_source_type） */
export type PlanSourceType = 'new' | 'carryover';

/** 任务人工控制状态（字典 recruit_plan_control_status） */
export type PlanControlStatus = 'normal' | 'paused' | 'cancelled';

/** 任务执行阶段（字典 recruit_plan_execution_status） */
export type PlanExecutionStatus = 'pending' | 'recruiting' | 'interviewing' | 'offer' | 'pending_arrival';

/** 任务完成状态（字典 recruit_plan_completion_status） */
export type PlanCompletionStatus = 'unfinished' | 'partial_completed' | 'completed' | 'rolled_over';

/** 任务动作（pause / resume / cancel） */
export type PlanItemAction = 'pause' | 'resume' | 'cancel';

/** 公司月度计划表头（hr_recruit_plan） */
export interface HrPlanVO extends BaseEntity {
  /** 计划ID（主键） */
  planId?: string | number;
  /** 计划编号（业务编号，唯一） */
  planNo?: string;
  /** 公司（平台部门）ID */
  companyDeptId?: string | number;
  /** 公司名称快照 */
  companyName?: string;
  /** 计划月份（yyyy-MM） */
  planMonth?: string;
  /** 计划状态（字典 recruit_plan_status） */
  status?: string;
  /** 是否已生成计划任务（0否 1是） */
  generatedFlag?: string;
  /** 生成时间 */
  generatedTime?: string;
  /** 确认人用户ID */
  confirmedBy?: string | number;
  /** 确认时间 */
  confirmedTime?: string;
  /** 关闭人用户ID */
  closedBy?: string | number;
  /** 关闭时间 */
  closedTime?: string;
  /** 计划总人数（非负整数） */
  totalPlanQty?: number;
  /** 累计计入到岗人数（非负整数） */
  totalCreditedQty?: number;
  /** 累计剩余人数（非负整数） */
  totalRemainingQty?: number;
  /** 备注 */
  remark?: string;
}

/** 计划表头新增/编辑表单（POST /recruit/plans、PUT /recruit/plans/{id}） */
export interface HrPlanForm {
  planId?: string | number;
  companyDeptId?: string | number;
  companyName?: string;
  /** 计划月份（yyyy-MM） */
  planMonth?: string;
  remark?: string;
}

/** 计划表头查询条件（GET /recruit/plans） */
export interface HrPlanQuery extends PageQuery {
  planNo?: string;
  companyDeptId?: string | number;
  planMonth?: string;
  status?: string;
  params?: Record<string, any>;
}

/** 月度计划任务（hr_recruit_plan_item） */
export interface HrPlanItemVO extends BaseEntity {
  /** 计划任务ID（主键） */
  itemId?: string | number;
  /** 计划任务编号（业务编号，唯一） */
  itemNo?: string;
  /** 所属月度计划表头ID */
  planId?: string | number;
  /** 来源招聘需求ID */
  demandId?: string | number;
  /** 关联岗位执行项ID */
  jobId?: string | number;
  /** 公司（平台部门）ID */
  companyDeptId?: string | number;
  /** 公司名称快照 */
  companyName?: string;
  /** 用工部门ID */
  useDeptId?: string | number;
  /** 用工部门名称快照 */
  useDeptName?: string;
  /** 岗位名称快照 */
  jobName?: string;
  /** 计划月份（yyyy-MM） */
  planMonth?: string;
  /** 来源类型（new 当月新增 / carryover 上月结转） */
  sourceType?: string;
  /** 计划人数（非负整数） */
  planQty?: number;
  /** 已计入到岗人数（非负整数） */
  creditedArrivalQty?: number;
  /** 剩余人数（非负整数） */
  remainingQty?: number;
  /** 人工控制状态（normal/paused/cancelled） */
  controlStatus?: string;
  /** 自动执行阶段（pending/recruiting/interviewing/offer/pending_arrival） */
  executionStatus?: string;
  /** 完成与结转状态（unfinished/partial_completed/completed/rolled_over） */
  completionStatus?: string;
  /** 是否允许自动结转（0否 1是） */
  carryoverEnabled?: string;
  /** 前置（来源）计划任务ID */
  previousPlanItemId?: string | number;
  /** 根计划任务ID（结转链起点） */
  rootPlanItemId?: string | number;
  /** 结转批次ID */
  carryoverBatchId?: string | number;
  /** 结转生成时间 */
  carryoverTime?: string;
  /** 任务负责人用户ID */
  ownerId?: string | number;
  /** 备注 */
  remark?: string;
  /** 展示用可选字段：缺失时回退显示 ownerId */
  ownerName?: string;
  /** 展示用可选字段：前置任务编号（结转链展示用） */
  previousItemNo?: string;
}

/** 计划任务新增表单（POST /recruit/plans/{planId}/items，必须新建记录，绝不合并） */
export interface HrPlanItemForm {
  itemId?: string | number;
  planId?: string | number;
  demandId?: string | number;
  jobId?: string | number;
  companyDeptId?: string | number;
  companyName?: string;
  useDeptId?: string | number;
  useDeptName?: string;
  jobName?: string;
  planMonth?: string;
  /** 计划人数（非负整数） */
  planQty?: number;
  /** 任务负责人用户ID */
  ownerId?: string | number;
  /** 是否允许自动结转（0否 1是） */
  carryoverEnabled?: string;
  remark?: string;
}

/** 计划任务编辑表单（PUT /recruit/plan-items/{id}，仅可改允许字段） */
export interface HrPlanItemEditForm {
  itemId: string | number;
  /** 计划人数 */
  planQty?: number;
  ownerId?: string | number;
  useDeptId?: string | number;
  useDeptName?: string;
  /** 是否允许自动结转 */
  carryoverEnabled?: string;
  remark?: string;
}

/** 计划任务查询条件（GET /recruit/plans/{planId}/items 与 GET /recruit/plan-items） */
export interface HrPlanItemQuery extends PageQuery {
  planId?: string | number;
  planMonth?: string;
  companyDeptId?: string | number;
  useDeptId?: string | number;
  jobName?: string;
  sourceType?: string;
  controlStatus?: string;
  executionStatus?: string;
  completionStatus?: string;
  ownerId?: string | number;
  params?: Record<string, any>;
}

/** 任务动作入参（暂停/取消需填写原因） */
export interface HrPlanItemActionForm {
  reason?: string;
}

/** 相似计划提示查询条件（GET /recruit/plan-items/similar，仅提示、不合并） */
export interface HrPlanItemSimilarQuery {
  companyDeptId?: string | number;
  useDeptId?: string | number;
  jobName?: string;
  planMonth?: string;
}
