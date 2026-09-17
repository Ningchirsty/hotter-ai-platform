/**
 * 视频创作模块 API 类型
 *
 * 与后端 `org.dromara.ai.video.controller.VideoCreationController` 一一对应。
 * 边界：类型里只有能力编码、workflowCode、字段白名单与展示信息，
 * 不含节点 ID、API Format JSON 或模型路径（那些只存在服务端）。
 */

/** 能力编码（与后端契约 capabilityCode 一致） */
export type VideoCapabilityCode = 'I2V' | 'T2V' | 'FL2V';

/** 任务状态（与后端 video_task.status 一致） */
export type VideoTaskStatus = 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELED' | 'TIMEOUT';

/**
 * 可提交工作流视图。
 *
 * `status` 为 DRAFT 时 `submittable`/`testable` 均为 false，前端必须禁用提交，
 * 不得用演示数据冒充可提交状态。
 */
export interface VideoWorkflowVO {
  workflowCode: string;
  capabilityCode: VideoCapabilityCode;
  modelCode?: string | null;
  version: string;
  status: 'DRAFT' | 'TESTING' | 'PUBLISHED' | 'RETIRED';
  /** 正式环境可提交（PUBLISHED） */
  submittable: boolean;
  /** 隔离联调环境可提交（PUBLISHED 或 TESTING） */
  testable: boolean;
  supportedTier?: string | null;
  supportedDuration?: string | null;
  maxDurationSeconds?: number | null;
}

/** 素材（上传素材或任务成片） */
export interface VideoAssetVO {
  id: number | string;
  assetType: 'IMAGE' | 'AUDIO' | 'VIDEO';
  sourceKind: 'UPLOAD' | 'OUTPUT';
  originalName?: string | null;
  contentType?: string | null;
  sizeBytes?: number | null;
  width?: number | null;
  height?: number | null;
  durationMs?: number | null;
  taskId?: number | string | null;
  createTime?: string | null;
}

/** 上传接口返回 */
export interface VideoUploadResult {
  assetId: number | string;
  contentType: string;
  sizeBytes: number;
}

/** 任务列表项 */
export interface VideoTaskVO {
  id: number | string;
  taskNo: string;
  taskName?: string | null;
  capabilityCode: VideoCapabilityCode;
  workflowCode: string;
  modelCode?: string | null;
  status: VideoTaskStatus;
  tier: string;
  durationSeconds: number;
  progress?: number | null;
  outputAssetId?: number | string | null;
  errorMessage?: string | null;
  createTime?: string | null;
  finishedTime?: string | null;
}

/** 任务事件（状态流转审计） */
export interface VideoTaskEventVO {
  sequence: number;
  eventType: string;
  detail?: string | null;
  createTime?: string | null;
}

/** 任务详情 */
export interface VideoTaskDetailVO extends VideoTaskVO {
  prompt?: string | null;
  inputJson?: string | null;
  comfyPromptId?: string | null;
  attemptCount?: number | null;
  outputWidth?: number | null;
  outputHeight?: number | null;
  outputFps?: number | null;
  outputDurationMs?: number | null;
  truncationApplied?: number | boolean | null;
  errorCode?: string | null;
  events?: VideoTaskEventVO[];
}

/**
 * 创建任务请求体。
 *
 * TypeScript 的键名与后端白名单一致；后端会再次校验，任何越界字段都会被拒绝。
 */
export interface VideoTaskCreateForm {
  capabilityCode: VideoCapabilityCode;
  workflowCode: string;
  /** 字段白名单：desc / tier / dur / img / first / last */
  fields: {
    desc?: string;
    tier?: string;
    dur?: string;
    /** 素材 ID，不是浏览器本地文件名 */
    img?: number | string;
    first?: number | string;
    last?: number | string;
  };
  taskName?: string;
  /** 幂等键，避免重复提交产生多份成片 */
  idempotencyKey?: string;
}

/** 执行结果 */
export interface VideoTaskExecutionResult {
  taskId: number | string;
  status: VideoTaskStatus;
  outputAssetId: number | string;
  truncated: boolean;
  width?: number | null;
  height?: number | null;
  fps?: number | null;
  durationMillis?: number | null;
}
