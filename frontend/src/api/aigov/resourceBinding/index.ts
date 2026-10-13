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

/**
 * 删除绑定（逻辑删除）。
 *
 * 存在的理由：范围列不可改、同一范围又只能绑一次，绑错了没有出路；
 * 服务端的重复检查文案自己写着「要改范围请删掉旧的那条再建」，这个接口就是那句话的前提。
 * 删掉之后同范围可以立刻重建（已删除的行由 @TableLogic 从查询里过滤掉）。
 */
export function delResourceBinding(bindingId: string | number) {
  return request({
    url: '/aigov/agent/resource-binding/' + bindingId,
    method: 'delete'
  });
}
