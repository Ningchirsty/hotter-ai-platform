import type { PageResult } from '@/api/types';
import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type { AigPortalRoleHomeVO, AigPortalRoleVO, AigPortalTaskQuery, AigPortalTaskVO } from './types';

/**
 * 员工 AI 工作台（门户）接口（主文档线增量 2）。
 *
 * 全部是**只读**接口：员工工作台不在这里启动能力——启动要走 Launch Resolver（增量 3），
 * 那样才会有"这次启动是谁、按哪张卡片、结果落在哪个任务"的可审计凭证。
 *
 * 这些接口不需要权限点（后端是 `@SaCheckLogin`）：它们只返回"当前用户自己"能看到的东西，
 * 范围由绑定与登录身份决定。
 */

/** 我的岗位 */
export function listMyRoles(): AxiosPromise<AigPortalRoleVO[]> {
  return request({
    url: '/aigov/portal/roles',
    method: 'get'
  });
}

/** 岗位首页（服务端过滤后的卡片） */
export function getRoleHome(roleCode: string): AxiosPromise<AigPortalRoleHomeVO> {
  return request({
    url: '/aigov/portal/roles/' + encodeURIComponent(roleCode) + '/home',
    method: 'get'
  });
}

/** 我的任务（提交人由服务端固定为当前用户） */
export function listMyTasks(query: AigPortalTaskQuery): AxiosPromise<PageResult<AigPortalTaskVO>> {
  return request({
    url: '/aigov/portal/my-tasks',
    method: 'get',
    params: query
  });
}
