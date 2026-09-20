/**
 * 互动确认卡类型定义（对齐后端 CpInteractionCardVo / ContentCardQueryBo / ContentCardResolveBo）。
 *
 * 卡片是「发现问题 → 向对的人提问」的载体：证据、影响、选项都以 JSON 文本落库，
 * 前端解析后逐条展示，绝不由系统代人选值（SPEC §4.3）。
 */

/** 证据来源条目（evidenceJson 解析后的元素） */
export interface CardEvidenceItem {
  value?: string;
  sourceFileName?: string;
  locator?: string;
  excerpt?: string;
  snapshotId?: string | number;
}

/** 影响对象条目（impactJson 解析后的元素） */
export interface CardImpactItem {
  deliverableType?: string;
  deliverableTypeName?: string;
  fieldName?: string;
  note?: string;
}

/** 处理选项条目（optionsJson 解析后的元素） */
export interface CardOptionItem {
  /** CONFIRM / OTHER / SUPPLEMENT / BLOCK */
  option?: string;
  label?: string;
  value?: string | null;
  snapshotId?: string | number;
}

/** 互动卡列表行 */
export interface CpInteractionCardVO extends BaseEntity {
  cardId?: string | number;
  taskId?: string | number;
  taskNo?: string;
  taskName?: string;
  /** 卡片类型：MISSING / CONFLICT / APPROVAL / SUPPLEMENT / EXCEPTION */
  cardType?: string;
  fieldCode?: string;
  /** 一句话问题 */
  title?: string;
  /** 问题详细描述 */
  question?: string;
  /** 证据来源（JSON 文本） */
  evidenceJson?: string;
  /** 影响对象（JSON 文本） */
  impactJson?: string;
  /** 处理选项（JSON 文本） */
  optionsJson?: string;
  /** 闸门等级：BLOCK / CONDITION / NOTICE */
  gateLevel?: string;
  /** 是否阻断（Y/N） */
  blocking?: string;
  assigneeId?: string | number;
  assigneeName?: string;
  dueAt?: string;
  /** 状态：PENDING / RESOLVED / BLOCKED / CLOSED */
  status?: string;
  resolvedValue?: string;
  resolvedOption?: string;
  resolvedBy?: string | number;
  resolvedAt?: string;
  remark?: string;
  createByName?: string;
}

/** 卡片查询条件 */
export interface CpCardQuery extends PageQuery {
  taskId?: string | number;
  /** 任务号（模糊） */
  taskNo?: string;
  cardType?: string;
  status?: string;
  gateLevel?: string;
  assigneeId?: string | number;
  /** 只看待我处理 */
  mineOnly?: boolean;
  /** 只看阻断项 */
  blockingOnly?: boolean;
  params?: Record<string, any>;
}

/** 卡片处理表单 */
export interface CpCardResolveForm {
  cardId: string | number;
  /** CONFIRM（采用已有候选，需带 value 与 snapshotId）/ OTHER（填写其他值） / SUPPLEMENT（补充资料） / BLOCK（暂不确认并阻断） */
  option: string;
  value?: string;
  snapshotId?: string | number;
  comment?: string;
}
