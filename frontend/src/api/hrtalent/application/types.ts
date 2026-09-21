/**
 * 候选人 / 应聘记录 / 阶段流转 域类型定义
 *
 * 契约来源（按优先级）：
 * 1. SPEC-P3 §2.1 / §2.2 / §3.1 / §3.2（docs/hr-talent/SPEC-P3-招聘流程闭环.md）
 * 2. 后端已实现类：CandidateVo / TalentProfileVo / RecruitApplicationVo / RecruitStageLogVo
 *    + CandidateQueryBo / CandidateCreateBo / TalentPrecheckBo / PhoneViewBo
 *    + RecruitApplicationBo / RecruitApplicationQueryBo / ApplicationTransitionBo / ApplicationTransferBo
 *    + OfferRegisterBo / ArrivalRegisterBo / NoArrivalBo
 * 3. DDL：script/sql/hr_talent.sql（hr_talent_profile）、script/sql/hr_recruit.sql（hr_recruit_application / hr_recruit_stage_log）
 *
 * 字段名 = DDL 列名去掉下划线后的 camelCase；bigint 一律用 string | number，避免 JS 精度丢失。
 * 列表返回的电话/邮箱**一律脱敏**（phoneMasked / emailMasked），明文只能经 phone-view 接口获取。
 */

/** 应聘阶段（hr_recruit_application.current_stage，字典 recruit_candidate_stage，对齐 CandidateStageEnum） */
export type CandidateStage =
  | 'new'
  | 'resume_review'
  | 'invite'
  | 'first_interview'
  | 'second_interview'
  | 'background'
  | 'offer'
  | 'pending_arrival'
  | 'arrived';

/** 应聘结果（hr_recruit_application.current_status，字典 recruit_application_result，对齐 ApplicationResultEnum） */
export type ApplicationResult = 'processing' | 'passed' | 'rejected' | 'withdrawn' | 'paused' | 'talent_pool';

/** 阶段历史操作动作（hr_recruit_stage_log.action_type，稳定编码） */
export type StageActionType = 'move' | 'reject' | 'withdraw' | 'pause' | 'talent_pool' | 'arrive';

/**
 * 候选人列表视图（CandidateVo extends TalentProfileVo）
 *
 * 一人一档：主档字段 + 「最近一次应聘记录」聚合字段。
 * 后端对可翻译字段回填了 xxxLabel（字典标签）/ xxxName（用户昵称），页面优先用它们展示，缺失再回退到编码 / ID。
 */
export interface HrCandidateVO extends BaseEntity {
  /* ---------------- 人才主档（hr_talent_profile → TalentProfileVo） ---------------- */

  /** 人才主档ID（talent_id） */
  talentId?: string | number;
  /** 人才编号（业务编号，唯一） */
  talentNo?: string;
  /** 姓名 */
  name?: string;
  /** 性别编码（male/female/unknown，无对应字典，后端另有 genderText 中文兜底） */
  gender?: string;
  /** 性别中文兜底（后端只读 getter） */
  genderText?: string;
  /** 最高学历编码（无对应字典，后端另有 highestEducationText 中文兜底） */
  highestEducation?: string;
  /** 最高学历中文兜底（后端只读 getter） */
  highestEducationText?: string;
  /** **脱敏**电话（列表默认口径） */
  phoneMasked?: string;
  /** **脱敏**邮箱（列表默认口径） */
  emailMasked?: string;
  /** 当前所在城市 */
  currentCity?: string;
  /** 期望工作城市 */
  expectedCity?: string;
  /** 当前公司 */
  currentCompany?: string;
  /** 当前职位 */
  currentPosition?: string;
  /** 期望岗位 */
  expectedPosition?: string;
  /** 工作年限 */
  workYears?: number;
  /** 所属行业 */
  industry?: string;
  /** 人才归属（负责人）用户ID */
  ownerId?: string | number;
  /** 负责人昵称（@Translation 回填） */
  ownerName?: string;
  /** 归属部门ID */
  ownerDeptId?: string | number;
  /** 归属部门名称快照 */
  ownerDeptName?: string;
  /** 协助人用户ID数组（收发对称） */
  assistantIds?: (string | number)[];
  /** 协助人昵称，逗号分隔（@Translation 回填） */
  assistantNames?: string;
  /** 人才生命周期状态编码（字典 talent_status） */
  talentStatus?: string;
  /** 人才状态标签（字典 talent_status） */
  talentStatusLabel?: string;
  /** 状态原因（限制/禁止联系时必填） */
  statusReason?: string;
  /** 状态到期日 */
  statusExpireDate?: string;
  /** 可见范围编码（字典 talent_visibility_type） */
  visibilityType?: string;
  /** 可见范围标签（字典 talent_visibility_type） */
  visibilityTypeLabel?: string;
  /** 数据分级编码（字典 recruit_data_level） */
  dataLevel?: string;
  /** 数据分级标签（字典 recruit_data_level） */
  dataLevelLabel?: string;
  /** 主档来源（manual/resume_import/application 等稳定编码） */
  sourceType?: string;
  /** 首次来源渠道ID */
  sourceChannelId?: string | number;
  /** 最近简历更新时间 */
  resumeUpdateTime?: string;
  /** 人才主档乐观锁版本号 */
  version?: number;

  /* ---------------- 最近一次应聘（CandidateVo 聚合字段） ---------------- */

  /** 应聘记录条数 */
  applicationCount?: number;
  /** 最近一次应聘记录ID */
  latestApplicationId?: string | number;
  /** 最近一次应聘编号 */
  latestApplicationNo?: string;
  /** 最近一次应聘的岗位执行项ID */
  latestJobId?: string | number;
  /** 最近一次应聘阶段编码（字典 recruit_candidate_stage） */
  latestStage?: string;
  /** 最近一次应聘阶段标签（字典 recruit_candidate_stage） */
  latestStageLabel?: string;
  /** 最近一次应聘阶段中文兜底（后端只读 getter） */
  latestStageText?: string;
  /** 最近一次应聘结果编码（字典 recruit_application_result） */
  latestStatus?: string;
  /** 最近一次应聘结果标签（字典 recruit_application_result） */
  latestStatusLabel?: string;
  /** 最近一次应聘结果中文兜底（后端只读 getter） */
  latestStatusText?: string;
  /** 最近一次应聘的招聘负责人用户ID */
  latestRecruiterId?: string | number;
  /** 最近一次应聘的招聘负责人昵称（@Translation 回填） */
  latestRecruiterName?: string;
  /** 最近一次应聘的联系日期 */
  latestContactDate?: string;
  /** 最近一次应聘的投递时间 */
  latestApplyTime?: string;
  /** 最近一次应聘的进入当前阶段时间 */
  latestStageEnterTime?: string;
  /** 最近一次应聘的计划报到日期 */
  latestPlanArrivalDate?: string;
  /** 最近一次应聘的实际到岗日期 */
  latestArrivalDate?: string;
  /** 最近一次应聘的期望薪资下限（业务快照） */
  latestExpectedSalaryMin?: number;
  /** 最近一次应聘的期望薪资上限（业务快照） */
  latestExpectedSalaryMax?: number;
  /** 最近一次应聘的乐观锁版本号（流转/转移/报到必须回传） */
  latestVersion?: number;
}

/** 候选人检索条件（GET /recruit/candidates，对齐 CandidateQueryBo + PageQuery） */
export interface HrCandidateQuery extends PageQuery {
  /** 人才编号 */
  talentNo?: string;
  /** 姓名 */
  name?: string;
  /** 电话（后端按标准化哈希匹配） */
  phone?: string;
  /** 邮箱（后端按标准化哈希匹配） */
  email?: string;
  /** 人才状态（字典 talent_status） */
  talentStatus?: string;
  /** 当前阶段（字典 recruit_candidate_stage） */
  stage?: string;
  /** 应聘结果（字典 recruit_application_result） */
  status?: string;
  /** 岗位执行项ID */
  jobId?: string | number;
  /** 招聘负责人用户ID */
  recruiterId?: string | number;
  /** 当前所在城市 */
  currentCity?: string;
  /** 期望岗位 */
  expectedPosition?: string;
  /** 当前公司 */
  currentCompany?: string;
  /** 最高学历编码 */
  highestEducation?: string;
  /** 归属部门ID */
  ownerDeptId?: string | number;
  /** 数据分级（字典 recruit_data_level） */
  dataLevel?: string;
  /** 首次来源渠道ID */
  sourceChannelId?: string | number;
  /** 期望薪资下限 */
  expectedSalaryMin?: number;
  /** 期望薪资上限 */
  expectedSalaryMax?: number;
}

/** 查重预检入参（POST /recruit/candidates/precheck，对齐 TalentPrecheckBo） */
export interface HrTalentPrecheckForm {
  /** 姓名（必填） */
  name?: string;
  /** 电话明文（服务端标准化后哈希比对） */
  phone?: string;
  /** 邮箱明文 */
  email?: string;
  /** 当前公司 */
  currentCompany?: string;
  /** 学校名称 */
  schoolName?: string;
  /** 简历哈希（SHA-256 十六进制） */
  resumeHash?: string;
  /** 期望岗位 */
  expectedPosition?: string;
}

/** 预检命中项（TalentPrecheckVo.TalentPrecheckItemVo，只含可展示摘要） */
export interface HrTalentPrecheckItemVO {
  /** 人才主档ID */
  talentId?: string | number;
  /** 人才编号 */
  talentNo?: string;
  /** 姓名 */
  name?: string;
  /** 脱敏电话 */
  phoneMasked?: string;
  /** 脱敏邮箱 */
  emailMasked?: string;
  /** 当前公司 */
  currentCompany?: string;
  /** 当前所在城市 */
  currentCity?: string;
  /** 期望岗位 */
  expectedPosition?: string;
  /** 人才状态编码（字典 talent_status） */
  talentStatus?: string;
  /** 人才状态中文兜底（后端只读 getter） */
  talentStatusText?: string;
  /** 归属负责人用户ID */
  ownerId?: string | number;
  /** 归属部门ID */
  ownerDeptId?: string | number;
  /** 创建日期 */
  createDate?: string;
  /** 命中原因（如「电话命中」） */
  reason?: string;
  /** 命中级别编码（strong/medium/weak） */
  matchLevel?: string;
}

/** 查重预检结果（TalentPrecheckVo） */
export interface HrTalentPrecheckVO {
  /** 是否存在任何级别命中 */
  duplicated?: boolean;
  /** 是否存在强匹配（存在时不得静默创建） */
  strongDuplicated?: boolean;
  /** 是否存在中匹配 */
  mediumDuplicated?: boolean;
  /** 是否存在弱匹配（仅提示） */
  weakDuplicated?: boolean;
  /** 强匹配项 */
  strongMatches?: HrTalentPrecheckItemVO[];
  /** 中匹配项 */
  mediumMatches?: HrTalentPrecheckItemVO[];
  /** 弱匹配项 */
  weakMatches?: HrTalentPrecheckItemVO[];
  /** 统一中文提示 */
  message?: string;
  /** 规范化电话哈希（不可逆，仅供排障对齐） */
  normalizedPhoneHash?: string;
  /** 规范化邮箱哈希（不可逆，仅供排障对齐） */
  normalizedEmailHash?: string;
}

/**
 * 候选人新增表单（POST /recruit/candidates，对齐 CandidateCreateBo extends TalentProfileBo）
 *
 * 联系方式只提交**明文**（phone/email），服务端负责标准化、哈希与密文落库；
 * assistantIds 提交与读回均为**数组**。
 */
export interface HrCandidateCreateForm {
  /* ---------------- 人才主档 ---------------- */
  /** 姓名（必填） */
  name?: string;
  /** 曾用名 / 英文名 */
  formerName?: string;
  /** 性别编码 */
  gender?: string;
  /** 出生日期（yyyy-MM-dd） */
  birthDate?: string;
  /** 年龄快照（仅导入原值，不反推出生日期） */
  ageSnapshot?: number;
  /** 最高学历编码 */
  highestEducation?: string;
  /** 电话明文 */
  phone?: string;
  /** 备用手机号明文 */
  backupPhone?: string;
  /** 邮箱明文 */
  email?: string;
  /** 其他联系方式明文 */
  otherContact?: string;
  /** 当前所在城市 */
  currentCity?: string;
  /** 期望工作城市 */
  expectedCity?: string;
  /** 当前公司 */
  currentCompany?: string;
  /** 当前职位 */
  currentPosition?: string;
  /** 期望岗位 */
  expectedPosition?: string;
  /** 期望薪资下限 */
  expectedSalaryMin?: number;
  /** 期望薪资上限 */
  expectedSalaryMax?: number;
  /** 工作年限 */
  workYears?: number;
  /** 所属行业 */
  industry?: string;
  /** 人才归属（负责人）用户ID */
  ownerId?: string | number;
  /** 归属部门ID */
  ownerDeptId?: string | number;
  /** 归属部门名称快照 */
  ownerDeptName?: string;
  /** 协助人用户ID数组（提交为数组） */
  assistantIds?: (string | number)[];
  /** 可见范围（字典 talent_visibility_type） */
  visibilityType?: string;
  /** 数据分级（字典 recruit_data_level） */
  dataLevel?: string;
  /** 主档来源（manual/resume_import/application 等稳定编码） */
  sourceType?: string;
  /** 首次来源渠道ID */
  sourceChannelId?: string | number;
  /** 状态原因 */
  statusReason?: string;
  /** 状态到期日 */
  statusExpireDate?: string;
  /** 备注 */
  remark?: string;

  /* ---------------- 应聘记录（可选创建） ---------------- */
  /** 复用已有主档时传入的人才ID（查重命中后「复用」路径） */
  talentId?: string | number;
  /** 已确认重复处置（true 表示已人工确认复用或确认非同一人） */
  duplicateAck?: boolean;
  /** 是否同时创建应聘记录 */
  createApplication?: boolean;
  /** 应聘岗位执行项ID（创建应聘记录时必填） */
  jobId?: string | number;
  /** 招聘负责人用户ID */
  recruiterId?: string | number;
  /** 联系日期（yyyy-MM-dd） */
  contactDate?: string;
  /** 应聘备注 */
  applicationRemark?: string;
}

/** 电话明文查看入参（POST /recruit/candidates/{id}/phone-view，对齐 PhoneViewBo） */
export interface HrPhoneViewForm {
  /** 查看用途（必填，最长 255；服务端写审计） */
  purpose: string;
}

/* ============================ 应聘记录（hr_recruit_application） ============================ */

/** 应聘记录视图（RecruitApplicationVo） */
export interface HrApplicationVO extends BaseEntity {
  /** 应聘记录ID */
  applicationId?: string | number;
  /** 应聘编号（业务编号，唯一） */
  applicationNo?: string;
  /** 人才主档ID */
  talentId?: string | number;
  /** 岗位执行项ID */
  jobId?: string | number;
  /** 本次应聘使用的简历版本ID */
  resumeId?: string | number;
  /** 当前阶段编码（字典 recruit_candidate_stage） */
  currentStage?: string;
  /** 当前阶段标签（字典 recruit_candidate_stage） */
  currentStageLabel?: string;
  /** 应聘结果编码（字典 recruit_application_result） */
  currentStatus?: string;
  /** 应聘结果标签（字典 recruit_application_result） */
  currentStatusLabel?: string;
  /** 来源渠道ID */
  sourceChannelId?: string | number;
  /** 来源方式（channel/referral/import 等稳定编码） */
  sourceType?: string;
  /** 期望薪资低值 */
  expectedSalaryMin?: number;
  /** 期望薪资高值 */
  expectedSalaryMax?: number;
  /** 招聘负责人用户ID */
  recruiterId?: string | number;
  /** 招聘负责人昵称（@Translation 回填） */
  recruiterName?: string;
  /** 联系日期 */
  contactDate?: string;
  /** 下次跟进时间 */
  nextFollowTime?: string;
  /** 计划报到日期 */
  planArrivalDate?: string;
  /** 录用邀约日期 */
  offerDate?: string;
  /** 邀约结果（accepted/rejected 等稳定编码） */
  offerResult?: string;
  /** 实际到岗日期 */
  arrivalDate?: string;
  /** 未报到原因 */
  noArrivalReason?: string;
  /** 应聘（投递）时间 */
  applyTime?: string;
  /** 进入当前阶段时间 */
  stageEnterTime?: string;
  /** 淘汰/撤回原因 */
  rejectReason?: string;
  /** 乐观锁版本号（流转/转移/报到必须回传） */
  version?: number;
  /** 备注 */
  remark?: string;
}

/** 应聘记录新增/编辑表单（POST /recruit/applications、PUT /recruit/applications/{id}） */
export interface HrApplicationForm {
  applicationId?: string | number;
  /** 人才主档ID（新增必填） */
  talentId?: string | number;
  /** 应聘岗位执行项ID（新增必填） */
  jobId?: string | number;
  resumeId?: string | number;
  sourceChannelId?: string | number;
  sourceType?: string;
  expectedSalaryMin?: number;
  expectedSalaryMax?: number;
  recruiterId?: string | number;
  contactDate?: string;
  nextFollowTime?: string;
  applyTime?: string;
  /** 乐观锁版本号（编辑必传） */
  version?: number;
  remark?: string;
}

/** 应聘记录检索条件（GET /recruit/applications，对齐 RecruitApplicationQueryBo + PageQuery） */
export interface HrApplicationQuery extends PageQuery {
  applicationNo?: string;
  talentId?: string | number;
  jobId?: string | number;
  /** 当前阶段（字典 recruit_candidate_stage） */
  currentStage?: string;
  /** 应聘结果（字典 recruit_application_result） */
  currentStatus?: string;
  recruiterId?: string | number;
  sourceChannelId?: string | number;
  sourceType?: string;
  /** 联系日期起（yyyy-MM-dd） */
  contactDateBegin?: string;
  /** 联系日期止（yyyy-MM-dd） */
  contactDateEnd?: string;
  /** 投递时间起 */
  applyTimeBegin?: string;
  /** 投递时间止 */
  applyTimeEnd?: string;
  /** 下次跟进时间起 */
  nextFollowTimeBegin?: string;
  /** 下次跟进时间止 */
  nextFollowTimeEnd?: string;
}

/**
 * 阶段流转入参（POST /recruit/applications/{id}/transition，对齐 ApplicationTransitionBo）
 *
 * toStage 与 result **二选一**：阶段前进填写 toStage，转入淘汰/放弃/暂缓/人才保留填写 result。
 * version 必填（乐观锁）；override=true 时为管理员例外跳转，必须填写 overrideReason。
 */
export interface HrApplicationTransitionForm {
  /** 原阶段编码（可选，后端会与当前阶段比对） */
  fromStage?: string;
  /** 目标阶段编码（阶段前进时填写；不得为 arrived，报到须走报到接口） */
  toStage?: string;
  /** 目标应聘结果编码（rejected/withdrawn/paused/talent_pool） */
  result?: string;
  /** 原因编码（字典编码，不存中文） */
  reasonCode?: string;
  /** 阶段说明（淘汰/放弃必填其一） */
  comment?: string;
  /** 本次跟进的下一步时间 */
  nextFollowTime?: string;
  /** 是否管理员例外跳转（可回退/跳级，必须填写原因并写审计） */
  override?: boolean;
  /** 例外跳转原因（override=true 时必填） */
  overrideReason?: string;
  /** 邀约结果（目标阶段为 pending_arrival 时必填） */
  offerResult?: string;
  /** 计划报到日期（目标阶段为 pending_arrival 时必填） */
  planArrivalDate?: string;
  /** 乐观锁版本号（必填） */
  version?: number;
}

/** 转移招聘负责人 / 岗位入参（POST /recruit/applications/{id}/transfer，对齐 ApplicationTransferBo） */
export interface HrApplicationTransferForm {
  /** 新的招聘负责人用户ID */
  recruiterId?: string | number;
  /** 新的岗位执行项ID */
  jobId?: string | number;
  /** 转移原因 */
  reason?: string;
  /** 乐观锁版本号（必填） */
  version?: number;
}

/** 邀约登记入参（POST /recruit/applications/{id}/offer，对齐 OfferRegisterBo） */
export interface HrOfferRegisterForm {
  /** 录用邀约日期（yyyy-MM-dd） */
  offerDate?: string;
  /** 邀约结果（必填，accepted/rejected 等稳定编码） */
  offerResult?: string;
  /** 计划报到日期（必填） */
  planArrivalDate?: string;
  remark?: string;
  /** 乐观锁版本号（必填） */
  version?: number;
}

/** 报到登记入参（POST /recruit/applications/{id}/arrival，对齐 ArrivalRegisterBo） */
export interface HrArrivalRegisterForm {
  /** 实际报到日期（必填） */
  arrivalDate?: string;
  /** 计入的月度计划任务ID（可空，由后端按关系表判定） */
  planItemId?: string | number;
  remark?: string;
  /** 乐观锁版本号（必填） */
  version?: number;
}

/** 未报到登记入参（POST /recruit/applications/{id}/no-arrival，对齐 NoArrivalBo） */
export interface HrNoArrivalForm {
  /** 未报到原因（必填，最长 500） */
  noArrivalReason?: string;
  /** 下次跟进时间 */
  nextFollowTime?: string;
  remark?: string;
  /** 乐观锁版本号（必填） */
  version?: number;
}

/** 阶段历史视图（RecruitStageLogVo，只追加不覆盖） */
export interface HrStageLogVO {
  /** 阶段历史ID */
  logId?: string | number;
  /** 应聘记录ID */
  applicationId?: string | number;
  /** 原阶段编码（字典 recruit_candidate_stage） */
  fromStage?: string;
  /** 原阶段标签（字典 recruit_candidate_stage） */
  fromStageLabel?: string;
  /** 目标阶段编码（字典 recruit_candidate_stage） */
  toStage?: string;
  /** 目标阶段标签（字典 recruit_candidate_stage） */
  toStageLabel?: string;
  /** 操作动作（move/reject/withdraw/pause/talent_pool/arrive） */
  actionType?: string;
  /** 阶段结果编码（字典 recruit_application_result） */
  result?: string;
  /** 阶段结果标签（字典 recruit_application_result） */
  resultLabel?: string;
  /** 原因编码（字典编码，不存中文） */
  reasonCode?: string;
  /** 阶段说明 */
  comment?: string;
  /** 本次跟进的下一步时间 */
  nextFollowTime?: string;
  /** 操作人用户ID */
  operatorId?: string | number;
  /** 操作人昵称（@Translation 回填） */
  operatorName?: string;
  /** 操作时间 */
  operateTime?: string;
  /** 备注 */
  remark?: string;
}
