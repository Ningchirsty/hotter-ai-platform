import type { PageResult } from '@/api/types';
import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type { CpOutputCheckQuery, CpOutputCheckVO, RunCheckForm } from './types';

// 查询成品检查分页列表
export function listCheck(query: CpOutputCheckQuery): AxiosPromise<PageResult<CpOutputCheckVO>> {
  return request({
    url: '/content/check/list',
    method: 'get',
    params: query
  });
}

// 查询某任务下的检查记录
export function listCheckByTask(taskId: string | number): AxiosPromise<CpOutputCheckVO[]> {
  return request({
    url: '/content/check/byTask/' + taskId,
    method: 'get'
  });
}

// 查询检查详情
export function getCheck(checkId: string | number): AxiosPromise<CpOutputCheckVO> {
  return request({
    url: '/content/check/' + checkId,
    method: 'get'
  });
}

/**
 * 发起检查（上传参考图与成品图，后端异步比对）。
 *
 * 三种组合由后端校验：引用已有附件（referenceFileId）、上传新参考图（referenceFile），
 * 两者至少给一个；resultFile 必填。
 */
export function runCheck(data: RunCheckForm): AxiosPromise<string | number> {
  const formData = new FormData();
  formData.append('taskId', String(data.taskId));
  if (data.referenceFileId !== undefined && data.referenceFileId !== null && data.referenceFileId !== '') {
    formData.append('referenceFileId', String(data.referenceFileId));
  }
  if (data.referenceFile) {
    formData.append('referenceFile', data.referenceFile);
  }
  formData.append('resultFile', data.resultFile);
  if (data.remark) {
    formData.append('remark', data.remark);
  }
  return request({
    url: '/content/check/run',
    method: 'post',
    data: formData,
    // 上传要走受限于生产 Tunnel 的上行（实测 10–20KB/s，而同一入口的下载有 ~300KB/s）。
    // 客户端已把图片压到 ~200KB，但慢的时候两张仍要几十秒，全局默认的 50s 会把「慢」误判成超时。
    // 不设到 100s 以上：Cloudflare 自身在 100s 会返回 524。
    timeout: 95000
  });
}

// 删除检查记录（逻辑删除）
export function delCheck(checkId: string | number | Array<string | number>) {
  return request({
    url: '/content/check/' + checkId,
    method: 'delete'
  });
}

/**
 * 读取检查涉及的图片，返回可交给 `<img>` 的 blob URL。
 *
 * 为什么不直接用对象存储直链：内容资料存在私有前缀且不登记 `sys_oss`，
 * 直链会绕过内容模块的授权。走带鉴权头的请求拿 blob 才安全。
 *
 * 调用方必须负责在不再使用时 `URL.revokeObjectURL` 释放。
 *
 * @param side `reference` 参考图 / `result` 成品图
 */
export const fetchCheckImageBlobUrl = async (
  checkId: string | number,
  side: 'reference' | 'result'
): Promise<string> => {
  const res = await request({
    url: `/content/check/${checkId}/image/${side}`,
    method: 'get',
    responseType: 'blob'
  });
  const data = res.data as unknown;
  if (!(data instanceof Blob)) {
    throw new Error('图片读取失败');
  }
  return URL.createObjectURL(data);
};
