import type { PageResult } from '@/api/types';
import type { CpTaskFileVO } from '@/api/content/task/types';
import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type {
  CreativeHeroForm,
  CreativeProjectForm,
  CreativeProjectQuery,
  CreativeProjectVO,
  CreativeWorkflowVO,
  DpGenerationVO,
  DpStageEventVO
} from './types';

// ------------------------------------------------------------------
// 视觉项目（= ECOM_DETAIL 的内容协同任务）
// ------------------------------------------------------------------

/** 视觉项目分页（后端强制 deliverableType=ECOM_DETAIL） */
export function listCreativeProject(query: CreativeProjectQuery): AxiosPromise<PageResult<CreativeProjectVO>> {
  return request({
    url: '/creative/projects/list',
    method: 'get',
    params: query
  });
}

/** 视觉项目详情 */
export function getCreativeProject(taskId: string | number): AxiosPromise<CreativeProjectVO> {
  return request({
    url: '/creative/projects/' + taskId,
    method: 'get'
  });
}

/** 新建视觉项目 */
export function addCreativeProject(data: CreativeProjectForm): AxiosPromise<string | number> {
  return request({
    url: '/creative/projects',
    method: 'post',
    data: data
  });
}

/** 上传项目参考图（表单字段名固定 file） */
export function uploadCreativeReference(taskId: string | number, file: File): AxiosPromise<string | number> {
  const formData = new FormData();
  formData.append('file', file);
  return request({
    url: '/creative/projects/' + taskId + '/reference',
    method: 'post',
    data: formData
  });
}

/** 项目附件列表（含参考图） */
export function listCreativeFiles(taskId: string | number): AxiosPromise<CpTaskFileVO[]> {
  return request({
    url: '/creative/projects/' + taskId + '/files',
    method: 'get'
  });
}

/** 项目阶段事件时间线 */
export function listCreativeTimeline(taskId: string | number): AxiosPromise<DpStageEventVO[]> {
  return request({
    url: '/creative/projects/' + taskId + '/timeline',
    method: 'get'
  });
}

// ------------------------------------------------------------------
// 出图生产
// ------------------------------------------------------------------

/** 提交一次 HERO 出图 */
export function submitHero(taskId: string | number, data: CreativeHeroForm): AxiosPromise<DpGenerationVO> {
  return request({
    url: '/creative/projects/' + taskId + '/generations',
    method: 'post',
    data: data
  });
}

/** 项目出图候选列表（返回前会刷新内核状态） */
export function listGenerations(taskId: string | number): AxiosPromise<DpGenerationVO[]> {
  return request({
    url: '/creative/projects/' + taskId + '/generations',
    method: 'get'
  });
}

/** 重试失败候选（新建一次候选） */
export function retryGeneration(generationId: string | number): AxiosPromise<DpGenerationVO> {
  return request({
    url: '/creative/generations/' + generationId + '/retry',
    method: 'post'
  });
}

/** 可用出图工作流 */
export function listCreativeWorkflows(): AxiosPromise<CreativeWorkflowVO[]> {
  return request({
    url: '/creative/workflows',
    method: 'get'
  });
}

/** 跨项目候选分页（AI 生产中心） */
export function listProductions(query: {
  pageNum?: number;
  pageSize?: number;
  status?: string;
}): AxiosPromise<PageResult<DpGenerationVO>> {
  return request({
    url: '/creative/productions',
    method: 'get',
    params: query
  });
}

/**
 * 把返回体转成可直接放进 `<img>` 的 blob URL。
 *
 * 为什么要判空与判 JSON：若依在未鉴权时返回 HTTP 200 + `{"code":401,...}`，
 * 直接 `URL.createObjectURL` 会得到一个「坏图」而不是可读的错误。
 */
const toMediaBlobUrl = async (data: unknown, what: string): Promise<string> => {
  const blob = data as Blob;
  if (!blob || blob.size === 0) {
    throw new Error(what + '内容为空');
  }
  const type = blob.type || '';
  if (type.includes('application/json') || type.startsWith('text/')) {
    const text = await blob.text();
    let message = text;
    try {
      const parsed = JSON.parse(text) as { msg?: string };
      message = parsed.msg || text;
    } catch {
      /* 保持原文 */
    }
    throw new Error(message || '读取' + what + '失败');
  }
  return URL.createObjectURL(blob);
};

/** 取候选缩略图 blob URL（失败时调用方退回原图；调用方负责 revokeObjectURL） */
export const fetchGenerationThumbnailBlobUrl = async (generationId: string | number): Promise<string> => {
  const res = await request({
    url: '/creative/generations/' + generationId + '/thumbnail',
    method: 'get',
    responseType: 'blob'
  });
  return toMediaBlobUrl(res.data, '缩略图');
};

/** 取候选原图 blob URL（调用方负责 revokeObjectURL） */
export const fetchGenerationPreviewBlobUrl = async (generationId: string | number): Promise<string> => {
  const res = await request({
    url: '/creative/generations/' + generationId + '/preview',
    method: 'get',
    responseType: 'blob'
  });
  return toMediaBlobUrl(res.data, '产出图');
};

/** 取项目附件（参考图）原图 blob URL（调用方负责 revokeObjectURL） */
export const fetchCreativeFileBlobUrl = async (
  taskId: string | number,
  fileId: string | number
): Promise<string> => {
  const res = await request({
    url: '/creative/projects/' + taskId + '/files/' + fileId + '/content',
    method: 'get',
    responseType: 'blob'
  });
  return toMediaBlobUrl(res.data, '参考图');
};
