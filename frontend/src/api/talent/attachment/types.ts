/**
 * 人才附件（简历/证件）类型定义
 * 对齐后端 TlTalentAttachmentVo —— 不包含 objectKey / bucket / 预签名 URL
 */
export interface TalentAttachmentVO extends BaseEntity {
  attachmentId?: string | number;
  talentId?: string | number;
  attachmentType?: string;
  attachmentTypeLabel?: string;
  originalName?: string;
  fileExt?: string;
  fileSize?: number;
  fileSizeText?: string;
  version?: number;
  isCurrent?: string;
  scanStatus?: string;
  scanStatusLabel?: string;
  createByName?: string;
  /** 服务端判定的可下载标记（扫描状态 + 授权） */
  downloadable?: boolean;
}

/** 上传入参（multipart/form-data） */
export interface TalentAttachmentUploadForm {
  talentId: string | number;
  attachmentType: string;
  file: File;
}

/** 前端按人才筛选附件时使用的人才下拉项 */
export interface TalentAttachmentTalentOption {
  talentId: string | number;
  name?: string;
  talentNo?: string;
}
