import type { PageResult } from '@/api/types';
import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type {
  AigRoutePolicyForm,
  AigRoutePolicyQuery,
  AigRoutePolicyVO,
  AigRouteScenarioBindingForm,
  AigRouteScenarioBindingQuery,
  AigRouteScenarioBindingVO
} from './types';

// 查询路由策略列表（能力 × 数据等级）
export function listRoutePolicy(query: AigRoutePolicyQuery): AxiosPromise<PageResult<AigRoutePolicyVO>> {
  return request({
    url: '/aigov/route/list',
    method: 'get',
    params: query
  });
}

// 查询路由策略详情
export function getRoutePolicy(policyId: string | number): AxiosPromise<AigRoutePolicyVO> {
  return request({
    url: '/aigov/route/' + policyId,
    method: 'get'
  });
}

// 新增路由策略
export function addRoutePolicy(data: AigRoutePolicyForm) {
  return request({
    url: '/aigov/route',
    method: 'post',
    data: data
  });
}

// 修改路由策略
export function updateRoutePolicy(data: AigRoutePolicyForm) {
  return request({
    url: '/aigov/route',
    method: 'put',
    data: data
  });
}

// 删除路由策略
export function delRoutePolicy(policyId: string | number | Array<string | number>) {
  return request({
    url: '/aigov/route/' + policyId,
    method: 'delete'
  });
}

// ------------------------------------------------------------------ 场景强制绑定
// 语义只收紧：命中绑定后候选被收窄为「仅指定供应商」，不会放宽任何治理口径。
// 因此它在界面上与路由策略放在同一页，但权限独立（aig:route:binding）。

// 查询场景强制绑定列表
export function listScenarioBinding(
  query: AigRouteScenarioBindingQuery
): AxiosPromise<PageResult<AigRouteScenarioBindingVO>> {
  return request({
    url: '/aigov/route/binding/list',
    method: 'get',
    params: query
  });
}

// 查询场景强制绑定详情
export function getScenarioBinding(bindId: string | number): AxiosPromise<AigRouteScenarioBindingVO> {
  return request({
    url: '/aigov/route/binding/' + bindId,
    method: 'get'
  });
}

// 新增场景强制绑定
export function addScenarioBinding(data: AigRouteScenarioBindingForm) {
  return request({
    url: '/aigov/route/binding',
    method: 'post',
    data: data
  });
}

// 修改场景强制绑定
export function updateScenarioBinding(data: AigRouteScenarioBindingForm) {
  return request({
    url: '/aigov/route/binding',
    method: 'put',
    data: data
  });
}

// 删除场景强制绑定（放开该场景的钉死，恢复按策略与优先级自由选取）
export function delScenarioBinding(bindId: string | number | Array<string | number>) {
  return request({
    url: '/aigov/route/binding/' + bindId,
    method: 'delete'
  });
}
