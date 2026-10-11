import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type {
  AigSandboxArtifactDownloadVO,
  AigSandboxArtifactManifestVO,
  AigSandboxRunEvidence,
  AigSandboxRunRecordForm
} from './types';

/**
 * 取某个版本当前的沙箱运行证据（发布门槛 SANDBOX_RUN）。
 *
 * 后端对"没有证据"也是 200 + `satisfied:false` + `reason`，所以页面直接渲染原因即可，
 * 不需要把 404/500 当成"没跑过"来猜。
 */
export function sandboxRunEvidence(targetType: string, targetVersionId: string | number): AxiosPromise<AigSandboxRunEvidence> {
  return request({
    url: '/aigov/sandbox/run/evidence',
    method: 'get',
    params: { targetType, targetVersionId }
  });
}

/**
 * 登记一次沙箱运行（提交宿主侧执行器输出的 result.json **原文**）。
 *
 * 只收原文、不收"你自己填的退出码/网络模式"：分别收字段就等于允许提交一份库里没有
 * 原文支撑的结论。服务端会校验必填字段、jobId 一致性，并对原文实算 SHA-256 留存。
 */
export function recordSandboxRun(data: AigSandboxRunRecordForm) {
  return request({
    url: '/aigov/sandbox/run/record',
    method: 'post',
    data: data
  });
}

/**
 * 沙箱产物清单（账本声明 + 是否已入库）。
 *
 * 列表由后端的 `result.json` 清单驱动，所以**未入库的产物也会出现**（带 `uploaded=false`）——
 * 这正是"都收齐了吗"要回答的。
 */
export function listSandboxArtifacts(
  runId: string | number
): AxiosPromise<AigSandboxArtifactManifestVO[]> {
  return request({
    url: `/aigov/sandbox/run/${runId}/artifacts`,
    method: 'get'
  });
}

/**
 * 登记一只产物的字节（逐文件上传，ADR-016 已裁定）。
 *
 * 服务端按 result.json 声明的字节数与 SHA-256 逐个校验，不符即拒绝并说明差在哪；
 * 因此这里**不做任何本地校验**——本地放过、服务端拒绝会得到两种口径。
 */
export function uploadSandboxArtifact(
  runId: string | number,
  path: string,
  file: File,
  contentType?: string
): AxiosPromise<string | number> {
  const data = new FormData();
  data.append('path', path);
  data.append('file', file);
  if (contentType) {
    data.append('contentType', contentType);
  }
  return request({
    url: `/aigov/sandbox/run/${runId}/artifact`,
    method: 'post',
    headers: { 'Content-Type': 'multipart/form-data' },
    data
  });
}

/**
 * 沙箱产物的下载留痕（最新在前；ADR-016 安全硬要求 ⑤）。
 *
 * 用途是回答安全事件的第一个问题：这段不可信内容被谁取走过、什么时候、从哪个 IP。
 */
export function listArtifactDownloads(
  runId: string | number,
  limit?: number
): AxiosPromise<AigSandboxArtifactDownloadVO[]> {
  return request({
    url: `/aigov/sandbox/run/${runId}/artifact-downloads`,
    method: 'get',
    params: limit ? { limit } : {}
  });
}

/**
 * 下载一只已入库的产物（返回 Blob）。
 *
 * **响应头硬要求由后端保证**：`application/octet-stream` + `attachment` + `nosniff`。
 * 调用方拿到 Blob 后交给 `saveBlob` 存盘，**不要**把它塞进 `<img>`/`<iframe>` 渲染——
 * 产物是不可信内容，这条通道刻意不做预览。
 *
 * 失败时后端返回的是 JSON（而不是字节），而 request 拦截器对 `responseType: 'blob'`
 * 是"整个响应透传"的，所以这里要把 JSON 内容读出来当错误消息，否则只会得到一张"坏文件"。
 */
export async function downloadSandboxArtifact(
  runId: string | number,
  path: string
): Promise<Blob> {
  const res = await request({
    url: `/aigov/sandbox/run/${runId}/artifact`,
    method: 'get',
    params: { path },
    responseType: 'blob'
  });
  const blob = res.data as Blob;
  const type = blob?.type || '';
  if (type.includes('application/json') || type.includes('text/')) {
    let message = '产物下载失败';
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
  return blob;
}
