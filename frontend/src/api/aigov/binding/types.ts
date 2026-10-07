/**
 * Agent 版本绑定类型定义（对齐后端 AigAgentBindingVo / AigAgentBindingBo）。
 *
 * 绑定决定「受限通道（TESTING/BRAND/DEPT）发布后谁能看见」——通用通道不需要绑定。
 */

/** 绑定行 */
export interface AigAgentBindingVO extends BaseEntity {
  bindingId?: string | number;
  agentVersionId?: string | number;
  companyId?: string | number;
  brandId?: string | number;
  scenarioCode?: string;
  roleScope?: string;
  knowledgeScope?: string;
  releaseChannel?: string;
  /** 是否启用（Y/N） */
  enabled?: string;
  effectiveFrom?: string;
  effectiveTo?: string;
  remark?: string;
}

/** 绑定清单查询 */
export interface AigAgentBindingQuery extends PageQuery {
  agentVersionId?: string | number;
  companyId?: string | number;
  brandId?: string | number;
  scenarioCode?: string;
  releaseChannel?: string;
  enabled?: string;
  params?: Record<string, any>;
}

/** 绑定新增入参 */
export interface AigAgentBindingForm {
  agentVersionId?: string | number;
  companyId?: string | number;
  brandId?: string | number;
  scenarioCode?: string;
  roleScope?: string;
  knowledgeScope?: string;
  /** 留空取版本当前通道；填了必须与版本一致（否则按通道查询会自相矛盾） */
  releaseChannel?: string;
  enabled?: string;
  effectiveFrom?: string;
  effectiveTo?: string;
  remark?: string;
}
