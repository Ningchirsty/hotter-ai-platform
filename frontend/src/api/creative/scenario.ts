import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';

/**
 * 场景配置层只读接口（V0.2 B1/R11 的 `/creative/v2/*`）。
 *
 * <p>这一层是"场景怎么生产"的权威：交付类型、步骤、输出规格、工作台装配。
 * 前端本轮**只读不驱动**（先让配置可见，流程仍走既有阶段机），所以这里只有 GET。</p>
 */

/** 交付类型 */
export interface ScenarioDeliveryType {
  id?: string | number;
  categoryCode?: string;
  deliveryType?: string;
  aliasCodes?: string;
  deliveryName?: string;
  mediaType?: string;
  renderMode?: string;
  defaultProfileId?: string | number;
  enabled?: string;
  sortNo?: number;
}

/** 场景档案 */
export interface ScenarioProfile {
  id?: string | number;
  profileCode?: string;
  profileName?: string;
  deliveryType?: string;
  version?: string;
  status?: string;
  inputSchemaJson?: string;
  workflowSchemaJson?: string;
  outputSchemaJson?: string;
  workspaceSchemaJson?: string;
}

/** 场景步骤 */
export interface ScenarioStep {
  id?: string | number;
  profileId?: string | number;
  stepCode?: string;
  stepName?: string;
  stepType?: string;
  stageCodes?: string;
  sortNo?: number;
  required?: string;
  gateType?: string;
  capabilityCode?: string;
  workspaceComponent?: string;
  configJson?: string;
}

/** 输出规格 */
export interface ScenarioOutputSpec {
  id?: string | number;
  specCode?: string;
  deliveryType?: string;
  channel?: string;
  width?: number;
  height?: number;
  heightMode?: string;
  ratio?: string;
  unit?: string;
  dpi?: number;
  colorMode?: string;
  isDefault?: string;
  sortNo?: number;
}

/** 全部启用的交付类型 */
export function listDeliveryTypes(): AxiosPromise<ScenarioDeliveryType[]> {
  return request({ url: '/creative/v2/delivery-types', method: 'get' });
}

/** 按编码或别名查交付类型 */
export function getDeliveryType(code: string): AxiosPromise<ScenarioDeliveryType> {
  return request({ url: `/creative/v2/delivery-types/${code}`, method: 'get' });
}

/** 取某交付类型的场景档案 */
export function getScenario(deliveryType: string): AxiosPromise<ScenarioProfile> {
  return request({ url: `/creative/v2/scenarios/${deliveryType}`, method: 'get' });
}

/** 取某交付类型的步骤 */
export function listScenarioSteps(deliveryType: string): AxiosPromise<ScenarioStep[]> {
  return request({ url: `/creative/v2/scenarios/${deliveryType}/steps`, method: 'get' });
}

/** 取某交付类型的输出规格（默认规格排最前） */
export function listOutputSpecs(deliveryType: string): AxiosPromise<ScenarioOutputSpec[]> {
  return request({ url: `/creative/v2/scenarios/${deliveryType}/output-specs`, method: 'get' });
}
