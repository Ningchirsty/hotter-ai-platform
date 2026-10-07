import type { PageResult } from '@/api/types';
import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type { AigUserQuotaForm, AigUserQuotaQuery, AigUserQuotaUsageVO, AigUserQuotaVO } from './types';

/** 分页查询人均配额清单 */
export function listQuota(query: AigUserQuotaQuery): AxiosPromise<PageResult<AigUserQuotaVO>> {
  return request({
    url: '/aigov/quota/list',
    method: 'get',
    params: query
  });
}

/** 查某人的用量（已用 / 上限 / 周期起点） */
export function quotaUsage(userId: string | number): AxiosPromise<AigUserQuotaUsageVO> {
  return request({
    url: '/aigov/quota/usage/' + userId,
    method: 'get'
  });
}

/** 保存配额（一人一行：按 userId upsert；上限传 null = 不限） */
export function saveQuota(data: AigUserQuotaForm): AxiosPromise<string | number> {
  return request({
    url: '/aigov/quota',
    method: 'post',
    data
  });
}

/** 删除配额——含义是回到「不限」，不是禁止调用 */
export function removeQuota(quotaId: string | number): AxiosPromise<void> {
  return request({
    url: '/aigov/quota/' + quotaId,
    method: 'delete'
  });
}

/** 当前登录人的用量（自查「我还能调几次」） */
export function myQuotaUsage(): AxiosPromise<AigUserQuotaUsageVO> {
  return request({
    url: '/aigov/quota/my-usage',
    method: 'get'
  });
}
