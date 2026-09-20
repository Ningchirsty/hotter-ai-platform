import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type { CpFactManualForm, CpFactSnapshotVO } from './types';

// 按任务列出事实行（含待确认候选与已确认事实）
export function listFact(taskId: string | number): AxiosPromise<CpFactSnapshotVO[]> {
  return request({
    url: '/content/fact/list',
    method: 'get',
    params: { taskId: taskId }
  });
}

// 确认单条候选值
export function confirmFact(snapshotId: string | number) {
  return request({
    url: '/content/fact/' + snapshotId + '/confirm',
    method: 'post'
  });
}

// 否决单条候选值
export function rejectFact(snapshotId: string | number) {
  return request({
    url: '/content/fact/' + snapshotId + '/reject',
    method: 'post'
  });
}

// 一键确认无争议项（同字段只有一个候选的行），返回本次确认条数
export function confirmUnambiguousFacts(taskId: string | number): AxiosPromise<number> {
  return request({
    url: '/content/fact/confirmUnambiguous',
    method: 'post',
    params: { taskId: taskId }
  });
}

// 人工录入事实（缺料时由责任人手工补齐）
export function addManualFact(data: CpFactManualForm): AxiosPromise<string | number> {
  return request({
    url: '/content/fact/manual',
    method: 'post',
    data: data
  });
}
