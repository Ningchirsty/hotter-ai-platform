import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type { BrandBriefForm, BrandBriefVO } from './types';

/**
 * 品牌要求（Brief）接口 —— 归属内容生产协同。
 *
 * <p><b>为什么在这里而不是创作域</b>：品牌部用内容协同，平面设计部用 AI 视觉工厂；
 * 要求由品牌部录入并确认，视觉工厂只读。后端已把接口搬到 `/content/task/{taskId}/brand-brief`，
 * 创作域的 `/creative/projects/{taskId}/brand-brief` 三个接口已删除——
 * 所以这里必须指向内容域，指向创作域会 404。</p>
 *
 * <p>权限沿用内容任务的既有权：读 `content:task:query`、写 `content:task:edit`。</p>
 */

/** 读任务的品牌要求（没填过时后端也返回对象：configured=false、status=DRAFT） */
export function getBrandBrief(taskId: string | number): AxiosPromise<BrandBriefVO> {
  return request({
    url: `/content/task/${taskId}/brand-brief`,
    method: 'get'
  });
}

/** 保存品牌要求（草稿；状态由 confirm 接口推进，这里不传 status） */
export function saveBrandBrief(taskId: string | number, data: BrandBriefForm): AxiosPromise<BrandBriefVO> {
  return request({
    url: `/content/task/${taskId}/brand-brief`,
    method: 'put',
    data: data
  });
}

/** 品牌方确认（把状态推进为已确认） */
export function confirmBrandBrief(taskId: string | number): AxiosPromise<BrandBriefVO> {
  return request({
    url: `/content/task/${taskId}/brand-brief/confirm`,
    method: 'post'
  });
}
