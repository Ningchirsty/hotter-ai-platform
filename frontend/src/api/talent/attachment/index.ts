import type { AxiosPromise } from '@/utils/api-types';
import request from '@/utils/request';
import type { TalentAttachmentVO, TalentAttachmentUploadForm } from './types';

// 查询某人才的附件版本列表
export function listAttachment(talentId: string | number): AxiosPromise<TalentAttachmentVO[]> {
  return request({
    url: '/talent/attachment/list/' + talentId,
    method: 'get'
  });
}

// 上传附件（新版本）：multipart/form-data
export function uploadAttachment(data: TalentAttachmentUploadForm): AxiosPromise<string | number> {
  const formData = new FormData();
  formData.append('talentId', String(data.talentId));
  formData.append('attachmentType', data.attachmentType);
  formData.append('file', data.file);
  return request({
    url: '/talent/attachment/upload',
    method: 'post',
    data: formData
  });
}

// 受控下载：后端返回二进制流，前端按 blob 保存
export function downloadAttachment(attachmentId: string | number): AxiosPromise<Blob> {
  return request({
    url: '/talent/attachment/download/' + attachmentId,
    method: 'get',
    responseType: 'blob'
  });
}

// 删除附件（逻辑删除，保留对象）
export function delAttachment(attachmentId: string | number) {
  return request({
    url: '/talent/attachment/' + attachmentId,
    method: 'delete'
  });
}
