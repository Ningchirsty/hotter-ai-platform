import type { PageResult } from '@/api/types';
import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type { ExportCreateForm, ExportTaskQuery, ExportTaskVO } from './types';

// 查询导出任务列表（仅本人或授权范围）
export function listExport(query: ExportTaskQuery): AxiosPromise<PageResult<ExportTaskVO>> {
  return request({
    url: '/talent/export/list',
    method: 'get',
    params: query
  });
}

// 创建导出任务（服务端异步生成）
export function createExport(data: ExportCreateForm) {
  return request({
    url: '/talent/export',
    method: 'post',
    data: data
  });
}

// 下载导出文件：后端返回二进制流
export function downloadExport(exportId: string | number): AxiosPromise<Blob> {
  return request({
    url: '/talent/export/download/' + exportId,
    method: 'get',
    responseType: 'blob'
  });
}
