import { describe, expect, it } from 'vitest';
import { formatStepHeading } from './stepNumbering';

/**
 * 「本页步骤编号」的回归钉子（v1 人工测试反馈）。
 *
 * <p>反馈的原始症状：出图区块显示成「7.」（全局流程第 7 步），
 * 而事实确认(4) 排在文案与要点(3) 前面——同一个页面上编号既跨页又乱序。
 * 这里钉住的新口径：编号只回答"在这个页面上按什么顺序看"。</p>
 *
 * @author creative
 */
describe('formatStepHeading', () => {
  it('一步一块时是「2. 」', () => {
    expect(formatStepHeading(2, 1, 1)).toBe('2. ');
  });

  it('一步多块时是子号「2.1 」「2.2 」', () => {
    expect(formatStepHeading(2, 2, 1)).toBe('2.1 ');
    expect(formatStepHeading(2, 2, 2)).toBe('2.2 ');
  });

  it('没有上下文（no=0）时不编号——宁可没号，也不要编一个可能错的号', () => {
    expect(formatStepHeading(0, 0, 0)).toBe('');
    expect(formatStepHeading(0, 2, 1)).toBe('');
  });

  it('一步多块但本区块不在这一步里：退回「2. 」，不显示「2.0 」', () => {
    expect(formatStepHeading(2, 3, 0)).toBe('2. ');
  });

  it('负数/小数防御：非正整数号一律不编号', () => {
    expect(formatStepHeading(-1, 2, 1)).toBe('');
  });
});
