/**
 * AI 路由策略类型定义（对齐后端 AigRoutePolicyVo / AigRoutePolicyBo）
 * 按「能力 × 数据等级」唯一确定一条策略，默认拒绝（无策略即 DENIED）。
 */

/** 路由策略列表行 */
export interface AigRoutePolicyVO extends BaseEntity {
  policyId?: string | number;
  capabilityCode?: string;
  /** 关联能力名称（后端联表冗余，可能为空） */
  capabilityName?: string;
  /** 数据等级 PUBLIC/INTERNAL/RESTRICTED */
  dataLevel?: string;
  /** 优先部署类型 LOCAL/GROUP/EXTERNAL_ENTERPRISE/EXTERNAL_API */
  preferredDeployment?: string;
  /** 是否允许外发（Y允许 N禁止）；Y 意味着数据出域，需醒目提示 */
  allowExternal?: string;
  /** 调用前是否需要审批（Y是 N否；阶段1仅作路由判定） */
  requireApproval?: string;
  /** 无可用模型时是否转人工待办（Y是 N否） */
  fallbackToManual?: string;
  /** 状态（0正常 1停用） */
  status?: string;
  remark?: string;
}

/** 新增/编辑表单 */
export interface AigRoutePolicyForm {
  policyId?: string | number;
  capabilityCode?: string;
  dataLevel?: string;
  preferredDeployment?: string;
  allowExternal?: string;
  requireApproval?: string;
  fallbackToManual?: string;
  status?: string;
  remark?: string;
}

/** 查询条件 */
export interface AigRoutePolicyQuery extends PageQuery {
  capabilityCode?: string;
  dataLevel?: string;
  preferredDeployment?: string;
  allowExternal?: string;
  status?: string;
  params?: Record<string, any>;
}
