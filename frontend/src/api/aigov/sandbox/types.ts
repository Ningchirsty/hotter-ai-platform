/**
 * 沙箱运行证据（发布门槛 SANDBOX_RUN）相关类型。
 *
 * 与后端 `AigSandboxRunEvidence` / `AigSandboxRunRecordBo` 对齐。
 *
 * @author ai-gov
 */

/** 某个版本当前的沙箱运行证据结论（后端恒返回对象，不抛异常） */
export interface AigSandboxRunEvidence {
  /** 是否满足（有记录、退出码 0、未超时、无网，四条全过） */
  satisfied: boolean;
  /** 运行记录ID（无记录时为空） */
  runId?: string;
  /** 作业ID */
  jobId?: string;
  /** 实际运行的镜像 ref */
  imageRef?: string;
  /** 容器退出码 */
  exitCode?: number;
  /** 是否超时被杀 */
  timedOut?: boolean;
  /** 网络模式（none=无网） */
  network?: string;
  /** 执行耗时毫秒 */
  durationMs?: number;
  /** 产物个数 */
  artifactCount?: number;
  /** 登记时间 */
  recordedAt?: string;
  /** 登记人ID */
  recordedBy?: string;
  /** 不满足时的可读原因（满足时为空） */
  reason?: string;
}

/** 登记请求：只提交执行器输出的 result.json **原文** */
export interface AigSandboxRunRecordForm {
  /** 对象类型（AGENT_VERSION/SKILL_VERSION/PACKAGE_VERSION） */
  targetType: string;
  /** 对象版本ID */
  targetVersionId: string | number;
  /** 作业ID（必须与 result.json 里的 jobId 一致） */
  jobId: string;
  /** Agent 编码（可空） */
  agentCode?: string;
  /** result.json 原文（一字不改） */
  resultJson: string;
}
