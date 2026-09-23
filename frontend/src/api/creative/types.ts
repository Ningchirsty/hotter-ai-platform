/**
 * AI 视觉工厂类型定义（对齐后端 CreativeProjectVo / DpGenerationVo / DpStageEventVo）。
 *
 * 项目本体是内容协同的 cp_task，因此这里不重复定义任务结构，只定义
 * 「视觉阶段 + 视觉侧计数」这部分增量，附件类型直接复用内容模块。
 */

/** 视觉项目（= ECOM_DETAIL 的内容协同任务 + 视觉阶段） */
export interface CreativeProjectVO {
  taskId?: string | number;
  taskNo?: string;
  taskName?: string;
  deliverableType?: string;
  productId?: string | number;
  productName?: string;
  productCode?: string;
  skuCode?: string;
  ownerId?: string | number;
  ownerName?: string;
  /** 内容协同任务状态（cp_task.status） */
  status?: string;
  blockReason?: string;
  /** 视觉阶段编码（cp_task.visual_stage） */
  visualStage?: string;
  /** 视觉阶段描述 */
  visualStageDesc?: string;
  pendingCardCount?: number;
  blockingCardCount?: number;
  /** 参考图数量（详情接口返回） */
  imageFileCount?: number;
  /** 出图候选数（详情接口返回） */
  generationCount?: number;
  deadline?: string;
  createTime?: string;
}

/** 新建视觉项目表单 */
export interface CreativeProjectForm {
  taskName?: string;
  productId?: string | number;
  skuCode?: string;
  ownerId?: string | number;
  ownerName?: string;
  deadline?: string;
  remark?: string;
}

/** 视觉项目查询条件 */
export interface CreativeProjectQuery extends PageQuery {
  queryTaskName?: string;
  queryStatus?: string;
  queryOwnerId?: string | number;
}

/** 出图候选 */
export interface DpGenerationVO {
  id: string | number;
  taskId: string | number;
  screenId?: string | number;
  candidateNo?: number;
  workflowCode?: string;
  workflowVersion?: string;
  prompt?: string;
  status?: string;
  statusDesc?: string;
  outputAssetId?: string | number;
  outputWidth?: number;
  outputHeight?: number;
  qaVerdict?: string;
  errorCode?: string;
  errorMessage?: string;
  durationMs?: number;
  gpuNode?: string;
  previewable?: boolean;
  retryable?: boolean;
  createTime?: string;
}

/** HERO 主图出图请求 */
export interface CreativeHeroForm {
  fileId?: string | number;
  prompt?: string;
  negativePrompt?: string;
  sizeLabel?: string;
  strengthLabel?: string;
  workflowCode?: string;
  count?: number;
}

/** 阶段事件（全链路可追溯） */
export interface DpStageEventVO {
  id: string | number;
  taskId: string | number;
  eventType?: string;
  fromStage?: string;
  toStage?: string;
  action?: string;
  detailJson?: string;
  actorId?: string | number;
  actorName?: string;
  createTime?: string;
}

/** 可用的出图工作流 */
export interface CreativeWorkflowVO {
  workflowCode: string;
  capabilityCode?: string;
  modelCode?: string;
  version?: string;
  status?: string;
  defaultSize?: string;
  defaultStrength?: string;
  published?: boolean;
}

/** 阶段编码 → 可读描述（前端兜底；权威描述在后端 DpVisualStageEnum） */
export const CREATIVE_STAGE_LABELS: Record<string, string> = {
  MATERIAL_READY: '资料就绪',
  DNA_GENERATING: '基因生成中',
  DNA_REVIEW: '基因待确认',
  DNA_LOCKED: '基因已锁定',
  DIRECTION_GENERATING: '方向生成中',
  DIRECTION_REVIEW: '方向待选定',
  DIRECTION_LOCKED: '方向已选定',
  STORYBOARD_GENERATING: '分镜生成中',
  STORYBOARD_REVIEW: '分镜待确认',
  STORYBOARD_LOCKED: '分镜已锁定',
  VISUAL_GATE: '视觉门审核中',
  VISUAL_LOCKED: '视觉已锁定',
  PRODUCING: '出图中',
  QA_PROCESSING: '质检中',
  LAYOUT_PROCESSING: '排版中',
  V08_READY: '机排版完成',
  DESIGN_REFINING: '人工精修中',
  FINAL_REVIEW: '终审中',
  COMPLETED: '已完成'
};

/** 阶段徽标色彩（与状态语义一致：进行中=蓝、待人工=橙、完成=绿） */
export const CREATIVE_STAGE_TYPES: Record<string, string> = {
  MATERIAL_READY: 'info',
  DNA_GENERATING: 'primary',
  DNA_REVIEW: 'warning',
  DNA_LOCKED: 'success',
  DIRECTION_GENERATING: 'primary',
  DIRECTION_REVIEW: 'warning',
  DIRECTION_LOCKED: 'success',
  STORYBOARD_GENERATING: 'primary',
  STORYBOARD_REVIEW: 'warning',
  STORYBOARD_LOCKED: 'success',
  VISUAL_GATE: 'warning',
  VISUAL_LOCKED: 'success',
  PRODUCING: 'primary',
  QA_PROCESSING: 'primary',
  LAYOUT_PROCESSING: 'primary',
  V08_READY: 'success',
  DESIGN_REFINING: 'warning',
  FINAL_REVIEW: 'warning',
  COMPLETED: 'success'
};

/** 候选状态 → 可读描述 */
export const GENERATION_STATUS_LABELS: Record<string, string> = {
  QUEUED: '排队中',
  RUNNING: '出图中',
  SUCCEEDED: '已出图',
  FAILED: '失败',
  CANCELED: '已取消',
  TIMEOUT: '超时',
  REJECTED: '已筛除',
  APPROVED: '已选定'
};

/** 候选状态 → Element Plus 标签类型 */
export const GENERATION_STATUS_TYPES: Record<string, string> = {
  QUEUED: 'info',
  RUNNING: 'primary',
  SUCCEEDED: 'success',
  FAILED: 'danger',
  CANCELED: 'info',
  TIMEOUT: 'danger',
  REJECTED: 'warning',
  APPROVED: 'success'
};
