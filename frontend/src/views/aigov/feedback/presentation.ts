/**
 * 反馈与运营页的展示口径（纯函数，可单测）。
 *
 * <p>把"码 → 文案 / 标签色 / 提交前校验"集中在一处：界面与后端判据分叉时，
 * 用户会看到"界面允许提交、后端拒绝"，而那只会在点下去之后才暴露。</p>
 *
 * @author ai-gov
 */

export interface CodeOption {
  code: string;
  label: string;
}

/** 反馈原因（与后端 AigFeedbackReasonEnum 一致） */
export const REASON_OPTIONS: readonly CodeOption[] = [
  { code: 'USEFUL', label: '有用' },
  { code: 'FACT_ERROR', label: '事实有误' },
  { code: 'CITATION_MISMATCH', label: '引用不符' },
  { code: 'MISSING_SOURCE', label: '资料缺失' },
  { code: 'FORMAT_MISMATCH', label: '格式不符' },
  { code: 'FLOW_INTERRUPTED', label: '流程中断' }
] as const;

/** 处理结论（与后端 AigFeedbackVerdictEnum 一致） */
export const VERDICT_OPTIONS: readonly CodeOption[] = [
  { code: 'PENDING', label: '待处理' },
  { code: 'CONFIRMED', label: '已确认问题' },
  { code: 'REJECTED', label: '已驳回' }
] as const;

/** 处理动作（与后端 AigFeedbackActionEnum 一致） */
export const ACTION_OPTIONS: readonly CodeOption[] = [
  { code: 'ADD_KNOWLEDGE', label: '补知识' },
  { code: 'CHANGE_RULE', label: '改规则' },
  { code: 'ADD_CASE', label: '加入用例' },
  { code: 'CLOSE', label: '关闭' }
] as const;

/** 队列过滤选项（比处理结论多一个"全部"） */
export const QUEUE_FILTERS: readonly CodeOption[] = [
  { code: '', label: '待处理（默认）' },
  { code: 'CONFIRMED', label: '已确认问题' },
  { code: 'REJECTED', label: '已驳回' }
] as const;

/** 队列的动作过滤（含"全部动作"） */
export const QUEUE_ACTION_FILTERS: readonly CodeOption[] = [
  { code: '', label: '全部动作' },
  { code: 'ADD_KNOWLEDGE', label: '补知识' },
  { code: 'CHANGE_RULE', label: '改规则' },
  { code: 'ADD_CASE', label: '加入用例' },
  { code: 'CLOSE', label: '关闭' }
] as const;

/** 队列的原因过滤（含"全部原因"） */
export const QUEUE_REASON_FILTERS: readonly CodeOption[] = [
  { code: '', label: '全部原因' },
  { code: 'USEFUL', label: '有用' },
  { code: 'FACT_ERROR', label: '事实有误' },
  { code: 'CITATION_MISMATCH', label: '引用不符' },
  { code: 'MISSING_SOURCE', label: '资料缺失' },
  { code: 'FORMAT_MISMATCH', label: '格式不符' },
  { code: 'FLOW_INTERRUPTED', label: '流程中断' }
] as const;

/**
 * 按动作过滤时给一句"接下来做什么"。
 *
 * <p>筛选结果本身不说明去向：把一批问题挑出来之后，人要知道它落到哪个模块才算闭环。
 * 这段话是"动作只是送进改进流程、不直接改线上版本"这条规矩的落地说明。</p>
 */
export function queueActionHint(actionCode?: string): string {
  switch (actionCode) {
    case 'ADD_KNOWLEDGE':
      return '这些是要补知识的问题：到知识中心登记/补版本，处理到「已就绪」才会被检索。';
    case 'CHANGE_RULE':
      return '这些是要改规则的问题：到训练台改草稿并提交 DRAFT 版本，仍走既有发布门槛。';
    case 'ADD_CASE':
      return '这些是要加入用例的问题：用例定义按版本的 golden_cases 声明，本页只负责把问题标给用例负责人；问题原文留在 detailRef 指向的受控出处，不直接进通用黄金数据集。';
    case 'CLOSE':
      return '这些是已判定无需动作的问题（重复反馈或已修）。';
    default:
      return '按动作过滤可以只挑出"要我处理的那一份"，而不是把整条队列从头翻一遍。';
  }
}

/**
 * 按码取文案；认不出的码原样展示（而不是显示空白——空白会让人以为没值）。
 */
export function labelOf(options: readonly CodeOption[], code?: string): string {
  if (!code) {
    return '—';
  }
  return options.find(item => item.code === code)?.label ?? code;
}

export function reasonLabel(code?: string): string {
  return labelOf(REASON_OPTIONS, code);
}

export function verdictLabel(code?: string): string {
  return labelOf(VERDICT_OPTIONS, code);
}

export function actionLabel(code?: string): string {
  return labelOf(ACTION_OPTIONS, code);
}

/** 结论标签色：待处理=橙（待确认）、已确认=绿（要跟进）、已驳回=灰（已结束） */
export function verdictTagType(code?: string): 'warning' | 'success' | 'info' {
  if (code === 'PENDING') {
    return 'warning';
  }
  if (code === 'CONFIRMED') {
    return 'success';
  }
  return 'info';
}

/**
 * 提交前的校验（与后端同一口径）。
 *
 * @returns 校验通过返回 null；否则返回可显示的拒绝原因
 */
export function handleValidation(form: { verdict?: string; actionCode?: string } | undefined): string | null {
  const verdict = form?.verdict;
  if (verdict !== 'CONFIRMED' && verdict !== 'REJECTED') {
    return '请选择处理结论（确认问题或驳回）';
  }
  if (verdict === 'CONFIRMED' && !form?.actionCode) {
    return '确认问题时必须选择处理动作（补知识/改规则/加入用例/关闭）';
  }
  return null;
}

/** 比率 → 百分比文本（保留 1 位；无值显示 —） */
export function rateText(rate?: number): string {
  if (rate === undefined || rate === null) {
    return '—';
  }
  return (rate * 100).toFixed(1) + '%';
}

/** 故障分类 → 表格行；后端已按次数降序返回，这里保持其顺序 */
export function faultClassRows(map?: Record<string, number>): Array<{ code: string; count: number }> {
  if (!map) {
    return [];
  }
  return Object.entries(map).map(([code, count]) => ({ code, count: Number(count) || 0 }));
}
