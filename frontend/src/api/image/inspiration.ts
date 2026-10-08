import request from '@/utils/request';
import type { AxiosPromise } from '@/utils/api-types';
import type { PageResult } from '@/api/types';

export interface GeneratedInspiration {
  id: string; assetId: string; taskId: string; taskNo: string; title: string;
  model: string; capability: string; prompt: string; width?: number; height?: number;
  source: 'bluocto'; createdAt: string; contentType: string;
}
/** 只读本人真实云端作品，不调用付费生成。 */
export const listImageInspirations = (params: {
  pageNum: number; pageSize: number; keyword?: string; model?: string; capability?: string; category?: string; savedIds?: string;
}): AxiosPromise<PageResult<GeneratedInspiration>> => request({ url: '/image/inspirations', method: 'get', params });
