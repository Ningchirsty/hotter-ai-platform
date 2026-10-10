import type { PageResult } from '@/api/types';
import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type {
  AigStudioDraftCreateForm,
  AigStudioDraftDetailVO,
  AigStudioDraftQuery,
  AigStudioDraftRollbackForm,
  AigStudioDraftSaveForm,
  AigStudioDraftSubmitForm,
  AigStudioDraftSubmitVO,
  AigStudioDraftVO,
  AigStudioRevisionVO,
  AigStudioValidateVO
} from './types';

/**
 * 训练台草稿接口。
 *
 * 注意：这里**没有**任何"推进发布状态"的调用——训练台的「部署」只到「提交 DRAFT 版本」，
 * 之后的沙箱/黄金用例/人工批准/灰度仍走既有发布状态机（`/aigov/agent/release/advance`）。
 */

/** 分页查询草稿 */
export function listDraft(query: AigStudioDraftQuery): AxiosPromise<PageResult<AigStudioDraftVO>> {
  return request({
    url: '/aigov/studio/drafts/list',
    method: 'get',
    params: query
  });
}

/** 草稿详情 */
export function getDraft(draftId: string | number): AxiosPromise<AigStudioDraftDetailVO> {
  return request({
    url: '/aigov/studio/drafts/' + draftId,
    method: 'get'
  });
}

/** 新建草稿（同时产生第 1 个修订） */
export function createDraft(data: AigStudioDraftCreateForm): AxiosPromise<string | number> {
  return request({
    url: '/aigov/studio/drafts',
    method: 'post',
    data
  });
}

/** 保存草稿（必须带 expectedRevision，冲突会明确报错） */
export function saveDraft(
  draftId: string | number,
  data: AigStudioDraftSaveForm
): AxiosPromise<AigStudioDraftDetailVO> {
  return request({
    url: '/aigov/studio/drafts/' + draftId,
    method: 'put',
    data
  });
}

/** 修订历史（最新在前） */
export function listRevisions(draftId: string | number): AxiosPromise<AigStudioRevisionVO[]> {
  return request({
    url: '/aigov/studio/drafts/' + draftId + '/revisions',
    method: 'get'
  });
}

/** 某个修订的内容快照（Diff / 回滚确认用） */
export function getRevision(revisionId: string | number): AxiosPromise<AigStudioRevisionVO> {
  return request({
    url: '/aigov/studio/drafts/revision/' + revisionId,
    method: 'get'
  });
}

/** 预检（只读校验，不产生版本） */
export function validateDraft(draftId: string | number): AxiosPromise<AigStudioValidateVO> {
  return request({
    url: '/aigov/studio/drafts/' + draftId + '/validate',
    method: 'post'
  });
}

/** 回滚到某个历史修订（产生新修订，不改历史） */
export function rollbackDraft(
  draftId: string | number,
  data: AigStudioDraftRollbackForm
): AxiosPromise<AigStudioDraftDetailVO> {
  return request({
    url: '/aigov/studio/drafts/' + draftId + '/rollback',
    method: 'post',
    data
  });
}

/** 归档草稿（终态） */
export function archiveDraft(draftId: string | number): AxiosPromise<void> {
  return request({
    url: '/aigov/studio/drafts/' + draftId + '/archive',
    method: 'post'
  });
}

/** 提交：固化成一条 DRAFT Agent 版本（不推进发布状态） */
export function submitDraft(
  draftId: string | number,
  data: AigStudioDraftSubmitForm
): AxiosPromise<AigStudioDraftSubmitVO> {
  return request({
    url: '/aigov/studio/drafts/' + draftId + '/submit',
    method: 'post',
    data
  });
}
