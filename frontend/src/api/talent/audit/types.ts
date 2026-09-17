/**
 * 敏感操作审计类型定义（对齐 TlSensitiveAuditVo）
 */
export interface SensitiveAuditVO {
  auditId?: string | number;
  operatorId?: string | number;
  operatorName?: string;
  /** VIEW_DETAIL/VIEW_FULL_PHONE/DOWNLOAD/EXPORT/CREATE_GRANT/DELETE/ARCHIVE/UPLOAD */
  action?: string;
  actionLabel?: string;
  /** TALENT/ATTACHMENT/EXPORT/GRANT/PARSE_TASK */
  targetType?: string;
  targetId?: string | number;
  talentId?: string | number;
  /** 0成功 1失败 */
  result?: string;
  reason?: string;
  ipDigest?: string;
  userAgentDigest?: string;
  operateTime?: string;
}

export interface SensitiveAuditQuery extends PageQuery {
  operatorName?: string;
  action?: string;
  targetType?: string;
  targetId?: string | number;
  talentId?: string | number;
  result?: string;
  params?: Record<string, any>;
}
