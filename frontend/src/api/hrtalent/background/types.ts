/**
 * 背调与业务附件 域类型定义
 *
 * 契约来源（按优先级）：
 * 1. SPEC-P3 §2.4 / §2.5 / §3.4 / §3.5（docs/hr-talent/SPEC-P3-招聘流程闭环.md）
 * 2. 后端已实现类：RecruitBackgroundVo / RecruitBackgroundDetailVo / RecruitAttachmentVo
 *    + RecruitBackgroundQueryBo / RecruitBackgroundBo / BackgroundDetailViewBo
 *    + RecruitAttachmentQueryBo（列表）与 AttachmentDownloadBo（预览/下载用途）
 * 3. DDL：script/sql/hr_recruit.sql（hr_recruit_background / hr_recruit_attachment）
 *
 * 安全口径：列表与普通详情**不含**背调敏感明细（detail_cipher）；
 * 明细只能经 GET /recruit/background-checks/{id}/detail（权限 recruit:background:view-sensitive + 必填 purpose + 写审计）获取。
 */

/** 背调结论（hr_recruit_background.result，字典 recruit_background_result，对齐 BackgroundResultEnum） */
export type BackgroundResult = 'pending' | 'pass' | 'fail' | 'waived';

/** 背调状态（hr_recruit_background.status，字典 recruit_background_status，对齐 BackgroundStatusEnum） */
export type BackgroundStatus = 'draft' | 'checking' | 'finished' | 'cancelled';

/** 背调记录视图（列表与普通详情，RecruitBackgroundVo —— 结构上不含 detail_cipher） */
export interface HrBackgroundVO extends BaseEntity {
  /** 背调记录ID */
  backgroundId?: string | number;
  /** 应聘记录ID（一条应聘记录仅一条当前有效背调） */
  applicationId?: string | number;
  /** 是否已获得候选人授权（0否 1是） */
  authorizedFlag?: string;
  /** 授权时间 */
  authorizeTime?: string;
  /** 背调负责人用户ID */
  checkerId?: string | number;
  /** 背调负责人昵称（@Translation 回填） */
  checkerName?: string;
  /** 背调完成时间 */
  checkTime?: string;
  /** 背调开始日期 */
  checkStartDate?: string;
  /** 背调结束日期 */
  checkEndDate?: string;
  /** 背调结论编码（字典 recruit_background_result） */
  result?: string;
  /** 背调结论标签（字典 recruit_background_result） */
  resultLabel?: string;
  /** 未通过原因编码（字典 recruit_background_failure_reason，不存中文） */
  failureReasonCode?: string;
  /** 背调核查项清单 */
  checkItems?: string;
  /** 免背调授权原因（result=waived 时必填） */
  waiveReason?: string;
  /** 背调状态编码（字典 recruit_background_status） */
  status?: string;
  /** 背调状态中文兜底（后端只读 getter） */
  statusLabel?: string;
  /** 备注 */
  remark?: string;
}

/**
 * 背调敏感明细视图（RecruitBackgroundDetailVo）
 * 仅 GET /recruit/background-checks/{id}/detail 返回；detailCipher 为服务端解密后的明文。
 */
export interface HrBackgroundDetailVO extends BaseEntity {
  backgroundId?: string | number;
  applicationId?: string | number;
  authorizedFlag?: string;
  authorizeTime?: string;
  checkerId?: string | number;
  checkerName?: string;
  checkTime?: string;
  checkStartDate?: string;
  checkEndDate?: string;
  result?: string;
  resultLabel?: string;
  failureReasonCode?: string;
  checkItems?: string;
  /** 背调敏感说明明文（禁止写日志/审计明细） */
  detailCipher?: string;
  waiveReason?: string;
  status?: string;
  statusLabel?: string;
  remark?: string;
}

/** 背调检索条件（GET /recruit/background-checks，对齐 RecruitBackgroundQueryBo + PageQuery） */
export interface HrBackgroundQuery extends PageQuery {
  /** 应聘记录ID */
  applicationId?: string | number;
  /** 背调负责人用户ID */
  checkerId?: string | number;
  /** 背调结论（字典 recruit_background_result） */
  result?: string;
  /** 背调状态（字典 recruit_background_status） */
  status?: string;
  /** 是否已授权（0/1） */
  authorizedFlag?: string;
  /** 未通过原因分类（字典 recruit_background_failure_reason） */
  failureReasonCode?: string;
  /** 背调完成时间起 */
  checkTimeBegin?: string;
  /** 背调完成时间止 */
  checkTimeEnd?: string;
}

/** 背调新增 / 编辑表单（POST /recruit/background-checks、PUT /recruit/background-checks/{id}） */
export interface HrBackgroundForm {
  backgroundId?: string | number;
  /** 应聘记录ID（新增必填） */
  applicationId?: string | number;
  /** 是否已获得候选人授权（0否 1是） */
  authorizedFlag?: string;
  /** 授权时间 */
  authorizeTime?: string;
  /** 背调负责人用户ID */
  checkerId?: string | number;
  /** 背调完成时间 */
  checkTime?: string;
  /** 背调开始日期 */
  checkStartDate?: string;
  /** 背调结束日期 */
  checkEndDate?: string;
  /** 背调结论（字典 recruit_background_result） */
  result?: string;
  /** 未通过原因分类（字典 recruit_background_failure_reason，仅 fail 时可填） */
  failureReasonCode?: string;
  /** 背调核查项清单 */
  checkItems?: string;
  /** 背调敏感说明（密文落库；留空表示不修改已有明细） */
  detailCipher?: string;
  /** 免背调授权原因（result=waived 时必填） */
  waiveReason?: string;
  /** 背调状态（字典 recruit_background_status） */
  status?: string;
  remark?: string;
}

/** 敏感明细查看入参（GET /recruit/background-checks/{id}/detail 的 purpose 查询参数） */
export interface HrBackgroundDetailQuery {
  /** 查看用途（必填；为空时服务端记 denied 审计并拒绝） */
  purpose: string;
}

/* ============================ 业务附件（只读消费，受控预览/下载） ============================ */

/** 附件视图（RecruitAttachmentVo） */
export interface HrAttachmentVO extends BaseEntity {
  /** 附件ID */
  attachmentId?: string | number;
  /** 业务类型（application/interview/background/offer 等稳定编码） */
  bizType?: string;
  /** 业务对象ID */
  bizId?: string | number;
  /** 附件类型（字典 recruit_attachment_type） */
  fileType?: string;
  /** 附件类型标签（字典 recruit_attachment_type） */
  fileTypeLabel?: string;
  /** 原始文件名 */
  originalName?: string;
  /** 文件后缀 */
  fileSuffix?: string;
  /** 文件大小（字节） */
  fileSize?: number;
  /** 文件哈希（SHA-256 十六进制） */
  fileHash?: string;
  /** 附件版本号 */
  versionNo?: number;
  /** 是否当前版本（0否 1是） */
  currentFlag?: string;
  /** 安全级别（字典 recruit_data_level） */
  securityLevel?: string;
  /** 安全级别标签（字典 recruit_data_level） */
  securityLevelLabel?: string;
  /** 上传人用户ID */
  uploadedBy?: string | number;
  /** 上传人昵称（@Translation 回填） */
  uploadedByName?: string;
  /** 上传时间 */
  uploadedTime?: string;
  /** 备注 */
  remark?: string;
}

/** 附件检索条件（GET /recruit/attachments，对齐 RecruitAttachmentQueryBo + PageQuery） */
export interface HrAttachmentQuery extends PageQuery {
  /** 业务类型 */
  bizType?: string;
  /** 业务对象ID */
  bizId?: string | number;
  /** 附件类型（字典 recruit_attachment_type） */
  fileType?: string;
  /** 是否当前版本（0/1） */
  currentFlag?: string;
  /** 安全级别（字典 recruit_data_level） */
  securityLevel?: string;
  /** 上传人用户ID */
  uploadedBy?: string | number;
  /** 上传时间起 */
  uploadedTimeBegin?: string;
  /** 上传时间止 */
  uploadedTimeEnd?: string;
}
