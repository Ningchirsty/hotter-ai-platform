import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type { ParseConfirmForm, ParseFieldVO, ParseTaskVO } from './types';

// 查询解析任务（TlParseTaskVo.fields 内已包含字段清单）
export function getParseTask(taskId: string | number): AxiosPromise<ParseTaskVO> {
  return request({
    url: '/talent/parse/' + taskId,
    method: 'get'
  });
}

// 查询解析任务的字段复核清单（与 getParseTask 的 fields 同源，单独拉取便于刷新）
export function listParseFields(taskId: string | number): AxiosPromise<ParseFieldVO[]> {
  return request({
    url: '/talent/parse/fields/' + taskId,
    method: 'get'
  });
}

// 为附件创建解析任务；解析服务未获批启用时后端会把任务落为 DISABLED，不处理真实简历
export function createParseTask(attachmentId: string | number) {
  return request({
    url: '/talent/parse',
    method: 'post',
    data: { attachmentId }
  });
}

// 提交字段人工确认结果
export function confirmParseFields(data: ParseConfirmForm) {
  return request({
    url: '/talent/parse/confirm',
    method: 'post',
    data: data
  });
}

// 重试解析任务
export function retryParseTask(taskId: string | number) {
  return request({
    url: '/talent/parse/retry/' + taskId,
    method: 'post'
  });
}
