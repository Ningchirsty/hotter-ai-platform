import type { PageResult } from '@/api/types';
import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type { TalentDuplicateConfirmForm, TalentDuplicateQuery, TalentDuplicateVO } from './types';

// 查询重复人才预警列表
export function listDuplicate(query: TalentDuplicateQuery): AxiosPromise<PageResult<TalentDuplicateVO>> {
  return request({
    url: '/talent/duplicate/list',
    method: 'get',
    params: query
  });
}

// 人工确认重复结论（不自动合并）
export function confirmDuplicate(data: TalentDuplicateConfirmForm) {
  return request({
    url: '/talent/duplicate/confirm',
    method: 'post',
    data: data
  });
}
