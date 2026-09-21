import type { PageResult } from '@/api/types';
import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type { HrSensitiveAuditQuery, HrSensitiveAuditVO } from './types';

/**
 * 敏感操作审计 域接口封装（SPEC-P3 §2.5 / §3.6）
 *
 * 路径与后端 SensitiveAuditController 逐字一致：
 * - GET /recruit/audits          分页查询（权限 recruit:audit:list）
 * - GET /recruit/audits/export   按当前筛选条件导出文件流（权限 recruit:audit:export）
 *
 * 审计表为追加型：本域**只有**查询与导出，没有新增/修改/删除接口；
 * 写入统一由后端 support/SensitiveAuditRecorder 完成。
 */

/** 分页查询敏感操作审计（GET /recruit/audits） */
export function listAudit(query: HrSensitiveAuditQuery): AxiosPromise<PageResult<HrSensitiveAuditVO>> {
  return request({
    url: '/recruit/audits',
    method: 'get',
    params: query
  });
}

/**
 * 按当前筛选条件导出审计记录（GET /recruit/audits/export，权限 recruit:audit:export）
 *
 * 文件流响应，**不包装 R**，且导出动作本身会写入一条 `export` 审计。
 * 后端导出接口只接收查询 BO（不接受分页参数），因此入参为去掉 pageNum/pageSize 的筛选条件。
 * 后端用 `FileUtils.setAttachmentResponseHeader` 设置了：
 * - `Content-Disposition: attachment; filename=<percentEncoded>;filename*=utf-8''<percentEncoded>`
 * - `download-filename: <percentEncoded>`（并已通过 `Access-Control-Expose-Headers` 暴露）
 *
 * 因此这里保持 responseType=blob（与仓库既有下载一致），由调用方取
 * `resp.headers['download-filename']` 解析文件名（见 `@/plugins/download` 的 oss() 同一写法）。
 */
export function exportAudit(query: Omit<HrSensitiveAuditQuery, 'pageNum' | 'pageSize'>) {
  return request({
    url: '/recruit/audits/export',
    method: 'get',
    params: query,
    responseType: 'blob'
  });
}
