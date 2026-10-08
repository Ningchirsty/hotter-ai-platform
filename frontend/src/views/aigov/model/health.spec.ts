import { describe, expect, it } from 'vitest';
import { describeHealth, isExcludedByHealth } from './health';

/**
 * 守的是 M-003 ①：展示口径必须与后端**路由判定**一致。
 *
 * 原实现是 `healthStatus === 'HEALTHY' ? '连通' : '不通'`，把 `UNKNOWN`
 * 也显示成「不通」——而路由对 UNKNOWN 是**放行**的。展示与真实行为相反，
 * 比不显示更坏：运维会以为该模型已被排除，实际它仍在被选中。
 */
describe('模型连通性展示口径', () => {
  it('HEALTHY 显示「连通」', () => {
    expect(describeHealth('HEALTHY')).toEqual({ label: '连通', tone: 'success' });
  });

  it('★ UNHEALTHY / DOWN 才显示「不通」（这两个才是路由真正排除的）', () => {
    expect(describeHealth('UNHEALTHY')).toEqual({ label: '不通', tone: 'danger' });
    expect(describeHealth('DOWN')).toEqual({ label: '不通', tone: 'danger' });
  });

  it('★ UNKNOWN 必须显示「未测」而不是「不通」——路由仍会放行它', () => {
    const shown = describeHealth('UNKNOWN');
    expect(shown.label).toBe('未测');
    expect(shown.label).not.toBe('不通');
    expect(shown.tone).toBe('info');
    // 与"会不会被排除"相互印证：UNKNOWN 不该被排除
    expect(isExcludedByHealth('UNKNOWN')).toBe(false);
  });

  it('★ 空值（从未测过）显示「未测试」且不被排除', () => {
    for (const v of [undefined, null, '', '   ']) {
      expect(describeHealth(v as string).label).toBe('未测试');
      expect(isExcludedByHealth(v as string)).toBe(false);
    }
  });

  it('大小写与两侧空白不影响判定（数据录入格式变化不该让展示变错）', () => {
    expect(describeHealth(' unhealthy ').label).toBe('不通');
    expect(describeHealth('unknown').label).toBe('未测');
  });

  it('兼容旧词汇 UP（历史数据/文档曾用 UP/DOWN/DEGRADED）', () => {
    expect(describeHealth('UP').label).toBe('连通');
  });

  it('★ 未识别的新词汇原样露出，不得猜成「不通」', () => {
    const shown = describeHealth('MAINTENANCE');
    expect(shown.label).toBe('MAINTENANCE');
    expect(shown.tone).toBe('info');
    expect(isExcludedByHealth('MAINTENANCE')).toBe(false);
  });

  it('DEGRADED 不被当作不通（后端只排除 DOWN/UNHEALTHY）', () => {
    expect(isExcludedByHealth('DEGRADED')).toBe(false);
    expect(describeHealth('DEGRADED').label).not.toBe('不通');
  });
});
