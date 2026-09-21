import type { PageResult } from '@/api/types';
import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type {
  HrTalentExportCreateForm,
  HrTalentExportQuery,
  HrTalentExportTaskVO
} from './types';

/**
 * 人才导出任务 域接口封装（SPEC-P4 §2.6 F 线、设计文档 §8.20）
 *
 * 路径与后端 TalentExportController 逐字一致：
 * - POST /talent/profiles/export            创建导出（普通台账 / 敏感台账）
 * - GET  /talent/exports                    导出任务分页列表
 * - GET  /talent/exports/{id}/download      受控下载（**用途必填**，过期拒绝）
 *
 * 权限串统一为 `talent:profile:export`；敏感台账另有服务层独立权限校验
 * （`talent:profile:phone-view`）与 `purpose` 必填校验。
 *
 * 安全口径：**不返回、不拼接任何对象存储永久地址或预签名地址**；
 * 下载一律走系统内受控接口，由服务端鉴权、用途校验与过期校验后流式输出。
 */

/** 创建人才导出（POST /talent/profiles/export），返回导出任务ID */
export function createTalentExport(data: HrTalentExportCreateForm): AxiosPromise<string | number> {
  return request({
    url: '/talent/profiles/export',
    method: 'post',
    data: data
  });
}

/** 导出任务分页列表（GET /talent/exports，只含系统内受控下载地址） */
export function listTalentExport(query: HrTalentExportQuery): AxiosPromise<PageResult<HrTalentExportTaskVO>> {
  return request({
    url: '/talent/exports',
    method: 'get',
    params: query
  });
}

/**
 * 受控下载导出结果文件（GET /talent/exports/{id}/download，**用途必填**）
 *
 * 二进制流返回（Controller 返回 void + HttpServletResponse），因此 responseType 为 blob；
 * `@/utils/request` 的响应拦截器对 blob **原样透传 AxiosResponse**，调用方取 `resp.data` 得到 Blob。
 *
 * ⚠️ 后端业务异常（用途为空、无敏感台账权限、任务未完成或已过期）是 **HTTP 200 + JSON 体**，
 * 在 responseType=blob 下会得到一个 `application/json` 类型的 Blob，
 * 调用方必须先用 `blob.type` 识别并解析出 `msg` 提示，**不要**把 JSON 当成文件保存。
 */
export function downloadTalentExport(id: string | number, purpose: string) {
  return request({
    url: `/talent/exports/${id}/download`,
    method: 'get',
    params: { purpose },
    responseType: 'blob'
  });
}
