import type { PageResult } from '@/api/types';
import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type { AigTaskDetailVO, AigTaskQuery, AigTaskSweepVO, AigTaskVO } from './types';

// 任务列表（只读视图，不含快照原文与事件流）
export function listAigTask(query: AigTaskQuery): AxiosPromise<PageResult<AigTaskVO>> {
  return request({
    url: '/aigov/task/list',
    method: 'get',
    params: query
  });
}

// 任务详情：任务 + 输入快照 + 事件流 + 候选结果，一次给全
export function getAigTask(taskId: string | number): AxiosPromise<AigTaskDetailVO> {
  return request({
    url: '/aigov/task/' + taskId,
    method: 'get'
  });
}

// 人工请求取消：只迁移到「已请求取消」，等 Provider 确认后才落「已取消」。
// 异步作业一旦提交，取消不是本地能立刻完成的——直接置已取消会出现
// 「账上已取消、对方仍在跑」，事后无从对账。
export function cancelAigTask(taskId: string | number, expectedVersion: number, reason?: string) {
  return request({
    url: '/aigov/task/' + taskId + '/cancel',
    method: 'post',
    params: { expectedVersion, reason }
  });
}

// 人工把「待人工处理」的任务重新入队（修好输入/配置后重跑）
export function requeueAigTask(taskId: string | number, expectedVersion: number, note?: string) {
  return request({
    url: '/aigov/task/' + taskId + '/requeue',
    method: 'post',
    params: { expectedVersion, note }
  });
}

// 执行一次任务（任务层 → 统一调用入口：路由 / 有序 fallback / 退避重试 / 逐次审计都在里面）
// 平时前端不该调这个：正常路径是业务域建任务后直接调用执行器（同进程）。
// 开放出来是为了联调期手动触发与运维重跑，两者都要求 aig:task:operate。
export function executeAigTask(taskId: string | number, prompt?: string, maxCost?: number) {
  return request({
    url: '/aigov/task/' + taskId + '/execute',
    method: 'post',
    data: { prompt, maxCost }
  });
}

// 手动触发一次调度扫描（供 SnailJob / 运维 cron 使用；
// aigov.task.scheduler.enabled=false 时这是唯一的触发路径）
export function sweepAigTask(): AxiosPromise<AigTaskSweepVO> {
  return request({
    url: '/aigov/task/scheduler/sweep',
    method: 'post'
  });
}
