import type { PageResult } from '@/api/types';
import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type {
  HrAttachmentQuery,
  HrAttachmentVO,
  HrBackgroundDetailQuery,
  HrBackgroundDetailVO,
  HrBackgroundForm,
  HrBackgroundQuery,
  HrBackgroundVO
} from './types';

/**
 * 背调与业务附件 域接口封装
 *
 * 路径与 docs/hr-talent/SPEC-P3-招聘流程闭环.md §2.4 / §2.5 逐字一致，并与后端
 * RecruitBackgroundController（/recruit/background-checks）、
 * RecruitAttachmentController（/recruit/attachments）完全对齐。
 * 权限串：recruit:background:list/add/edit/view-sensitive、recruit:attachment:preview/download。
 */

/* ============================ 背调 ============================ */

/** 分页查询背调记录（不含敏感明细） */
export function listBackground(query: HrBackgroundQuery): AxiosPromise<PageResult<HrBackgroundVO>> {
  return request({
    url: '/recruit/background-checks',
    method: 'get',
    params: query
  });
}

/** 查询背调普通详情（不含敏感明细） */
export function getBackground(id: string | number): AxiosPromise<HrBackgroundVO> {
  return request({
    url: '/recruit/background-checks/' + id,
    method: 'get'
  });
}

/** 新增背调记录 */
export function addBackground(data: HrBackgroundForm): AxiosPromise<string | number> {
  return request({
    url: '/recruit/background-checks',
    method: 'post',
    data: data
  });
}

/** 更新背调记录 */
export function updateBackground(data: HrBackgroundForm) {
  return request({
    url: '/recruit/background-checks/' + data.backgroundId,
    method: 'put',
    data: data
  });
}

/**
 * 查看背调敏感明细（独立权限 recruit:background:view-sensitive）
 * **必须**先填写 purpose，服务端会先写 background_view 审计再返回明文。
 */
export function getBackgroundDetail(id: string | number, query: HrBackgroundDetailQuery): AxiosPromise<HrBackgroundDetailVO> {
  return request({
    url: `/recruit/background-checks/${id}/detail`,
    method: 'get',
    params: query
  });
}

/* ============================ 业务附件（受控预览 / 下载） ============================ */

/**
 * 查询业务对象附件列表（GET /recruit/attachments，权限 recruit:attachment:preview）
 * 背调附件固定使用 bizType = background。
 */
export function listAttachment(query: HrAttachmentQuery): AxiosPromise<PageResult<HrAttachmentVO>> {
  return request({
    url: '/recruit/attachments',
    method: 'get',
    params: query
  });
}

/**
 * 鉴权并审计后预览附件（GET /recruit/attachments/{id}/preview，权限 recruit:attachment:preview）
 *
 * 说明：该接口以二进制流（inline）返回，不走若依 JSON 出参，因此 responseType 为 blob；
 * `@/utils/request` 的响应拦截器对 blob 会**原样透传 AxiosResponse**，调用方取 `resp.data` 得到 Blob。
 * purpose 为空时服务端记 denied 审计并返回错误，页面必须先校验用途非空。
 */
export function previewAttachment(id: string | number, purpose: string) {
  return request({
    url: `/recruit/attachments/${id}/preview`,
    method: 'get',
    params: { purpose },
    responseType: 'blob'
  });
}

/**
 * 鉴权并审计后下载附件（GET /recruit/attachments/{id}/download，权限 recruit:attachment:download）
 * 同样以二进制流返回（attachment），调用方取 `resp.data` 得到 Blob 后自行保存。
 */
export function downloadAttachment(id: string | number, purpose: string) {
  return request({
    url: `/recruit/attachments/${id}/download`,
    method: 'get',
    params: { purpose },
    responseType: 'blob'
  });
}
