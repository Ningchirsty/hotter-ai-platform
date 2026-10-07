import type { PageResult } from '@/api/types';
import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type {
  AigManifestScanVO,
  AigPackageDisableVO,
  AigPackageInstallLogVO,
  AigPackageInstallVO,
  AigPackageQuery,
  AigPackageRegisterVO,
  AigPackageStatusVO,
  AigPackageUploadForm,
  AigPackageVO,
  AigPackageVersionQuery,
  AigPackageVersionVO
} from './types';

/** 分页查询 Package 清单 */
export function listPackage(query: AigPackageQuery): AxiosPromise<PageResult<AigPackageVO>> {
  return request({
    url: '/aigov/agent/package/list',
    method: 'get',
    params: query
  });
}

/** 查询 Package 详情 */
export function getPackage(packageId: string | number): AxiosPromise<AigPackageVO> {
  return request({
    url: '/aigov/agent/package/' + packageId,
    method: 'get'
  });
}

/** 分页查询 Package 版本清单 */
export function listPackageVersion(query: AigPackageVersionQuery): AxiosPromise<PageResult<AigPackageVersionVO>> {
  return request({
    url: '/aigov/agent/package/version/list',
    method: 'get',
    params: query
  });
}

/** 查询 Package 版本详情（含扫描结论） */
export function getPackageVersion(packageVersionId: string | number): AxiosPromise<AigPackageVersionVO> {
  return request({
    url: '/aigov/agent/package/version/' + packageVersionId,
    method: 'get'
  });
}

/** 扫描 Manifest（§6.2 五类拒绝规则），结论落库 */
export function scanManifest(packageVersionId: string | number): AxiosPromise<AigManifestScanVO> {
  return request({
    url: '/aigov/agent/package/version/' + packageVersionId + '/scan',
    method: 'post'
  });
}

/**
 * 上传并登记 Package（携包体）。
 *
 * 服务端对上传字节算 SHA-256 并与 Manifest 声明的 checksum 比对：不一致整笔拒绝、不落库。
 */
export function uploadPackage(file: File, form: AigPackageUploadForm): AxiosPromise<AigPackageRegisterVO> {
  const data = new FormData();
  data.append('file', file);
  data.append('manifestJson', form.manifestJson);
  if (form.sourceRef) {
    data.append('sourceRef', form.sourceRef);
  }
  if (form.remark) {
    data.append('remark', form.remark);
  }
  return request({
    url: '/aigov/agent/package/upload',
    method: 'post',
    data: data,
    headers: { 'Content-Type': 'multipart/form-data' }
  });
}

/** 安装：按 Manifest 声明的 agents/skills 建出 DRAFT 版本（幂等） */
export function installPackage(packageVersionId: string | number): AxiosPromise<AigPackageInstallVO> {
  return request({
    url: '/aigov/agent/package/version/' + packageVersionId + '/install',
    method: 'post'
  });
}

/** 查安装账本（追加型：UPLOAD / INSTALL / ...） */
export function listInstallLog(packageVersionId: string | number): AxiosPromise<AigPackageInstallLogVO[]> {
  return request({
    url: '/aigov/agent/package/version/' + packageVersionId + '/install-log',
    method: 'get'
  });
}

/**
 * 停用【版本级】：把该 Package 版本带进来的 Agent/Skill 版本批量下线。
 *
 * 停的是「这个包带进来的」（按 `package_version_id` 精确判定），同一个 Agent 的其它版本不动。
 * 已在停用状态的按幂等处理；已归档的跳过并给出原因（不让一个归档版本阻断其余版本下线）。
 * **影响已经装出去、可能正在被使用的版本**——与下面的包级停用不是一回事。
 */
export function disablePackageVersion(packageVersionId: string | number): AxiosPromise<AigPackageDisableVO> {
  return request({
    url: '/aigov/agent/package/version/' + packageVersionId + '/disable',
    method: 'post'
  });
}

/**
 * 停用【包级】：此后不再接受该包的新版本上传，也不能安装。
 *
 * **不会动已经装出去、正在被使用的版本**（要下线它们用 `disablePackageVersion`）。
 * 启用用 `enablePackage`。
 */
export function disablePackage(packageId: string | number): AxiosPromise<AigPackageStatusVO> {
  return request({
    url: '/aigov/agent/package/' + packageId + '/disable',
    method: 'post'
  });
}

/** 启用【包级】：恢复「可上传新版本、可安装」（不会让被停用的版本恢复） */
export function enablePackage(packageId: string | number): AxiosPromise<AigPackageStatusVO> {
  return request({
    url: '/aigov/agent/package/' + packageId + '/enable',
    method: 'post'
  });
}
