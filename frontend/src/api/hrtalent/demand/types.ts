/**
 * 招聘需求域类型定义（对齐 script/sql/hr_recruit.sql 的 hr_recruit_demand / hr_recruit_demand_change）
 *
 * 字段名 = DDL 列名去掉下划线后的 camelCase；不臆造后端字段。
 * 接口契约见 docs/hr-talent/SPEC-P2-招聘主线.md §3.1。
 */

/** 需求状态（hr_recruit_demand.status，字典 recruit_demand_status） */
export type DemandStatus = 'draft' | 'submitted' | 'recruiting' | 'paused' | 'completed' | 'closed';

/**
 * 需求动作（POST /recruit/demands/{id}/actions/{action}）
 * submit：草稿提交；confirm：已提交 → 招聘中（权限复用 recruit:demand:submit）；
 * pause/resume：暂停/复开；complete：招聘中 → 已完成；close：关闭（终态）。
 */
export type DemandAction = 'submit' | 'confirm' | 'pause' | 'resume' | 'complete' | 'close';

/**
 * 招聘需求（hr_recruit_demand）
 *
 * 主键列为 demand_id → demandId；bigint 一律用 string | number，避免 JS 精度丢失。
 */
export interface HrDemandVO extends BaseEntity {
  /** 需求ID（主键） */
  demandId?: string | number;
  /** 需求编号（业务编号，唯一） */
  demandNo?: string;
  /** 需求标题 */
  demandTitle?: string;
  /** 公司（平台部门）ID */
  companyDeptId?: string | number;
  /** 公司名称快照 */
  companyName?: string;
  /** 用工部门ID */
  useDeptId?: string | number;
  /** 用工部门名称快照 */
  useDeptName?: string;
  /** 招聘负责人用户ID */
  recruiterId?: string | number;
  /** 需求申请日期（yyyy-MM-dd） */
  applyDate?: string;
  /** 期望到岗日期（yyyy-MM-dd） */
  expectArrivalDate?: string;
  /** 需求人数（非负整数） */
  demandCount?: number;
  /** 已到岗人数（非负整数） */
  hiredCount?: number;
  /** 紧急程度（字典 recruit_urgency） */
  urgency?: string;
  /** 招聘形式（字典 recruit_mode） */
  recruitMode?: string;
  /** 需求岗位名称 */
  jobName?: string;
  /** 岗位职级（字典编码） */
  jobLevel?: string;
  /** 工作城市 */
  workCity?: string;
  /** 需求原因说明 */
  demandReason?: string;
  /** 需求状态（字典 recruit_demand_status） */
  status?: string;
  /** 提交人用户ID */
  submittedBy?: string | number;
  /** 提交时间 */
  submittedTime?: string;
  /** 确认人用户ID */
  confirmedBy?: string | number;
  /** 确认时间 */
  confirmedTime?: string;
  /** 关闭人用户ID */
  closedBy?: string | number;
  /** 关闭时间 */
  closedTime?: string;
  /** 乐观锁版本号（更新时必须回传） */
  version?: number;
  /** 备注 */
  remark?: string;
  /**
   * 展示用可选字段：若有则由后端关联回填，缺失时前端回退显示 recruiterId。
   * hr_recruit_demand 无该列，仅作展示，不参与提交。
   */
  recruiterName?: string;
}

/** 需求新增/编辑表单（POST /recruit/demands、PUT /recruit/demands/{id}） */
export interface HrDemandForm {
  demandId?: string | number;
  demandNo?: string;
  demandTitle?: string;
  companyDeptId?: string | number;
  companyName?: string;
  useDeptId?: string | number;
  useDeptName?: string;
  recruiterId?: string | number;
  applyDate?: string;
  expectArrivalDate?: string;
  demandCount?: number;
  urgency?: string;
  recruitMode?: string;
  jobName?: string;
  jobLevel?: string;
  workCity?: string;
  demandReason?: string;
  /** 乐观锁版本号：编辑时必传 */
  version?: number;
  remark?: string;
}

/** 需求查询条件（GET /recruit/demands） */
export interface HrDemandQuery extends PageQuery {
  demandNo?: string;
  demandTitle?: string;
  companyDeptId?: string | number;
  useDeptId?: string | number;
  recruiterId?: string | number;
  jobName?: string;
  workCity?: string;
  status?: string;
  urgency?: string;
  recruitMode?: string;
  /** 日期区间走 RuoYi 约定的 params.beginXxx / params.endXxx */
  params?: Record<string, any>;
}

/** 需求动作入参（暂停/复开/关闭/完成必须填写原因；提交与确认原因可选） */
export interface HrDemandActionForm {
  /** 动作原因（submit/confirm 可不填，pause/resume/close/complete 必填） */
  reason?: string;
  /** 乐观锁版本号（可选，后端可据此校验） */
  version?: number;
}

/**
 * 需求变更历史（hr_recruit_demand_change）
 *
 * before_json / after_json 为 json 列：后端可能下发字符串或对象，渲染时统一格式化。
 */
export interface HrDemandChangeVO extends BaseEntity {
  /** 变更记录ID */
  changeId?: string | number;
  /** 招聘需求ID */
  demandId?: string | number;
  /** 变更类型（create/update/submit/pause/close 等稳定编码） */
  changeType?: string;
  /** 变更前快照 */
  beforeJson?: Record<string, any> | string | null;
  /** 变更后快照 */
  afterJson?: Record<string, any> | string | null;
  /** 变更原因 */
  reason?: string;
  /** 操作人用户ID */
  operatorId?: string | number;
  /** 操作时间 */
  operateTime?: string;
  /** 备注 */
  remark?: string;
  /** 展示用可选字段：缺失时回退显示 operatorId */
  operatorName?: string;
}
