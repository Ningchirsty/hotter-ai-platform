/**
 * 内容生产任务类型定义（对齐后端 CpTaskVo / ContentTaskBo / ContentTaskDetailVo）。
 *
 * 详情接口一次给全「任务 + 附件 + 事实 + 卡片 + 作业 + 闸门结论 + 开工包」，
 * 前端不为一个详情页打五六个接口（设计文档 §18.1 把交互成本列为验收指标）。
 */
import type { CpInteractionCardVO } from '../card/types';
import type { CpFactSnapshotVO } from '../fact/types';
import type { CpGateRuleVO } from '../gateRule/types';
import type { CpWorkPackageVO } from '../workPackage/types';

/** 任务列表/详情主体 */
export interface CpTaskVO extends BaseEntity {
  taskId?: string | number;
  /** 任务号，唯一 */
  taskNo?: string;
  taskName?: string;
  /** 交付类型（cp_deliverable_type） */
  deliverableType?: string;
  productId?: string | number;
  productName?: string;
  productCode?: string;
  skuCode?: string;
  deadline?: string;
  /** 任务负责人（互动卡默认指派人） */
  ownerId?: string | number;
  ownerName?: string;
  /** 资料敏感级别（aig_data_level） */
  dataLevel?: string;
  /** 是否允许外部AI（Y/N），阶段1A 一律仅本地 */
  allowExternal?: string;
  /** 任务状态（cp_task_status） */
  status?: string;
  /** 当前阻断原因（闸门写入） */
  blockReason?: string;
  parseDoneAt?: string;
  /** 待处理互动卡数量 */
  pendingCardCount?: number;
  /** 阻断卡数量 */
  blockingCardCount?: number;
  remark?: string;
  createByName?: string;
}

/** 任务新增/编辑表单 */
export interface CpTaskForm {
  taskId?: string | number;
  taskName?: string;
  deliverableType?: string;
  productId?: string | number;
  skuCode?: string;
  deadline?: string;
  ownerId?: string | number;
  ownerName?: string;
  dataLevel?: string;
  remark?: string;
}

/** 任务查询条件 */
export interface CpTaskQuery extends PageQuery {
  queryTaskNo?: string;
  queryTaskName?: string;
  queryDeliverableType?: string;
  queryStatus?: string;
  queryOwnerId?: string | number;
  productId?: string | number;
  params?: Record<string, any>;
}

/** 任务附件 */
export interface CpTaskFileVO extends BaseEntity {
  fileId?: string | number;
  taskId?: string | number;
  fileName?: string;
  fileExt?: string;
  fileSize?: number;
  /** 文件引用（业务库不存文件本体） */
  fileRef?: string;
  /** 文件类型（cp_file_kind） */
  fileKind?: string;
  /** UPLOAD / REFERENCE */
  sourceType?: string;
  dataLevel?: string;
  /** 解析状态（cp_parse_status） */
  parseStatus?: string;
  /** 失败/跳过的可读原因 */
  parseMessage?: string;
  parsedTextRef?: string;
  remark?: string;
  createByName?: string;
}

/** 异步作业 */
export interface CpAsyncJobVO {
  jobId?: string | number;
  taskId?: string | number;
  /** PARSE / PRECHECK / PACKAGE */
  jobType?: string;
  /** QUEUED / RUNNING / SUCCESS / FAILED */
  status?: string;
  progress?: number;
  message?: string;
  startedAt?: string;
  finishedAt?: string;
}

/** 闸门判定结论（对齐后端 ContentGateEngine.GateResult） */
export interface ContentGateResult {
  status?: string;
  blockReason?: string;
  /** 未满足的强制阻断项 */
  blockUnsatisfied?: CpGateRuleVO[];
  /** 未满足的条件项 */
  conditionUnsatisfied?: CpGateRuleVO[];
  /** 非阻断提醒项（不参与流转判定） */
  notices?: CpGateRuleVO[];
}

/** 任务详情 */
export interface ContentTaskDetailVO {
  task?: CpTaskVO;
  files?: CpTaskFileVO[];
  facts?: CpFactSnapshotVO[];
  cards?: CpInteractionCardVO[];
  jobs?: CpAsyncJobVO[];
  gate?: ContentGateResult;
  workPackage?: CpWorkPackageVO;
}

/** 附件上传表单（multipart，表单字段名固定为 file） */
export interface TaskFileUploadForm {
  taskId: string | number;
  /** 该文件的数据等级（可空，可高于任务等级） */
  dataLevel?: string;
  file: File;
}

/** 产品主数据 → 产品事实 的同步结果 */
export interface ContentFactSyncVO {
  /** 写入为已确认事实的条数 */
  synced?: number;
  /** 已有同值已确认事实、无需处理的条数 */
  skipped?: number;
  /** 与既有取值冲突、只落为待确认候选的条数 */
  conflicts?: number;
  /** 逐条说明 */
  messages?: string[];
}
