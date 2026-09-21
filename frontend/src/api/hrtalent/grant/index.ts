import type { PageResult } from '@/api/types';
import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type { HrTalentScopeGrantForm, HrTalentScopeGrantQuery, HrTalentScopeGrantVO } from './types';

/**
 * 人才共享授权 域接口封装
 *
 * 路径与后端已实现 TalentScopeGrantController 逐字一致：
 * - GET    /talent/profiles/{id}/grants   某位人才的授权列表（权限 talent:grant:list）
 * - GET    /talent/grants                 当前用户可见范围内的授权列表（权限 talent:grant:list）
 * - GET    /talent/grants/{id}            授权详情（权限 talent:grant:list）
 * - POST   /talent/profiles/{id}/grants   新增授权（权限 talent:grant:add）
 * - POST   /talent/grants/{id}/revoke     撤销授权（权限 talent:grant:revoke，立即失效并保留审计轨迹）
 * - DELETE /talent/grants/{ids}           逻辑删除（数据清理用，日常业务走 revoke）
 *
 * ⚠️ 共享只扩大查看范围，**不自动授予电话明文、附件下载、背调和导出权限**（设计文档 §8.19）。
 */

/** 分页查询某位人才的授权列表（默认只返回当前有效授权） */
export function listGrantByTalent(
  id: string | number,
  query?: HrTalentScopeGrantQuery
): AxiosPromise<PageResult<HrTalentScopeGrantVO>> {
  return request({
    url: `/talent/profiles/${id}/grants`,
    method: 'get',
    params: query
  });
}

/** 分页查询当前用户可见范围内的授权列表 */
export function listGrant(query: HrTalentScopeGrantQuery): AxiosPromise<PageResult<HrTalentScopeGrantVO>> {
  return request({
    url: '/talent/grants',
    method: 'get',
    params: query
  });
}

/** 查询授权详情 */
export function getGrant(id: string | number): AxiosPromise<HrTalentScopeGrantVO> {
  return request({
    url: '/talent/grants/' + id,
    method: 'get'
  });
}

/** 新增共享授权（POST /talent/profiles/{id}/grants，权限 talent:grant:add） */
export function addGrant(id: string | number, data: HrTalentScopeGrantForm): AxiosPromise<string | number> {
  return request({
    url: `/talent/profiles/${id}/grants`,
    method: 'post',
    data: data
  });
}

/** 撤销共享授权（授权立即失效，保留审计轨迹；reason 为 query 参数） */
export function revokeGrant(id: string | number, reason?: string) {
  return request({
    url: `/talent/grants/${id}/revoke`,
    method: 'post',
    params: { reason }
  });
}

/** 逻辑删除授权（数据清理用；日常业务请使用 revokeGrant） */
export function delGrant(ids: string | number | Array<string | number>) {
  return request({
    url: '/talent/grants/' + ids,
    method: 'delete'
  });
}
