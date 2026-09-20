import type { PageResult } from '@/api/types';
import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type { CpGateRuleForm, CpGateRuleQuery, CpGateRuleVO } from './types';

// 查询闸门规则分页列表
export function listGateRule(query: CpGateRuleQuery): AxiosPromise<PageResult<CpGateRuleVO>> {
  return request({
    url: '/content/gateRule/list',
    method: 'get',
    params: query
  });
}

// 查询闸门规则详情
export function getGateRule(ruleId: string | number): AxiosPromise<CpGateRuleVO> {
  return request({
    url: '/content/gateRule/' + ruleId,
    method: 'get'
  });
}

// 新增闸门规则（后端 POST 落在 /content/gateRule 根路径）
export function addGateRule(data: CpGateRuleForm) {
  return request({
    url: '/content/gateRule',
    method: 'post',
    data: data
  });
}

// 修改闸门规则（后端 PUT 落在 /content/gateRule 根路径，规则ID随body提交）
export function updateGateRule(data: CpGateRuleForm) {
  return request({
    url: '/content/gateRule',
    method: 'put',
    data: data
  });
}

// 删除闸门规则
export function delGateRule(ruleId: string | number | Array<string | number>) {
  return request({
    url: '/content/gateRule/' + ruleId,
    method: 'delete'
  });
}
