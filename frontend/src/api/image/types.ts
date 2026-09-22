/**
 * 图像创作模块 API 类型。
 *
 * 与后端 `org.dromara.ai.image.controller.ImageCreationController` 一一对应。
 *
 * 安全边界（与视频模块同一章程）：前端只持有能力编码、workflowCode、字段白名单与档位选项；
 * **节点 ID、API Format JSON、模型路径一律不在前端类型里出现**，
 * 完整契约见后端工件 `script/image/workflows/image-workflow-contracts.json`。
 */

/** 图像创作能力编码。 */
export type ImageCapabilityCode = 'T2I' | 'I2I' | 'EDIT' | 'BGREMOVE' | 'WHITEBG';

/** 图像任务状态。 */
export type ImageTaskStatus = 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELED' | 'TIMEOUT';

/** 工作流发布状态。DRAFT / RETIRED 不可提交。 */
export type ImageWorkflowStatus = 'DRAFT' | 'TESTING' | 'PUBLISHED' | 'RETIRED';

/** size 档位（文生图）。 */
export interface ImageSizeOption {
  label: string;
  width: number;
  height: number;
}

/** 工作流视图（`GET /image/capabilities`）。 */
export interface ImageWorkflowVO {
  workflowCode: string;
  capabilityCode: ImageCapabilityCode;
  modelCode?: string | null;
  version: string;
  status: ImageWorkflowStatus;
  /** 服务端判定的可提交性：只有 PUBLISHED 为 true。 */
  submittable: boolean;
  /** 是否可用于隔离联调（PUBLISHED 或 TESTING）。 */
  testable: boolean;
  /** 该能力是否必须输出带 alpha 的 PNG（抠图）。 */
  requireAlpha: boolean;
  maxPixels: number;
  defaultSize?: string | null;
  sizes: ImageSizeOption[];
  defaultStrength?: string | null;
  strengths: string[];
}

/** 素材视图。 */
export interface ImageAssetVO {
  id: number | string;
  assetType: 'IMAGE';
  sourceKind: 'UPLOAD' | 'OUTPUT';
  originalName?: string | null;
  contentType?: string | null;
  sizeBytes?: number | null;
  width?: number | null;
  height?: number | null;
  hasAlpha?: number | boolean | null;
  taskId?: number | string | null;
  createTime?: string | null;
}

/** 上传结果。 */
export interface ImageUploadResult {
  assetId: number | string;
  contentType: string;
  sizeBytes: number;
}

/** 任务视图。 */
export interface ImageTaskVO {
  id: number | string;
  taskNo: string;
  taskName?: string | null;
  capabilityCode: string;
  workflowCode: string;
  modelCode?: string | null;
  status: ImageTaskStatus;
  sizeLabel?: string | null;
  strengthLabel?: string | null;
  progress?: number | null;
  outputAssetId?: number | string | null;
  outputWidth?: number | null;
  outputHeight?: number | null;
  outputHasAlpha?: number | boolean | null;
  outputSizeBytes?: number | null;
  errorCode?: string | null;
  errorMessage?: string | null;
  createTime?: string | null;
  finishedTime?: string | null;
}

/** 任务事件。 */
export interface ImageTaskEventVO {
  sequence: number;
  eventType: string;
  detail?: string | null;
  createTime?: string | null;
}

/** 任务详情。 */
export interface ImageTaskDetailVO extends ImageTaskVO {
  prompt?: string | null;
  negativePrompt?: string | null;
  inputJson?: string | null;
  comfyPromptId?: string | null;
  attemptCount?: number | null;
  events?: ImageTaskEventVO[];
}

/** 创建任务的字段（只允许契约声明的键）。 */
export interface ImageTaskFields {
  prompt?: string;
  negative_prompt?: string;
  size?: string;
  strength?: string;
  img?: number | string;
  image1?: number | string;
  image2?: number | string;
  image3?: number | string;
}

/** 创建任务表单。 */
export interface ImageTaskCreateForm {
  capabilityCode: ImageCapabilityCode;
  workflowCode: string;
  taskName?: string;
  idempotencyKey?: string;
  fields: ImageTaskFields;
}

/** 执行结果。 */
export interface ImageTaskExecutionResult {
  taskId: number | string;
  status: ImageTaskStatus;
  accepted?: boolean;
  outputAssetId?: number | string | null;
  width?: number | null;
  height?: number | null;
  hasAlpha?: boolean | null;
  errorCode?: string | null;
  errorMessage?: string | null;
}
