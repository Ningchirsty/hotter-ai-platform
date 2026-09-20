import type { PageResult } from '@/api/types';
import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type { AigModelGovernanceForm, AigModelGovernanceVO, AigModelQuery } from './types';

// 查询模型清单（以 snail-ai 的 sai_model_config 为主表，左连治理属性）
// apiEndpoint / secretRef 由后端按 aig:model:secret 权限决定是否下发
export function listModel(query: AigModelQuery): AxiosPromise<PageResult<AigModelGovernanceVO>> {
  return request({
    url: '/aigov/model/list',
    method: 'get',
    params: query
  });
}

// 查询单个模型及其治理属性
export function getModel(modelId: string | number): AxiosPromise<AigModelGovernanceVO> {
  return request({
    url: '/aigov/model/' + modelId,
    method: 'get'
  });
}

// 登记/更新模型治理属性（不存在则新建治理记录）
export function updateModelGovernance(data: AigModelGovernanceForm) {
  return request({
    url: '/aigov/model/governance',
    method: 'put',
    data: data
  });
}
