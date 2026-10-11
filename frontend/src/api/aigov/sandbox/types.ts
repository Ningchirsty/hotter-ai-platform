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
  /**
   * 证据可信度来源。
   *
   * `UNATTESTED` = 人工登记，**无密码学保证**（当前唯一可能的值）；`SIGNED` = 执行器私钥签名 +
   * 平台公钥验签通过（未实现）。它不影响 `satisfied`——只是让"这条证据是怎么来的"看得见：
   * 未验签只能证明"有人提交了这份结果、提交后没被改过"，**不证明它来自一次真实运行**。
   */
  attestation?: string;
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

/**
 * 沙箱产物清单项（ADR-016：产物进平台）。
 *
 * **以「声明的产物」为准**：列表来自 result.json 的 `artifacts`，每一项带 `uploaded`——
 * 只列已入库的行答不出"这次作业产出的东西都收齐了吗"，漏传的那个恰恰不会出现。
 */
export interface AigSandboxArtifactManifestVO {
  /** 产物相对路径（上传/下载都用它作参数） */
  path: string;
  /** 账本声明的字节数 */
  bytes?: number;
  /** 账本声明的 SHA-256 */
  sha256?: string;
  /** 是否已入库（字节已在对象存储且账本有行） */
  uploaded?: boolean;
  /** 入库的产物记录ID（未入库时为空） */
  artifactId?: string | number;
  /** 入库时上传方声明的类型（仅记录；下载一律按附件处理） */
  contentType?: string;
  /** 入库时间 */
  uploadedAt?: string;
}

/**
 * 沙箱产物**下载**留痕（ADR-016 安全硬要求 ⑤：谁下载了哪个产物要留痕）。
 *
 * 记的是"本次下发的摘要与字节数"，而不只是产物ID——事后要核对"当时给他的到底是哪一份字节"。
 */
export interface AigSandboxArtifactDownloadVO {
  downloadId: string | number;
  sandboxRunId?: string | number;
  artifactId?: string | number;
  path?: string;
  sha256?: string;
  bytes?: number;
  /** 下载人ID（取自登录态） */
  downloadedBy?: string | number | null;
  /** 来源 IP（审计用） */
  clientIp?: string | null;
  createTime?: string;
}
