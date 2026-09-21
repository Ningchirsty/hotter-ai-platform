/**
 * 敏感操作审计 域类型定义（SPEC-P3 §2.5 / §3.6，设计文档 §8.7、§15.3）
 *
 * 契约来源（按优先级）：
 * 1. 后端已实现类：SensitiveAuditController（/recruit/audits、/recruit/audits/export）
 *    + RecruitSensitiveAuditVo、RecruitSensitiveAuditQueryBo
 * 2. 编码字典来源：support/SensitiveAuditRecorder 的 EVENT_/BIZ_/RESULT_ 常量
 *    （后端未为这三类编码定义 sys_dict 组，故页面用下方镜像映射展示中文，不新造编码）
 *
 * 审计表是**追加型**（只插入、不修改、不物理删除），因此本域只有查询与导出两个只读接口。
 */

/* ============================ 编码镜像 ============================ */

/** 事件类型中文（逐字对齐 SensitiveAuditRecorder.EVENT_*，后端无字典组） */
export const AUDIT_EVENT_TYPE_TEXT: Record<string, string> = {
  phone_view: '电话明文查看',
  background_view: '背调明细查看',
  attachment_preview: '附件预览',
  attachment_download: '附件下载',
  attachment_delete: '附件删除',
  export: '数据导出',
  stage_override: '管理员例外跳转'
};

/** 业务类型中文（逐字对齐 SensitiveAuditRecorder.BIZ_*，后端无字典组） */
export const AUDIT_BIZ_TYPE_TEXT: Record<string, string> = {
  talent: '人才',
  application: '应聘记录',
  interview: '面试',
  background: '背调',
  attachment: '附件',
  audit: '审计',
  offer: '录用邀约',
  follow_up: '人才跟进'
};

/** 操作结果中文（逐字对齐 SensitiveAuditRecorder.RESULT_*，后端无字典组） */
export const AUDIT_RESULT_TEXT: Record<string, string> = {
  success: '成功',
  denied: '已拒绝',
  failed: '失败'
};

/* ============================ 视图与查询 ============================ */

/**
 * 敏感操作审计视图（RecruitSensitiveAuditVo）
 *
 * 审计记录本身**不含**电话明文、简历正文与背调明细，因此可按 `recruit:audit:list` 直接返回；
 * `detailJson` 为脱敏后的附加明细快照。
 */
export interface HrSensitiveAuditVO extends BaseEntity {
  /** 审计ID */
  auditId?: string | number;
  /** 事件类型（见 AUDIT_EVENT_TYPE_TEXT） */
  eventType?: string;
  /** 业务类型（见 AUDIT_BIZ_TYPE_TEXT） */
  bizType?: string;
  /** 业务对象ID */
  bizId?: string | number;
  /** 操作人用户ID */
  operatorId?: string | number;
  /** 操作人账号 */
  operatorName?: string;
  /** 操作事由 / 用途 */
  purpose?: string;
  /** 操作结果（见 AUDIT_RESULT_TEXT） */
  result?: string;
  /** 客户端IP */
  ip?: string;
  /** 客户端 User-Agent */
  userAgent?: string;
  /** 附加明细快照（脱敏 JSON，禁止包含联系方式与背调明细） */
  detailJson?: string;
  /** 事件时间 */
  eventTime?: string;
  /** 事件时间文本（导出用） */
  eventTimeText?: string;
  /** 创建人用户ID */
  createBy?: string | number;
  /** 创建人昵称（@Translation 回填） */
  createByName?: string;
}

/**
 * 审计检索条件（RecruitSensitiveAuditQueryBo + PageQuery）
 *
 * **导出复用同一查询对象**：`GET /recruit/audits/export` 接受同一 BO 作为查询串，
 * 因此把当前筛选条件原样传给导出即可保证「看到什么就导出什么」。
 */
export interface HrSensitiveAuditQuery extends PageQuery {
  /** 事件类型（见 AUDIT_EVENT_TYPE_TEXT） */
  eventType?: string;
  /** 业务类型（见 AUDIT_BIZ_TYPE_TEXT） */
  bizType?: string;
  /** 业务对象ID */
  bizId?: string | number;
  /** 操作人用户ID */
  operatorId?: string | number;
  /** 操作结果（见 AUDIT_RESULT_TEXT） */
  result?: string;
  /** 事件时间起（yyyy-MM-dd HH:mm:ss） */
  eventTimeBegin?: string;
  /** 事件时间止（yyyy-MM-dd HH:mm:ss） */
  eventTimeEnd?: string;
}
