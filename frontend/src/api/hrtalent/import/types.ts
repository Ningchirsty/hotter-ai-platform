/**
 * 招聘数据导入 共享类型（招聘期限标准 / 月度招聘计划 通用）。
 *
 * 两段式：先 importPreview 预检（不写业务数据），再由人确认 importConfirm。
 * 预检结果里的 issues 是 error/warning 混合的逐行问题清单。
 */

/** 导入类型 */
export type RecruitImportType = 'standard' | 'plan';

/** 导入批次状态 */
export type RecruitImportBatchStatus =
  | 'pending'
  | 'confirming'
  | 'success'
  | 'partial_failed'
  | 'failed'
  | 'cancelled';

/** 一条导入问题（错误或提示） */
export interface RecruitImportIssueVO {
  errorId?: string | number;
  batchId?: string | number;
  sheetName?: string;
  /** Excel 中的行号（1 起，含表头行），可直接定位到表格 */
  rowNo?: number;
  /** 表头名 */
  fieldName?: string;
  /** 原始值（截断片段） */
  rawValue?: string;
  errorCode?: string;
  /** error 会被拦下不导入；warning 只提示，仍会导入 */
  severity?: 'error' | 'warning';
  errorMessage?: string;
}

/** 模板列说明 */
export interface RecruitImportColumnVO {
  header?: string;
  required?: boolean;
  hint?: string;
}

/** 预检结果 */
export interface RecruitImportPreviewVO {
  batchId?: string | number;
  batchNo?: string;
  importType?: RecruitImportType;
  importTypeName?: string;
  sourceFileName?: string;
  totalCount?: number;
  validCount?: number;
  errorCount?: number;
  warningCount?: number;
  columns?: RecruitImportColumnVO[];
  issues?: RecruitImportIssueVO[];
  /** 前若干行解析预览（键为表头名 + rowNo） */
  preview?: Record<string, any>[];
  message?: string;
}

/** 确认导入结果 */
export interface RecruitImportResultVO {
  batchId?: string | number;
  batchNo?: string;
  status?: RecruitImportBatchStatus;
  statusLabel?: string;
  totalCount?: number;
  successCount?: number;
  failureCount?: number;
  issues?: RecruitImportIssueVO[];
  messages?: string[];
}

/** 导入批次 */
export interface RecruitImportBatchVO {
  batchId?: string | number;
  batchNo?: string;
  importType?: RecruitImportType;
  importTypeName?: string;
  sourceFileName?: string;
  totalCount?: number;
  successCount?: number;
  failureCount?: number;
  status?: RecruitImportBatchStatus;
  statusLabel?: string;
  startedTime?: string;
  finishedTime?: string;
  operatorId?: string | number;
  operatorName?: string;
  remark?: string;
  createTime?: string;
}
