/**
 * 工具注册类型（对应后端 `/aigov/tools`）。
 *
 * @author ai-gov
 */

/** 工具版本（可绑定清单行） */
export interface AigToolVersionVO {
  toolCode?: string;
  toolName?: string;
  version?: string;
  /** READ_ONLY_QUERY / GENERATE_DRAFT / MUTATE_BUSINESS_DATA / EXTERNAL_SEND */
  operationType?: string;
  operationTypeDesc?: string;
  /** 是否必须人工确认 */
  requiresConfirmation?: boolean;
  /** 是否已接入执行器（false=已登记 待接入，不可执行） */
  executable?: boolean;
  permissionPoint?: string;
  dataLevelMax?: string;
}

/** 新建工具定义 */
export interface AigToolDefinitionForm {
  toolCode?: string;
  toolName?: string;
  description?: string;
  dataPolicy?: string;
  remark?: string;
}

/** 新增工具版本（执行契约） */
export interface AigToolVersionForm {
  toolCode?: string;
  version?: string;
  /** 可空=仅登记、待接入 */
  executorRef?: string;
  operationType?: string;
  inputSchema?: string;
  outputSchema?: string;
  timeoutMs?: number;
  maxAttempts?: number;
  permissionPoint?: string;
  dataLevelMax?: string;
  remark?: string;
}
