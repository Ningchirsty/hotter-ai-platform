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
  /** 进入条件（结构化 JSON 字符串，如 `{"requireDna":true}`）——R16 起前端据此判"被阻塞" */
  entryConditionJson?: string;
  /** 完成判定（结构化 JSON 字符串，如 `{"status":"LOCKED"}`） */
  completionRuleJson?: string;
  configJson?: string;
}

/**
 * 项目步骤状态（`GET /creative/v2/projects/{taskId}/steps`，R14/D2）。
 *
 * <p>它是 `cp_task.visual_stage` 的**派生投影**：有持久化行时 `source=PERSISTED`，
 * 否则按当前阶段推导（`DERIVED`）。R16 起指引线的"已完成/进行中/待办"以它为准。</p>
 */
export interface ProjectStepState {
  stepCode?: string;
  stepName?: string;
  sortNo?: number;
  status?: string;
  stageCode?: string;
  startedAt?: string;
  completedAt?: string;
  source?: string;
}

/** 工作台装配（`dp_workspace_schema`） */
export interface ScenarioWorkspace {
  id?: string | number;
  schemaCode?: string;
  deliveryType?: string;
  profileId?: string | number;
  version?: string;
  /**
   * 装配定义（文档 §49 口径）：`{workspace, panels[], steps:[{code, component}]}`。
   *
   * <p><b>注意别取错源</b>：场景档案（{@link ScenarioProfile}）里的 `workspaceSchemaJson`
   * 只是**引用**——内容形如 `{"schemaCode":"WS_LONG_PAGE"}`；真正的装配定义在本表这一列。
   * R17 第一版就是取错了源，对照整块静默不显示。</p>
   */
  layoutJson?: string;
  status?: string;
  remark?: string;
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

/**
 * 取某交付类型的工作台装配（已发布优先）。
 *
 * <p>装配定义（panels 与 steps→component）在**这里**的 `layoutJson`，不在场景档案里
 * ——档案里那个字段只是引用（见 {@link ScenarioWorkspace.layoutJson} 的说明）。</p>
 *
 * @param deliveryType 交付类型编码或别名
 * @returns 工作台装配
 */
export function getWorkspace(deliveryType: string): AxiosPromise<ScenarioWorkspace> {
  return request({ url: `/creative/v2/scenarios/${deliveryType}/workspace`, method: 'get' });
}

/** 取某交付类型的输出规格（默认规格排最前） */
export function listOutputSpecs(deliveryType: string): AxiosPromise<ScenarioOutputSpec[]> {
  return request({ url: `/creative/v2/scenarios/${deliveryType}/output-specs`, method: 'get' });
}

/**
 * 取某项目的「配置步骤 + 步骤状态」（R14/D2 的只读接口）。
 *
 * <p>只读：没有持久化行的项目由后端按当前阶段推导（`source=DERIVED`），查询本身不写库。</p>
 *
 * @param taskId 项目ID
 * @returns 步骤状态列表（按配置顺序）
 */
export function listProjectSteps(taskId: string | number): AxiosPromise<ProjectStepState[]> {
  return request({ url: `/creative/v2/projects/${taskId}/steps`, method: 'get' });
}
