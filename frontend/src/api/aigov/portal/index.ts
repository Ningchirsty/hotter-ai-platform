import type { PageResult } from '@/api/types';
import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type {
  AigLaunchCommitVO,
  AigLaunchPrepareVO,
  AigLaunchRecordVO,
  AigLaunchRequestForm,
  AigPortalRoleHomeVO,
  AigPortalRoleVO,
  AigPortalTaskQuery,
  AigPortalTaskVO
} from './types';

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

/**
 * 预检并领取启动票据（**无副作用**：不建任务、不改业务数据）。
 *
 * 校验没过时返回 `problems`（码 + 文案）且**没有票据**——拿到票才能 commit，所以"不通不发票"。
 */
export function prepareLaunch(data: AigLaunchRequestForm): AxiosPromise<AigLaunchPrepareVO> {
  return request({
    url: '/aigov/portal/launch/prepare',
    method: 'post',
    data
  });
}

/**
 * 确认启动（**幂等**：同一个幂等键重复提交只得到同一次启动）。
 *
 * 业务拒绝（票据过期/配额耗尽/权限或输入不对）以 `passed=false` + `problems` 返回，
 * 调用方必须看 `passed`，不能只看 HTTP 状态。
 */
export function commitLaunch(data: AigLaunchRequestForm): AxiosPromise<AigLaunchCommitVO> {
  return request({
    url: '/aigov/portal/launch/commit',
    method: 'post',
    data
  });
}

/**
 * 按任务查"这次启动是什么"（专业台回跳入口用）。
 *
 * 只返回**当前用户自己发起**的那一次；不是自己发起的会报错（服务端不区分"不存在"与"不是你的"）。
 */
export function getLaunchRecordByTask(taskId: string | number): AxiosPromise<AigLaunchRecordVO> {
  return request({
    url: '/aigov/portal/launch/records/by-task/' + taskId,
    method: 'get'
  });
}
