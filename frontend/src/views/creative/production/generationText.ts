import type { DpGenerationVO, DpStoryboardScreenVO, ScreenProductionVO, TagType } from '@/api/creative/types';
import {
  GENERATION_STATUS_LABELS,
  GENERATION_STATUS_TYPES,
  PRODUCT_VERDICT_LABELS,
  QA_VERDICT_LABELS,
  SCREEN_STATUS_LABELS
} from '@/api/creative/types';

/**
 * 出图候选的**展示口径**（V0.2 R41：从生产页里抽出来，页面与「出图」这一步的组件共用）。
 *
 * <p>为什么单独一个模块：这一步的组件（`GenerationBoard`）与页面上的"跨项目候选总览"都要
 * 显示同一批判定（状态 / 质检 / 产品基准 / 规则体检 / 尺寸 / 耗时 / 所属屏）。
 * 抄两份迟早会漂移——而"同一个 null 两个叫法"正是这一页反复强调要避免的事。</p>
 *
 * <p>纯函数，不依赖任何运行时状态（除传入的 screenMap），可被 vitest 直接断言。</p>
 */

/** 规则体检结论（从 `qaFindingsJson` 解析出来的部分） */
export interface RuleCheck {
  verdict: string;
  failed: Array<{ label: string; level: string }>;
}

/** 规则体检总结论的中文名（后端只在 UNREADABLE 时才是"真有问题"；其余按语义如实显示） */
export const RULE_VERDICT_LABELS: Record<string, string> = {
  NOT_CONFIGURED: '未配置规则',
  PASS: '通过',
  HARD_FAILED: '硬性项未过',
  SOFT_ONLY: '参考项未过',
  UNREADABLE: '读不出图'
};

/**
 * el-table 的插槽行类型是 DefaultRow（不含我们的字段），
 * 数据本身来自 DpGenerationVO，故在模板里做一次显式收窄，而不是把函数参数放宽成 any。
 */
export function asGen(row: unknown): DpGenerationVO {
  return row as DpGenerationVO;
}

export function statusType(value?: string): string {
  return (value && GENERATION_STATUS_TYPES[value]) || 'info';
}

export function statusLabel(value?: string): string {
  return (value && GENERATION_STATUS_LABELS[value]) || value || '';
}

export function qaVerdictLabel(verdict?: string): string {
  if (!verdict) return '未质检';
  return QA_VERDICT_LABELS[verdict] || verdict;
}

/** 产品基准结论：null/空一律显示「未质检」，绝不当成通过 */
export function productVerdictLabel(verdict?: string): string {
  if (!verdict) return '未质检';
  return PRODUCT_VERDICT_LABELS[verdict] || verdict;
}

export function qaClass(verdict?: string): string {
  if (verdict === 'CONSISTENT') return 'good';
  if (verdict === 'INCONSISTENT') return 'bad';
  if (verdict === 'UNCERTAIN') return 'warn';
  return 'muted';
}

/**
 * 解析屏级规则体检结论。
 *
 * 返回 null 表示"这一屏没有体检结论"（没配规则，或这一轮还没有体检过）——
 * 页面据此显示「未配置规则」，绝不显示成"通过"。
 *
 * @param gen 候选
 * @returns 解析结果；无结论或解析失败返回 null
 */
export function ruleCheck(gen: DpGenerationVO): RuleCheck | null {
  const raw = gen.qaFindingsJson;
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as {
      configured?: boolean;
      verdict?: string;
      findings?: { label?: string; level?: string; ok?: boolean }[];
    };
    if (!parsed || !parsed.verdict) return null;
    const failed = (parsed.findings || [])
      .filter((item) => item && item.ok === false)
      .map((item) => ({ label: item.label || '未命名检查项', level: item.level || 'SOFT' }));
    return { verdict: parsed.verdict, failed };
  } catch {
    return null;
  }
}

export function ruleLabel(verdict: string): string {
  return RULE_VERDICT_LABELS[verdict] || verdict;
}

/** 规则体检的样式：HARD 未过才是"必须处理"，SOFT 未过是"值得看一眼"，其余中性 */
export function ruleClass(verdict: string): string {
  if (verdict === 'HARD_FAILED' || verdict === 'UNREADABLE') return 'bad';
  if (verdict === 'SOFT_ONLY') return 'warn';
  if (verdict === 'PASS') return 'good';
  return 'muted';
}

export function formatTime(value?: string): string {
  return value ? value.replace('T', ' ').slice(0, 19) : '';
}

export function sizeText(gen: DpGenerationVO): string {
  return gen.outputWidth ? `${gen.outputWidth}×${gen.outputHeight}` : '—';
}

export function durationText(ms?: number): string {
  return ms ? `${(ms / 1000).toFixed(1)}s` : '—';
}

/**
 * 候选所属的屏（来自 getStoryboard 的最新分镜；取不到就是 null，不编造屏号）。
 *
 * @param gen       候选
 * @param screenMap 屏 id → 屏
 * @returns 屏；找不到返回 null
 */
export function screenOf(
  gen: DpGenerationVO,
  screenMap: Record<string, DpStoryboardScreenVO>
): DpStoryboardScreenVO | null {
  if (gen.screenId == null) return null;
  return screenMap[String(gen.screenId)] || null;
}

export function screenLabel(gen: DpGenerationVO, screenMap: Record<string, DpStoryboardScreenVO>): string {
  const screen = screenOf(gen, screenMap);
  if (screen) return screen.screenNo || String(screen.id);
  return gen.screenId != null ? `屏 ${gen.screenId}` : '未归属屏';
}

export function screenTypeDesc(gen: DpGenerationVO, screenMap: Record<string, DpStoryboardScreenVO>): string {
  return screenOf(gen, screenMap)?.screenTypeDesc || '—';
}

/**
 * 把「页面已加载的屏表」与「按需取到的那一屏」合成一张表。
 *
 * <p><b>为什么需要它</b>：跨项目总览里，候选所属项目的分镜是**预览时按需取一次**的
 * （页面的 `screenMap` 只装当前选中项目的分镜）。如果「屏」这一格用的是页面的 `screenMap`、
 * 而「用途」用的是按需取到的屏，两个字段就会对同一件事给出不同答案——
 * 真机上出现过「屏**屏 2104582808140242946**」（屏号退化成 id）与「用途 卖点」并排。
 * 同一件事必须读同一张表。</p>
 *
 * @param screenMap     页面已加载的屏表（键为屏 id 的字符串形式）
 * @param extraScreen   按需取到的那一屏（可空）
 * @returns 合并后的屏表（`extraScreen` 优先）
 */
export function mergeScreenMap(
  screenMap: Record<string, DpStoryboardScreenVO>,
  extraScreen?: DpStoryboardScreenVO | null
): Record<string, DpStoryboardScreenVO> {
  const merged: Record<string, DpStoryboardScreenVO> = { ...screenMap };
  if (extraScreen && extraScreen.id != null) {
    merged[String(extraScreen.id)] = extraScreen;
  }
  return merged;
}

/** 只有出图完成且还没选定的候选才需要（且能够）选定 */
export function canSelect(gen: DpGenerationVO): boolean {
  return gen.status === 'SUCCEEDED';
}

// ---------------------------------------------------------------------------
// 「按屏」侧的口径（R43：分镜页的"逐屏出图与质检"与生产页的逐屏候选表收敛成同一个组件）
// ---------------------------------------------------------------------------

/**
 * el-table 行收窄成"屏的生产状态"。
 *
 * @param row 表格行
 * @returns 屏生产状态
 */
export function asScreen(row: unknown): ScreenProductionVO {
  return row as ScreenProductionVO;
}

/**
 * 这一屏最新候选的 id（"选定候选/质检"按它发起；没有候选时为空）。
 *
 * @param row 屏生产状态
 * @returns 最新候选 id
 */
export function latestGenerationOf(row: ScreenProductionVO): string | number | undefined {
  return row.latestGenerationId;
}

/**
 * 屏生产状态的标签样式。
 *
 * @param status 状态码
 * @returns Element Plus 的 tag type
 */
export function screenStatusType(status?: string): TagType {
  switch (status) {
    case 'APPROVED':
      return 'success';
    case 'GENERATED':
      return 'primary';
    case 'GENERATING':
      return 'warning';
    case 'REJECTED':
      return 'danger';
    default:
      return 'info';
  }
}

/**
 * 屏生产状态的中文名。
 *
 * @param status 状态码
 * @returns 文本
 */
export function screenStatusText(status?: string): string {
  return (status && SCREEN_STATUS_LABELS[status]) || status || '';
}

/**
 * 最新候选状态的中文名（空值显示破折号——"没有候选"不是"某个状态"）。
 *
 * @param status 状态码
 * @returns 文本
 */
export function latestStatusText(status?: string): string {
  return (status && GENERATION_STATUS_LABELS[status]) || status || '—';
}

/**
 * 质检结论的中文名（空值一律「未质检」，绝不当成通过）。
 *
 * @param verdict 结论码
 * @returns 文本
 */
export function qaVerdictText(verdict?: string): string {
  return (verdict && QA_VERDICT_LABELS[verdict]) || verdict || '未质检';
}
