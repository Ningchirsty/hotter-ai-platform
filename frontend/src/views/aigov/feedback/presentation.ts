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
