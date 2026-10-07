import type { PageResult } from '@/api/types';
import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type {
  AigAgentQuery,
  AigAgentVersionQuery,
  AigAgentVersionVO,
  AigAgentVO,
  AigReleaseAdvanceForm
} from './types';

/** 分页查询 Agent 清单 */
export function listAgent(query: AigAgentQuery): AxiosPromise<PageResult<AigAgentVO>> {
  return request({
    url: '/aigov/agent/list',
    method: 'get',
    params: query
  });
}

/** 查询 Agent 详情 */
export function getAgent(agentId: string | number): AxiosPromise<AigAgentVO> {
  return request({
    url: '/aigov/agent/' + agentId,
    method: 'get'
  });
}

/** 分页查询 Agent 版本清单 */
export function listAgentVersion(query: AigAgentVersionQuery): AxiosPromise<PageResult<AigAgentVersionVO>> {
  return request({
    url: '/aigov/agent/version/list',
    method: 'get',
    params: query
  });
}

/** 查询 Agent 版本详情 */
export function getAgentVersion(agentVersionId: string | number): AxiosPromise<AigAgentVersionVO> {
  return request({
    url: '/aigov/agent/version/' + agentVersionId,
    method: 'get'
  });
}

/**
 * 推进发布状态（唯一写入口）。
 *
 * 服务层只接受「门槛驱动」的合法推进：跳步、缺门槛、声明已通过而库里没有证据都会被拒。
 */
export function advanceRelease(data: AigReleaseAdvanceForm) {
  return request({
    url: '/aigov/agent/release/advance',
    method: 'post',
    data: data
  });
}

/** 当前状态前进所缺的门槛（页面显示「还差：沙箱运行、人工批准」） */
export function missingGates(targetType: string, targetVersionId: string | number, passedGates?: string[]) {
  return request({
    url: '/aigov/agent/release/missing-gates',
    method: 'get',
    params: { targetType, targetVersionId, passedGates: passedGates?.join(',') }
  });
}
