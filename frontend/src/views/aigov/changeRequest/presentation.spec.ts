import { describe, expect, it } from 'vitest';
import type { AigChangeRequestVO } from '@/api/aigov/changeRequest/types';
import { outcomeText, parse, snapshotLines, statusMeta, typeMeta } from './presentation';

/**
 * 变更单展示口径（Q1C 完整版）。
 *
 * <p>重点钉两件：①**CONFLICTED ≠ REJECTED**（系统拒绝执行 vs 审批人不同意，处置方向不同）；
 * ②快照对比要说清"从什么改成了什么"，否则审批人等于在盲批。</p>
 */
describe('change request presentation', () => {
  it('★ 状态：冲突未生效与已驳回必须分开（一个找系统、一个找审批人）', () => {
    expect(statusMeta('PENDING').type).toBe('warning');
    expect(statusMeta('APPLIED').type).toBe('success');
    expect(statusMeta('REJECTED').type).toBe('danger');
    expect(statusMeta('CONFLICTED').label).toContain('冲突');
    expect(statusMeta('CONFLICTED').type).not.toBe('danger');
    expect(statusMeta('SOMETHING').label).toBe('SOMETHING');
    expect(statusMeta(undefined).type).toBe('gray');
  });

  it('变更类型三类 + 认不出原样回显', () => {
    expect(typeMeta('POLICY_CREATE').label).toBe('新增策略');
    expect(typeMeta('POLICY_UPDATE').type).toBe('warning');
    expect(typeMeta('POLICY_DELETE').type).toBe('danger');
    expect(typeMeta('X').label).toBe('X');
  });

  it('★ 快照对比：只列改动的键，格式是"旧 → 新"（审批人要一眼看出批的是什么）', () => {
    const before = '{"dataLevel":"INTERNAL","allowExternal":"N","requireApproval":"N"}';
    const after = '{"dataLevel":"INTERNAL","allowExternal":"Y","requireApproval":"N"}';

    const changed = snapshotLines(after, before);
    expect(changed).toEqual(['allowExternal: N → Y']);

    // 不传 before 时全列（用于"新增策略"这种没有旧值的情况）
    expect(snapshotLines(after)).toHaveLength(3);
  });

  it('快照解析失败不抛错（页面另外显示原始文本）', () => {
    expect(parse('{not json')).toEqual({});
    expect(parse(undefined)).toEqual({});
    expect(parse('{"a":1}')).toEqual({ a: 1 });
    expect(snapshotLines('{not json')).toEqual([]);
  });

  it('★ 一行结论：已生效带时间与批准人；冲突要说"未生效"', () => {
    expect(outcomeText({ status: 'APPLIED', appliedAt: '2026-10-16 10:00', approverName: 'lisi' }))
      .toContain('已于 2026-10-16 10:00 生效');
    expect(outcomeText({ status: 'CONFLICTED', resultRemark: '策略行已被他人改动' }))
      .toContain('未生效');
    expect(outcomeText({ status: 'REJECTED', decisionRemark: '理由不充分' })).toContain('理由不充分');
    expect(outcomeText({ status: 'PENDING' })).toContain('等待');
    expect(outcomeText(undefined)).toBe('');
  });
});
