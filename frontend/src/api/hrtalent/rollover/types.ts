/**
 * 月度结转中心类型定义
 * 对齐 script/sql/hr_recruit.sql 的 hr_recruit_plan_rollover，并引用计划任务 VO。
 * 接口契约见 docs/hr-talent/SPEC-P2-招聘主线.md §3.3。
 *
 * ⚠ 存疑点（已在交付汇报中声明）：
 * 1. `hr_recruit_plan_rollover` 是「每条结转明细一行」，DDL 中没有批次表；
 *    而 §3.3 要求 `GET /recruit/plan-rollovers` 返回「批次分页」，
 *    故 HrRolloverBatchVO 只能是后端按 batch_no/batch_id 聚合出的对象，
 *    其字段名无法从 DDL 逐字推导，此处按 DDL 可用列的最小并集定义，全部可选。
 * 2. 预览接口 `POST /recruit/plan-rollovers/preview` 的出参结构在 §3 未定义，
 *    此处按「待结转任务列表 + 合计」定义，并在页面侧同时兼容「直接返回数组」的形态。
 */

import type { HrPlanItemVO } from '../plan/types';

/** 结转批次（由 hr_recruit_plan_rollover 按批次聚合/或批次表下发，字段待后端确认） */
export interface HrRolloverBatchVO {
  /** 批次ID（对应 hr_recruit_plan_item.carryover_batch_id） */
  batchId?: string | number;
  /** 批次号（可读编号） */
  batchNo?: string;
  /** 来源月份（yyyy-MM） */
  sourceMonth?: string;
  /** 目标月份（yyyy-MM） */
  targetMonth?: string;
  /** 批次内结转记录总数 */
  totalCount?: number;
  /** 成功条数 */
  successCount?: number;
  /** 失败条数 */
  failedCount?: number;
  /** 批次结转人数合计 */
  carryoverQty?: number;
  /** 执行时间 */
  executeTime?: string;
  /** 操作人用户ID */
  operatorId?: string | number;
  /** 批次结果（success/failed/partial 等稳定编码） */
  result?: string;
  /** 备注 */
  remark?: string;
}

/** 结转明细（hr_recruit_plan_rollover 逐字段对应） */
export interface HrRolloverRecordVO extends BaseEntity {
  /** 结转记录ID */
  rolloverId?: string | number;
  /** 来源（上月）计划任务ID */
  sourceItemId?: string | number;
  /** 目标（本月）计划任务ID */
  targetItemId?: string | number;
  /** 来源月份 */
  sourceMonth?: string;
  /** 目标月份 */
  targetMonth?: string;
  /** 结转人数 */
  carryoverQty?: number;
  /** 结转批次ID */
  batchId?: string | number;
  /** 结转批次号 */
  batchNo?: string;
  /** 执行时间 */
  executeTime?: string;
  /** 执行结果（success/failed/skipped） */
  result?: string;
  /** 失败原因（单条失败可安全重试） */
  failureReason?: string;
  /** 重试次数 */
  retryCount?: number;
  /** 操作人用户ID */
  operatorId?: string | number;
  /** 备注 */
  remark?: string;
  /** 展示用可选字段：来源任务编号 */
  sourceItemNo?: string;
  /** 展示用可选字段：目标任务编号 */
  targetItemNo?: string;
}

/** 结转批次明细（GET /recruit/plan-rollovers/{batchNo}） */
export interface HrRolloverBatchDetailVO extends HrRolloverBatchVO {
  /** 批次明细行 */
  records?: HrRolloverRecordVO[];
  /** 部分实现可能用 items 命名明细 */
  items?: HrRolloverRecordVO[];
}

/** 结转预览入参（POST /recruit/plan-rollovers/preview） */
export interface HrRolloverPreviewForm {
  /** 目标月份（yyyy-MM） */
  targetMonth: string;
}

/** 结转预览出参：待结转过任务与人数 */
export interface HrRolloverPreviewVO {
  targetMonth?: string;
  /** 待结转任务条数 */
  totalCount?: number;
  /** 待结转人数合计 */
  totalQty?: number;
  /** 待结转任务明细 */
  items?: HrPlanItemVO[];
}

/** 执行结转入参（POST /recruit/plan-rollovers/execute） */
export interface HrRolloverExecuteForm {
  /** 目标月份（yyyy-MM） */
  targetMonth: string;
  /** 指定要结转的来源任务ID；为空表示按规则结转到目标月份的全部符合条件任务 */
  sourceItemIds?: (string | number)[];
  /** 备注 */
  remark?: string;
}

/** 执行结转出参 */
export interface HrRolloverExecuteVO {
  batchId?: string | number;
  batchNo?: string;
  targetMonth?: string;
  totalCount?: number;
  successCount?: number;
  failedCount?: number;
  carryoverQty?: number;
  /** 逐条执行结果 */
  results?: HrRolloverRecordVO[];
}

/** 批次列表查询条件（GET /recruit/plan-rollovers） */
export interface HrRolloverQuery extends PageQuery {
  batchNo?: string;
  sourceMonth?: string;
  targetMonth?: string;
  result?: string;
  operatorId?: string | number;
  params?: Record<string, any>;
}
