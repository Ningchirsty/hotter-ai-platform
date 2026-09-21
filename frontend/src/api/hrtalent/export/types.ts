/**
 * 人才导出任务 域类型定义（SPEC-P4 §2.6 F 线、设计文档 §8.20）
 *
 * 契约来源（按优先级）：
 * 1. 后端已实现类：
 *    - TalentExportController（POST /talent/profiles/export、GET /talent/exports、GET /talent/exports/{id}/download）
 *    - TalentExportCreateBo、TalentExportQueryBo、TalentExportTaskVo、TalentExportRowVo
 *    - TalentExportServiceImpl（字段白名单、状态取值、用途校验）
 * 2. DDL：script/sql/hr_talent.sql（hr_talent_export_task）
 *
 * 安全口径（§8.13、§11.1）：
 * - 导出文件存**私有对象存储**，**不导出对象存储永久地址**；VO 只给系统内受控下载地址 downloadApi；
 * - 敏感台账需要**独立权限** `talent:profile:phone-view` 与**非空用途**，为空时先写 denied 审计再拒绝；
 * - 导出为**异步任务或限制最大条数**，页面不得假设立即可下载，须按任务状态展示。
 */

/* ============================ 枚举与常量 ============================ */

/** 导出类型（对齐 TalentExportServiceImpl.EXPORT_TYPE_*） */
export type TalentExportType = 'normal' | 'sensitive';

/** 导出任务状态（对齐 TalentExportTaskVo JavaDoc：pending/running/success/failed/expired） */
export type TalentExportStatus = 'pending' | 'running' | 'success' | 'failed' | 'expired';

/**
 * 导出字段白名单（与后端 `TalentExportServiceImpl#resolveFields` 的 NORMAL_FIELDS /
 * SENSITIVE_EXTRA_FIELDS **逐字一致**；非白名单字段后端会抛「不支持的导出字段」）。
 */
export const EXPORT_FIELD_OPTIONS = {
  /** 普通台账字段（顺序即后端默认列顺序） */
  normal: [
    { value: 'talentNo', label: '人才编号' },
    { value: 'name', label: '姓名（脱敏）' },
    { value: 'positionDirection', label: '岗位方向' },
    { value: 'education', label: '学历' },
    { value: 'region', label: '区域' },
    { value: 'talentStatus', label: '状态' },
    { value: 'tags', label: '标签' },
    { value: 'owner', label: '负责人' }
  ],
  /** 敏感台账额外允许的字段（需独立权限与用途） */
  sensitiveExtra: [
    { value: 'phone', label: '联系方式' },
    { value: 'email', label: '邮箱' },
    { value: 'salary', label: '薪资' },
    { value: 'profileAccess', label: '档案受控访问地址' },
    { value: 'resumeAccess', label: '简历受控访问地址' }
  ]
} as const;

/* ============================ 导出任务 ============================ */

/** 导出任务视图（TalentExportTaskVo，**不含** oss_id 与任何对象存储地址） */
export interface HrTalentExportTaskVO extends BaseEntity {
  /** 导出任务ID */
  taskId?: string | number;
  /** 任务编号 */
  taskNo?: string;
  /** 导出类型（normal/sensitive） */
  exportType?: string;
  /** 导出类型中文兜底（后端只读 getter） */
  exportTypeText?: string;
  /** 筛选条件快照（结构化 JSON，不含敏感明文） */
  scopeJson?: string;
  /** 导出字段清单快照（结构化 JSON） */
  fieldsJson?: string;
  /** 导出人用户ID */
  exportedBy?: string | number;
  /** 导出人昵称（@Translation 回填） */
  exportedByName?: string;
  /** 导出用途/原因（敏感台账必填） */
  purpose?: string;
  /** 导出记录数 */
  recordCount?: number;
  /** 结果文件名（仅用于展示与响应头，不含对象存储信息） */
  fileName?: string;
  /** 任务状态（pending/running/success/failed/expired） */
  status?: string;
  /** 任务状态中文兜底（后端只读 getter） */
  statusText?: string;
  /** 失败原因（技术性中文说明） */
  failureReason?: string;
  /** 结果文件过期时间（到期不可下载） */
  expireTime?: string;
  /** 完成时间 */
  finishedTime?: string;
  /** 创建时间 */
  createTime?: string;
  /** 系统内受控下载地址；仅任务成功且未过期时由服务端填充，其余为 null */
  downloadApi?: string;
}

/**
 * 创建导出入参（TalentExportCreateBo）
 *
 * filters 与 `GET /talent/profiles` 复用同一个查询 BO（TalentProfileQueryBo），
 * 因此直接传人才检索页的筛选条件即可，保证「搜得到的」与「导出的」口径一致。
 */
export interface HrTalentExportCreateForm {
  /** 导出类型：normal 普通台账 / sensitive 敏感台账 */
  exportType: TalentExportType;
  /** 筛选条件快照（与人才列表检索 BO 同构；不传表示不加筛选） */
  filters?: Record<string, any>;
  /** 字段清单（服务端白名单；为空数组或不传表示取该类型默认字段集） */
  fields?: string[];
  /** 导出用途（敏感台账必填，最长 255；非空校验在服务端） */
  purpose?: string;
  /** 备注（最长 500） */
  remark?: string;
}

/** 导出任务检索条件（TalentExportQueryBo + PageQuery） */
export interface HrTalentExportQuery extends PageQuery {
  /** 导出类型（normal/sensitive） */
  exportType?: string;
  /** 任务状态（pending/running/success/failed/expired） */
  status?: string;
  /** 导出人用户ID */
  exportedBy?: string | number;
  /** 创建日期起（yyyy-MM-dd） */
  createDateBegin?: string;
  /** 创建日期止（yyyy-MM-dd） */
  createDateEnd?: string;
}

/** 受控下载入参（query 参数 purpose；为空时服务端写 denied 审计并拒绝） */
export interface HrTalentExportDownloadForm {
  /** 下载用途（必填，最长 255） */
  purpose: string;
}
