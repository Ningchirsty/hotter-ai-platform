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
