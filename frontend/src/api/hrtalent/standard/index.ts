import type { PageResult } from '@/api/types';
import type { AxiosPromise } from '@/utils/api-types';
import type { RecruitStandardForm, RecruitStandardQuery, RecruitStandardVO } from './types';
import request from '@/utils/request';

/**
 * 招聘期限标准 接口封装。
 *
 * 除单条维护外，导入三段式（模板 / 预检 / 确认）由
 * `@/api/hrtalent/import` 的工厂按本模块前缀生成，见页面里的 useImportApi。
 */

/** 分页查询 */
export function listStandard(query: RecruitStandardQuery): AxiosPromise<PageResult<RecruitStandardVO>> {
  return request({
    url: '/recruit/standards',
    method: 'get',
    params: query
  });
}

/** 详情 */
export function getStandard(id: string | number): AxiosPromise<RecruitStandardVO> {
  return request({
    url: '/recruit/standards/' + id,
    method: 'get'
  });
}

/** 新增 */
export function addStandard(data: RecruitStandardForm) {
  return request({
    url: '/recruit/standards',
    method: 'post',
    data
  });
}

/** 修改 */
export function updateStandard(data: RecruitStandardForm) {
  return request({
    url: '/recruit/standards/' + data.standardId,
    method: 'put',
    data
  });
}

/** 删除 */
export function delStandard(id: string | number) {
  return request({
    url: '/recruit/standards/' + id,
    method: 'delete'
  });
}
