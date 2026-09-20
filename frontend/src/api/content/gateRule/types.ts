/**
 * 闸门规则类型定义（对齐后端 CpGateRuleVo / ContentGateRuleBo）。
 *
 * 闸门规则是表驱动的强制项清单：运营可改，不发版（SPEC 基线 B7）。
 */

/** 规则列表/详情行 */
export interface CpGateRuleVO extends BaseEntity {
  ruleId?: string | number;
  /** 交付类型（cp_deliverable_type） */
  deliverableType?: string;
  /** 要求确认的事实字段编码 */
  fieldCode?: string;
  fieldName?: string;
  /** 闸门等级：BLOCK / CONDITION / NOTICE（cp_gate_level） */
  gateLevel?: string;
  /** 是否必须存在（Y/N） */
  requirePresent?: string;
  /** 是否启用（0启用 1停用），与平台 sys_normal_disable 同口径 */
  enabled?: string;
  sortNo?: number;
  remark?: string;
}

/** 新增/编辑表单 */
export interface CpGateRuleForm {
  ruleId?: string | number;
  deliverableType?: string;
  fieldCode?: string;
  fieldName?: string;
  gateLevel?: string;
  requirePresent?: string;
  enabled?: string;
  sortNo?: number;
  remark?: string;
}

/** 查询条件（交付类型走 queryDeliverableType，其余为字段直传） */
export interface CpGateRuleQuery extends PageQuery {
  queryDeliverableType?: string;
  fieldCode?: string;
  gateLevel?: string;
  enabled?: string;
  params?: Record<string, any>;
}
