import type { PageResult } from '@/api/types';
import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type { AigAgentBindingForm, AigAgentBindingQuery, AigAgentBindingVO } from './types';

/** 分页查询 Agent 版本绑定清单 */
export function listBinding(query: AigAgentBindingQuery): AxiosPromise<PageResult<AigAgentBindingVO>> {
  return request({
    url: '/aigov/agent/binding/list',
    method: 'get',
    params: query
  });
}

/** 新增绑定（通道留空取版本当前通道；填了必须与版本一致） */
export function addBinding(data: AigAgentBindingForm) {
  return request({
    url: '/aigov/agent/binding',
    method: 'post',
    data: data
  });
}
