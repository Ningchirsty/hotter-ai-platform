/**
 * 岗位包管理（主文档线增量 1b）类型定义。
 *
 * 与后端 `org.dromara.aigov.workspace.domain.vo` / `bo` 一一对应。
 * `allowedTransitions` 与 `visibleToEmployees` **由服务端算好**——前端不再自己判断状态机，
 * 否则会出现"界面说能点、后端拒绝"的两套口径。
 */

/** 岗位包版本（列表行） */
export interface AigRoleVersionVO {
  roleVersionId: string | number;
  roleId: string | number;
  roleCode?: string;
  roleName?: string;
  version?: string;
  releaseStatus?: string;
  rolloutChannel?: string;
  manifestSha256?: string;
  publishedAt?: string;
  disabledAt?: string;
  status?: string;
  remark?: string;
  updateTime?: string;
  /** 从当前状态允许流转到哪些状态（服务端按边表算） */
  allowedTransitions?: string[];
  /** 是否对员工可见（只有 PUBLISHED 为 true） */
  visibleToEmployees?: boolean;
}

/** 能力卡片 */
export interface AigRoleActionVO {
  actionId?: string | number;
  actionCode?: string;
  categoryCode?: string;
  title?: string;
  description?: string;
  launchMode?: string;
  targetType?: string;
  targetRef?: string;
  studioRouteKey?: string;
  requiredContext?: string;
  sortOrder?: number;
  enabled?: string;
}

/** 岗位包版本详情 */
export interface AigRoleVersionDetailVO extends AigRoleVersionVO {
  roleDescription?: string;
  manifestJson?: string;
  actions?: AigRoleActionVO[];
  /** 静态校验结论（为空即通过） */
  problems?: string[];
}

/** 预检结论 */
export interface AigRolePackageValidateVO {
  manifestSha256?: string;
  passed?: boolean;
  problems?: string[];
}

/** 分类（表单） */
export interface AigRolePackageCategoryForm {
  code: string;
  name: string;
}

/** 卡片（表单） */
export interface AigRolePackageActionForm {
  code: string;
  categoryCode: string;
  title: string;
  description?: string;
  launchMode: string;
  targetType: string;
  targetRef?: string;
  studioRouteKey?: string;
  requiredContext?: string;
  enabled: boolean;
}

/** 保存草稿入参 */
export interface AigRolePackageSaveForm {
  roleId?: string | number;
  roleVersionId?: string | number;
  /** 覆盖草稿时必填：保存前读到的清单哈希（CAS，服务端会核对） */
  expectedManifestSha256?: string;
  roleCode: string;
  roleName: string;
  version: string;
  description?: string;
  categories: AigRolePackageCategoryForm[];
  defaultCategory?: string;
  actions: AigRolePackageActionForm[];
  audienceScope: string;
  defaultDataLevel: string;
  maxDataLevel: string;
  remark?: string;
}

/** 查询入参 */
export interface AigRolePackageQuery {
  roleCode?: string;
  roleName?: string;
  releaseStatus?: string;
  pageNum?: number;
  pageSize?: number;
}

/** 发布流转入参 */
export interface AigRoleReleaseAdvanceForm {
  targetStatus: string;
  remark?: string;
}

/** 停用入参 */
export interface AigRolePackageDisableForm {
  remark?: string;
}
