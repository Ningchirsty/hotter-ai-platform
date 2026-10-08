import type { PageResult } from '@/api/types';
import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type {
  AigCallApprovalDecideForm,
  AigCallApprovalForm,
  AigCallApprovalQuery,
  AigCallApprovalSweepVO,
  AigCallApprovalVO
} from './types';

/** 分页查询审批单/授权清单（治理台） */
export function listApproval(query: AigCallApprovalQuery): AxiosPromise<PageResult<AigCallApprovalVO>> {
  return request({
    url: '/aigov/approval/list',
    method: 'get',
    params: query
  });
}

/** 查自己的申请与授权（后端用登录态覆盖 requesterId，不需要治理权限） */
export function myApproval(query: AigCallApprovalQuery): AxiosPromise<PageResult<AigCallApprovalVO>> {
  return request({
    url: '/aigov/approval/my',
    method: 'get',
    params: query
  });
}

/** 提交一张调用授权申请单 */
export function applyApproval(data: AigCallApprovalForm): AxiosPromise<string | number> {
  return request({
    url: '/aigov/approval',
    method: 'post',
    data
  });
}

/** 批准或驳回一张待审批单 */
export function decideApproval(
  approvalId: string | number,
  data: AigCallApprovalDecideForm
): AxiosPromise<void> {
  return request({
    url: '/aigov/approval/' + approvalId + '/decide',
    method: 'post',
    data
  });
}

/** 撤回自己提交的、尚未出结论的申请单 */
export function cancelApproval(approvalId: string | number): AxiosPromise<void> {
  return request({
    url: '/aigov/approval/' + approvalId + '/cancel',
    method: 'post'
  });
}

/** 手动触发一次「超时扫描」（把超过审批时限的待审批单置为已超时） */
export function expireScanApproval(): AxiosPromise<AigCallApprovalSweepVO> {
  return request({
    url: '/aigov/approval/expire-scan',
    method: 'post'
  });
}
