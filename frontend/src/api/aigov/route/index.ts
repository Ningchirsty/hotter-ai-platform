import type { PageResult } from '@/api/types';
import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type { AigRoutePolicyForm, AigRoutePolicyQuery, AigRoutePolicyVO } from './types';

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
