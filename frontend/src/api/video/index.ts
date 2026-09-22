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
  VideoWorkersVO,
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
 *
 * <p><b>为什么单独放大超时。</b>全局超时是 50 秒，而经 Cloudflare 实测吞吐只有
 * 258 KB/s ~ 790 KB/s：一张 5MB 的手机原图就要 7~20 秒，15MB 的截图在慢链路下会
 * 超过 50 秒。用户看到的是「系统接口请求超时」，但文件其实完全合法——
 * 这是把网络慢误报成失败。给上传单独留 3 分钟。</p>
 *
 * <p><b>为什么要有 onProgress。</b>源站是 cloudflared tunnel，Cloudflare 免费版对
 * 源站响应有 100 秒上限（超时返回 524）。上传本身就要几十秒，期间界面若毫无动静，
 * 用户只会觉得「点了没反应」。把进度回传出来，等待才是可解释的。</p>
 */
export const uploadVideoAsset = (
  file: File,
  onProgress?: (percent: number) => void
): AxiosPromise<VideoUploadResult> => {
  const data = new FormData();
  data.append('file', file);
  return request({
    url: '/video/assets',
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
 * 重新执行一个失败/超时/被取消的任务（后端把终态退回 QUEUED 再认领入队）。
 *
 * <p>为什么需要它：后端进程重启会把遗留的 RUNNING 任务收敛为 FAILED 并提示
 * 「请重新执行该任务」，但 /execute 只接受 QUEUED/RUNNING——提示让用户做的事当时并没有入口。</p>
 */
export const retryVideoTask = (taskId: number | string): AxiosPromise<VideoTaskExecutionResult> => {
  return request({
    url: '/video/tasks/' + taskId + '/retry',
    method: 'post'
  });
};

/**
 * 查询 GPU 工作节点与执行队列的实时状态。
 *
 * <p>为什么前端要知道这个：生成一次要 2 到 12 分钟，双卡也只有两个并发位。
 * 用户点了"生成"之后如果什么都不显示，就分不清"在排队"还是"卡住了"。
 * 把"几张卡在跑、前面还排着几个"如实显示出来，等待才是可解释的。</p>
 */
export const getVideoWorkers = (): AxiosPromise<VideoWorkersVO> => {
  return request({
    url: '/video/workers',
    method: 'get'
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
 * 把带鉴权取回的响应转成 blob URL。
 *
 * <p><b>为什么必须判断类型。</b>若依用「HTTP 200 + 业务码」表达失败：token 失效时
 * 后端返回的是 {@code {"code":401,...}} 的 JSON，HTTP 状态仍是 200。
 * 而 {@code responseType:'blob'} 会把这段 JSON 原样当成文件返回，
 * 于是 {@code <video src="blob:...">} 拿到一个几十字节的 JSON，
 * 播放失败却<b>不报错</b>——用户看到的就是"预览有时候行、有时候不行"。</p>
 *
 * <p>这里显式拦掉非媒体类型与空内容，把静默失败变成明确的错误信息。</p>
 */
const toMediaBlobUrl = async (data: unknown, what: string): Promise<string> => {
  const blob = data as Blob | undefined;
  if (!blob || blob.size === 0) {
    throw new Error(`${what}内容为空`);
  }
  const type = (blob.type || '').toLowerCase();
  if (type.includes('application/json') || type.includes('text/')) {
    let message = `${what}读取失败`;
    try {
      const parsed = JSON.parse(await blob.text());
      if (parsed?.msg) {
        message = String(parsed.msg);
      }
    } catch {
      // 解析失败就沿用默认文案
    }
    throw new Error(message);
  }
  return URL.createObjectURL(blob);
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
  return toMediaBlobUrl(res.data, '成片');
};

/**
 * 读取图片/视频素材的缩略图，返回可交给 `<img>` 的 blob URL。
 *
 * <p>为什么不直接用原素材：素材库格子只有一两百像素，任务封面更是只要一帧。
 * 原图可能几 MB，成片更是整段 mp4（9 个成片合计约 13 MB），而经 Cloudflare 实测吞吐
 * 只有 258 KB/s ~ 790 KB/s —— 把带宽占满后，同一时刻发起的预览请求就会排队甚至超时。
 * 后端用 ffmpeg 生成并缓存缩略图（最长边 480px）：图片缩放，视频抽 0.5s 处一帧。</p>
 *
 * <p>取不到时抛错，由调用方决定是否回退到原素材——缩略图只是为了快，不该成为能不能看的开关。</p>
 */
export const fetchVideoAssetThumbnailBlobUrl = async (assetId: number | string): Promise<string> => {
  const res = await request({
    url: '/video/assets/' + assetId + '/thumbnail',
    method: 'get',
    responseType: 'blob'
  });
  return toMediaBlobUrl(res.data, '缩略图');
};
