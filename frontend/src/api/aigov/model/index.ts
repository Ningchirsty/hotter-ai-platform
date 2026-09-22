import type { PageResult } from '@/api/types';
import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type {
  AigModelBaseForm,
  AigModelCreateForm,
  AigModelGovernanceForm,
  AigModelGovernanceVO,
  AigModelProviderForm,
  AigModelProviderOption,
  AigModelQuery,
  AigModelSecretForm,
  AigModelTestResult
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

// 供应商下拉选项（新增模型表单用，仅启用项）
export function listModelProviders(): AxiosPromise<AigModelProviderOption[]> {
  return request({
    url: '/aigov/model/providers',
    method: 'get'
  });
}

// 供应商管理列表（含停用项与各供应商下模型数量）
export function listAllModelProviders(): AxiosPromise<AigModelProviderOption[]> {
  return request({
    url: '/aigov/model/providers/all',
    method: 'get'
  });
}

// 新增供应商：内置 7 家之外接入自建服务/新厂商时使用（表内无密钥列）
export function createModelProvider(data: AigModelProviderForm) {
  return request({
    url: '/aigov/model/provider',
    method: 'post',
    data: data
  });
}

// 修改供应商：只允许名称/说明/图标/启停，标识不可改
export function updateModelProvider(data: AigModelProviderForm) {
  return request({
    url: '/aigov/model/provider',
    method: 'put',
    data: data
  });
}

// 连通性测试：后端按部署类型/适配器分流探测，并把健康状态写回治理表
export function testModelConnection(modelId: string | number): AxiosPromise<AigModelTestResult> {
  return request({
    url: '/aigov/model/' + modelId + '/test',
    method: 'post'
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

// 编辑模型主数据（PUT /aigov/model/base）
// 只改主数据白名单列，不含密钥；apiEndpoint 对无 aig:model:secret 的账号应不下发。
export function updateModelBase(data: AigModelBaseForm) {
  return request({
    url: '/aigov/model/base',
    method: 'put',
    data: data
  });
}

// 写入/清除模型密钥（PUT /aigov/model/secret）
// 明文只在请求体里出现一次，后端加密后落库；任何查询接口都不会回显密钥。
// clearKey=true 表示清除已有密钥（显式语义，避免「留空=不修改」的歧义）。
export function updateModelSecret(data: AigModelSecretForm) {
  return request({
    url: '/aigov/model/secret',
    method: 'put',
    data: data
  });
}
