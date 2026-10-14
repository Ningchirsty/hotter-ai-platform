import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type { AigToolDefinitionForm, AigToolVersionForm, AigToolVersionVO } from './types';

/**
 * 工具注册接口（设计 §6.5、§9.2）。
 *
 * 读（清单）与写（登记定义/版本）分开授权：登记工具会改变"Agent 能申请什么范围"。
 */

/** 可绑定工具清单（含是否已接入执行器） */
export function listTools(): AxiosPromise<AigToolVersionVO[]> {
  return request({ url: '/aigov/tools', method: 'get' });
}

/** 新建工具定义 */
export function createToolDefinition(data: AigToolDefinitionForm): AxiosPromise<number | string> {
  return request({ url: '/aigov/tools/definitions', method: 'post', data });
}

/** 新增工具版本（执行契约） */
export function addToolVersion(data: AigToolVersionForm): AxiosPromise<number | string> {
  return request({ url: '/aigov/tools/versions', method: 'post', data });
}
