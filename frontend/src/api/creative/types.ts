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

/** Visual DNA（视觉基因） */
export interface DpVisualDnaVO {
  id: string | number;
  taskId: string | number;
  dnaNo?: string;
  version?: number;
  status?: string;
  statusDesc?: string;
  subject?: string;
  styleKeywords?: string[];
  avoidKeywords?: string[];
  colors?: Record<string, string>;
  lighting?: Record<string, string>;
  productRatio?: Record<string, number>;
  saturation?: string;
  contrastLevel?: string;
  whitespaceLevel?: string;
  typographyStyle?: string;
  sceneType?: string;
  dnaJson?: string;
  /** AI / MANUAL / FACTS */
  source?: string;
  sourceDesc?: string;
  modelKey?: string;
  traceId?: string;
  evidence?: Array<Record<string, string>>;
  /** 非空表示不可锁定 */
  issues?: string[];
  approvedBy?: string | number;
  approvedAt?: string;
  remark?: string;
  createTime?: string;
  locked?: boolean;
}

/** 视觉基因编辑表单 */
export interface CreativeDnaForm {
  id?: string | number;
  styleKeywords?: string[];
  avoidKeywords?: string[];
  colorPrimary?: string;
  colorSecondary?: string;
  colorAccent?: string;
  colorBg?: string;
  saturation?: string;
  contrastLevel?: string;
  whitespaceLevel?: string;
  lightingType?: string;
  lightingDir?: string;
  productRatioMin?: number;
  productRatioMax?: number;
  typographyStyle?: string;
  sceneType?: string;
  remark?: string;
}

/** DNA 派生的提示词 */
export interface DnaPromptVO {
  prompt: string;
  negativePrompt: string;
  applied: string[];
}

/** 饱和度/对比度/留白档位 */
export const DNA_LEVEL_OPTIONS = [
  { value: 'LOW', label: '低' },
  { value: 'MEDIUM', label: '中' },
  { value: 'HIGH', label: '高' }
];

/** 光线类型 */
export const DNA_LIGHTING_TYPES = [
  { value: 'SOFT', label: '柔和散射光' },
  { value: 'HARD', label: '硬质方向光' },
  { value: 'STUDIO', label: '影棚布光' },
  { value: 'NATURAL', label: '自然光' }
];

/** 光位 */
export const DNA_LIGHTING_DIRS = [
  { value: 'FRONT', label: '正面光' },
  { value: 'SIDE', label: '侧光' },
  { value: 'TOP', label: '顶光' },
  { value: 'BACK', label: '背光/轮廓光' }
];

/** Element Plus 标签类型（限制成联合类型，模板里 :type 才通得过类型检查） */
export type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger';

/** 来源徽标颜色 */
export const DNA_SOURCE_TYPES: Record<string, TagType> = {
  AI: 'success',
  MANUAL: 'warning',
  FACTS: 'info'
};

/** 来源徽标文案 */
export const DNA_SOURCE_LABELS: Record<string, string> = {
  AI: '视觉模型',
  MANUAL: '人工编辑',
  FACTS: '事实推导'
};

// ------------------------------------------------------------------
// 视觉方向 / 分镜 / 视觉门
// ------------------------------------------------------------------

/** 视觉方向 */
export interface DpVisualDirectionVO {
  id: string | number;
  taskId: string | number;
  directionCode?: string;
  directionName?: string;
  concept?: string;
  strategy?: Record<string, unknown>;
  strategyJson?: string;
  differences?: string[];
  previewFileIds?: string[];
  status?: string;
  statusDesc?: string;
  sortNo?: number;
  source?: string;
  selectedBy?: string | number;
  selectedAt?: string;
  remark?: string;
  createTime?: string;
}

/** 分镜单屏 */
export interface DpStoryboardScreenVO {
  id: string | number;
  storyboardId: string | number;
  taskId: string | number;
  screenNo?: string;
  sortNo?: number;
  screenType?: string;
  screenTypeDesc?: string;
  title?: string;
  subtitle?: string;
  bodyText?: string;
  pictureSoloStatement?: string;
  spec?: Record<string, unknown>;
  specJson?: string;
  workflowCode?: string;
  productLockLevel?: string;
  status?: string;
  remark?: string;
}

/** 分镜版本 */
export interface DpStoryboardVO {
  id: string | number;
  taskId: string | number;
  storyboardNo?: string;
  version?: number;
  visualDirectionId?: string | number;
  visualDirectionName?: string;
  visualDnaId?: string | number;
  visualDnaVersion?: number;
  screenCount?: number;
  status?: string;
  statusDesc?: string;
  source?: string;
  sourceDesc?: string;
  approvedBy?: string | number;
  approvedAt?: string;
  remark?: string;
  createTime?: string;
  screens?: DpStoryboardScreenVO[];
}

/** 方向编辑表单 */
export interface CreativeDirectionForm {
  id: string | number;
  directionName?: string;
  concept?: string;
  remark?: string;
}

/** 分镜单屏编辑表单 */
export interface CreativeScreenForm {
  id: string | number;
  title?: string;
  subtitle?: string;
  bodyText?: string;
  pictureSoloStatement?: string;
  shot?: string;
  composition?: string;
  lighting?: string;
  background?: string;
  workflowCode?: string;
  productLockLevel?: string;
  remark?: string;
}

/** 视觉门准入项 */
export interface GateItem {
  code: string;
  label: string;
  /** BLOCK 硬性 / CONDITION 建议 */
  level: string;
  passed: boolean;
  detail: string;
}

/** 视觉门评估 */
export interface GateEvaluationVO {
  items: GateItem[];
  blocked: string[];
  submittable: boolean;
  passed: boolean;
  cardStatus?: string;
  cardId?: string | number;
  stage?: string;
  stageDesc?: string;
}

/** 方向来源 */
export const DIRECTION_SOURCE_LABELS: Record<string, string> = {
  TEMPLATE: '取舍模板派生',
  AI: '模型生成',
  MANUAL: '人工'
};

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
