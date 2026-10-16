import type { PageResult } from '@/api/types';
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

/**
 * 问题队列（结论/后续动作/反馈原因三个过滤都可空）。
 *
/**
 * 问题队列（**全量**；页面请用 {@link listFeedbackQueuePage}）。
 *
 * <p>保留它只为小批量/兼容场景：队列会一直增长，页面全量拉取既慢又难用。</p>
 *
 * 按动作过滤是给"接着处理的人"用的：负责用例的人要能问出"哪些是要我加入用例的"，
 * 而不是把整条队列从头翻一遍。
 */
export function listFeedbackQueue(
  verdict?: string,
  actionCode?: string,
  reasonCode?: string
): AxiosPromise<AigFeedbackVO[]> {
  return request({
    url: '/aigov/feedback/queue',
    method: 'get',
    params: { verdict, actionCode, reasonCode }
  });
}

/**
 * 问题队列（**分页**；页面走这个）。
 *
 * <p>队列是"收件箱"、行数只增不减：全量拉取在队列长了以后既慢又难用。
 * 过滤口径与 {@link listFeedbackQueue} 完全一致（服务端共用同一份条件构造）。</p>
 */
export function listFeedbackQueuePage(query: {
  verdict?: string;
  actionCode?: string;
  reasonCode?: string;
  pageNum?: number;
  pageSize?: number;
}): AxiosPromise<PageResult<AigFeedbackVO>> {
  return request({
    url: '/aigov/feedback/queue/page',
    method: 'get',
    params: query
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
