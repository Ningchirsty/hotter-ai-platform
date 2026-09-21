/**
 * 重复人才治理与合并 域类型定义
 *
 * 契约来源（按优先级）：
 * 1. SPEC-P4 §2.5 E 线（docs/hr-talent/SPEC-P4-人才管理.md）
 * 2. 后端已实现类：TalentDuplicateController（/talent/duplicates）
 *    + TalentDuplicateCaseVo、TalentDuplicateQueryBo
 *    + TalentMergePreviewVo（FieldDiff / RelationSummary）、TalentMergeBo（FieldDecision）、TalentMergeLogVo
 *    + DuplicateConfirmBo、DuplicateIgnoreBo、TalentMergeFields
 * 3. DDL：script/sql/hr_talent.sql（hr_talent_duplicate_case / hr_talent_merge_log）
 *
 * 字段名 = 后端 VO/BO 字段名逐字对齐；bigint 一律用 string | number。
 * 三级匹配（§8.18）：强 = 标准化手机号/邮箱哈希相同（阻止静默新增）；
 * 中 = 姓名 + 公司/学校/简历哈希；弱 = 姓名 + 岗位方向（仅提示）。
 */

import type { HrTalentProfileVO } from '../profile/types';

/** 疑似重复案件视图（TalentDuplicateCaseVo） */
export interface HrTalentDuplicateCaseVO extends BaseEntity {
  /** 疑似重复案件ID */
  caseId?: string | number;
  /** 来源（疑似重复）人才主档ID */
  sourceTalentId?: string | number;
  /** 目标人才主档ID */
  targetTalentId?: string | number;
  /** 匹配级别编码（strong/medium/weak） */
  matchLevel?: string;
  /** 匹配级别标签（后端 fillEnumLabels 回填：强匹配/中匹配/弱匹配） */
  matchLevelLabel?: string;
  /** 匹配原因 */
  matchReason?: string;
  /** 匹配得分 */
  matchScore?: number;
  /** 处理状态编码（字典 talent_duplicate_status：pending/merged/not_same/ignored/confirmed） */
  status?: string;
  /** 处理状态标签（后端 fillEnumLabels 回填） */
  statusLabel?: string;
  /** 处理人用户ID */
  handledBy?: string | number;
  /** 处理人昵称（@Translation 回填） */
  handledByName?: string;
  /** 处理时间 */
  handledTime?: string;
  /** 来源人才姓名 */
  sourceTalentName?: string;
  /** 来源人才编号 */
  sourceTalentNo?: string;
  /** 来源人才当前公司 */
  sourceCurrentCompany?: string;
  /** 来源人才期望岗位 */
  sourceExpectedPosition?: string;
  /** 来源人才归属部门名称 */
  sourceOwnerDeptName?: string;
  /** 目标人才姓名 */
  targetTalentName?: string;
  /** 目标人才编号 */
  targetTalentNo?: string;
  /** 目标人才当前公司 */
  targetCurrentCompany?: string;
  /** 目标人才期望岗位 */
  targetExpectedPosition?: string;
  /** 目标人才归属部门名称 */
  targetOwnerDeptName?: string;
  remark?: string;
}

/** 疑似重复案件检索条件（TalentDuplicateQueryBo + PageQuery） */
export interface HrTalentDuplicateQuery extends PageQuery {
  /** 处理状态（字典 talent_duplicate_status） */
  status?: string;
  /** 匹配级别（strong/medium/weak） */
  matchLevel?: string;
  /** 来源人才主档ID */
  sourceTalentId?: string | number;
  /** 目标人才主档ID */
  targetTalentId?: string | number;
  /** 姓名（来源或目标命中） */
  name?: string;
  /** 人才编号（来源或目标命中） */
  talentNo?: string;
  /** 创建日期起（yyyy-MM-dd） */
  createDateBegin?: string;
  /** 创建日期止（yyyy-MM-dd） */
  createDateEnd?: string;
}

/** 确认疑似重复入参（DuplicateConfirmBo；确认后案件置为「已确认待合并」，不改动人才资料） */
export interface HrTalentDuplicateConfirmForm {
  /** 确认结论：是否为同一人（必填） */
  samePerson: boolean;
  /** 确认依据（服务层要求填写，用于审计追溯） */
  reason?: string;
  /** 下一次复核提醒日期（可空） */
  reviewDate?: string;
}

/** 忽略疑似重复入参（DuplicateIgnoreBo；不改动任何人才资料） */
export interface HrTalentDuplicateIgnoreForm {
  /** 忽略原因（服务端建议填写，用于留痕） */
  reason?: string;
  /** 处理结论：是否判定为非同一人（true 非同一人 / false 仅本次忽略） */
  notSamePerson?: boolean;
}

/** 合并预览的单个冲突字段差异（TalentMergePreviewVo.FieldDiff） */
export interface HrTalentMergeFieldDiff {
  /** 字段名（见后端 TalentMergeFields） */
  field?: string;
  /** 字段中文标签（服务端下发，页面直接展示） */
  label?: string;
  /** 保留主档当前值 */
  keepValue?: string;
  /** 被合并主档当前值 */
  mergedValue?: string;
  /** 是否允许人工选择取值来源 */
  selectable?: boolean;
  /** 默认取值来源（keep / merged） */
  defaultFrom?: string;
}

/** 合并预览的关系迁移统计（TalentMergePreviewVo.RelationSummary） */
export interface HrTalentMergeRelationSummary {
  /** 关系所在表名 */
  table?: string;
  /** 中文标签 */
  label?: string;
  /** 被合并主档侧关系数量 */
  mergedCount?: number;
  /** 保留主档侧关系数量 */
  keepCount?: number;
  /** 迁移方式说明 */
  migrateMode?: string;
}

/** 合并预览结果（TalentMergePreviewVo，不落库不改数据） */
export interface HrTalentMergePreviewVO {
  /** 保留主档（含 version，合并时必须回传） */
  keepTalent?: HrTalentProfileVO;
  /** 被合并主档（含 version，合并时必须回传） */
  mergedTalent?: HrTalentProfileVO;
  /** 冲突字段差异列表 */
  fieldDiffs?: HrTalentMergeFieldDiff[];
  /** 关系迁移统计 */
  relations?: HrTalentMergeRelationSummary[];
  /** 合并风险提示 */
  warnings?: string[];
  /** 服务端是否允许本次合并（仅集团人才管理员可执行） */
  mergeAllowed?: boolean;
}

/** 合并入参（TalentMergeBo；单事务合并，仅集团人才管理员可执行） */
export interface HrTalentMergeForm {
  /** 保留（主）人才主档ID（必填） */
  keepTalentId: string | number;
  /** 保留主档乐观锁版本号（必填） */
  keepVersion: number;
  /** 被合并（从）人才主档ID（必填） */
  mergedTalentId: string | number;
  /** 被合并主档乐观锁版本号（必填） */
  mergedVersion: number;
  /** 合并原因（必填） */
  mergeReason: string;
  /** 冲突字段取值决策（为空表示所有冲突字段保留「保留主档」的现值） */
  fieldDecisions?: HrTalentMergeFieldDecision[];
}

/** 单个冲突字段的取值来源（TalentMergeBo.FieldDecision，from 只接受 keep / merged） */
export interface HrTalentMergeFieldDecision {
  /** 字段名 */
  field: string;
  /** 取值来源（keep / merged） */
  from: 'keep' | 'merged';
}

/** 合并日志快照（TalentMergeLogVo，合并后不可普通撤销，只能查看快照） */
export interface HrTalentMergeLogVO extends BaseEntity {
  /** 合并日志ID */
  mergeId?: string | number;
  /** 保留主档ID */
  keepTalentId?: string | number;
  /** 被合并主档ID */
  mergedTalentId?: string | number;
  /** 冲突字段决策快照（JSON 字符串） */
  fieldDecisionJson?: string;
  /** 关系迁移数量快照（JSON 字符串） */
  relationCountJson?: string;
  /** 合并原因 */
  mergeReason?: string;
  /** 操作人用户ID */
  operatorId?: string | number;
  /** 操作人昵称（@Translation 回填） */
  operatorName?: string;
  /** 操作时间 */
  operateTime?: string;
}
