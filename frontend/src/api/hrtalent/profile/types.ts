/**
 * 人才档案（人才主档 + 教育/工作/项目经历 + 标签 + 跟进）域类型定义
 *
 * 契约来源（按优先级）：
 * 1. SPEC-P4 §2.2 / §2.3 / §2.4 / §2.6（docs/hr-talent/SPEC-P4-人才管理.md）
 * 2. 后端已实现类：
 *    - TalentProfileController（/talent/profiles）+ TalentProfileVo / TalentProfileDetailVo
 *      + TalentProfileQueryBo / TalentProfileBo / TalentPrecheckBo / TalentPrecheckVo
 *    - TalentExperienceController（/talent/profiles/{id}/educations|works|projects）
 *      + TalentEducationVo/Bo、TalentWorkVo/Bo、TalentProjectVo/Bo
 *    - TalentTagController（/talent/profiles/{id}/tags）+ TalentProfileTagBo
 *    - TalentFollowUpController（/talent/profiles/{id}/follow-ups）+ TalentFollowUpVo/Bo
 * 3. DDL：script/sql/hr_talent.sql
 *
 * 字段名 = 后端 VO/BO 字段名逐字对齐；bigint 一律用 string | number，避免 JS 精度丢失。
 * 列表返回的电话/邮箱**一律脱敏**（phoneMasked / emailMasked）。
 */

/* ============================ 人才主档 ============================ */

/**
 * 人才主档视图（TalentProfileVo）
 *
 * 后端对可翻译字段回填了 xxxLabel（字典标签）/ xxxName（用户昵称），页面优先使用它们。
 */
export interface HrTalentProfileVO extends BaseEntity {
  /** 人才主档ID */
  talentId?: string | number;
  /** 人才编号（业务编号，唯一） */
  talentNo?: string;
  /** 姓名 */
  name?: string;
  /** 性别编码（male/female/unknown，无字典组，后端另有 genderText 兜底） */
  gender?: string;
  /** 性别中文兜底（后端只读 getter） */
  genderText?: string;
  /** 最高学历编码（字典 talent_education） */
  highestEducation?: string;
  /** 最高学历中文兜底（后端只读 getter，编码口径与 talent_education 不完全一致，优先用字典） */
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
  /** 协助人用户ID数组 */
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
  /** 乐观锁版本号（编辑提交时必须回传） */
  version?: number;
  /** 创建时间 */
  createTime?: string;
  /** 更新时间 */
  updateTime?: string;
  /** 资料完整度（0~100，服务端按主档基础字段实时计算） */
  completeness?: number;
  /** 资料完整度分档（high/medium/low 稳定编码） */
  completenessLevel?: string;
  /** 资料完整度分档中文兜底（后端只读 getter） */
  completenessLevelText?: string;
}

/** 人才主档详情视图（TalentProfileDetailVo extends TalentProfileVo） */
export interface HrTalentProfileDetailVO extends HrTalentProfileVO {
  /** 曾用名（或英文名） */
  formerName?: string;
  /** 出生日期 */
  birthDate?: string;
  /** 年龄快照（仅导入原值，不反推出生日期） */
  ageSnapshot?: number;
  /** 期望薪资下限 */
  expectedSalaryMin?: number;
  /** 期望薪资上限 */
  expectedSalaryMax?: number;
  /** 当前简历版本ID */
  currentResumeId?: string | number;
  /** 是否已存在应聘记录（仅无应聘记录的人才可删除） */
  hasApplication?: boolean;
  /** 被合并到的目标主档ID（非空表示已合并） */
  mergedToId?: string | number;
  /** 最近跟进时间 */
  lastFollowTime?: string;
  /** 下次联系时间 */
  nextFollowTime?: string;
  /** 备注 */
  remark?: string;
}

/**
 * 组合检索条件（GET /talent/profiles，TalentProfileQueryBo + PageQuery）
 *
 * **资料完整度筛选（minCompleteness）本期后端 fail-fast 拒绝**，页面不提供该筛选条件。
 */
export interface HrTalentProfileQuery extends PageQuery {
  /** 人才编号（模糊匹配） */
  talentNo?: string;
  /** 姓名（模糊匹配） */
  name?: string;
  /** 电话（服务端标准化后按哈希精确匹配，不做模糊匹配；页面不提供该筛选，保留契约） */
  phone?: string;
  /** 邮箱（服务端小写标准化后按哈希精确匹配；页面不提供该筛选，保留契约） */
  email?: string;
  /** 人才生命周期状态（字典 talent_status） */
  talentStatus?: string;
  /** 当前所在城市（模糊匹配） */
  currentCity?: string;
  /** 期望工作城市（模糊匹配） */
  expectedCity?: string;
  /** 期望岗位（模糊匹配） */
  expectedPosition?: string;
  /** 当前公司（模糊匹配） */
  currentCompany?: string;
  /** 最高学历编码（字典 talent_education，精确匹配） */
  highestEducation?: string;
  /** 所属行业（模糊匹配） */
  industry?: string;
  /** 人才归属（负责人）用户ID */
  ownerId?: string | number;
  /** 归属部门ID */
  ownerDeptId?: string | number;
  /** 可见范围（字典 talent_visibility_type） */
  visibilityType?: string;
  /** 数据分级（字典 recruit_data_level） */
  dataLevel?: string;
  /** 主档来源（manual/resume_import/application 等稳定编码） */
  sourceType?: string;
  /** 协助人用户ID */
  assistantId?: string | number;
  /** 工作年限下限（含） */
  workYearsBegin?: number;
  /** 工作年限上限（含） */
  workYearsEnd?: number;
  /** 创建时间起（yyyy-MM-dd） */
  createDateBegin?: string;
  /** 创建时间止（yyyy-MM-dd） */
  createDateEnd?: string;
  /** 是否包含已归档人才（默认 false） */
  includeArchived?: boolean;
  /** 是否只查未被任何应聘记录引用的人才 */
  onlyWithoutApplication?: boolean;
  /** 手机号后四位（基于 phone_tail4 精确匹配，不解密全量比对） */
  phoneTail4?: string;
  /** 当前岗位（模糊匹配） */
  currentPosition?: string;
  /** 历史岗位（模糊匹配工作经历岗位名，命中任一即可） */
  historyPosition?: string;
  /** 人才标签ID集合（命中任一标签即可） */
  tagIds?: (string | number)[];
  /** 专业（模糊匹配教育经历专业） */
  major?: string;
  /** 毕业院校（模糊匹配教育经历院校） */
  schoolName?: string;
  /** 来源渠道ID */
  sourceChannelId?: string | number;
  /** 人才池ID（人才需为该池有效成员） */
  poolId?: string | number;
  /** 最近联系时间起（yyyy-MM-dd HH:mm:ss） */
  lastFollowTimeBegin?: string;
  /** 最近联系时间止（yyyy-MM-dd HH:mm:ss） */
  lastFollowTimeEnd?: string;
  /** 是否存在当前简历（true/false，null 不过滤） */
  hasCurrentResume?: boolean;
  /** 当前简历解析状态（字典 talent_resume_parse_status） */
  resumeParseStatus?: string;
}

/** 人才主档新增/编辑表单（POST /talent/profiles、PUT /talent/profiles/{id}，TalentProfileBo） */
export interface HrTalentProfileForm {
  /** 人才主档ID（编辑必填） */
  talentId?: string | number;
  /** 姓名（必填，最长 64） */
  name?: string;
  /** 曾用名 / 英文名 */
  formerName?: string;
  /** 性别编码（male/female/unknown） */
  gender?: string;
  /** 出生日期（yyyy-MM-dd） */
  birthDate?: string;
  /** 年龄快照（仅导入原值） */
  ageSnapshot?: number;
  /** 最高学历编码（字典 talent_education） */
  highestEducation?: string;
  /** 电话明文（服务端标准化、哈希与密文落库） */
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
  /** 协助人用户ID数组 */
  assistantIds?: (string | number)[];
  /** 可见范围（字典 talent_visibility_type） */
  visibilityType?: string;
  /** 数据分级（字典 recruit_data_level） */
  dataLevel?: string;
  /** 主档来源（manual/resume_import/application 等稳定编码） */
  sourceType?: string;
  /** 首次来源渠道ID */
  sourceChannelId?: string | number;
  /** 限制/禁止联系状态的原因 */
  statusReason?: string;
  /** 状态到期日 */
  statusExpireDate?: string;
  /** 乐观锁版本号（编辑时必填） */
  version?: number;
  /** 备注 */
  remark?: string;
}

/** 关键字段变更历史（TalentProfileChangeVo） */
export interface HrTalentProfileChangeVO extends BaseEntity {
  /** 变更历史ID */
  changeId?: string | number;
  /** 人才主档ID */
  talentId?: string | number;
  /** 变更类型（稳定编码） */
  changeType?: string;
  /** 变更类型中文（后端只读 getter） */
  changeTypeLabel?: string;
  /** 变更前快照（JSON 字符串） */
  beforeJson?: string;
  /** 变更后快照（JSON 字符串） */
  afterJson?: string;
  /** 操作人用户ID */
  operatorId?: string | number;
  /** 操作时间 */
  operateTime?: string;
  /** 备注 */
  remark?: string;
}

/* ============================ 重复预检（POST /talent/profiles/precheck） ============================ */

/** 查重预检入参（TalentPrecheckBo） */
export interface HrTalentPrecheckForm {
  /** 姓名（查重前置条件） */
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
  /** 命中原因 */
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
 * 电话明文查看入参（PhoneViewBo）
 *
 * 后端 `purpose` 仅带 `@Size(max = 255)`，**非空校验在服务端**：为空时先写 `denied` 审计
 * 再返回中文业务错误（R.fail）。页面做非空校验只是为了避免无谓请求，不作为唯一防线。
 *
 * 两个端点并存：人才档案页走 `POST /talent/profiles/{id}/phone-view`
 * （权限 `talent:profile:phone-view`，**不要求存在应聘记录**）；
 * 候选人跟进页走 `POST /recruit/candidates/{id}/phone-view`
 * （权限 `recruit:candidate:phone-view`，服务层 assertCandidate 要求存在应聘记录）。
 */
export interface HrPhoneViewForm {
  /** 查看用途（必填，最长 255；为空时服务端写 denied 审计并拒绝） */
  purpose: string;
}

/* ============================ 教育经历 ============================ */

/** 教育经历视图（TalentEducationVo） */
export interface HrTalentEducationVO extends BaseEntity {
  /** 教育经历ID */
  educationId?: string | number;
  /** 人才主档ID */
  talentId?: string | number;
  /** 学校名称 */
  schoolName?: string;
  /** 专业 */
  major?: string;
  /** 学历编码（字典 talent_education） */
  education?: string;
  /** 学历中文兜底（后端只读 getter） */
  educationText?: string;
  /** 学位编码（字典 talent_degree） */
  degree?: string;
  /** 学位中文兜底（后端只读 getter） */
  degreeText?: string;
  /** 开始日期 */
  startDate?: string;
  /** 结束日期 */
  endDate?: string;
  /** 是否全日制（1是 0否） */
  fullTimeFlag?: string;
  /** 全日制中文兜底（后端只读 getter） */
  fullTimeFlagText?: string;
  /** 来源类型（字典 talent_source_type） */
  sourceType?: string;
  /** 来源类型中文兜底（后端只读 getter） */
  sourceTypeText?: string;
  /** 来源简历版本ID */
  resumeId?: string | number;
  /** 排序号 */
  sortNo?: number;
  /** 备注 */
  remark?: string;
  /** 创建人ID */
  createBy?: string | number;
  /** 创建人昵称（@Translation 回填） */
  createByName?: string;
}

/** 教育经历新增/编辑表单（TalentEducationBo） */
export interface HrTalentEducationForm {
  educationId?: string | number;
  talentId?: string | number;
  /** 学校名称 */
  schoolName?: string;
  /** 专业 */
  major?: string;
  /** 学历编码（字典 talent_education） */
  education?: string;
  /** 学位编码（字典 talent_degree） */
  degree?: string;
  /** 开始日期（yyyy-MM-dd） */
  startDate?: string;
  /** 结束日期（yyyy-MM-dd） */
  endDate?: string;
  /** 是否全日制（1/0） */
  fullTimeFlag?: string;
  /** 来源类型（字典 talent_source_type） */
  sourceType?: string;
  /** 来源简历版本ID */
  resumeId?: string | number;
  /** 排序号 */
  sortNo?: number;
  remark?: string;
}

/* ============================ 工作经历 ============================ */

/** 工作经历视图（TalentWorkVo） */
export interface HrTalentWorkVO extends BaseEntity {
  /** 工作经历ID */
  workId?: string | number;
  /** 人才主档ID */
  talentId?: string | number;
  /** 公司名称 */
  companyName?: string;
  /** 部门名称 */
  departmentName?: string;
  /** 职位名称 */
  positionName?: string;
  /** 所属行业 */
  industry?: string;
  /** 开始日期 */
  startDate?: string;
  /** 结束日期 */
  endDate?: string;
  /** 离职原因 */
  leaveReason?: string;
  /** 是否当前任职（1是 0否） */
  currentFlag?: string;
  /** 是否当前任职中文兜底（后端只读 getter） */
  currentFlagText?: string;
  /** 职责描述 */
  responsibility?: string;
  /** 业绩描述 */
  achievement?: string;
  /** 来源类型（字典 talent_source_type） */
  sourceType?: string;
  /** 来源类型中文兜底（后端只读 getter） */
  sourceTypeText?: string;
  /** 来源简历版本ID */
  resumeId?: string | number;
  /** 排序号 */
  sortNo?: number;
  remark?: string;
  createBy?: string | number;
  createByName?: string;
}

/** 工作经历新增/编辑表单（TalentWorkBo） */
export interface HrTalentWorkForm {
  workId?: string | number;
  talentId?: string | number;
  companyName?: string;
  departmentName?: string;
  positionName?: string;
  industry?: string;
  startDate?: string;
  endDate?: string;
  leaveReason?: string;
  /** 是否当前任职（1/0）；为 1 时服务端把离职日期置空 */
  currentFlag?: string;
  responsibility?: string;
  achievement?: string;
  sourceType?: string;
  resumeId?: string | number;
  sortNo?: number;
  remark?: string;
}

/* ============================ 项目经历 ============================ */

/** 项目经历视图（TalentProjectVo） */
export interface HrTalentProjectVO extends BaseEntity {
  /** 项目经历ID */
  projectId?: string | number;
  /** 人才主档ID */
  talentId?: string | number;
  /** 项目名称 */
  projectName?: string;
  /** 项目角色 */
  projectRole?: string;
  /** 开始日期 */
  startDate?: string;
  /** 结束日期 */
  endDate?: string;
  /** 项目说明 */
  description?: string;
  /** 职责 */
  responsibility?: string;
  /** 成果 */
  achievement?: string;
  /** 来源类型（字典 talent_source_type） */
  sourceType?: string;
  /** 来源类型中文兜底（后端只读 getter） */
  sourceTypeText?: string;
  /** 来源简历版本ID */
  resumeId?: string | number;
  /** 关联工作经历ID */
  workId?: string | number;
  /** 排序号 */
  sortNo?: number;
  remark?: string;
  createBy?: string | number;
  createByName?: string;
}

/** 项目经历新增/编辑表单（TalentProjectBo） */
export interface HrTalentProjectForm {
  projectId?: string | number;
  talentId?: string | number;
  projectName?: string;
  projectRole?: string;
  startDate?: string;
  endDate?: string;
  description?: string;
  responsibility?: string;
  achievement?: string;
  sourceType?: string;
  resumeId?: string | number;
  workId?: string | number;
  sortNo?: number;
  remark?: string;
}

/* ============================ 人才标签（人才侧） ============================ */

/** 人才标签视图（TalentTagVo） */
export interface HrTalentTagVO extends BaseEntity {
  /** 标签ID */
  tagId?: string | number;
  /** 标签编码（服务端生成） */
  tagCode?: string;
  /** 标签名称 */
  tagName?: string;
  /** 标签类别编码（字典 talent_tag_category） */
  tagCategory?: string;
  /** 标签类别标签（字典 talent_tag_category） */
  tagCategoryLabel?: string;
  /** 标签类别中文兜底（后端只读 getter） */
  tagCategoryText?: string;
  /** 是否敏感（1是 0否） */
  sensitiveFlag?: string;
  /** 排序号 */
  sortNo?: number;
  /** 状态（active/disabled 等稳定编码） */
  status?: string;
  remark?: string;
}

/** 人才标签关系更新入参（PUT /talent/profiles/{id}/tags，TalentProfileTagBo，全量覆盖语义） */
export interface HrTalentProfileTagForm {
  /** 标签ID数组（全量覆盖；空数组表示清空） */
  tagIds?: (string | number)[];
  /** 来源类型（字典 talent_source_type） */
  sourceType?: string;
}

/* ============================ 人才跟进 ============================ */

/** 跟进记录视图（TalentFollowUpVo） */
export interface HrTalentFollowUpVO extends BaseEntity {
  /** 跟进记录ID */
  followId?: string | number;
  /** 人才主档ID */
  talentId?: string | number;
  /** 联系时间 */
  contactTime?: string;
  /** 联系方式（稳定编码，如 phone/wechat/email） */
  contactMethod?: string;
  /** 联系结果编码（字典 talent_contact_result） */
  contactResult?: string;
  /** 联系结果标签（字典 talent_contact_result） */
  contactResultLabel?: string;
  /** 意向变化 */
  intentChange?: string;
  /** 沟通摘要 */
  summary?: string;
  /** 下次联系时间 */
  nextContactTime?: string;
  /** 跟进人用户ID */
  followerId?: string | number;
  /** 跟进人昵称（@Translation 回填） */
  followerName?: string;
  remark?: string;
}

/** 跟进记录检索条件（TalentFollowUpQueryBo + PageQuery） */
export interface HrTalentFollowUpQuery extends PageQuery {
  /** 联系方式 */
  contactMethod?: string;
  /** 联系结果（字典 talent_contact_result） */
  contactResult?: string;
  /** 跟进人用户ID */
  followerId?: string | number;
  /** 联系日期起（yyyy-MM-dd） */
  contactDateBegin?: string;
  /** 联系日期止（yyyy-MM-dd） */
  contactDateEnd?: string;
  /** 下次联系日期起（yyyy-MM-dd） */
  nextContactDateBegin?: string;
  /** 下次联系日期止（yyyy-MM-dd） */
  nextContactDateEnd?: string;
}

/** 跟进记录新增/编辑表单（TalentFollowUpBo） */
export interface HrTalentFollowUpForm {
  followId?: string | number;
  talentId?: string | number;
  /** 联系时间（yyyy-MM-dd HH:mm:ss） */
  contactTime?: string;
  /** 联系方式（稳定编码） */
  contactMethod?: string;
  /** 联系结果（字典 talent_contact_result） */
  contactResult?: string;
  /** 意向变化 */
  intentChange?: string;
  /** 沟通摘要（服务层校验：不得保存与招聘无关的高度敏感个人信息） */
  summary?: string;
  /** 下次联系时间（yyyy-MM-dd HH:mm:ss） */
  nextContactTime?: string;
  /** 跟进人用户ID（为空由服务端取登录人） */
  followerId?: string | number;
  remark?: string;
}
