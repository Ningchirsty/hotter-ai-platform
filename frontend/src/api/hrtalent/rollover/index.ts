import type { PageResult } from '@/api/types';
import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type {
  HrRolloverBatchDetailVO,
  HrRolloverBatchVO,
  HrRolloverExecuteForm,
  HrRolloverExecuteVO,
  HrRolloverPreviewForm,
  HrRolloverPreviewVO,
  HrRolloverQuery
} from './types';

/**
 * 月度结转中心接口封装
 * 路径与 docs/hr-talent/SPEC-P2-招聘主线.md §3.3 逐字一致。
 * 结转本身幂等（唯一索引 source_item_id + target_month），重试安全。
 */

/** 结转预览：列出目标月份待结转任务与人数 */
export function previewRollover(data: HrRolloverPreviewForm): AxiosPromise<HrRolloverPreviewVO> {
  return request({
    url: '/recruit/plan-rollovers/preview',
    method: 'post',
    data: data
  });
}

/** 执行结转（幂等；重复调用不会重复生成任务） */
export function executeRollover(data: HrRolloverExecuteForm): AxiosPromise<HrRolloverExecuteVO> {
  return request({
    url: '/recruit/plan-rollovers/execute',
    method: 'post',
    data: data
  });
}

/** 批次分页查询 */
export function listRollover(query: HrRolloverQuery): AxiosPromise<PageResult<HrRolloverBatchVO>> {
  return request({
    url: '/recruit/plan-rollovers',
    method: 'get',
    params: query
  });
}

/** 查询结转批次明细（按批次号） */
export function getRolloverBatch(batchNo: string): AxiosPromise<HrRolloverBatchDetailVO> {
  return request({
    url: '/recruit/plan-rollovers/' + batchNo,
    method: 'get'
  });
}

/** 重试批次中失败/跳过的结转条目（不整批回滚已成功条目） */
export function retryRollover(batchNo: string): AxiosPromise<HrRolloverExecuteVO> {
  return request({
    url: `/recruit/plan-rollovers/${batchNo}/retry`,
    method: 'post'
  });
}
