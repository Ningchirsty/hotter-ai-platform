import type { PageResult } from '@/api/types';
import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type { AigChangeRequestForm, AigChangeRequestQuery, AigChangeRequestVO } from './types';

/**
 * 高风险变更单接口（Q1C 完整版）。
 *
 * <p>权限分两档：**申请**用 `aig:capability:edit`（能改配置的人就能提单，这正是完整版的意义），
 * **批准/驳回**用 `aig:capability:high-risk`（与轻量版闸门同一权限点，不新增权限点）。</p>
 */

/** 提交变更单 */
export function submitChangeRequest(data: AigChangeRequestForm): AxiosPromise<string | number> {
  return request({
    url: '/aigov/change-request',
    method: 'post',
    data
  });
}

/** 变更单清单（按能力/状态过滤） */
export function listChangeRequest(query: AigChangeRequestQuery): AxiosPromise<PageResult<AigChangeRequestVO>> {
  return request({
    url: '/aigov/change-request/list',
    method: 'get',
    params: query
  });
}

/** 我提交的变更单（后端按登录态覆盖申请人） */
export function listMyChangeRequest(query: AigChangeRequestQuery): AxiosPromise<PageResult<AigChangeRequestVO>> {
  return request({
    url: '/aigov/change-request/my',
    method: 'get',
    params: query
  });
}

/**
 * 批准（**批准即执行**）。
 *
 * <p>返回处理后的单子：status=APPLIED 才是"已生效"；CONFLICTED 表示未生效，
 * 原因在 resultRemark——调用方必须如实展示。</p>
 */
export function approveChangeRequest(
  changeRequestId: string | number,
  remark?: string
): AxiosPromise<AigChangeRequestVO> {
  return request({
    url: '/aigov/change-request/' + changeRequestId + '/approve',
    method: 'post',
    params: { remark }
  });
}

/** 驳回（必须写明理由） */
export function rejectChangeRequest(
  changeRequestId: string | number,
  remark: string
): AxiosPromise<void> {
  return request({
    url: '/aigov/change-request/' + changeRequestId + '/reject',
    method: 'post',
    params: { remark }
  });
}

/** 撤回（只有申请人本人可以，且只能在待批准时） */
export function cancelChangeRequest(changeRequestId: string | number): AxiosPromise<void> {
  return request({
    url: '/aigov/change-request/' + changeRequestId + '/cancel',
    method: 'post'
  });
}
