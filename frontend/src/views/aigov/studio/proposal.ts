import type { AigStudioChangeProposalVO, AigStudioProposalDiffItem } from '@/api/aigov/studio/types';

/**
 * 训练台：变更提案的纯展示助手（服务端只给 code，页面这一层不该各写一套映射）。
 *
 * 为什么抽出来单测：这几句在页面上"看不出来错"——状态标签映射错了只会显示错的中文，
 * 预览列表降级错了只会少显示几条改动，而人正是靠这个决定"要不要采纳"。
 *
 * @author ai-gov
 */

/** 提案状态 → 中文（与后端 AigStudioProposalStatusEnum 一致） */
export const PROPOSAL_STATUS_LABELS: Record<string, string> = {
  PENDING: '待处理',
  APPLIED: '已采纳',
  REJECTED: '已拒绝'
};

/** 提案来源 → 中文（与后端 AigStudioProposalSourceEnum 一致） */
export const PROPOSAL_SOURCE_LABELS: Record<string, string> = {
  COPILOT: '培训助手',
  MANUAL: '人工草拟'
};

/**
 * 状态中文名；认不出的 code 原样返回（比硬套一个"未知"更有助于排查）。
 *
 * @param status 状态 code
 * @returns 展示名
 */
export function proposalStatusLabel(status?: string | null): string {
  if (!status) return '未知';
  return PROPOSAL_STATUS_LABELS[status] ?? status;
}

/**
 * 状态对应的标签色（Element Plus tag type）。
 *
 * @param status 状态 code
 * @returns tag type
 */
export function proposalStatusTag(status?: string | null): 'warning' | 'success' | 'info' {
  if (status === 'APPLIED') return 'success';
  if (status === 'REJECTED') return 'info';
  return 'warning';
}

/**
 * 来源中文名；认不出的 code 原样返回。
 *
 * @param source 来源 code
 * @returns 展示名
 */
export function proposalSourceLabel(source?: string | null): string {
  if (!source) return '未知';
  return PROPOSAL_SOURCE_LABELS[source] ?? source;
}

/**
 * 是否还能处理（只有待处理的提案能采纳/拒绝）。
 *
 * @param proposal 提案
 * @returns 可处理则 true
 */
export function isPending(proposal?: AigStudioChangeProposalVO | null): boolean {
  return proposal?.status === 'PENDING';
}

/**
 * 改动预览项（防御：服务端可能不返回 preview，或返回非数组）。
 *
 * @param proposal 提案
 * @returns 预览项
 */
export function previewItems(proposal?: AigStudioChangeProposalVO | null): AigStudioProposalDiffItem[] {
  const preview = proposal?.preview;
  if (!Array.isArray(preview)) return [];
  return preview.filter(item => item && typeof item.path === 'string' && item.path !== '');
}

/**
 * 改前/改后值的展示文本：空值明确显示"（空）"，而不是留白让人以为是漏了。
 *
 * @param value 值
 * @returns 展示文本
 */
export function formatDiffValue(value?: string | null): string {
  if (value === null || value === undefined || String(value) === '') return '（空）';
  return String(value);
}

/**
 * 一句话摘要（用于列表里那行"这条提案想改什么"）。
 *
 * @param proposal 提案
 * @returns 摘要
 */
export function proposalSummary(proposal?: AigStudioChangeProposalVO | null): string {
  const paths = previewItems(proposal).map(item => item.path);
  if (paths.length === 0) return '这个提案没有可展示的改动';
  if (paths.length <= 3) return `改 ${paths.length} 处：${paths.join('、')}`;
  return `改 ${paths.length} 处：${paths.slice(0, 3).join('、')} …`;
}
