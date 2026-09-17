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
  /**
   * 允许的输出档位（清晰度）。由契约 `fixedFieldValidation.supportedTiers` 声明。
   *
   * 空数组时退化为只用 `supportedTier` 单一档位，保证旧后端兼容。
   */
  supportedTiers?: string[] | null;
  /**
   * 各档位允许的时长，形如 `{ '标清 · 480P': ['5 秒','10 秒','20 秒'] }`。
   *
   * 时长与档位互相约束：H3 的帧数随时长线性增长，显存与耗时显著上升，
   * 因此长时长只在低分辨率档位开放。取不到时前端退回内置兜底值。
   */
  supportedDurationsByTier?: Record<string, string[]> | null;
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
  /**
   * 承担本次生成的工作节点（GPU 实例名）。多卡部署时用于排障：
   * 同一个 prompt_id 只在提交它的那台 ComfyUI 进程里可查。
   */
  comfyWorker?: string | null;
  createTime?: string | null;
  finishedTime?: string | null;
}

/** 一个 ComfyUI 工作节点（一张卡）的实时状态 */
export interface VideoWorkerVO {
  name: string;
  baseUrl: string;
  busy: boolean;
  unavailable: boolean;
  reason?: string | null;
  cooldownSecondsLeft: number;
}

/** GPU 工作节点与执行队列的实时状态 */
export interface VideoWorkersVO {
  workers: VideoWorkerVO[];
  concurrency: number;
  running: boolean;
  queued: number;
  queueCapacity: number;
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
  /**
   * 是否被本次请求接受并进入后台执行。
   *
   * <p>生成耗时 130 秒到 11 分钟，远超 Cloudflare 对源站响应的等待上限（约 100 秒），
   * 因此后端不再同步等出片：这个字段只表示「已排进后台队列」，
   * 最终结果要靠轮询 {@code GET /video/tasks/{taskId}} 拿。</p>
   */
  accepted?: boolean;
  outputAssetId?: number | string;
  truncated?: boolean;
  width?: number | null;
  height?: number | null;
  fps?: number | null;
  durationMillis?: number | null;
  errorCode?: string | null;
  errorMessage?: string | null;
}
