/**
 * AI 模型治理类型定义（对齐后端 AigModelGovernanceVo / AigModelGovernanceBo）
 *
 * 模型主数据来自 snail-ai 的 sai_model_config（只读），本层只补治理属性；
 * apiEndpoint / secretRef 仅在具备 aig:model:secret 权限时由后端下发，
 * 且 secretRef 只是「引用文本」，绝不含明文密钥。
 */

/** 模型治理列表行（sai_model_config 左连 aig_model_governance） */
export interface AigModelGovernanceVO extends BaseEntity {
  /** 关联 sai_model_config.id */
  modelId?: string | number;
  /** snail-ai 模型键 */
  modelKey?: string;
  /** snail-ai 模型名称 */
  modelName?: string;
  /** snail-ai 模型类型，如 CHAT / EMBEDDING */
  modelType?: string;
  /** 是否默认模型（1是 0否） */
  isDefault?: number | string;
  /** 是否启用（1启用 0停用） */
  isEnabled?: number | string;
  /** 端点：仅 aig:model:secret 权限时下发 */
  apiEndpoint?: string;
  /** 治理记录ID，为空表示尚未登记治理属性 */
  governanceId?: string | number;
  /** 部署类型：LOCAL/GROUP/EXTERNAL_ENTERPRISE/EXTERNAL_API */
  deploymentType?: string;
  /** 允许处理的最高数据等级：PUBLIC/INTERNAL/RESTRICTED */
  dataLevelMax?: string;
  /** 可用状态：CANDIDATE/TRIAL/GRAY/PRODUCTION/SUSPENDED/RETIRED */
  lifecycleStatus?: string;
  /** 密钥引用：仅 aig:model:secret 权限时下发，禁止出现明文密钥 */
  secretRef?: string;
  /** 输入限制：文本长度/文件类型/图片视频大小/并发 */
  inputLimits?: string;
  /** 输出限制：格式/时长/分辨率/结构化输出能力 */
  outputLimits?: string;
  /** 成本与配额：单次/单项目/单日预算与限流规则 */
  costLimit?: string;
  /** 技术负责人 */
  ownerTech?: string;
  /** 业务负责人 */
  ownerBiz?: string;
  /** 安全审批人 */
  ownerSecurity?: string;
  /** 有效期起 */
  validFrom?: string;
  /** 有效期止 */
  validTo?: string;
  /** 最近健康检查结果 UP/DOWN/DEGRADED */
  healthStatus?: string;
  healthTime?: string;
  /** 状态（0正常 1停用） */
  status?: string;
  remark?: string;
}

/** 治理属性编辑表单（PUT /aigov/model/governance） */
export interface AigModelGovernanceForm {
  governanceId?: string | number;
  modelId?: string | number;
  deploymentType?: string;
  dataLevelMax?: string;
  lifecycleStatus?: string;
  secretRef?: string;
  inputLimits?: string;
  outputLimits?: string;
  costLimit?: string;
  ownerTech?: string;
  ownerBiz?: string;
  ownerSecurity?: string;
  validFrom?: string;
  validTo?: string;
  status?: string;
  remark?: string;
}

/** 查询条件 */
export interface AigModelQuery extends PageQuery {
  modelKey?: string;
  modelName?: string;
  modelType?: string;
  deploymentType?: string;
  dataLevelMax?: string;
  lifecycleStatus?: string;
  isEnabled?: number | string;
  params?: Record<string, any>;
}

/**
 * 供应商下拉选项（GET /aigov/model/providers）
 * 只含名称与标识，不含任何连接凭据。
 */
export interface AigModelProviderOption {
  providerId?: string | number;
  providerName?: string;
  providerKey?: string;
}

/**
 * 新增模型表单（POST /aigov/model）
 * 一次提交两件事：模型主数据 + 首份治理属性。
 * 治理三项（deploymentType/dataLevelMax/lifecycleStatus）必填——缺治理属性的模型
 * 会被路由引擎静默排除，形成「存在但永远选不中」的孤儿模型。
 */
export interface AigModelCreateForm {
  providerId?: string | number;
  modelName?: string;
  modelKey?: string;
  modelType?: string;
  adapterKey?: string;
  apiEndpoint?: string;
  description?: string;
  scope?: string;
  isDefault?: boolean;
  isEnabled?: boolean;
  deploymentType?: string;
  dataLevelMax?: string;
  lifecycleStatus?: string;
  secretRef?: string;
  costLimit?: string;
  ownerTech?: string;
  ownerBiz?: string;
  ownerSecurity?: string;
  validFrom?: string;
  validTo?: string;
  remark?: string;
}
