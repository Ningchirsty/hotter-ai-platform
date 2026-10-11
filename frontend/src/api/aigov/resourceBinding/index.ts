import type { PageResult } from '@/api/types';
import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type {
  AigAgentDeclaredResourceVO,
  AigAgentResourceBindingForm,
  AigAgentResourceBindingQuery,
  AigAgentResourceBindingVO
} from './types';

/** 分页查询工具/知识绑定清单 */
export function listResourceBinding(
  query: AigAgentResourceBindingQuery
): AxiosPromise<PageResult<AigAgentResourceBindingVO>> {
  return request({
    url: '/aigov/agent/resource-binding/list',
    method: 'get',
    params: query
  });
}

/**
 * 读取某个 Agent 版本「声明了什么」（允许工具 / 禁止工具 / 知识范围 + 是否已绑定）。
 *
 * 页面据此让人从声明里挑一项，而不是手打编码再看服务端拒绝。
 */
export function getDeclaredResources(
  agentVersionId: string | number
): AxiosPromise<AigAgentDeclaredResourceVO> {
  return request({
    url: '/aigov/agent/version/' + agentVersionId + '/declared-resources',
    method: 'get'
  });
}

/** 新增绑定（编码必须命中该版本已声明的项） */
export function addResourceBinding(data: AigAgentResourceBindingForm) {
  return request({
    url: '/aigov/agent/resource-binding',
    method: 'post',
    data
  });
}
