/**
 * 人才池 / 分组 / 标签字典 域类型定义
 *
 * 契约来源（按优先级）：
 * 1. SPEC-P4 §2.3 C 线（docs/hr-talent/SPEC-P4-人才管理.md）
 * 2. 后端已实现类：
 *    - TalentPoolController（/talent/pools）+ TalentPoolVo/Bo、TalentPoolMemberVo/Bo、TalentPoolQueryBo
 *    - TalentGroupController（/talent/groups）+ TalentGroupVo/Bo、TalentGroupMemberVo、TalentGroupQueryBo
 *    - TalentTagController（/talent/tags）+ TalentTagVo、TalentTagBo、TalentTagQueryBo
 * 3. DDL：script/sql/hr_talent.sql（hr_talent_pool / pool_member / tag / profile_tag / group / group_member）
 *
 * 字段名 = 后端 VO/BO 字段名逐字对齐；bigint 一律用 string | number。
 * 分组接口权限为「talent:pool:member 或 talent:profile:list」二者满足其一（后端 SaMode.OR）。
 */

import type { HrTalentTagVO } from '../profile/types';

/** 人才标签视图与人才侧共用同一后端 VO（TalentTagVo），此处直接复用避免字段漂移 */
export type { HrTalentTagVO };

/* ============================ 人才池 ============================ */

/** 人才池视图（TalentPoolVo） */
export interface HrTalentPoolVO extends BaseEntity {
  /** 人才池ID */
  poolId?: string | number;
  /** 人才池编号（服务端生成） */
  poolCode?: string;
  /** 人才池名称 */
  poolName?: string;
  /** 人才池类型（reserve储备/position岗位定向/talent专项等稳定编码，无字典组） */
  poolType?: string;
  /** 归属部门ID */
  ownerDeptId?: string | number;
  /** 归属部门名称快照 */
  ownerDeptName?: string;
  /** 池管理员用户ID */
  managerId?: string | number;
  /** 池管理员昵称（@Translation 回填） */
  managerName?: string;
  /** 可见范围编码（字典 talent_visibility_type） */
  visibilityType?: string;
  /** 可见范围标签（字典 talent_visibility_type） */
  visibilityTypeLabel?: string;
  /** 人才池说明 */
  poolDesc?: string;
  /** 成员数（服务端重算） */
  memberCount?: number;
  /** 状态（active启用/archived归档等稳定编码，无字典组） */
  status?: string;
  remark?: string;
}

/** 人才池检索条件（TalentPoolQueryBo + PageQuery） */
export interface HrTalentPoolQuery extends PageQuery {
  /** 人才池名称（模糊匹配） */
  poolName?: string;
  /** 人才池编号 */
  poolCode?: string;
  /** 人才池类型 */
  poolType?: string;
  /** 池管理员用户ID */
  managerId?: string | number;
  /** 归属部门ID */
  ownerDeptId?: string | number;
  /** 可见范围（字典 talent_visibility_type） */
  visibilityType?: string;
  /** 状态 */
  status?: string;
}

/** 人才池新增/编辑表单（TalentPoolBo） */
export interface HrTalentPoolForm {
  /** 人才池ID（编辑必填） */
  poolId?: string | number;
  /** 人才池名称（必填，最长 100） */
  poolName?: string;
  /** 人才池类型（稳定编码，无字典组） */
  poolType?: string;
  /** 归属部门ID */
  ownerDeptId?: string | number;
  /** 归属部门名称快照 */
  ownerDeptName?: string;
  /** 池管理员用户ID */
  managerId?: string | number;
  /** 可见范围（字典 talent_visibility_type） */
  visibilityType?: string;
  /** 人才池说明 */
  poolDesc?: string;
  /** 状态 */
  status?: string;
  remark?: string;
}

/* ============================ 人才池成员 ============================ */

/** 人才池成员视图（TalentPoolMemberVo） */
export interface HrTalentPoolMemberVO extends BaseEntity {
  /** 成员关系ID */
  memberId?: string | number;
  /** 人才池ID */
  poolId?: string | number;
  /** 人才主档ID */
  talentId?: string | number;
  /** 人才姓名快照 */
  talentName?: string;
  /** 人才编号 */
  talentNo?: string;
  /** 适配等级（high/medium/low 等稳定编码，无字典组） */
  fitLevel?: string;
  /** 推荐岗位 */
  recommendedJob?: string;
  /** 加入原因 */
  joinReason?: string;
  /** 下次联系时间 */
  nextContactTime?: string;
  /** 成员状态编码（字典 talent_pool_member_status） */
  memberStatus?: string;
  /** 加入人用户ID */
  joinedBy?: string | number;
  /** 加入人昵称（@Translation 回填） */
  joinedByName?: string;
  /** 加入时间 */
  joinedTime?: string;
  /** 移出时间 */
  removedTime?: string;
  /** 移出原因 */
  removedReason?: string;
  remark?: string;
}

/** 加入人才池入参（TalentPoolMemberBo；重复加入后端返回已有关系，不报错） */
export interface HrTalentPoolMemberForm {
  /** 人才主档ID（必填） */
  talentId?: string | number;
  /** 适配等级（稳定编码） */
  fitLevel?: string;
  /** 推荐岗位 */
  recommendedJob?: string;
  /** 加入原因 */
  joinReason?: string;
  /** 下次联系时间（yyyy-MM-dd HH:mm:ss） */
  nextContactTime?: string;
  /** 成员状态（字典 talent_pool_member_status） */
  memberStatus?: string;
  remark?: string;
}

/* ============================ 人才分组 ============================ */

/** 人才分组视图（TalentGroupVo） */
export interface HrTalentGroupVO extends BaseEntity {
  /** 分组ID */
  groupId?: string | number;
  /** 分组编码（服务端生成） */
  groupCode?: string;
  /** 分组名称 */
  groupName?: string;
  /** 分组类型编码（字典 talent_group_type：public/personal） */
  groupType?: string;
  /** 分组类型中文兜底（后端只读 getter） */
  groupTypeText?: string;
  /** 归属部门ID */
  ownerDeptId?: string | number;
  /** 负责人 / 收藏人用户ID */
  ownerId?: string | number;
  /** 负责人昵称（@Translation 回填） */
  ownerName?: string;
  /** 可见范围编码（字典 talent_visibility_type） */
  visibilityType?: string;
  /** 可见范围标签（字典 talent_visibility_type） */
  visibilityTypeLabel?: string;
  /** 状态（active生效/inactive停用等稳定编码） */
  status?: string;
  /** 分组成员数 */
  talentCount?: number;
  remark?: string;
}

/** 人才分组检索条件（TalentGroupQueryBo + PageQuery） */
export interface HrTalentGroupQuery extends PageQuery {
  /** 分组名称（模糊匹配） */
  groupName?: string;
  /** 分组类型（字典 talent_group_type） */
  groupType?: string;
  /** 分组编码 */
  groupCode?: string;
  /** 负责人 / 收藏人用户ID */
  ownerId?: string | number;
  /** 归属部门ID */
  ownerDeptId?: string | number;
  /** 可见范围（字典 talent_visibility_type） */
  visibilityType?: string;
  /** 状态 */
  status?: string;
}

/** 人才分组新增/编辑表单（TalentGroupBo） */
export interface HrTalentGroupForm {
  /** 分组ID（编辑必填） */
  groupId?: string | number;
  /** 分组名称（必填，最长 128） */
  groupName?: string;
  /** 分组类型（字典 talent_group_type：public/personal，缺省 personal） */
  groupType?: string;
  /** 归属部门ID（公共分组的维护部门） */
  ownerDeptId?: string | number;
  /** 负责人 / 收藏人用户ID（为空由服务端取登录用户） */
  ownerId?: string | number;
  /** 可见范围（字典 talent_visibility_type） */
  visibilityType?: string;
  /** 状态 */
  status?: string;
  remark?: string;
}

/** 分组成员视图（TalentGroupMemberVo） */
export interface HrTalentGroupMemberVO extends BaseEntity {
  /** 成员关系ID */
  memberId?: string | number;
  /** 分组ID */
  groupId?: string | number;
  /** 人才主档ID */
  talentId?: string | number;
  /** 人才姓名快照 */
  talentName?: string;
  /** 人才编号 */
  talentNo?: string;
  /** 加入人用户ID */
  addedBy?: string | number;
  /** 加入人昵称（@Translation 回填） */
  addedByName?: string;
  /** 加入时间 */
  addedTime?: string;
  remark?: string;
}

/* ============================ 标签字典 ============================ */

/** 标签字典检索条件（TalentTagQueryBo + PageQuery） */
export interface HrTalentTagQuery extends PageQuery {
  /** 标签名称（模糊匹配） */
  tagName?: string;
  /** 标签编码 */
  tagCode?: string;
  /** 标签类别（字典 talent_tag_category） */
  tagCategory?: string;
  /** 是否敏感（1是 0否） */
  sensitiveFlag?: string;
  /** 状态 */
  status?: string;
  /** 是否只查启用标签 */
  onlyEnabled?: boolean;
}

/**
 * 标签字典新增/编辑表单（TalentTagBo）
 *
 * 服务层会校验敏感标签与歧视性名称：背调失败/健康/家庭/年龄等敏感内容不得自动生成
 * 可被普通用户检索的标签（设计文档 §7.6.4、§8.15）。
 */
export interface HrTalentTagForm {
  /** 标签ID（编辑必填） */
  tagId?: string | number;
  /** 标签名称（必填） */
  tagName?: string;
  /** 标签类别（字典 talent_tag_category） */
  tagCategory?: string;
  /** 是否敏感（1是 0否） */
  sensitiveFlag?: string;
  /** 排序号 */
  sortNo?: number;
  /** 状态 */
  status?: string;
  remark?: string;
}
