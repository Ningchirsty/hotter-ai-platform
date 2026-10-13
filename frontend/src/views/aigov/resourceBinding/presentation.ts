import type { AigAgentDeclaredItem, AigAgentDeclaredResourceVO } from '@/api/aigov/resourceBinding/types';

/**
 * 工具/知识绑定的展示助手（纯函数）。
 *
 * <p>为什么抽出来单测：这里的每一句都是"错了不报错"——类型标签映射错了只是显示错的中文；
 * 声明清单读空了会让下拉变成空的，人只会以为"这个版本没声明"；而"已有启用绑定"标错时，
 * 人会以为不能再绑、或以为已经绑好了。</p>
 *
 * @author ai-gov
 */

/** 资源类型 → 中文（与后端 AigAgentResourceTypeEnum 一致） */
export const RESOURCE_TYPE_LABELS: Record<string, string> = {
  TOOL: '工具',
  KNOWLEDGE: '知识范围'
};

/** 资源类型下拉项 */
export const RESOURCE_TYPE_OPTIONS = [
  { value: 'TOOL', label: 'TOOL 工具' },
  { value: 'KNOWLEDGE', label: 'KNOWLEDGE 知识范围' }
];

/** 声明项落在版本上的字段名（报错/提示里点名，人才知道去哪里补声明） */
export const DECLARED_FIELD_NAMES: Record<string, string> = {
  TOOL: 'allowed_tools',
  KNOWLEDGE: 'knowledge_scope_json'
};

/**
 * 资源类型中文名；认不出的 code 原样返回（比硬套「未知」更便于排查）。
 *
 * @param type 类型 code
 * @returns 展示名
 */
export function resourceTypeLabel(type?: string | null): string {
  if (!type) return '未知';
  return RESOURCE_TYPE_LABELS[type] ?? type;
}

/**
 * 版本声明项（防御：服务端可能不返回该项，或返回非数组）。
 *
 * @param declared 声明视图
 * @param type     资源类型
 * @returns 声明项清单
 */
export function declaredItems(
  declared?: AigAgentDeclaredResourceVO | null,
  type?: string | null
): AigAgentDeclaredItem[] {
  const raw = type === 'KNOWLEDGE' ? declared?.knowledgeScopes : declared?.allowedTools;
  if (!Array.isArray(raw)) return [];
  return raw.filter((item): item is AigAgentDeclaredItem => !!item && typeof item.code === 'string');
}

/**
 * 下拉项文案。
 *
 * <p><b>"已有启用绑定"不等于"不能再绑"</b>：那是"某个范围下已经绑过"，
 * 换一个范围（别的品牌/场景）再绑一条是常规做法。所以这里标注而不禁用——
 * 禁用会让一次本来合法的操作变得做不到。</p>
 *
 * @param item 声明项
 * @returns 文案
 */
export function declaredOptionLabel(item: AigAgentDeclaredItem): string {
  return item.bound ? `${item.code}（已有启用绑定）` : item.code;
}

/**
 * 版本未声明任何该项时的提示（可操作：去补哪个字段）。
 *
 * @param declared 声明视图
 * @param type     资源类型
 * @returns 提示文案；有声明时返回空串
 */
export function declaredHint(
  declared?: AigAgentDeclaredResourceVO | null,
  type?: string | null
): string {
  if (!declared) return '先填 Agent 版本ID 并读取声明';
  const items = declaredItems(declared, type);
  if (items.length > 0) return '';
  const field = DECLARED_FIELD_NAMES[type ?? ''] ?? '声明字段';
  return `该版本没有声明任何${resourceTypeLabel(type)}（${field} 为空）。绑定表是声明的收窄，` +
    '请先在版本内容里声明后再绑定——服务端会拒绝绑定未声明的项。';
}

/**
 * 禁止工具提示（解释"为什么扫不到这个工具"）。
 *
 * @param declared 声明视图
 * @returns 提示文案；没有禁止项时返回空串
 */
export function forbiddenNote(declared?: AigAgentDeclaredResourceVO | null): string {
  const forbidden = declared?.forbiddenTools;
  if (!Array.isArray(forbidden) || forbidden.length === 0) return '';
  return `该版本明确禁止：${forbidden.join('、')}（这些工具绑不上，服务端会拒绝）`;
}

/**
 * 绑定行的范围展示（空=不限，必须显式说出来，否则运维会以为漏填了）。
 *
 * @param value 范围值
 * @returns 展示文案
 */
export function scopeLabel(value?: string | number | null): string {
  if (value === null || value === undefined || value === '') return '不限';
  return String(value);
}

/**
 * 一行绑定的范围摘要（四列拼一起，给人"这条到底限定在哪"的一句话）。
 *
 * @param row 绑定行
 * @returns 形如 `不限 / 不限 / 不限 / 不限`
 */
export function scopeSummary(row?: {
  companyId?: string | number | null;
  brandId?: string | number | null;
  scenarioCode?: string | null;
  roleScope?: string | null;
} | null): string {
  if (!row) return '不限 / 不限 / 不限 / 不限';
  return [scopeLabel(row.companyId), scopeLabel(row.brandId),
    scopeLabel(row.scenarioCode), scopeLabel(row.roleScope)].join(' / ');
}

/**
 * 删除确认文案。
 *
 * <p><b>为什么值得单独一个函数并单测</b>：删除是不可撤销的动作，而这句文案要同时说清三件事——
 * 删的是哪一条（类型+编码）、它的范围是什么（否则按编码看一眼就点了，其实可能删错范围）、
 * 以及"删完还能不能重绑"。少说最后一句，人就不敢点，于是绑错的范围永远卡在那里；
 * 少说范围，就会删错那一条。</p>
 *
 * @param row 绑定行
 * @returns 确认文案
 */
export function bindingDeleteConfirmText(row?: {
  bindingId?: string | number | null;
  resourceType?: string | null;
  resourceCode?: string | null;
  companyId?: string | number | null;
  brandId?: string | number | null;
  scenarioCode?: string | null;
  roleScope?: string | null;
} | null): string {
  const code = row?.resourceCode || '（无编码）';
  return `确认删除这条绑定吗？${resourceTypeLabel(row?.resourceType)}「${code}」，`
    + `范围：${scopeSummary(row)}。删除后同范围可以重新绑定。`;
}

/**
 * 绑定行的绑定ID是否可用于删除。
 *
 * <p>服务端返回的是雪花 ID（本仓以字符串下发），但列表数据异常时可能为空——
 * 空 ID 拼进 URL 会打到一个不存在的地址上，得到一个与"删对了"无法区分的错误。
 * 所以在发请求之前先挡住。</p>
 *
 * @param row 绑定行
 * @returns 可用则 true
 */
export function hasDeletableId(row?: { bindingId?: string | number | null } | null): boolean {
  const id = row?.bindingId;
  if (id === null || id === undefined || id === '') return false;
  return String(id).trim() !== '' && String(id).trim() !== 'null' && String(id).trim() !== 'undefined';
}
