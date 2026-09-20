/**
 * AI 业务能力模板类型定义（对齐后端 AigCapabilityVo / AigCapabilityBo / AigCapabilityQueryBo）
 * 字段名与表 aig_capability 列名驼峰化保持一致。
 */

/** 能力列表行 */
export interface AigCapabilityVO extends BaseEntity {
  capabilityId?: string | number;
  /** 能力编码，对外稳定契约，如 talent_match / brief_precheck */
  capabilityCode?: string;
  capabilityName?: string;
  /** 业务目标：该能力服务的业务结果 */
  bizGoal?: string;
  /** 需要的模型能力标签，逗号分隔：TEXT/VISION/OCR/IMAGE/VIDEO/EMBEDDING/RERANK/AGENT */
  requiredTags?: string;
  /** 输入 Schema（JSON 文本） */
  inputSchema?: string;
  /** 输出 Schema（JSON 文本） */
  outputSchema?: string;
  /** 数据策略：LOCAL_ONLY 仅本地 / LOCAL_FIRST 本地优先 / EXTERNAL_ALLOWED 允许外部 */
  dataPolicy?: string;
  /** 必须人工确认的结论点 */
  humanConfirmPoints?: string;
  /** 质量阈值：格式/完整性/可信度/超时与失败处理 */
  qualityThreshold?: string;
  /** 审计等级：SUMMARY 摘要 / FULL 完整输出 / HASH_ONLY 仅哈希 */
  auditLevel?: string;
  /** 状态（0正常 1停用），对应字典 sys_normal_disable */
  status?: string;
  remark?: string;
}

/** 新增/编辑表单 */
export interface AigCapabilityForm {
  capabilityId?: string | number;
  capabilityCode?: string;
  capabilityName?: string;
  bizGoal?: string;
  requiredTags?: string;
  inputSchema?: string;
  outputSchema?: string;
  dataPolicy?: string;
  humanConfirmPoints?: string;
  qualityThreshold?: string;
  auditLevel?: string;
  status?: string;
  remark?: string;
}

/** 查询条件（能力编码/名称/策略/等级/状态） */
export interface AigCapabilityQuery extends PageQuery {
  capabilityCode?: string;
  capabilityName?: string;
  dataPolicy?: string;
  auditLevel?: string;
  status?: string;
  params?: Record<string, any>;
}
