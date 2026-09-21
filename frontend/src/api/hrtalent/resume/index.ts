import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type {
  HrTalentParseConfirmForm,
  HrTalentParseTaskVO,
  HrTalentResumeQuery,
  HrTalentResumeUploadForm,
  HrTalentResumeVO
} from './types';

/**
 * 简历中心 域接口封装
 *
 * 路径与后端已实现 Controller 逐字一致（SPEC-P4 §2.1）：
 * - TalentResumeController：/talent/profiles/{id}/resumes、/talent/resumes/{id}/current|versions|download|parse
 * - TalentParseController：/talent/parse-tasks/{id}、/talent/parse-tasks/{id}/confirm
 *
 * 权限串：talent:resume:list/upload/version/download/parse/review。
 *
 * 安全口径：
 * - 上传新简历**默认创建新版本，绝不覆盖旧文件**；
 * - 下载是受控二进制流，**purpose 必填**，服务端先做资源级鉴权并写审计再返回内容；
 * - 解析只创建异步任务，正式字段只有人工确认后才写入主档。
 */

/** 查询指定人才的简历版本列表（GET /talent/profiles/{id}/resumes，权限 talent:resume:list） */
export function listResume(id: string | number, query?: HrTalentResumeQuery): AxiosPromise<HrTalentResumeVO[]> {
  return request({
    url: `/talent/profiles/${id}/resumes`,
    method: 'get',
    params: query
  });
}

/**
 * 上传简历新版本（POST /talent/profiles/{id}/resumes，multipart/form-data）
 *
 * 表单字段名固定为 file；sourceType/remark 作为同一表单的业务字段提交。
 * `@/utils/request` 已对 FormData 清理默认 JSON 头，浏览器会自动补充 boundary。
 */
export function uploadResume(data: HrTalentResumeUploadForm): AxiosPromise<HrTalentResumeVO> {
  const formData = new FormData();
  formData.append('file', data.file);
  if (data.sourceType) {
    formData.append('sourceType', data.sourceType);
  }
  if (data.remark) {
    formData.append('remark', data.remark);
  }
  return request({
    url: `/talent/profiles/${data.talentId}/resumes`,
    method: 'post',
    data: formData
  });
}

/** 指定某简历版本为当前简历（POST /talent/resumes/{id}/current，权限 talent:resume:version） */
export function setCurrentResume(id: string | number) {
  return request({
    url: `/talent/resumes/${id}/current`,
    method: 'post'
  });
}

/** 查询某简历版本所属人才的全部版本（GET /talent/resumes/{id}/versions，权限 talent:resume:list） */
export function listResumeVersions(id: string | number): AxiosPromise<HrTalentResumeVO[]> {
  return request({
    url: `/talent/resumes/${id}/versions`,
    method: 'get'
  });
}

/**
 * 鉴权并审计后受控下载简历（GET /talent/resumes/{id}/download，权限 talent:resume:download）
 *
 * 二进制流返回（Controller 返回 void），因此 responseType 为 blob；
 * `@/utils/request` 的响应拦截器对 blob 原样透传 AxiosResponse，调用方取 resp.data 得到 Blob。
 * purpose 为空时服务端记 denied 审计并返回错误，页面必须先校验用途非空。
 */
export function downloadResume(id: string | number, purpose: string) {
  return request({
    url: `/talent/resumes/${id}/download`,
    method: 'get',
    params: { purpose },
    responseType: 'blob'
  });
}

/** 创建异步解析任务（POST /talent/resumes/{id}/parse，权限 talent:resume:parse），返回任务ID */
export function createParseTask(id: string | number): AxiosPromise<string | number> {
  return request({
    url: `/talent/resumes/${id}/parse`,
    method: 'post'
  });
}

/** 查询解析任务与候选结果（GET /talent/parse-tasks/{id}，权限 talent:resume:review） */
export function getParseTask(id: string | number): AxiosPromise<HrTalentParseTaskVO> {
  return request({
    url: `/talent/parse-tasks/${id}`,
    method: 'get'
  });
}

/**
 * 人工确认选定字段并更新主档/经历（POST /talent/parse-tasks/{id}/confirm，权限 talent:resume:review）
 *
 * 只有 accepted = true 的字段写入正式数据；未勾选字段记为已否决但不写入。
 */
export function confirmParseTask(
  id: string | number,
  data: HrTalentParseConfirmForm
): AxiosPromise<HrTalentParseTaskVO> {
  return request({
    url: `/talent/parse-tasks/${id}/confirm`,
    method: 'post',
    data: data
  });
}
