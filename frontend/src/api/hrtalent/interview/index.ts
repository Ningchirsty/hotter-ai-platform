import type { PageResult } from '@/api/types';
import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type {
  HrInterviewCancelForm,
  HrInterviewFeedbackForm,
  HrInterviewForm,
  HrInterviewQuery,
  HrInterviewVO
} from './types';

/**
 * 面试管理 域接口封装
 *
 * 路径与 docs/hr-talent/SPEC-P3-招聘流程闭环.md §2.3 逐字一致，并与后端
 * RecruitInterviewController（/recruit/interviews）完全对齐。
 * 权限串：recruit:interview:list / schedule / cancel / feedback。
 */

/** 分页查询面试记录 */
export function listInterview(query: HrInterviewQuery): AxiosPromise<PageResult<HrInterviewVO>> {
  return request({
    url: '/recruit/interviews',
    method: 'get',
    params: query
  });
}

/** 面试官待办：当前登录用户为面试官且尚未反馈的面试 */
export function listMyInterviewTodos(): AxiosPromise<HrInterviewVO[]> {
  return request({
    url: '/recruit/interviews/my-todos',
    method: 'get'
  });
}

/** 查询面试详情（含各面试官个人意见） */
export function getInterview(id: string | number): AxiosPromise<HrInterviewVO> {
  return request({
    url: '/recruit/interviews/' + id,
    method: 'get'
  });
}

/** 安排面试（指定轮次/时间/方式/地点/一名或多名面试官） */
export function addInterview(data: HrInterviewForm): AxiosPromise<string | number> {
  return request({
    url: '/recruit/interviews',
    method: 'post',
    data: data
  });
}

/** 面试改期（保留操作记录：原记录置为已改期，新增一条有效记录） */
export function rescheduleInterview(id: string | number, data: HrInterviewForm): AxiosPromise<string | number> {
  return request({
    url: '/recruit/interviews/' + id,
    method: 'put',
    data: data
  });
}

/** 取消面试（必须填写原因） */
export function cancelInterview(id: string | number, data: HrInterviewCancelForm) {
  return request({
    url: `/recruit/interviews/${id}/cancel`,
    method: 'post',
    data: data
  });
}

/** 面试官提交个人评分 / 结论 / 意见 */
export function submitInterviewFeedback(id: string | number, data: HrInterviewFeedbackForm) {
  return request({
    url: `/recruit/interviews/${id}/feedback`,
    method: 'post',
    data: data
  });
}
