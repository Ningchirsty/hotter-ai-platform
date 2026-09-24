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

/**
 * 项目的产品图视图（对齐后端 ICreativeProjectService.ProductImageView）。
 *
 * 产品图挂在产品主数据（cp_product.product_image）上，不属于任何单个任务；
 * 因此 fileId / sourceTaskId 可能是别的项目里的附件，页面要如实标注来源。
 */
export interface ProjectProductImageVO {
  productId?: string | number;
  productName?: string;
  /** 该产品是否已有产品图；只有 true 才能用于「产品图 | 生成图」并排对比 */
  configured?: boolean;
  /** 本项目里产品图角色的附件ID（可空：产品图来自别的项目） */
  fileId?: string | number;
  fileName?: string;
  /** 产品图来源任务（不是本项目时用于如实标注） */
  sourceTaskId?: string | number;
  setAt?: string;
  setBy?: string | number;
  /** 后端代理预览地址（未配置时为 null）；页面仍走 blob 助手取图，不直接用对象存储键 */
  previewUrl?: string;
  /** 可读说明（未配置时告诉人怎么补） */
  note?: string;
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
  /**
   * 以「产品图」为基准的质检结论（CONSISTENT / INCONSISTENT / UNCERTAIN）。
   *
   * 只提示、不自动筛除：产品图与生成图的差异很可能正是设计意图（换背景/换角度）。
   * 可能为 null（尚未质检或基准跑不起来）——页面必须如实显示「未质检」，不得当成通过。
   */
  productVerdict?: string;
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

// ------------------------------------------------------------------
// R2：逐屏生产
// ------------------------------------------------------------------

/** 单屏生产状态 */
export interface ScreenProductionVO {
  screenId: string | number;
  screenNo?: string;
  screenTypeDesc?: string;
  /** DRAFT/GENERATING/GENERATED/APPROVED/REJECTED */
  status?: string;
  candidateCount?: number;
  selectedGenerationId?: string | number;
  latestGenerationId?: string | number;
  latestStatus?: string;
  qaVerdict?: string;
  note?: string;
}

/** 一轮批量生产结果 */
export interface ProductionRunVO {
  submitted: number;
  skipped: number;
  screens: ScreenProductionVO[];
}

/** 屏生产状态 → 展示 */
export const SCREEN_STATUS_LABELS: Record<string, string> = {
  DRAFT: '未出图',
  GENERATING: '出图中',
  GENERATED: '已出图',
  APPROVED: '已选定',
  REJECTED: '待人工处理'
};

/** QA 结论 → 展示（不一致的候选是否已被筛除，看该屏的说明列） */
export const QA_VERDICT_LABELS: Record<string, string> = {
  CONSISTENT: '一致',
  INCONSISTENT: '不一致',
  UNCERTAIN: '无法判定（转人工）'
};

/**
 * 产品基准结论 → 展示。
 *
 * 与 QA_VERDICT_LABELS 的区别是「基准不同」：QA 比的是出图输入图，这条比的是产品图。
 * 只提示，不自动筛除。
 */
export const PRODUCT_VERDICT_LABELS: Record<string, string> = {
  CONSISTENT: '与产品图一致',
  INCONSISTENT: '与产品图有差异',
  UNCERTAIN: '无法判定（转人工）'
};

/**
 * 附件来源角色 → 展示（cp_task_file.source_type，R4 统一口径）。
 *
 * UPLOAD 人工上传 / REFERENCE 被引用为参考图 / PRODUCT 产品图 / GENERATED 系统生成。
 * 页面靠它把「产品图 / 参考图 / 生成图」标清楚，避免三种图混在一起分不出谁是谁。
 */
export const FILE_SOURCE_LABELS: Record<string, string> = {
  UPLOAD: '上传图',
  REFERENCE: '参考图',
  PRODUCT: '产品图',
  GENERATED: '生成图'
};

/** 附件来源角色 → Element Plus 标签类型 */
export const FILE_SOURCE_TYPES: Record<string, TagType> = {
  UPLOAD: 'info',
  REFERENCE: 'primary',
  PRODUCT: 'success',
  GENERATED: 'warning'
};

// ------------------------------------------------------------------
// R3：模板库与详情页排版
// ------------------------------------------------------------------

/** 视觉模板（含与渲染服务的实时对账） */
export interface DpLayoutTemplateVO {
  id: string | number;
  templateCode?: string;
  templateName?: string;
  templateType?: string;
  version?: string;
  htmlTemplateKey?: string;
  status?: string;
  statusDesc?: string;
  enabled?: string;
  remark?: string;
  registeredChecksum?: string;
  rendererChecksum?: string;
  checksumMatches?: boolean;
  rendererAvailable?: boolean;
  templateBytes?: number;
}

/** 详情页版本 */
export interface DpDetailPageVersionVO {
  id: string | number;
  version?: number;
  kind?: string;
  kindDesc?: string;
  renderedFileId?: string | number;
  pageWidth?: number;
  pageHeight?: number;
  screenCount?: number;
  status?: string;
  statusDesc?: string;
  reviewBy?: string | number;
  reviewAt?: string;
  reviewComment?: string;
  remark?: string;
  createTime?: string;
  previewable?: boolean;
}

/** 详情页 */
export interface DpDetailPageVO {
  id?: string | number;
  taskId?: string | number;
  currentVersion?: number;
  status?: string;
  statusDesc?: string;
  remark?: string;
  versions?: DpDetailPageVersionVO[];
  /** 还没有已选定产出的屏号 */
  screensWithoutSelection?: string[];
  rendererAvailable?: boolean;
  templateKey?: string;
}

/** 模板状态 → 展示 */
export const TEMPLATE_STATUS_LABELS: Record<string, string> = {
  DRAFT: '草稿（待发布）',
  PUBLISHED: '已发布',
  RETIRED: '已退役'
};

/** 详情页版本状态 → 展示 */
export const LAYOUT_VERSION_STATUS_LABELS: Record<string, string> = {
  RENDERED: '已渲染待终审',
  APPROVED: '已通过',
  REJECTED: '已打回'
};

/** 「按参考图推荐」结果 */
export interface DnaRecommendationVO {
  analyzed: boolean;
  imageName?: string;
  imageWidth?: number;
  imageHeight?: number;
  colorPrimary?: string;
  colorSecondary?: string;
  colorAccent?: string;
  colorBg?: string;
  saturation?: string;
  contrastLevel?: string;
  whitespaceLevel?: string;
  sceneType?: string;
  lightingType?: string;
  lightingDir?: string;
  productRatioMin?: number;
  productRatioMax?: number;
  observedProductRatio?: number;
  /** 逐字段依据：field / value / basis / reliability */
  evidence?: Array<{ field?: string; value?: string; basis?: string; reliability?: string }>;
  /** 与已确认事实的冲突 */
  conflicts?: string[];
  /** 测不出来、明确不猜的字段与原因 */
  skipped?: string[];
  notes?: string[];
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
