import type { PageResult } from '@/api/types';
import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type {
  HrTalentDuplicateCaseVO,
  HrTalentDuplicateConfirmForm,
  HrTalentDuplicateIgnoreForm,
  HrTalentDuplicateQuery,
  HrTalentMergeForm,
  HrTalentMergeLogVO,
  HrTalentMergePreviewVO
} from './types';

/**
 * 重复人才治理与合并 域接口封装
 *
 * 路径与后端已实现 TalentDuplicateController（/talent/duplicates）逐字一致。
 * 权限串：talent:duplicate:list/confirm/merge/ignore。
 *
 * 安全口径：合并会在**单个数据库事务**内转移应聘记录、简历、经历、附件、人才池成员、标签、
 * 跟进记录与授权范围，并写入合并快照与审计；**只允许集团人才管理员执行**（服务层判定）。
 */

/** 分页查询疑似重复案件（自动套人才可见范围，只返回摘要字段） */
export function listDuplicate(
  query: HrTalentDuplicateQuery
): AxiosPromise<PageResult<HrTalentDuplicateCaseVO>> {
  return request({
    url: '/talent/duplicates',
    method: 'get',
    params: query
  });
}

/** 合并预览：冲突字段差异与关系数量（GET /talent/duplicates/{id}/preview，权限 talent:duplicate:list） */
export function previewMerge(id: string | number): AxiosPromise<HrTalentMergePreviewVO> {
  return request({
    url: `/talent/duplicates/${id}/preview`,
    method: 'get'
  });
}

/** 确认疑似重复（POST /talent/duplicates/{id}/confirm，权限 talent:duplicate:confirm，置为「已确认待合并」） */
export function confirmDuplicate(id: string | number, data: HrTalentDuplicateConfirmForm) {
  return request({
    url: `/talent/duplicates/${id}/confirm`,
    method: 'post',
    data: data
  });
}

/** 忽略疑似重复（POST /talent/duplicates/{id}/ignore，权限 talent:duplicate:ignore） */
export function ignoreDuplicate(id: string | number, data: HrTalentDuplicateIgnoreForm) {
  return request({
    url: `/talent/duplicates/${id}/ignore`,
    method: 'post',
    data: data
  });
}

/** 事务合并两名人才（POST /talent/duplicates/{id}/merge，权限 talent:duplicate:merge），返回合并日志ID */
export function mergeDuplicate(id: string | number, data: HrTalentMergeForm): AxiosPromise<string | number> {
  return request({
    url: `/talent/duplicates/${id}/merge`,
    method: 'post',
    data: data
  });
}

/** 查询合并日志快照（GET /talent/duplicates/merge-logs，权限 talent:duplicate:merge） */
export function listMergeLogs(query: {
  keepTalentId?: string | number;
  mergedTalentId?: string | number;
  pageNum?: number;
  pageSize?: number;
}): AxiosPromise<PageResult<HrTalentMergeLogVO>> {
  return request({
    url: '/talent/duplicates/merge-logs',
    method: 'get',
    params: query
  });
}
