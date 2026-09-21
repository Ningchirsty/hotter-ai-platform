/**
 * 简历中心（简历版本 + 解析任务 + 人工逐字段复核）域类型定义
 *
 * 契约来源（按优先级）：
 * 1. SPEC-P4 §2.1 A 线（docs/hr-talent/SPEC-P4-人才管理.md）
 * 2. 后端已实现类：
 *    - TalentResumeController（/talent/profiles/{id}/resumes、/talent/resumes/{id}/...）
 *      + TalentResumeVo、TalentResumeUploadBo、TalentResumeQueryBo、ResumeDownloadBo
 *    - TalentParseController（/talent/parse-tasks/{id}）
 *      + TalentParseTaskVo、TalentParseResultVo、ParseConfirmBo
 * 3. DDL：script/sql/hr_talent.sql（hr_talent_resume / hr_talent_parse_task / hr_talent_parse_result）
 *
 * 字段名 = 后端 VO/BO 字段名逐字对齐；bigint 一律用 string | number。
 * 下载是二进制流（Controller 返回 void + HttpServletResponse），调用方以 Blob 处理；
 * 解析一期只创建任务 + 人工复核，**不在请求内同步执行** OCR/大模型调用。
 */

/** 简历版本视图（TalentResumeVo） */
export interface HrTalentResumeVO extends BaseEntity {
  /** 简历版本ID */
  resumeId?: string | number;
  /** 人才主档ID */
  talentId?: string | number;
  /** 版本号（服务端单调递增） */
  versionNo?: number;
  /** 原始文件名 */
  originalName?: string;
  /** 文件后缀 */
  fileSuffix?: string;
  /** 文件大小（字节） */
  fileSize?: number;
  /** 文件哈希（SHA-256 十六进制） */
  fileHash?: string;
  /** 是否当前版本（0否 1是） */
  currentFlag?: string;
  /** 安全扫描状态（pending/scanning/passed/failed 等稳定编码，无字典组） */
  scanStatus?: string;
  /** 解析状态编码（字典 talent_resume_parse_status） */
  parseStatus?: string;
  /** 解析状态标签（字典 talent_resume_parse_status） */
  parseStatusLabel?: string;
  /** 复核状态编码（字典 talent_resume_review_status） */
  reviewStatus?: string;
  /** 复核状态标签（字典 talent_resume_review_status） */
  reviewStatusLabel?: string;
  /** 解析器版本 */
  parserVersion?: string;
  /** 来源类型（字典 talent_source_type） */
  sourceType?: string;
  /** 上传人用户ID */
  uploadedBy?: string | number;
  /** 上传人昵称（@Translation 回填） */
  uploadedByName?: string;
  /** 上传时间 */
  uploadedTime?: string;
  remark?: string;
  /** 服务端下发的受控下载地址（不含对象存储永久地址） */
  downloadApi?: string;
  /** 重复文件命中的已有版本ID（上传同名同哈希文件时返回） */
  duplicateResumeId?: string | number;
  /** 重复文件中文提示 */
  duplicateFileHint?: string;
}

/** 简历版本检索条件（TalentResumeQueryBo） */
export interface HrTalentResumeQuery {
  /** 是否当前版本（0/1） */
  currentFlag?: string;
  /** 解析状态（字典 talent_resume_parse_status） */
  parseStatus?: string;
  /** 复核状态（字典 talent_resume_review_status） */
  reviewStatus?: string;
  /** 安全扫描状态 */
  scanStatus?: string;
  /** 文件后缀 */
  fileSuffix?: string;
  /** 来源类型（字典 talent_source_type） */
  sourceType?: string;
  /** 上传人用户ID */
  uploadedBy?: string | number;
  /** 上传时间起（yyyy-MM-dd HH:mm:ss） */
  uploadedTimeBegin?: string;
  /** 上传时间止（yyyy-MM-dd HH:mm:ss） */
  uploadedTimeEnd?: string;
}

/** 上传简历新版本表单（TalentResumeUploadBo + 表单字段名固定为 file） */
export interface HrTalentResumeUploadForm {
  /** 人才主档ID（路径参数） */
  talentId: string | number;
  /** 简历文件 */
  file: File;
  /** 来源类型（字典 talent_source_type） */
  sourceType?: string;
  /** 备注 */
  remark?: string;
}

/** 受控下载入参（ResumeDownloadBo，用途必填；为空时服务端写 denied 审计并拒绝） */
export interface HrTalentResumeDownloadForm {
  /** 下载用途（必填） */
  purpose: string;
}

/** 解析任务视图（TalentParseTaskVo） */
export interface HrTalentParseTaskVO extends BaseEntity {
  /** 解析任务ID */
  taskId?: string | number;
  /** 简历版本ID */
  resumeId?: string | number;
  /** 人才主档ID */
  talentId?: string | number;
  /** 任务状态（pending/running/success/failed/cancelled 等稳定编码，无字典组） */
  taskStatus?: string;
  /** 解析器类型 */
  parserType?: string;
  /** 解析器版本 */
  parserVersion?: string;
  /** 重试次数 */
  retryCount?: number;
  /** 开始时间 */
  startedTime?: string;
  /** 完成时间 */
  finishedTime?: string;
  /** 错误码 */
  errorCode?: string;
  /** 错误信息（一期解析引擎未接入时给出明确中文提示） */
  errorMessage?: string;
  /** 创建人用户ID */
  createBy?: string | number;
  /** 创建人昵称（@Translation 回填） */
  createByName?: string;
  /** 创建时间 */
  createTime?: string;
  remark?: string;
  /** 候选项列表（逐字段保存原始值/标准化值/置信度/来源位置/复核结论） */
  results?: HrTalentParseResultVO[];
}

/** 解析候选结果视图（TalentParseResultVo） */
export interface HrTalentParseResultVO extends BaseEntity {
  /** 解析结果ID */
  resultId?: string | number;
  /** 解析任务ID */
  taskId?: string | number;
  /** 字段路径（如 basic.name、education.school_name） */
  fieldPath?: string;
  /** 原始值（简历原文） */
  rawValue?: string;
  /** 标准化值（人工确认后可写入正式字段） */
  normalizedValue?: string;
  /** 置信度 */
  confidence?: number;
  /** 来源位置（页码/段落等） */
  sourceLocation?: string;
  /** 复核状态编码（字典 talent_resume_review_status） */
  reviewStatus?: string;
  /** 复核状态标签（字典 talent_resume_review_status） */
  reviewStatusLabel?: string;
  /** 复核人用户ID */
  reviewedBy?: string | number;
  /** 复核人昵称（@Translation 回填） */
  reviewedByName?: string;
  /** 复核时间 */
  reviewedTime?: string;
  /** 是否默认勾选（低置信度默认 false，页面据此默认不勾选） */
  defaultSelected?: boolean;
  remark?: string;
}

/**
 * 人工逐字段复核入参（ParseConfirmBo）
 *
 * 未勾选（accepted 非 true）的候选项也必须回传，用于记录 rejected 复核结论；
 * 只有 accepted = true 的字段才写入人才主档/经历。
 */
export interface HrTalentParseConfirmForm {
  /** 确认备注（可选，最长 500） */
  remark?: string;
  /** 逐字段确认项（至少一项） */
  items: HrTalentParseConfirmItem[];
}

/** 单个字段的复核入参（ParseConfirmBo.Item） */
export interface HrTalentParseConfirmItem {
  /** 解析结果ID（必填） */
  resultId: string | number;
  /** 是否勾选确认（null 视为未勾选，记为 rejected） */
  accepted?: boolean;
  /** 人工修正后的标准化值（为空时使用解析结果自身的标准化值） */
  normalizedValue?: string;
}
