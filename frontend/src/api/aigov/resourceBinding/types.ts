/**
 * 工具/知识绑定类型定义（对齐后端 AigAgentResourceBindingVo / AigAgentDeclaredResourceVo）。
 *
 * <p>绑定表是版本声明的**收窄**：声明说"这个版本可以碰哪些工具/知识"，绑定说
 * "在什么范围下真的给它用"。它只登记与收窄，**没有执行语义**（本仓无 Tool Gateway / Knowledge 引擎）。</p>
 */

/** 绑定行 */
export interface AigAgentResourceBindingVO extends BaseEntity {
  bindingId?: string | number;
  agentVersionId?: string | number;
  /** TOOL=工具 / KNOWLEDGE=知识范围 */
  resourceType?: string;
  resourceCode?: string;
  companyId?: string | number;
  brandId?: string | number;
  scenarioCode?: string;
  roleScope?: string;
  /** 是否启用（Y/N） */
  enabled?: string;
  createTime?: string;
  updateTime?: string;
  remark?: string;
}

/** 绑定清单查询 */
export interface AigAgentResourceBindingQuery extends PageQuery {
  agentVersionId?: string | number;
  resourceType?: string;
  resourceCode?: string;
  companyId?: string | number;
  brandId?: string | number;
  enabled?: string;
  params?: Record<string, any>;
}

/** 新增绑定入参 */
export interface AigAgentResourceBindingForm {
  agentVersionId?: string | number;
  resourceType?: string;
  resourceCode?: string;
  companyId?: string | number;
  brandId?: string | number;
  scenarioCode?: string;
  roleScope?: string;
  enabled?: string;
  remark?: string;
}

/** 一项声明 */
export interface AigAgentDeclaredItem {
  code: string;
  /** 是否已有启用中的绑定（注意：这是"任意范围"，换个范围仍可再绑一条） */
  bound: boolean;
}

/** 某个版本的声明视图（服务端算好；页面不自己 split 版本行上的逗号串） */
export interface AigAgentDeclaredResourceVO {
  agentVersionId?: string | number;
  version?: string;
  allowedTools?: AigAgentDeclaredItem[];
  forbiddenTools?: string[];
  knowledgeScopes?: AigAgentDeclaredItem[];
}
