/**
 * 工具页展示口径（纯函数，可单测）。
 *
 * @author ai-gov
 */

export interface CodeOption {
  code: string;
  label: string;
}

/** 操作类型（与后端 AigToolOperationTypeEnum 一致） */
export const OPERATION_TYPE_OPTIONS: readonly CodeOption[] = [
  { code: 'READ_ONLY_QUERY', label: '只读查询' },
  { code: 'GENERATE_DRAFT', label: '生成草稿' },
  { code: 'MUTATE_BUSINESS_DATA', label: '变更业务数据' },
  { code: 'EXTERNAL_SEND', label: '外部发送' }
] as const;

/** 需要人工确认的操作类型（有业务后果） */
const MUTATING = new Set(['MUTATE_BUSINESS_DATA', 'EXTERNAL_SEND']);

/** 按码取文案；认不出的码原样展示 */
export function operationTypeLabel(code?: string): string {
  if (!code) {
    return '—';
  }
  return OPERATION_TYPE_OPTIONS.find(item => item.code === code)?.label ?? code;
}

/** 是否属于需要人工确认的操作 */
export function requiresConfirmation(code?: string): boolean {
  return !!code && MUTATING.has(code);
}

/** 接入状态文案：未接入一律显示「已登记 待接入」，不能显示为可用 */
export function executableText(executable?: boolean): string {
  return executable ? '可执行' : '已登记 待接入';
}

/** 接入状态标签色 */
export function executableTagType(executable?: boolean): 'success' | 'info' {
  return executable ? 'success' : 'info';
}

/** 写操作提示（确认绑定输入摘要，参数变化会使原批准失效） */
export function confirmationHint(operationType?: string): string {
  return requiresConfirmation(operationType)
    ? '该工具会改变业务数据或对外发送：调用前必须取得人工确认，且确认绑定输入摘要，参数变化会使原批准失效。'
    : '';
}

/**
 * 所需权限点文案（留空 = 不额外要求，与后端 `permissionPermit` 同口径）。
 *
 * <p>空白显示成空白，读表的人分不清"没声明"和"数据没带回来"，所以空值要给一句明确的话。</p>
 */
export function permissionPointText(point?: string): string {
  const text = point?.trim();
  return text ? text : '无需额外权限';
}

/** 权限点提示：填了会发生什么（这段话是"配置不生效"那类误解的解药） */
export function permissionPointHint(point?: string): string {
  const text = point?.trim();
  return text
    ? `调用人必须持有权限点「${text}」，否则调用会被拒绝（TOOL_PERMISSION_DENIED）。`
    : '留空 = 不额外要求权限点；调用人仍须通过有效绑定判定，写操作仍须人工确认。';
}

/**
 * 新建工具定义校验。
 *
 * @returns 通过返回 null；否则返回可显示的拒绝原因
 */
export function definitionValidation(form: { toolCode?: string; toolName?: string }): string | null {
  if (!form?.toolCode || !form.toolCode.trim()) {
    return '请填写工具编码';
  }
  if (!form?.toolName || !form.toolName.trim()) {
    return '请填写工具名称';
  }
  return null;
}

/**
 * 新增工具版本校验（与后端同口径）。
 *
 * @returns 通过返回 null；否则返回可显示的拒绝原因
 */
export function versionValidation(form: {
  toolCode?: string;
  version?: string;
  operationType?: string;
  timeoutMs?: number;
}): string | null {
  if (!form?.toolCode || !form.toolCode.trim()) {
    return '请填写工具编码';
  }
  if (!form?.version || !form.version.trim()) {
    return '请填写版本号';
  }
  if (!OPERATION_TYPE_OPTIONS.some(item => item.code === form.operationType)) {
    return '请选择操作类型（只读查询/生成草稿/变更业务数据/外部发送）';
  }
  if (form.timeoutMs !== undefined && form.timeoutMs !== null && form.timeoutMs <= 0) {
    return '超时必须是正数（毫秒）';
  }
  return null;
}
