import type { PageResult } from '@/api/types';
import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type { HrJobAssignForm, HrJobForm, HrJobQuery, HrJobVO, JobAction } from './types';

/**
 * 岗位执行项接口封装
 * 路径与 docs/hr-talent/SPEC-P2-招聘主线.md §3.4 逐字一致。
 */

/** 分页查询岗位需求 */
export function listJob(query: HrJobQuery): AxiosPromise<PageResult<HrJobVO>> {
  return request({
    url: '/recruit/jobs',
    method: 'get',
    params: query
  });
}

/** 查询岗位详情 */
export function getJob(jobId: string | number): AxiosPromise<HrJobVO> {
  return request({
    url: '/recruit/jobs/' + jobId,
    method: 'get'
  });
}

/** 新增岗位 */
export function addJob(data: HrJobForm) {
  return request({
    url: '/recruit/jobs',
    method: 'post',
    data: data
  });
}

/** 更新岗位（带 version 乐观锁） */
export function updateJob(data: HrJobForm) {
  return request({
    url: '/recruit/jobs/' + data.jobId,
    method: 'put',
    data: data
  });
}

/** 逻辑删除岗位 */
export function delJob(jobIds: string | number | (string | number)[]) {
  return request({
    url: '/recruit/jobs/' + jobIds,
    method: 'delete'
  });
}

/** 岗位动作：close / reopen */
export function actionJob(jobId: string | number, action: JobAction) {
  return request({
    url: `/recruit/jobs/${jobId}/actions/${action}`,
    method: 'post'
  });
}

/** 分配岗位负责人/协助人/面试官 */
export function assignJob(data: HrJobAssignForm) {
  const { jobId, ...rest } = data;
  return request({
    url: `/recruit/jobs/${jobId}/assign`,
    method: 'post',
    data: rest
  });
}
