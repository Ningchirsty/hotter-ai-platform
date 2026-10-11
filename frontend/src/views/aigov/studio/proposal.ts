import type {
  AigStudioChangeProposalVO,
  AigStudioCopilotTurnVO,
  AigStudioProposalDiffItem
} from '@/api/aigov/studio/types';

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

/* ------------------------------------------------------------------ 对话式 Copilot */

/** 对话结论 → 中文（与后端 AigStudioCopilotStatusEnum 一致） */
export const COPILOT_STATUS_LABELS: Record<string, string> = {
  SUCCEEDED: '已产出提案',
  REJECTED: '提案被拒绝',
  FAILED: '调用失败',
  RUNNING: '进行中'
};

/** 数据等级（与后端 AigInvokeBo 的取值一致） */
export const DATA_LEVEL_OPTIONS = [
  { value: 'PUBLIC', label: 'PUBLIC（公开）' },
  { value: 'INTERNAL', label: 'INTERNAL（内部）' },
  { value: 'RESTRICTED', label: 'RESTRICTED（受限）' },
  { value: 'STRICT', label: 'STRICT（严格级，通常禁止外发）' }
];

/**
 * 对话结论中文名；认不出的 code 原样返回。
 *
 * @param status 结论 code
 * @returns 展示名
 */
export function copilotStatusLabel(status?: string | null): string {
  if (!status) return '未知';
  return COPILOT_STATUS_LABELS[status] ?? status;
}

/**
 * 结论标签色。
 *
 * <p><b>REJECTED 必须是"警告"而不是"错误"</b>：它是"模型试图改白名单外的东西被平台挡住了"，
 * 这正是这条通道在正常工作；标成红色会让人以为系统坏了，进而去找系统的错。</p>
 *
 * @param status 结论 code
 * @returns tag type
 */
export function copilotStatusTag(status?: string | null): 'success' | 'warning' | 'danger' | 'info' {
  if (status === 'SUCCEEDED') return 'success';
  if (status === 'REJECTED') return 'warning';
  if (status === 'FAILED') return 'danger';
  return 'info';
}

/**
 * 一轮对话的一句话结论（用于会话历史里那行）。
 *
 * @param turn 轮次
 * @returns 摘要
 */
export function copilotTurnSummary(turn?: AigStudioCopilotTurnVO | null): string {
  if (!turn) return '';
  const revision = turn.revision === undefined || turn.revision === null ? '—' : String(turn.revision);
  if (turn.status === 'SUCCEEDED' && turn.proposalId !== undefined && turn.proposalId !== null) {
    return `已产出提案 #${turn.proposalId}（基于修订 #${revision}）`;
  }
  return `${copilotStatusLabel(turn.status)}（基于修订 #${revision}）`;
}

/**
 * 能不能发送：消息非空且选了数据等级。
 *
 * <p>数据等级必填不是形式主义：它决定这次内容能不能外发，选错等级等于测错了对象
 * （与训练台测试调用同一条口径）。</p>
 *
 * @param message    消息
 * @param dataLevel  数据等级
 * @returns 可发送则 true
 */
export function canChat(message?: string | null, dataLevel?: string | null): boolean {
  return !!message && message.trim() !== '' && !!dataLevel && String(dataLevel).trim() !== '';
}
