import type { PageResult } from '@/api/types';
import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type {
  VideoAssetVO,
  VideoTaskCreateForm,
  VideoTaskDetailVO,
  VideoTaskExecutionResult,
  VideoTaskVO,
  VideoUploadResult,
  VideoWorkflowVO
} from './types';

/**
 * 查询可提交的工作流视图。
 *
 * 返回的 `status` 决定提交按钮是否可用：仓库中三个 H3 模板当前均为 DRAFT，
 * 实机验收通过并发布前，前端必须保持禁用并显示明确状态。
 */
export const listVideoWorkflows = (): AxiosPromise<VideoWorkflowVO[]> => {
  return request({
    url: '/video/capabilities',
    method: 'get'
  });
};

/**
 * 上传素材，返回后端素材 ID。
 *
 * 注意：提交任务时使用返回的 `assetId`，不能使用浏览器本地文件名。
 */
export const uploadVideoAsset = (file: File): AxiosPromise<VideoUploadResult> => {
  const data = new FormData();
  data.append('file', file);
  return request({
    url: '/video/assets',
    method: 'post',
    headers: { 'Content-Type': 'multipart/form-data' },
    data
  });
};

/**
 * 查询本人素材列表。
 */
export const listVideoAssets = (query?: { pageNum?: number; pageSize?: number }): AxiosPromise<PageResult<VideoAssetVO>> => {
  return request({
    url: '/video/assets',
    method: 'get',
    params: query
  });
};

/**
 * 删除本人素材。
 */
export const deleteVideoAsset = (assetId: number | string): AxiosPromise<void> => {
  return request({
    url: '/video/assets/' + assetId,
    method: 'delete'
  });
};

/**
 * 创建视频任务（入库排队，尚不执行）。
 */
export const createVideoTask = (data: VideoTaskCreateForm): AxiosPromise<{ taskId: number | string; taskNo: string; status: string }> => {
  return request({
    url: '/video/tasks',
    method: 'post',
    data
  });
};

/**
 * 执行任务并等待成片。
 *
 * 浏览器不直连 ComfyUI，必须经此后端接口。
 */
export const executeVideoTask = (taskId: number | string): AxiosPromise<VideoTaskExecutionResult> => {
  return request({
    url: '/video/tasks/' + taskId + '/execute',
    method: 'post'
  });
};

/**
 * 查询本人任务列表。
 */
export const listVideoTasks = (query?: {
  pageNum?: number;
  pageSize?: number;
  status?: string;
}): AxiosPromise<PageResult<VideoTaskVO>> => {
  return request({
    url: '/video/tasks',
    method: 'get',
    params: query
  });
};

/**
 * 查询任务详情（含事件序列，用于展示失败原因与断点恢复）。
 */
export const getVideoTask = (taskId: number | string): AxiosPromise<VideoTaskDetailVO> => {
  return request({
    url: '/video/tasks/' + taskId,
    method: 'get'
  });
};

/**
 * 取消排队中的任务。
 */
export const cancelVideoTask = (taskId: number | string): AxiosPromise<void> => {
  return request({
    url: '/video/tasks/' + taskId + '/cancel',
    method: 'post'
  });
};

/**
 * 读取素材/成片内容，返回可直接交给 `<img>` / `<video>` 的 blob URL。
 *
 * <p>为什么不能直接把接口地址写进 `src`：`<img>` / `<video>` 发出的请求
 * <b>不会</b>携带 Authorization 头，会被后端鉴权拒绝；而且后端要求按属主校验，
 * 也不适合用公开直链。因此改为带鉴权取回二进制，再转成 blob URL 交给标签使用。</p>
 *
 * <p>调用方必须负责在不再使用时 `URL.revokeObjectURL` 释放，否则会持续占用内存。</p>
 */
export const fetchVideoAssetBlobUrl = async (assetId: number | string): Promise<string> => {
  const res = await request({
    url: '/video/assets/' + assetId + '/content',
    method: 'get',
    responseType: 'blob'
  });
  // request 拦截器对 blob 响应原样透传，因此这里拿到的就是 Blob。
  return URL.createObjectURL(res.data as unknown as Blob);
};

/**
 * 读取图片素材的缩略图，返回可交给 `<img>` 的 blob URL。
 *
 * <p>为什么不直接用原图：素材库格子只有一两百像素，而原图可能有几 MB。
 * 经 Cloudflare 的链路实测吞吐 258 KB/s ~ 790 KB/s，一屏几张图就要好几秒。
 * 后端用 ffmpeg 生成并缓存缩略图（最长边 480px）。</p>
 *
 * <p>取不到时抛错，由调用方回退到原图——缩略图只是为了快，不该成为能不能看的开关。</p>
 */
export const fetchVideoAssetThumbnailBlobUrl = async (assetId: number | string): Promise<string> => {
  const res = await request({
    url: '/video/assets/' + assetId + '/thumbnail',
    method: 'get',
    responseType: 'blob'
  });
  return URL.createObjectURL(res.data as unknown as Blob);
};
