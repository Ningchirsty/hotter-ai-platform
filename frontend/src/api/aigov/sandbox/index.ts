import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type { AigSandboxRunEvidence, AigSandboxRunRecordForm } from './types';

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
