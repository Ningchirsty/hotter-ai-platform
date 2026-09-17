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
