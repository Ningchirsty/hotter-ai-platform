import type { PageResult } from '@/api/types';
import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type {
  AigRolePackageDisableForm,
  AigRolePackageQuery,
  AigRolePackageSaveForm,
  AigRolePackageValidateVO,
  AigRoleReleaseAdvanceForm,
  AigRoleVersionDetailVO,
  AigRoleVersionVO
} from './types';

/**
 * 岗位包管理接口。
 *
 * 注意：这里**没有**"把状态改成任意值"的通用入口。发布（publish）只接受
 * TESTING/PUBLISHED，停用（disable）是另一个端点、另一个权限点——
 * 一个能改任意状态的接口会让"叫停问题版本"和"上架"变成同一个权限。
 */

/** 分页查询岗位包版本 */
export function listRoleVersions(query: AigRolePackageQuery): AxiosPromise<PageResult<AigRoleVersionVO>> {
  return request({
    url: '/aigov/roles/list',
    method: 'get',
    params: query
  });
}

/** 某个岗位的全部版本（最新在前） */
export function listRoleVersionsOfRole(roleId: string | number): AxiosPromise<AigRoleVersionVO[]> {
  return request({
    url: '/aigov/roles/' + roleId + '/versions',
    method: 'get'
  });
}

/** 版本详情（含卡片、清单原文与校验结论） */
export function getRoleVersion(roleVersionId: string | number): AxiosPromise<AigRoleVersionDetailVO> {
  return request({
    url: '/aigov/roles/version/' + roleVersionId,
    method: 'get'
  });
}

/** 预检一份"还没入库"的岗位包（只读，不落库） */
export function validateRolePackage(data: AigRolePackageSaveForm): AxiosPromise<AigRolePackageValidateVO> {
  return request({
    url: '/aigov/roles/validate',
    method: 'post',
    data
  });
}

/** 校验库里已存在的那一份版本（发布前必做） */
export function validateStoredRoleVersion(roleVersionId: string | number): AxiosPromise<AigRolePackageValidateVO> {
  return request({
    url: '/aigov/roles/version/' + roleVersionId + '/validate',
    method: 'post'
  });
}

/** 保存岗位包草稿（新建岗位/新版本，或覆盖 DRAFT 版本） */
export function saveRolePackage(data: AigRolePackageSaveForm): AxiosPromise<AigRoleVersionDetailVO> {
  return request({
    url: '/aigov/roles',
    method: 'post',
    data
  });
}

/** 覆盖保存一个 DRAFT 版本（必须带 expectedManifestSha256） */
export function updateRoleVersion(
  roleVersionId: string | number,
  data: AigRolePackageSaveForm
): AxiosPromise<AigRoleVersionDetailVO> {
  return request({
    url: '/aigov/roles/version/' + roleVersionId,
    method: 'put',
    data
  });
}

/** 发布流转（只接受 TESTING 或 PUBLISHED；离开 DRAFT 前服务端会重新校验） */
export function publishRoleVersion(
  roleVersionId: string | number,
  data: AigRoleReleaseAdvanceForm
): AxiosPromise<AigRoleVersionDetailVO> {
  return request({
    url: '/aigov/roles/version/' + roleVersionId + '/publish',
    method: 'post',
    data
  });
}

/** 停用版本（可撤销；与发布分开授权） */
export function disableRoleVersion(
  roleVersionId: string | number,
  data: AigRolePackageDisableForm
): AxiosPromise<AigRoleVersionDetailVO> {
  return request({
    url: '/aigov/roles/version/' + roleVersionId + '/disable',
    method: 'post',
    data
  });
}
