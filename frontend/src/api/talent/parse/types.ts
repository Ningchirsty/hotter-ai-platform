/**
 * 简历解析复核类型定义（对齐 TlParseTaskVo / TlParseFieldVo / TlParseFieldConfirmBo）
 */
export interface ParseFieldVO extends BaseEntity {
  fieldId?: string | number;
  fieldName?: string;
  parsedValue?: string;
  /** 置信度 0-1 */
  confidence?: number;
  confirmedValue?: string;
  /** 0待确认 1已确认 2已忽略 */
  confirmStatus?: string;
  confirmBy?: string | number;
  confirmByName?: string;
  confirmTime?: string;
}

export interface ParseTaskVO extends BaseEntity {
  taskId?: string | number;
  talentId?: string | number;
  attachmentId?: string | number;
  attachmentName?: string;
  /** DISABLED/PENDING/PROCESSING/SUCCESS/FAILED */
  status?: string;
  statusLabel?: string;
  parserVersion?: string;
  retryCount?: number;
  errorSummary?: string;
  startTime?: string;
  finishTime?: string;
  fields?: ParseFieldVO[];
}

/** 字段人工确认（TlParseFieldConfirmBo） */
export interface ParseFieldConfirmForm {
  fieldId: string | number;
  confirmedValue?: string;
  confirmStatus?: string;
}

/** 提交确认请求体：taskId + 待确认字段清单 */
export interface ParseConfirmForm {
  taskId: string | number;
  fields: ParseFieldConfirmForm[];
}
