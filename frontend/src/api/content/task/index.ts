import type { PageResult } from '@/api/types';
import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type {
  ContentFactSyncVO,
  ContentGateResult,
  ContentTaskDetailVO,
  CpTaskFileVO,
  CpTaskForm,
  CpTaskQuery,
  CpTaskVO,
  TaskFileUploadForm
} from './types';

// 查询任务分页列表
export function listTask(query: CpTaskQuery): AxiosPromise<PageResult<CpTaskVO>> {
  return request({
    url: '/content/task/list',
    method: 'get',
    params: query
  });
}

// 查询任务详情（含附件/事实/卡片/作业/闸门结论/开工包）
export function getTask(taskId: string | number): AxiosPromise<ContentTaskDetailVO> {
  return request({
    url: '/content/task/' + taskId,
    method: 'get'
  });
}

// 新建任务，返回任务ID
export function addTask(data: CpTaskForm): AxiosPromise<string | number> {
  return request({
    url: '/content/task',
    method: 'post',
    data: data
  });
}

// 修改任务
export function updateTask(data: CpTaskForm) {
  return request({
    url: '/content/task',
    method: 'put',
    data: data
  });
}

// 删除任务
export function delTask(taskId: string | number | Array<string | number>) {
  return request({
    url: '/content/task/' + taskId,
    method: 'delete'
  });
}

// 上传资料附件（multipart/form-data，表单字段名固定为 file，dataLevel 走 query）
export function uploadTaskFile(data: TaskFileUploadForm): AxiosPromise<string | number> {
  const formData = new FormData();
  formData.append('file', data.file);
  return request({
    url: '/content/task/' + data.taskId + '/file',
    method: 'post',
    params: data.dataLevel ? { dataLevel: data.dataLevel } : undefined,
    data: formData
  });
}

// 查询任务附件列表
export function listTaskFiles(taskId: string | number): AxiosPromise<CpTaskFileVO[]> {
  return request({
    url: '/content/task/' + taskId + '/files',
    method: 'get'
  });
}

/** 图片扩展名 → MIME：接口没给出 Content-Type 时按附件名兜底，避免浏览器把它当二进制拒绝显示 */
const IMAGE_MIME_BY_EXT: Record<string, string> = {
  png: 'image/png',
  jpg: 'image/jpeg',
  jpeg: 'image/jpeg',
  gif: 'image/gif',
  bmp: 'image/bmp',
  webp: 'image/webp'
};

/** 附件名 → 图片 MIME（取不到扩展名或不是已知图片类型时返回空串） */
const mimeByFileName = (fileName?: string): string => {
  const ext = (fileName || '').split('.').pop()?.toLowerCase() || '';
  return IMAGE_MIME_BY_EXT[ext] || '';
};

/**
 * 从二进制响应里读 Content-Type。
 *
 * 为什么能读到：响应拦截器对 `responseType: 'blob'` 是「整个 axios 响应」透传的
 * （见 `utils/request.ts` 的二进制分支），所以 `data` 是 Blob、`headers` 也在。
 * 为什么不用 `any`：本项目的 `AxiosPromise` 只声明了 `{ code, msg, data }`，
 * 这里按实际会读的字段做最小断言，不把整个响应放开成 any。
 */
const contentTypeOf = (raw: unknown): string => {
  const headers = (raw as { headers?: unknown } | null | undefined)?.headers;
  if (!headers || typeof headers !== 'object') {
    return '';
  }
  const value = (headers as Record<string, unknown>)['content-type'];
  return typeof value === 'string' ? value.split(';')[0].trim() : '';
};

/**
 * 把二进制响应转成可直接放进 `<img>` 的 blob URL。
 *
 * 判空与判 JSON 的原因：若依在未鉴权/出错时会返回 HTTP 200 + `{"code":...,"msg":...}`，
 * 直接 `URL.createObjectURL` 只会得到一张「坏图」，看不出真实原因。
 */
const toImageBlobUrl = async (raw: unknown, what: string, fileName?: string): Promise<string> => {
  const data = (raw as { data?: unknown } | null | undefined)?.data;
  if (!(data instanceof Blob) || data.size === 0) {
    throw new Error(what + '内容为空');
  }
  if (data.type.includes('application/json') || data.type.startsWith('text/')) {
    const text = await data.text();
    let message = text;
    try {
      const parsed = JSON.parse(text) as { msg?: string };
      message = parsed.msg || text;
    } catch {
      /* 不是 JSON 就保持原文 */
    }
    throw new Error(message || '读取' + what + '失败');
  }
  const type = contentTypeOf(raw) || data.type || mimeByFileName(fileName);
  // MIME 不一致（或 Blob 没带类型）时重建一个带正确类型的 Blob，否则浏览器可能不按图片渲染
  const blob = type && type !== data.type ? new Blob([data], { type }) : data;
  return URL.createObjectURL(blob);
};

/**
 * 读取任务附件的图片字节，返回可交给 `<img>` 的 blob URL。
 *
 * 为什么不直接用对象存储直链：内容资料存在私有前缀，直链会绕过内容模块的授权，
 * 必须走带鉴权头的接口代理。与 `fetchCheckImageBlobUrl` 同构：只做「字节 → 对象 URL」，
 * 不做任何自动回收——回收时机由页面决定（调用方必须 `URL.revokeObjectURL`），
 * 避免图片在渲染前就被释放。
 *
 * @param taskId   任务ID
 * @param fileId   附件ID
 * @param fileName 附件名（可选）：接口未给出可用 Content-Type 时按扩展名兜底 MIME
 */
export const fetchTaskFileBlobUrl = async (
  taskId: string | number,
  fileId: string | number,
  fileName?: string
): Promise<string> => {
  const res = await request({
    url: '/content/task/' + taskId + '/files/' + fileId + '/content',
    method: 'get',
    responseType: 'blob'
  });
  return toImageBlobUrl(res, '附件预览', fileName);
};

// 触发解析（异步），返回作业ID
export function triggerParse(taskId: string | number): AxiosPromise<string | number> {
  return request({
    url: '/content/task/' + taskId + '/parse',
    method: 'post'
  });
}

// 触发预检（异步），返回作业ID
export function triggerPrecheck(taskId: string | number): AxiosPromise<string | number> {
  return request({
    url: '/content/task/' + taskId + '/precheck',
    method: 'post'
  });
}

// 重算闸门并刷新任务状态，返回判定结论
export function recheckGate(taskId: string | number): AxiosPromise<ContentGateResult> {
  return request({
    url: '/content/task/' + taskId + '/recheck',
    method: 'post'
  });
}

// 把任务所选产品在「产品与SKU」里的主数据（名称/SKU）同步为产品事实
export function syncProductFacts(taskId: string | number): AxiosPromise<ContentFactSyncVO> {
  return request({
    url: '/content/task/' + taskId + '/productFacts/sync',
    method: 'post'
  });
}
