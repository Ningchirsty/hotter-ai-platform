import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type {
  AigFeedbackHandleForm,
  AigFeedbackSubmitForm,
  AigFeedbackVO,
  AigOperationsMetricsVO
} from './types';

/**
 * 结果反馈与运营指标接口（设计 §6.9）。
 *
 * 提交反馈是员工动作（后端 `@SaCheckLogin`）；问题队列与处理需要权限点。
 */

/** 员工提交结果反馈（进入问题队列） */
export function submitFeedback(data: AigFeedbackSubmitForm): AxiosPromise<number | string> {
  return request({
    url: '/aigov/feedback',
    method: 'post',
    data
  });
}

/** 问题队列（verdict 可空=默认 PENDING） */
export function listFeedbackQueue(verdict?: string): AxiosPromise<AigFeedbackVO[]> {
  return request({
    url: '/aigov/feedback/queue',
    method: 'get',
    params: { verdict }
  });
}

/** 处理一条反馈 */
export function handleFeedback(
  feedbackId: string | number,
  data: AigFeedbackHandleForm
): AxiosPromise<AigFeedbackVO> {
  return request({
    url: '/aigov/feedback/' + feedbackId + '/handle',
    method: 'post',
    data
  });
}

/** 运营指标 */
export function getOperationsMetrics(): AxiosPromise<AigOperationsMetricsVO> {
  return request({
    url: '/aigov/operations/metrics',
    method: 'get'
  });
}

/** 故障分类计数（分类 → 次数） */
export function listFaultClasses(): AxiosPromise<Record<string, number>> {
  return request({
    url: '/aigov/operations/fault-classes',
    method: 'get'
  });
}
