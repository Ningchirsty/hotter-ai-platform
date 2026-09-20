import type { PageResult } from '@/api/types';
import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type { AigInvocationAuditQuery, AigInvocationAuditVO } from './types';

// 查询 AI 调用审计列表（只读，追加型审计不做删除/修改）
export function listInvocationAudit(
  query: AigInvocationAuditQuery
): AxiosPromise<PageResult<AigInvocationAuditVO>> {
  return request({
    url: '/aigov/audit/list',
    method: 'get',
    params: query
  });
}
