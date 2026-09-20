/**
 * 产品事实快照类型定义（对齐后端 CpFactSnapshotVo / ContentFactManualBo）。
 *
 * 事实只能来自经确认的产品资料：解析结果一律以 PENDING 落库，
 * 由人确认后才成为 CONFIRMED（SPEC §0.1 红线）。
 */

/** 事实行 */
export interface CpFactSnapshotVO extends BaseEntity {
  snapshotId?: string | number;
  taskId?: string | number;
  /** 快照版本，每次确认递增 */
  snapshotVersion?: number;
  /** 事实字段编码，如 product_height */
  fieldCode?: string;
  fieldName?: string;
  fieldValue?: string;
  unit?: string;
  sourceFileId?: string | number;
  /** 来源文件名（联表带出） */
  sourceFileName?: string;
  /** 来源定位，如「产品参数表 V2 第 3 行」 */
  sourceLocator?: string;
  /** 原文摘录（证据，用户可见） */
  sourceExcerpt?: string;
  /** 解析置信度 */
  confidence?: number | string;
  /** 确认状态：PENDING / CONFIRMED / CONFLICT / REJECTED */
  confirmStatus?: string;
  confirmedBy?: string | number;
  confirmedAt?: string;
  remark?: string;
  createByName?: string;
}

/** 人工录入事实表单 */
export interface CpFactManualForm {
  taskId: string | number;
  fieldCode: string;
  value: string;
  remark?: string;
}
