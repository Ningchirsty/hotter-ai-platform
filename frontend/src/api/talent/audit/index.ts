import type { PageResult } from '@/api/types';
import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type { SensitiveAuditQuery, SensitiveAuditVO } from './types';

// 查询敏感操作审计列表
export function listAudit(query: SensitiveAuditQuery): AxiosPromise<PageResult<SensitiveAuditVO>> {
  return request({
    url: '/talent/audit/list',
    method: 'get',
    params: query
  });
}
