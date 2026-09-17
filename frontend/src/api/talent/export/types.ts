/**
 * Excel 异步导出任务类型定义（对齐 TlExportTaskVo / TlExportCreateBo）
 * 注意：VO 不返回 objectKey / bucket / 预签名 URL
 */
export interface ExportTaskVO extends BaseEntity {
  exportId?: string | number;
  taskName?: string;
  /** PENDING/RUNNING/SUCCESS/FAILED/EXPIRED */
  status?: string;
  statusLabel?: string;
  fileName?: string;
  rowCount?: number;
  errorSummary?: string;
  expireTime?: string;
  finishTime?: string;
  createByName?: string;
  expired?: boolean;
}

/** 导出任务的查询条件快照（与 TlTalentQueryBo 一致） */
export interface ExportQuerySnapshot {
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
}

export interface ExportTaskQuery extends PageQuery {
  taskName?: string;
  status?: string;
  params?: Record<string, any>;
}

/** 创建导出任务（TlExportCreateBo） */
export interface ExportCreateForm extends ExportQuerySnapshot {
  taskName: string;
}
