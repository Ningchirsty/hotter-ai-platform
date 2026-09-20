import type { PageResult } from '@/api/types';
import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type {
  AigModelCreateForm,
  AigModelGovernanceForm,
  AigModelGovernanceVO,
  AigModelProviderOption,
  AigModelQuery
} from './types';

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

// 供应商下拉选项（新增模型表单用）
export function listModelProviders(): AxiosPromise<AigModelProviderOption[]> {
  return request({
    url: '/aigov/model/providers',
    method: 'get'
  });
}

// 新增模型：登记模型主数据（sai_model_config）+ 首份治理属性，同一事务提交
export function createModel(data: AigModelCreateForm) {
  return request({
    url: '/aigov/model',
    method: 'post',
    data: data
  });
}
