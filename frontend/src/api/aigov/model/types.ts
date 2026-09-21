/**
 * AI 模型治理类型定义（对齐后端 AigModelGovernanceVo / AigModelGovernanceBo）
 *
 * 模型主数据来自 snail-ai 的 sai_model_config；
 * apiEndpoint / secretRef 仅在具备 aig:model:secret 权限时由后端下发，
 * 且 secretRef 只是「引用文本」，绝不含明文密钥。
 *
 * 模型密钥（sai_model_config.api_key）**只写不读**：
 * 表单可提交明文 apiKey，由后端加密落库；列表/详情只回 keyConfigured 布尔位，
 * 任何响应都不含密钥原值。
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
  /** 是否已配置模型密钥（布尔位，由后端在 SQL 内算好，不含密钥原值） */
  keyConfigured?: boolean;
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
 * 模型密钥写入表单（PUT /aigov/model/secret）
 *
 * - apiKey 为**明文**，由后端按 snail-ai 的 SM4 口径加密后落库；
 * - clearKey=true 时忽略 apiKey 并清除已有密钥（显式语义，避免「留空=不修改」的歧义）。
 */
export interface AigModelSecretForm {
  modelId: string | number;
  /** 明文密钥；clearKey=true 时可省略 */
  apiKey?: string;
  /** 是否清除已有密钥 */
  clearKey?: boolean;
}

/**
 * 供应商（GET /aigov/model/providers 与 /providers/all）
 * 只含名称/标识/说明/图标/启停，不含任何连接凭据——供应商表本身就没有密钥列。
 */
export interface AigModelProviderOption {
  providerId?: string | number;
  providerName?: string;
  providerKey?: string;
  description?: string;
  iconUrl?: string;
  isEnabled?: boolean | number;
  /** 该供应商下已登记的模型数量（供应商管理列表用） */
  modelCount?: number;
  createdDt?: string;
}

/** 供应商新增/修改表单（POST/PUT /aigov/model/provider） */
export interface AigModelProviderForm {
  id?: string | number;
  providerName?: string;
  providerKey?: string;
  description?: string;
  iconUrl?: string;
  isEnabled?: boolean;
}

/** 连通性测试结果（POST /aigov/model/{modelId}/test） */
export interface AigModelTestResult {
  ok?: boolean;
  /** LOCAL_ENGINE / SNAIL_AI / OPENAI_COMPATIBLE / UNSUPPORTED */
  probe?: string;
  endpointHost?: string;
  latencyMs?: number;
  message?: string;
  detail?: string;
  /** HEALTHY / UNHEALTHY */
  healthStatus?: string;
  checkedAt?: string;
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
  /** 明文 API 密钥（可选）：由后端加密落库，非空时额外要求 aig:model:secret */
  apiKey?: string;
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
