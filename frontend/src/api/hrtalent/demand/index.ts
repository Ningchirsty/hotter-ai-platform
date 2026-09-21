import type { PageResult } from '@/api/types';
import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type {
  DemandAction,
  HrDemandActionForm,
  HrDemandChangeVO,
  HrDemandForm,
  HrDemandQuery,
  HrDemandVO
} from './types';

/**
 * 招聘需求域接口封装（路径与 docs/hr-talent/SPEC-P2-招聘主线.md §3.1 逐字一致）
 * 对应权限串：recruit:demand:list/query/add/edit/submit/pause/close
 */

/** 分页查询招聘需求（自动应用数据权限） */
export function listDemand(query: HrDemandQuery): AxiosPromise<PageResult<HrDemandVO>> {
  return request({
    url: '/recruit/demands',
    method: 'get',
    params: query
  });
}

/** 查询需求详情 */
export function getDemand(demandId: string | number): AxiosPromise<HrDemandVO> {
  return request({
    url: '/recruit/demands/' + demandId,
    method: 'get'
  });
}

/** 新增需求草稿 */
export function addDemand(data: HrDemandForm) {
  return request({
    url: '/recruit/demands',
    method: 'post',
    data: data
  });
}

/** 更新需求（带 version 乐观锁） */
export function updateDemand(data: HrDemandForm) {
  return request({
    url: '/recruit/demands/' + data.demandId,
    method: 'put',
    data: data
  });
}

/** 逻辑删除需求（仅草稿可删） */
export function delDemand(demandIds: string | number | (string | number)[]) {
  return request({
    url: '/recruit/demands/' + demandIds,
    method: 'delete'
  });
}

/**
 * 需求动作：submit / pause / resume / complete / close
 * 暂停、复开、关闭、完成必须填写原因（reason）。
 */
export function actionDemand(demandId: string | number, action: DemandAction, data: HrDemandActionForm) {
  return request({
    url: `/recruit/demands/${demandId}/actions/${action}`,
    method: 'post',
    data: data
  });
}

/** 查询需求变更历史 */
export function listDemandChanges(demandId: string | number): AxiosPromise<HrDemandChangeVO[]> {
  return request({
    url: '/recruit/demands/' + demandId + '/changes',
    method: 'get'
  });
}
