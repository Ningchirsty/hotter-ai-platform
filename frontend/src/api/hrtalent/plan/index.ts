import type { PageResult } from '@/api/types';
import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type {
  HrPlanForm,
  HrPlanItemActionForm,
  HrPlanItemEditForm,
  HrPlanItemForm,
  HrPlanItemQuery,
  HrPlanItemSimilarQuery,
  HrPlanItemVO,
  HrPlanQuery,
  HrPlanVO,
  PlanAction,
  PlanItemAction
} from './types';

/**
 * 公司月度计划与计划任务接口封装
 * 路径与 docs/hr-talent/SPEC-P2-招聘主线.md §3.2 逐字一致。
 * 注意：新增任务永远创建新记录，相似计划只做提示，绝不合并（设计 §8.2.1）。
 */

/** 分页查询月度计划表头 */
export function listPlan(query: HrPlanQuery): AxiosPromise<PageResult<HrPlanVO>> {
  return request({
    url: '/recruit/plans',
    method: 'get',
    params: query
  });
}

/** 查询月度计划表头详情 */
export function getPlan(planId: string | number): AxiosPromise<HrPlanVO> {
  return request({
    url: '/recruit/plans/' + planId,
    method: 'get'
  });
}

/** 新增月度计划表头（一个公司 + 一个自然月只有一张表头，由服务层判定） */
export function addPlan(data: HrPlanForm) {
  return request({
    url: '/recruit/plans',
    method: 'post',
    data: data
  });
}

/** 更新月度计划表头 */
export function updatePlan(data: HrPlanForm) {
  return request({
    url: '/recruit/plans/' + data.planId,
    method: 'put',
    data: data
  });
}

/** 计划表头动作：confirm / close */
export function actionPlan(planId: string | number, action: PlanAction) {
  return request({
    url: `/recruit/plans/${planId}/actions/${action}`,
    method: 'post'
  });
}

/** 查询表头下的计划任务（分页） */
export function listPlanItemByPlan(
  planId: string | number,
  query: HrPlanItemQuery
): AxiosPromise<PageResult<HrPlanItemVO>> {
  return request({
    url: `/recruit/plans/${planId}/items`,
    method: 'get',
    params: query
  });
}

/** 计划任务分页（跨计划查询） */
export function listPlanItem(query: HrPlanItemQuery): AxiosPromise<PageResult<HrPlanItemVO>> {
  return request({
    url: '/recruit/plan-items',
    method: 'get',
    params: query
  });
}

/** 在指定表头下新增独立任务（source_type = new） */
export function addPlanItem(planId: string | number, data: HrPlanItemForm) {
  return request({
    url: `/recruit/plans/${planId}/items`,
    method: 'post',
    data: data
  });
}

/** 编辑计划任务（仅可改允许字段） */
export function updatePlanItem(data: HrPlanItemEditForm) {
  return request({
    url: '/recruit/plan-items/' + data.itemId,
    method: 'put',
    data: data
  });
}

/** 计划任务动作：pause / resume / cancel */
export function actionPlanItem(itemId: string | number, action: PlanItemAction, data: HrPlanItemActionForm) {
  return request({
    url: `/recruit/plan-items/${itemId}/actions/${action}`,
    method: 'post',
    data: data
  });
}

/** 查询跨月结转链 */
export function listPlanItemRolloverChain(itemId: string | number): AxiosPromise<HrPlanItemVO[]> {
  return request({
    url: `/recruit/plan-items/${itemId}/rollover-chain`,
    method: 'get'
  });
}

/** 触发单任务状态重算 */
export function refreshPlanItemStatus(itemId: string | number) {
  return request({
    url: `/recruit/plan-items/${itemId}/refresh-status`,
    method: 'post'
  });
}

/** 相似计划提示（同公司 + 部门 + 岗位，仅提示存在相似任务，不合并） */
export function listSimilarPlanItem(query: HrPlanItemSimilarQuery): AxiosPromise<HrPlanItemVO[]> {
  return request({
    url: '/recruit/plan-items/similar',
    method: 'get',
    params: query
  });
}
