import type { PageResult } from '@/api/types';
import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type {
  AigStudioActionDraftForm,
  AigStudioActionDraftVO,
  AigStudioArtifactDraftQuery,
  AigStudioArtifactGenerateForm,
  AigStudioArtifactGenerateVO,
  AigStudioChildAgentDraftForm,
  AigStudioChildAgentDraftVO,
  AigStudioSkillDraftForm,
  AigStudioSkillDraftVO
} from './types';

/**
 * 草案生成器接口（附件 07 增量 7）。
 *
 * <p>三类草案**各自落表**，因此各自有自己的新增与清单端点；而"预览/生成"是共用的
 * ——生成物与生成规则对三类是同一条通道（都产一份可提交的训练台草稿内容）。</p>
 *
 * <p><b>权限口径</b>：新增与清单要 `aig:studio:artifact-draft`；
 * <b>生成</b>另外要 `aig:studio:draft:edit`——因为生成是写草稿（产生一条新修订）。</p>
 */

/** 新增技能草案 */
export function addSkillDraft(data: AigStudioSkillDraftForm): AxiosPromise<string | number> {
  return request({ url: '/aigov/studio/artifact-drafts/skill', method: 'post', data });
}

/** 新增子 Agent 草案 */
export function addChildAgentDraft(data: AigStudioChildAgentDraftForm): AxiosPromise<string | number> {
  return request({ url: '/aigov/studio/artifact-drafts/child-agent', method: 'post', data });
}

/** 新增快捷指令草案 */
export function addActionDraft(data: AigStudioActionDraftForm): AxiosPromise<string | number> {
  return request({ url: '/aigov/studio/artifact-drafts/action', method: 'post', data });
}

/** 技能草案清单 */
export function listSkillDrafts(
  query: AigStudioArtifactDraftQuery
): AxiosPromise<PageResult<AigStudioSkillDraftVO>> {
  return request({ url: '/aigov/studio/artifact-drafts/skill/list', method: 'get', params: query });
}

/** 子 Agent 草案清单 */
export function listChildAgentDrafts(
  query: AigStudioArtifactDraftQuery
): AxiosPromise<PageResult<AigStudioChildAgentDraftVO>> {
  return request({
    url: '/aigov/studio/artifact-drafts/child-agent/list',
    method: 'get',
    params: query
  });
}

/** 快捷指令草案清单 */
export function listActionDrafts(
  query: AigStudioArtifactDraftQuery
): AxiosPromise<PageResult<AigStudioActionDraftVO>> {
  return request({ url: '/aigov/studio/artifact-drafts/action/list', method: 'get', params: query });
}

/**
 * 预览生成物（只读，不写任何表）。
 *
 * @param path 草案类型路径片段（skill / child-agent / action）
 * @param draftId 草案ID
 */
export function previewArtifactDraft(
  path: string,
  draftId: string | number
): AxiosPromise<AigStudioArtifactGenerateVO> {
  return request({ url: `/aigov/studio/artifact-drafts/${path}/${draftId}/preview`, method: 'get' });
}

/**
 * 把生成物落到目标训练草稿（写草稿：产生一条 source=DRAFT_GENERATED 的新修订）。
 *
 * @param path 草案类型路径片段
 * @param draftId 草案ID
 * @param data 目标草稿 + 期望修订号（CAS）
 */
export function generateArtifactDraft(
  path: string,
  draftId: string | number,
  data: AigStudioArtifactGenerateForm
): AxiosPromise<AigStudioArtifactGenerateVO> {
  return request({
    url: `/aigov/studio/artifact-drafts/${path}/${draftId}/generate`,
    method: 'post',
    data
  });
}
