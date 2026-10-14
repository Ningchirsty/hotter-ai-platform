import request from '@/utils/request';
import type { AxiosPromise } from '@/utils/api-types';

export interface FeedVariable { key: string; label: { zh: string; en: string }; type: 'text' | 'select' | 'image'; required: boolean; max_len?: number; options?: string[] }
export interface FeedTemplate { id: string; revision: number; title: { zh: string; en: string }; category: string; tags: string[]; variables: FeedVariable[]; cover: string; status: 'published' | 'maintenance'; canGenerate: boolean; outputSize: string; model?: string; durationSeconds?: number; hasVideoPreview?: boolean; promptTemplate?: string; output?: import('./types').CloudImageOutputParams }
export interface FeedPage { items: FeedTemplate[]; total: number; categories: { id: string; zh: string; en: string }[]; feed: { version: number; online: boolean; checkedAt: string; syncCode: string } }
export interface TemplateSubmission { template_id: string; revision: number; client_request_id: string; variables: Record<string, string> }
export interface TemplateGeneration { request_id: string; status: 'pending' | 'submitted' | 'succeeded' | 'failed' | 'unknown' | 'archived'; summary_status?: string; task_id?: string; result_urls?: string[]; error?: { code: string; message: string } }
export const listTemplates = (params: { page: number; page_size: number; category?: string; keyword?: string }, media: 'image' | 'video' = 'image'): AxiosPromise<FeedPage> => request({ url: '/' + media + '/templates', params });
export const getTemplate = (id: string, media: 'image' | 'video' = 'image'): AxiosPromise<FeedTemplate> => request({ url: '/' + media + '/templates/' + encodeURIComponent(id) });
export const generateTemplate = (data: TemplateSubmission): AxiosPromise<TemplateGeneration> => request({ url: '/image/templates/generate', method: 'post', data, headers: { repeatSubmit: false } });
export const getTemplateGeneration = (id: string): AxiosPromise<TemplateGeneration> => request({ url: '/image/templates/generate/' + encodeURIComponent(id) });
export const retryTemplateGeneration = (id: string): AxiosPromise<TemplateGeneration> => request({ url: '/image/templates/generate/' + encodeURIComponent(id) + '/retry', method: 'post', headers: { repeatSubmit: false } });
export const templateCover = async (id: string, revision: number, media: 'image' | 'video' = 'image') => {
  const r = await request({ url: '/' + media + '/templates/' + encodeURIComponent(id) + '/cover', params: { revision }, responseType: 'blob' });
  const blob = r.data as Blob;
  if (!blob?.size || !blob.type.startsWith('image/')) throw new Error('模板封面暂不可用');
  return URL.createObjectURL(blob);
};
export const templateVideoPreview = async (id: string, revision: number): Promise<string> => {
  const response = await request({ url: '/video/templates/' + encodeURIComponent(id) + '/preview', params: { revision }, responseType: 'blob', timeout: 300000 });
  const blob = response.data as Blob;
  if (!blob?.size || blob.size > 16 * 1024 * 1024 || blob.type.split(';')[0] !== 'video/mp4') throw new Error('示例视频暂不可用');
  return URL.createObjectURL(blob);
};

export interface TemplateDraft { templateId: string; revision: number; model: string; capability: 'T2I'; prompt: string; referenceAssetIds: (number | string)[]; output: import('./types').CloudImageOutputParams }
export const prepareTemplate = (data: Omit<TemplateSubmission, 'client_request_id'>): AxiosPromise<TemplateDraft> => request({ url: '/image/templates/prepare', method: 'post', data, headers: { repeatSubmit: false } });
export const prepareVideoTemplate = (data: Omit<TemplateSubmission, 'client_request_id'>): AxiosPromise<import('@/api/video/cloud').VideoCloudDraft> => request({ url: '/video/templates/prepare', method: 'post', data, headers: { repeatSubmit: false } });
