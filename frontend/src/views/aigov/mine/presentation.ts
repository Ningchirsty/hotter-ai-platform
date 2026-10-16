import type { AigCallApprovalVO } from '@/api/aigov/approval/types';
import type { AigUserQuotaUsageVO } from '@/api/aigov/quota/types';

/**
 * 员工自助「我的额度 / 我的申请」的展示口径（Q5）。
 *
 * <p><b>为什么这两个数必须说清"未知"</b>：额度与授权都是"被拦下来时才想起来看"的东西。
 * 如果读不到就显示 0 / 空表，人得到的是"我没有额度""我没有申请过"——两句话都是错的，
 * 而且会把人推向错误的处置（去申请一个其实已经有的授权）。所以这里把
 * <b>不限 / 已超限 / 还剩 N / 读不到</b> 分成四种状态。</p>
 */

/** 额度状态：ok 正常有余量 / exceeded 已超限 / unlimited 不限 / unknown 读不到 */
export type QuotaTone = 'ok' | 'exceeded' | 'unlimited' | 'unknown';

/** 额度摘要 */
export interface QuotaSummary {
  tone: QuotaTone;
  /** 一句话结论（页面顶部直接用） */
  headline: string;
  /** 逐行明细（日/月/周期起点） */
  lines: string[];
}

/** 标签元数据 */
export interface TagMeta {
  label: string;
  type: 'primary' | 'success' | 'warning' | 'danger' | 'info' | 'gray';
}

/**
 * 计算"最紧的剩余次数"：日/月里取更小的那个，已超限时下限 0，全不限时为空。
 *
 * @param usage 用量视图（可空）
 * @returns 剩余次数；无限额或读不到返回 null
 */
export function remainingOf(usage?: AigUserQuotaUsageVO): number | null {
  if (!usage) {
    return null;
  }
  let remaining: number | null = null;
  if (usage.dailyLimit !== null && usage.dailyLimit !== undefined) {
    remaining = Math.max(0, usage.dailyLimit - (usage.dailyUsed ?? 0));
  }
  if (usage.monthlyLimit !== null && usage.monthlyLimit !== undefined) {
    const monthly = Math.max(0, usage.monthlyLimit - (usage.monthlyUsed ?? 0));
    remaining = remaining === null ? monthly : Math.min(remaining, monthly);
  }
  return remaining;
}

/**
 * 额度摘要（四态 + 明细）。
 *
 * @param usage 用量视图（可空 = 读不到）
 * @returns 摘要
 */
export function quotaSummary(usage?: AigUserQuotaUsageVO): QuotaSummary {
  if (!usage) {
    return {
      tone: 'unknown',
      headline: '额度读取失败：现在无法判断你还能调几次（这不等于"没有额度"）',
      lines: []
    };
  }
  const lines = [
    `今日：${usage.dailyUsed ?? 0} 次${usage.dailyLimit === null || usage.dailyLimit === undefined ? '（不设上限）' : ` / 上限 ${usage.dailyLimit}`}`,
    `本月：${usage.monthlyUsed ?? 0} 次${usage.monthlyLimit === null || usage.monthlyLimit === undefined ? '（不设上限）' : ` / 上限 ${usage.monthlyLimit}`}`
  ];
  if (usage.dailyFrom) {
    lines.push(`今日统计起点：${usage.dailyFrom}`);
  }
  if (!usage.limited) {
    return { tone: 'unlimited', headline: '当前对你没有生效中的调用次数限制', lines };
  }
  if (usage.exceeded) {
    return { tone: 'exceeded', headline: '额度已用完：现在的调用会被拦下（等到下一个自然日/自然月自动恢复）', lines };
  }
  return { tone: 'ok', headline: `还能调用 ${remainingOf(usage) ?? '—'} 次（按日/月里最紧的那条算）`, lines };
}

/**
 * 授权是否**现在**仍管用（"批过"与"还管用"是两件事）。
 *
 * @param row 审批单
 * @param now 当前时间（默认取系统时间；便于测试注入）
 * @returns 仍有效返回 true
 */
export function grantActive(row?: AigCallApprovalVO, now: number = Date.now()): boolean {
  if (!row || row.status !== 'APPROVED' || !row.validUntil) {
    return false;
  }
  const until = new Date(row.validUntil).getTime();
  return Number.isFinite(until) && until > now;
}

/**
 * 审批状态标签（含「已批准但已过期」这一态——它最容易看起来像"授权还在"）。
 *
 * @param row 审批单
 * @param now 当前时间
 * @returns 标签
 */
export function approvalStatusMeta(row?: AigCallApprovalVO, now: number = Date.now()): TagMeta {
  const status = row?.status;
  if (status === 'PENDING') {
    return { label: '待审批', type: 'warning' };
  }
  if (status === 'APPROVED') {
    return grantActive(row, now)
      ? { label: '授权有效中', type: 'success' }
      : { label: '已批准但已过期', type: 'info' };
  }
  if (status === 'REJECTED') {
    return { label: '已驳回', type: 'danger' };
  }
  if (status === 'EXPIRED') {
    return { label: '已超时未批', type: 'info' };
  }
  if (status === 'CANCELLED') {
    return { label: '已撤回', type: 'gray' };
  }
  return { label: status || '未知状态', type: 'gray' };
}

/**
 * 这一行是否值得提供「重新申请」（续期=新单）。
 *
 * <p>被驳回 / 已超时未批 / 自己撤回 / 已批准但已过期 —— 这四种都是"现在没有有效授权"，
 * 提供重新申请是合理的；<b>待审批</b>不该重复提交（会多出一张一样的单子）；
 * <b>授权仍在有效期内</b>更不该（重复单只会打扰审批人）。</p>
 *
 * @param row 审批单
 * @param now 当前时间
 * @returns 可重新申请返回 true
 */
export function renewable(row?: AigCallApprovalVO, now: number = Date.now()): boolean {
  if (!row) {
    return false;
  }
  if (row.status === 'REJECTED' || row.status === 'EXPIRED' || row.status === 'CANCELLED') {
    return true;
  }
  return row.status === 'APPROVED' && !grantActive(row, now);
}

/**
 * 续期（重新申请）提示：说清"怎么续"与"续期不是把旧单延期"。
 *
 * @param row 审批单
 * @param canApply 当前账号是否有「提交调用授权申请」权限
 * @returns 提示文案
 */
export function renewalHint(row?: AigCallApprovalVO, canApply = false): string {
  const status = row?.status;
  if (status === 'PENDING') {
    return '这张单还在待审批；无需重复提交，可催审批人（治理台「调用授权」页）。';
  }
  if (status === 'APPROVED' && row?.validUntil) {
    return `有效期止 ${row.validUntil}。到期后需要重新申请一张（续期=新单，旧单不会被延长）。`;
  }
  if (status === 'CANCELLED') {
    return '这张单是你自己撤回的；如需继续使用，重新提交一张申请。';
  }
  if (status === 'REJECTED' || status === 'EXPIRED') {
    return canApply
      ? '可以按下面的参数重新提交一张申请（记得把上次被驳回的原因补上）。'
      : '重新申请需要「提交调用授权申请」权限（aig:approval:apply），请联系 AI 治理管理员。';
  }
  return canApply ? '如需再次使用，可重新提交一张申请。' : '如需申请，请联系 AI 治理管理员（需要 aig:approval:apply 权限）。';
}
