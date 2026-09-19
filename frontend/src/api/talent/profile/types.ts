/**
 * 人才档案类型定义，字段名与后端 TlTalentVo / TlTalentBo / TlTalentQueryBo 对齐（camelCase）
 */
import type { TalentAttachmentVO } from '../attachment/types';

/** 当前用户对该条档案的权限（后端 TalentPermissionVo） */
export interface TalentPermissionVO {
  view?: boolean;
  download?: boolean;
  viewFullPhone?: boolean;
}

/** 列表行（TlTalentVo） */
export interface TalentVO extends BaseEntity {
  talentId?: string | number;
  talentNo?: string;
  name?: string;
  gender?: string;
  genderLabel?: string;
  birthDate?: string;
  /** 服务端按出生日期实时计算 */
  age?: number;
  ageOnly?: number;
  ageSourceDate?: string;
  education?: string;
  educationLabel?: string;
  expectSalaryMin?: number;
  expectSalaryMax?: number;
  /** 服务端格式化文本，如 8.5K */
  expectSalaryText?: string;
  position?: string;
  contactDate?: string;
  regionCode?: string;
  regionLabel?: string;
  status?: string;
  statusLabel?: string;
  shareScope?: string;
  source?: string;
  sourceLabel?: string;
  remark?: string;
  /** 服务端脱敏备份值（列表默认展示） */
  phoneMasked?: string;
  /** 完整手机号：仅命中 talent:profile:phone 权限时才由后端返回明文 */
  phone?: string;
  createByName?: string;
}

/** 联系跟进记录（TlTalentContactVo） */
export interface TalentContactVO extends BaseEntity {
  contactId?: string | number;
  talentId?: string | number;
  contactTime?: string;
  contactorId?: string | number;
  contactorName?: string;
  contactResult?: string;
  contactResultLabel?: string;
  content?: string;
}

/** 单条/临时访问授权（后端返回 TlTalentAccessGrant 实体） */
export interface TalentGrantVO extends BaseEntity {
  grantId?: string | number;
  talentId?: string | number;
  granteeType?: string;
  granteeId?: string | number;
  /** 逗号分隔多值：VIEW,DOWNLOAD,VIEW_FULL_PHONE */
  permission?: string;
  startTime?: string;
  endTime?: string;
  grantBy?: string | number;
  grantTime?: string;
  reason?: string;
  status?: string;
}

/** 详情（TlTalentDetailVo） */
export interface TalentDetailVO extends TalentVO {
  attachments?: TalentAttachmentVO[];
  contacts?: TalentContactVO[];
  permissions?: TalentPermissionVO;
}

/** 新增/编辑表单（TlTalentBo） */
export interface TalentForm {
  talentId?: string | number;
  name?: string;
  gender?: string;
  birthDate?: string;
  ageOnly?: number;
  ageSourceDate?: string;
  /** 明文手机号入参 */
  phone?: string;
  education?: string;
  expectSalaryMin?: number;
  expectSalaryMax?: number;
  position?: string;
  contactDate?: string;
  regionCode?: string;
  status?: string;
  shareScope?: string;
  source?: string;
  remark?: string;
  /** 重复预检确认标记：true 才允许继续入库 */
  duplicateConfirmed?: boolean;
  duplicateRemark?: string;
}

/** 查询条件（TlTalentQueryBo）：regionCode 仅作收窄筛选，数据范围由服务端决定 */
export interface TalentQuery extends PageQuery {
  name?: string;
  phoneTail4?: string;
  talentNo?: string;
  regionCode?: string;
  status?: string;
  education?: string;
  position?: string;
  source?: string;
  contactDateStart?: string;
  contactDateEnd?: string;
  duplicateOnly?: boolean;
  params?: Record<string, any>;
}

/** 归档（TlTalentArchiveBo） */
export interface TalentArchiveForm {
  talentId: string | number;
  remark?: string;
}

/** 联系记录新增（TlTalentContactBo） */
export interface TalentContactForm {
  contactId?: string | number;
  talentId?: string | number;
  contactTime?: string;
  contactResult?: string;
  content?: string;
}

/**
 * 简历导入单字段候选（ResumeFieldCandidateVo）：field/label/value/confidence/source/hint
 * source 取值 FILENAME（文件名）/ TEXT（正文）/ DEFAULT（默认值）
 */
export interface ResumeFieldCandidateVO {
  field?: string;
  label?: string;
  /** 文本形态的候选值，前端需写回表单再提交 */
  value?: string;
  /** 置信度 0-1，低于 0.8 前端需提示人工核对 */
  confidence?: number;
  source?: string;
  hint?: string;
}

/** 简历导入预览结果（ResumeImportPreviewVo） */
export interface ResumeImportPreviewVO {
  /** 服务端临时文件令牌，确认时原样回传；不落库 */
  importToken?: string;
  originalName?: string;
  ext?: string;
  size?: number;
  /** 是否成功提取出正文文本；图片型简历为 false */
  textExtracted?: boolean;
  textLength?: number;
  candidates?: ResumeFieldCandidateVO[];
  warnings?: string[];
}

/**
 * 简历导入确认表单（ResumeImportConfirmBo）
 * 服务端不会回填未提交的抽取值，因此前端必须先把预览值写入 talent 再提交。
 */
export interface ResumeImportConfirmForm {
  importToken: string;
  /** 复用新增主档入参；email/experienceText 非主档字段，需拼入 remark 提交 */
  talent: TalentForm;
}

/** 授权新增（TlAccessGrantBo） */
export interface TalentGrantForm {
  grantId?: string | number;
  talentId?: string | number;
  granteeType?: string;
  granteeId?: string | number;
  permissions?: string[];
  startTime?: string;
  endTime?: string;
  reason?: string;
}
