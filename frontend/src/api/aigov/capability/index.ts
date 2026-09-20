import type { PageResult } from '@/api/types';
import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type { AigCapabilityForm, AigCapabilityQuery, AigCapabilityVO } from './types';

// 查询 AI 能力目录列表
export function listCapability(query: AigCapabilityQuery): AxiosPromise<PageResult<AigCapabilityVO>> {
  return request({
    url: '/aigov/capability/list',
    method: 'get',
    params: query
  });
}

// 查询 AI 能力详情
export function getCapability(capabilityId: string | number): AxiosPromise<AigCapabilityVO> {
  return request({
    url: '/aigov/capability/' + capabilityId,
    method: 'get'
  });
}

// 新增 AI 能力
export function addCapability(data: AigCapabilityForm) {
  return request({
    url: '/aigov/capability',
    method: 'post',
    data: data
  });
}

// 修改 AI 能力
export function updateCapability(data: AigCapabilityForm) {
  return request({
    url: '/aigov/capability',
    method: 'put',
    data: data
  });
}

// 删除 AI 能力
export function delCapability(capabilityId: string | number | Array<string | number>) {
  return request({
    url: '/aigov/capability/' + capabilityId,
    method: 'delete'
  });
}
