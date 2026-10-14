import type { PageResult } from '@/api/types';
import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type { AigTaskDetailVO, AigTaskFlowNodeVO, AigTaskMirrorQuery, AigTaskMirrorSourceVO, AigTaskMirrorVO, AigTaskQuery, AigTaskSweepVO, AigTaskVO } from './types';

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

// 人工选定候选资产（自动流程只筛除、不放行，选定必须人工）
// 口径：任务须处于「待人工复核」；被自动质检筛除的候选不接受选定；同一任务单选
export function selectAigTaskResult(taskId: string | number, resultId: string | number, remark?: string) {
  return request({
    url: `/aigov/task/${taskId}/result/${resultId}/select`,
    method: 'post',
    params: { remark }
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

// 存量任务的只读镜像（设计 §9：新任务走 aig_task，存量只读镜像）
// 来源清单必须从后端取：后端要求列表必须指定来源，清单若只能靠读代码知道，
// 那个「必填」就变成了使用障碍。
export function listAigTaskMirrorSources(): AxiosPromise<AigTaskMirrorSourceVO[]> {
  return request({
    url: '/aigov/task/mirror/sources',
    method: 'get'
  });
}

// 分页查询某个来源的镜像行（来源必填；后端不提供跨来源合并分页）
export function listAigTaskMirror(query: AigTaskMirrorQuery): AxiosPromise<PageResult<AigTaskMirrorVO>> {
  return request({
    url: '/aigov/task/mirror/list',
    method: 'get',
    params: query
  });
}

// 任务的流程节点（谁停住了、停在第几个、是不是人工确认）：
// 节点的身份与状态都在步骤账本里，前端只读不改。
export function listAigTaskFlowNodes(taskId: string | number): AxiosPromise<AigTaskFlowNodeVO[]> {
  return request({
    url: `/aigov/task/${taskId}/flow-nodes`,
    method: 'get'
  });
}

// 记录一次人工确认（挂起后的恢复入口）。
// 批准引用必填：没有凭据的「确认」事后无法追溯是谁批的。
// 刻意不自动重新入队——确认只回答「这个节点批准了」，是否继续跑由人再点一次重新入队决定，
// 否则一次点击就会夹带一次可能计费的执行。
export function confirmAigTaskFlowNode(
  taskId: string | number,
  nodeIndex: number,
  approvalRef: string
): AxiosPromise<AigTaskFlowNodeVO> {
  return request({
    url: `/aigov/task/${taskId}/flow/confirm`,
    method: 'post',
    params: { nodeIndex, approvalRef }
  });
}
