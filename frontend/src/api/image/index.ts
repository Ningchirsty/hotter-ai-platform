import type { PageResult } from '@/api/types';
import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type {
  ImageAssetVO,
  ImageTaskCreateForm,
  ImageTaskDetailVO,
  ImageTaskExecutionResult,
  ImageTaskVO,
  ImageUploadResult,
  ImageWorkflowVO
} from './types';

/**
 * 查询可提交的工作流视图。
 *
 * 返回的 `status` / `submittable` 决定提交按钮是否可用：图像模块四个模板当前均为 DRAFT
 * （真机已实测出图，但任务 API 与生产环境尚未联调），因此页面必须保持禁用并显示真实原因，
 * 不允许把「模板已导入」伪装成「服务已联通」。
 */
export const listImageWorkflows = (): AxiosPromise<ImageWorkflowVO[]> => {
  return request({
    url: '/image/capabilities',
    method: 'get'
  });
};

/**
 * 上传素材，返回后端素材 ID。提交任务时只传这个 ID，不传浏览器本地文件名。
 *
 * 上传单独放大超时并回传进度：源站经 Cloudflare 时吞吐可能很低，
 * 一张几 MB 的图就可能超过全局 50 秒超时，用户会把「网络慢」误读成「失败」。
 */
export const uploadImageAsset = (
  file: File,
  onProgress?: (percent: number) => void
): AxiosPromise<ImageUploadResult> => {
  const data = new FormData();
  data.append('file', file);
  return request({
    url: '/image/assets',
    method: 'post',
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 180000,
    onUploadProgress: (event: { loaded: number; total?: number }) => {
      if (!onProgress || !event.total) return;
      onProgress(Math.min(100, Math.round((event.loaded / event.total) * 100)));
    },
    data
  });
};

/** 查询本人素材列表。 */
export const listImageAssets = (query?: { pageNum?: number; pageSize?: number }): AxiosPromise<PageResult<ImageAssetVO>> => {
  return request({
    url: '/image/assets',
    method: 'get',
    params: query
  });
};

/** 删除本人素材（软删）。 */
export const deleteImageAsset = (assetId: number | string): AxiosPromise<void> => {
  return request({
    url: '/image/assets/' + assetId,
    method: 'delete'
  });
};

/** 创建图像任务（入库排队，尚不执行）。 */
export const createImageTask = (
  data: ImageTaskCreateForm
): AxiosPromise<{ taskId: number | string; taskNo: string; status: string; idempotent?: boolean }> => {
  return request({
    url: '/image/tasks',
    method: 'post',
    data
  });
};

/** 触发执行（后台执行，立即返回，之后靠轮询拿结果）。 */
export const executeImageTask = (taskId: number | string): AxiosPromise<ImageTaskExecutionResult> => {
  return request({
    url: '/image/tasks/' + taskId + '/execute',
    method: 'post'
  });
};

/** 查询本人任务列表。 */
export const listImageTasks = (query?: {
  pageNum?: number;
  pageSize?: number;
  status?: string;
}): AxiosPromise<PageResult<ImageTaskVO>> => {
  return request({
    url: '/image/tasks',
    method: 'get',
    params: query
  });
};

/** 查询任务详情（含事件时间线）。 */
export const getImageTask = (taskId: number | string): AxiosPromise<ImageTaskDetailVO> => {
  return request({
    url: '/image/tasks/' + taskId,
    method: 'get'
  });
};

/** 取消排队中的任务。 */
export const cancelImageTask = (taskId: number | string): AxiosPromise<void> => {
  return request({
    url: '/image/tasks/' + taskId + '/cancel',
    method: 'post'
  });
};

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

/** 取素材原图 blob URL（调用方负责 revokeObjectURL）。 */
export const fetchImageAssetBlobUrl = async (assetId: number | string): Promise<string> => {
  const res = await request({
    url: '/image/assets/' + assetId + '/content',
    method: 'get',
    responseType: 'blob'
  });
  return toMediaBlobUrl(res.data, '素材');
};

/** 取素材缩略图 blob URL（失败时调用方退回原图）。 */
export const fetchImageAssetThumbnailBlobUrl = async (assetId: number | string): Promise<string> => {
  const res = await request({
    url: '/image/assets/' + assetId + '/thumbnail',
    method: 'get',
    responseType: 'blob'
  });
  return toMediaBlobUrl(res.data, '缩略图');
};
