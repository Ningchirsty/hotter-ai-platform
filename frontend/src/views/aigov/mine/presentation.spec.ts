import { describe, expect, it } from 'vitest';
import type { AigCallApprovalVO } from '@/api/aigov/approval/types';
import type { AigUserQuotaUsageVO } from '@/api/aigov/quota/types';
import { approvalStatusMeta, grantActive, quotaSummary, remainingOf, renewable, renewalHint } from './presentation';

/**
 * 员工自助「我的额度 / 我的申请」的展示口径（Q5）。
 *
 * <p>重点钉两件：①**"读不到"不能显示成"没有"**（额度读不到说 0 会把人推去申请一个本来有的额度）；
 * ②**"批过"与"还管用"要分开**（已批准但已过期的授权看起来最像"还能用"）。</p>
 */
describe('mine presentation', () => {
  const NOW = new Date('2026-10-16T10:00:00Z').getTime();

  it('★ 剩余次数取日/月里最紧的那条；不限或读不到返回 null', () => {
    expect(remainingOf({ dailyLimit: 10, dailyUsed: 8, monthlyLimit: 100, monthlyUsed: 95 })).toBe(2);
    expect(remainingOf({ dailyLimit: 10, dailyUsed: 8, monthlyLimit: 100, monthlyUsed: 20 })).toBe(2);
    expect(remainingOf({ dailyLimit: 10, dailyUsed: 8, monthlyLimit: null, monthlyUsed: 20 })).toBe(2);
    expect(remainingOf({ dailyLimit: null, monthlyLimit: null })).toBeNull();
    expect(remainingOf(undefined)).toBeNull();
    // 已超限不能给出负数（否则看起来像"倒欠"）
    expect(remainingOf({ dailyLimit: 5, dailyUsed: 9, monthlyLimit: null })).toBe(0);
  });

  it('★ 额度四态：正常 / 已超限 / 不限 / 读不到（读不到绝不能说成"没有额度"）', () => {
    const ok: AigUserQuotaUsageVO = { limited: true, exceeded: false, dailyLimit: 10, dailyUsed: 8, monthlyLimit: null, monthlyUsed: 3 };
    expect(quotaSummary(ok).tone).toBe('ok');
    expect(quotaSummary(ok).headline).toContain('还能调用 2 次');
    expect(quotaSummary(ok).lines.join(' ')).toContain('不设上限');

    const exceeded: AigUserQuotaUsageVO = { limited: true, exceeded: true, dailyLimit: 5, dailyUsed: 5, monthlyLimit: 100, monthlyUsed: 20 };
    expect(quotaSummary(exceeded).tone).toBe('exceeded');
    expect(quotaSummary(exceeded).headline).toContain('额度已用完');
    expect(quotaSummary(exceeded).headline).toContain('自动恢复');

    const unlimited: AigUserQuotaUsageVO = { limited: false, exceeded: false, dailyLimit: null, monthlyLimit: null, dailyUsed: 3, monthlyUsed: 9 };
    expect(quotaSummary(unlimited).tone).toBe('unlimited');
    expect(quotaSummary(unlimited).headline).toContain('没有生效中的');

    const unknown = quotaSummary(undefined);
    expect(unknown.tone).toBe('unknown');
    expect(unknown.headline).toContain('读取失败');
    expect(unknown.headline).toContain('不等于"没有额度"');
    expect(unknown.lines).toEqual([]);
  });

  it('明细带上周期起点（"今天"的起止要能核对）', () => {
    const summary = quotaSummary({
      dailyLimit: 10, dailyUsed: 1, dailyFrom: '2026-10-16 00:00:00',
      monthlyLimit: 100, monthlyUsed: 5, limited: true, exceeded: false
    });
    expect(summary.lines.join(' ')).toContain('今日统计起点：2026-10-16 00:00:00');
    expect(summary.lines[1]).toContain('本月：5 次 / 上限 100');
  });

  it('★ 授权"批过"≠"还管用"：已批准但已过期要说清', () => {
    const active: AigCallApprovalVO = { status: 'APPROVED', validUntil: '2026-10-17T00:00:00Z' };
    const stale: AigCallApprovalVO = { status: 'APPROVED', validUntil: '2026-10-15T00:00:00Z' };
    expect(grantActive(active, NOW)).toBe(true);
    expect(grantActive(stale, NOW)).toBe(false);
    expect(grantActive({ status: 'PENDING' }, NOW)).toBe(false);
    // 无效时间字符串不能被当成"有效"
    expect(grantActive({ status: 'APPROVED', validUntil: 'not-a-date' }, NOW)).toBe(false);

    expect(approvalStatusMeta(active, NOW)).toEqual({ label: '授权有效中', type: 'success' });
    expect(approvalStatusMeta(stale, NOW).label).toContain('已过期');
    expect(approvalStatusMeta(stale, NOW).type).toBe('info');
  });

  it('状态标签五态 + 认不出的原样回显（不硬编"未知成功"）', () => {
    expect(approvalStatusMeta({ status: 'PENDING' }).label).toBe('待审批');
    expect(approvalStatusMeta({ status: 'REJECTED' }).type).toBe('danger');
    expect(approvalStatusMeta({ status: 'EXPIRED' }).label).toContain('已超时');
    expect(approvalStatusMeta({ status: 'CANCELLED' }).label).toBe('已撤回');
    expect(approvalStatusMeta({ status: 'SOMETHING_NEW' }).label).toBe('SOMETHING_NEW');
    expect(approvalStatusMeta(undefined).type).toBe('gray');
  });

  it('★ 续期提示：续期=新单（旧单不会延长）；无申请权限要说清找谁', () => {
    expect(renewalHint({ status: 'APPROVED', validUntil: '2026-11-01T00:00:00Z' })).toContain('续期=新单');
    expect(renewalHint({ status: 'PENDING' })).toContain('无需重复提交');
    expect(renewalHint({ status: 'REJECTED' }, false)).toContain('aig:approval:apply');
    expect(renewalHint({ status: 'REJECTED' }, true)).toContain('重新提交一张申请');
    expect(renewalHint({ status: 'CANCELLED' })).toContain('撤回');
  });

  it('★ 只有"现在没有有效授权"的行才给重新申请（待审批不该重复提交）', () => {
    expect(renewable({ status: 'REJECTED' }, NOW)).toBe(true);
    expect(renewable({ status: 'EXPIRED' }, NOW)).toBe(true);
    expect(renewable({ status: 'CANCELLED' }, NOW)).toBe(true);
    expect(renewable({ status: 'APPROVED', validUntil: '2026-10-15T00:00:00Z' }, NOW)).toBe(true);
    expect(renewable({ status: 'APPROVED', validUntil: '2026-11-01T00:00:00Z' }, NOW)).toBe(false);
    expect(renewable({ status: 'PENDING' }, NOW)).toBe(false);
    expect(renewable(undefined, NOW)).toBe(false);
  });
});
